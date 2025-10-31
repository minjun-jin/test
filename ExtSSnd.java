package com.jpmc.kcg.ext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.slf4j.MDC;

import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ExtSSnd implements Runnable {

	private final Properties properties;
	private final ExecutorService executorService;
	private final Map<String, BlockingQueue<Entry<String, String>>> blckQueMap;
	private final Map<String, Entry<String, String>> ssnSttsMap;
	private final Map<String, LocalDateTime> sndDttmMap;
	private final String propertyName;

	@Override
	public void run() {
		Entry<String, String> ssnStts = ssnSttsMap.get(propertyName);
		boolean isEcho = Boolean.getBoolean("echo");
		boolean isTest = StringUtils.endsWithAny(propertyName, "_S", "_SS", "_SSS", "_SIM");
		long nWait = NumberUtils.toLong(properties.getProperty(StringUtils.join(StringUtils.left(StringUtils.replace(propertyName, "SSND_", "TSND_"), 8), "_W")));
		int tryMax = isTest ? 5 : // 시뮬레이터타임아웃강제보정
		NumberUtils.toInt(properties.getProperty(StringUtils.join(StringUtils.left(StringUtils.replace(propertyName, "SSND_", "TSND_"), 8), "_X")));
		String rcvBlckQueNm = StringUtils.left(StringUtils.replace(propertyName, "SSND_", "QRCV_"), 8);
		String sndBlckQueNm = isTest ? StringUtils.left(propertyName, 8) : // 시뮬레이터전송큐강제보정
		StringUtils.left(StringUtils.replace(propertyName, "SSND_", "QSND_"), 8);
		BlockingQueue<Entry<String, String>> rcvBlckQue = blckQueMap.get(rcvBlckQueNm);
		BlockingQueue<Entry<String, String>> sndBlckQue = blckQueMap.get(sndBlckQueNm);
		String[] stringArray = StringUtils.split(properties.getProperty(propertyName), ':');
		String host = stringArray[0];
		int port = NumberUtils.toInt(stringArray[1]);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		MDC.put("key", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")));
		Thread.currentThread().setName(propertyName);
		log.info("start");
		AtomicInteger atomicInteger = new AtomicInteger(0);
		while (!executorService.isShutdown()) {
			ssnStts.setValue("0");
			try (Socket socket = EXUtils.newSocket(host, port)) {
				socket.setKeepAlive(true);
				socket.setReuseAddress(true);
				socket.setSoLinger(true, 1);
				socket.setSoTimeout(1000);
				socket.setTcpNoDelay(true);
				log.info("{}", socket);
				ssnStts.setValue("1");
				try (InputStream inputStream = socket.getInputStream()) {
					executorService.execute(() -> {
						MDC.put("key", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")));
						Thread.currentThread().setName(propertyName);
						Entry<String, String> entry = null;
						try (OutputStream outputStream = socket.getOutputStream()) {
							int tryCnt = 0;
							while (!executorService.isShutdown() || !sndBlckQue.isEmpty()) {
								if (socket.isClosed()) {
									break;
								}
								entry = sndBlckQue.poll(1000L, TimeUnit.MILLISECONDS);
								if (null == entry) {
									tryCnt++;
									if ((tryMax - 1) > tryCnt) {
										continue;
									}
									tryCnt = 0;
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////// 회선시험 ///////////////////////////////////
									String tlgCtt = StringUtils.join("0020HDRREQPOLL", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")));
									log.trace(">{}]", tlgCtt);
									IOUtils.write(tlgCtt, outputStream, "EUC-KR");
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
									continue;
								}
								tryCnt = 0;
								atomicInteger.set(0);
								String tlgCtt = entry.getKey();
								log.info(">{}]", tlgCtt);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////// 타행이체보정 ///////////////////////////////////
								if (StringUtils.contains(propertyName, "_HOF_") && 0L < nWait &&
									StringUtils.endsWithAny(StringUtils.left(tlgCtt, 20), "ELB0200400000", "ELB0400400000")) { // 타행이체 | 타행이체취소
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
									if (isEcho &&
										isTest) {
										StringBuilder sb = new StringBuilder(tlgCtt);
										sb.setCharAt(12, '1');
										sb.setCharAt(20, '2');
										sb.replace(24, 27, "000");
										tlgCtt = sb.toString();
										log.info("<{}]", tlgCtt);
										int i = Message.DEFAULT_PRIORITY;
										EXUtils.write(i, rcvBlckQueNm, tlgCtt);
										rcvBlckQue.put(Map.entry(tlgCtt, String.valueOf(i)));
										continue;
									}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
									String key = StringUtils.join(StringUtils.substring(tlgCtt, 70, 73), // 개설기관대표코드
									StringUtils.substring(tlgCtt, 80, 96)); // 수취계좌번호
									LocalDateTime sndDttm = sndDttmMap.get(key);
									if (ObjectUtils.isNotEmpty(sndDttm)) {
										long l = nWait - ChronoUnit.MILLIS.between(sndDttm, LocalDateTime.now());
										if (0 < l) {
											ThreadUtils.sleepQuietly(Duration.ofMillis(l));
										}
									}
									sndDttmMap.put(key, LocalDateTime.now());
								}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
								byte[] byteArray = StringUtils.getBytes(tlgCtt, "EUC-KR");
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////// 헤더보정 ///////////////////////////////////////
								byte[] tempArray = StringUtils.getBytes(StringUtils.join(StringUtils.leftPad(String.valueOf(byteArray.length - 4), 4, '0'), "HDR"), StandardCharsets.US_ASCII);
								System.arraycopy(tempArray, 0, byteArray, 0, tempArray.length);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
								IOUtils.write(byteArray, outputStream);
								EXUtils.write(0, sndBlckQueNm, tlgCtt);
								entry = null;
							}
						} catch (Throwable t) {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////// 장애전문복원 ///////////////////////////////////////
							if (null != entry) {
								sndBlckQue.add(Map.entry(entry.getKey(), String.valueOf(9)));
							}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
							log.error(ExceptionUtils.getRootCauseMessage(t), t);
						} finally {
							IOUtils.closeQuietly(socket);
						}
					});
//					int tryCnt = 0;
					atomicInteger.set(0);
					while (!executorService.isShutdown() || !sndBlckQue.isEmpty()) {
						if (socket.isClosed()) {
							break;
						}
						byte[] byteArray = null;
						try {
							byteArray = IOUtils.toByteArray(inputStream, 7);
						} catch (SocketTimeoutException e) {
//							tryCnt++;
//							if ((tryMax + 9) < tryCnt) {
							if ((tryMax + 9) < atomicInteger.incrementAndGet()) {
								throw e;
							}
							continue;
						}
//						tryCnt = 0;
						atomicInteger.set(0);
						String tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
						if (!StringUtils.isNumeric(StringUtils.left(tlgCtt, 4))) {
							throw new IOException(tlgCtt);
						}
						try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
							socket.setSoTimeout(9999); // 네트워크대역폭한계보정
							IOUtils.write(byteArray, byteArrayOutputStream);
							byteArray = IOUtils.toByteArray(inputStream, NumberUtils.toInt(StringUtils.left(tlgCtt, 4)) - 3);
							IOUtils.write(byteArray, byteArrayOutputStream);
							byteArray = byteArrayOutputStream.toByteArray();
						} finally {
							socket.setSoTimeout(1000); // 네트워크대역폭한계보정
						}
						tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 회선시험 ///////////////////////////////////////////////
						if (StringUtils.startsWith(tlgCtt, "0020HDRRESPOLL")) {
							log.trace("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
							continue;
						}
						throw new IOException(tlgCtt);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
					}
				}
			} catch (Throwable t) {
				if (t instanceof ConnectException) {
					log.error(ExceptionUtils.getRootCauseMessage(t));
				} else if (!executorService.isShutdown()) {
					log.error(ExceptionUtils.getRootCauseMessage(t), t);
				}
			}
			ssnStts.setValue("0");
			ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
		}
		log.info("stop");
	}

}

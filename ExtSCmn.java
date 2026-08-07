package com.jpmc.kcg.ext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.MDC;

import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ExtSCmn implements Runnable {

	private final Properties properties;
	private final ExecutorService executorService;
	private final Map<String, BlockingQueue<Entry<String, String>>> blckQueMap;
	private final Map<String, Entry<String, String>> ssnSttsMap;
	private final String propertyName;
	private String msgNbr;

	@Override
	public void run() {
		Entry<String, String> ssnStts = ssnSttsMap.get(propertyName);
		boolean isTest = Strings.CS.endsWithAny(propertyName, "_S", "_SS", "_SSS", "_SIM");
		int tryMax = isTest ? 5 : // 시뮬레이터타임아웃강제보정
		NumberUtils.toInt(properties.getProperty(StringUtils.join(StringUtils.left(Strings.CS.replace(propertyName, "SCMN_", "TCMN_"), 8), "_X")));
		String rcvBlckQueNm = StringUtils.left(Strings.CS.replace(propertyName, "SCMN_", "QRCV_"), 8);
		String sndBlckQueNm = StringUtils.left(Strings.CS.replace(propertyName, "SCMN_", isTest ? "SSND_" : "QSND_"), 8); // 시뮬레이터전송큐강제보정
		BlockingQueue<Entry<String, String>> rcvBlckQue = blckQueMap.get(rcvBlckQueNm);
		BlockingQueue<Entry<String, String>> sndBlckQue = blckQueMap.get(sndBlckQueNm);
		String[] stringArray = StringUtils.split(properties.getProperty(propertyName), ':');
		String host = stringArray[0];
		int port = NumberUtils.toInt(stringArray[1]);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		MDC.put("key", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")));
		Thread.currentThread().setName(Strings.CS.replace(propertyName, "SCMN_", "SRCV_"));
		log.info("start");
		AtomicInteger atomicInteger = new AtomicInteger(0);
		SocketFactory socketFactory = SocketFactory.getDefault();
		while (!executorService.isShutdown()) {
			ssnStts.setValue("0");
			try (Socket socket = socketFactory.createSocket(host, port)) {
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
						Thread.currentThread().setName(Strings.CS.replace(propertyName, "SCMN_", "SSND_"));
						log.info("start");
						Entry<String, String> entry = null;
						try (OutputStream outputStream = socket.getOutputStream()) {
							int tryCnt = 0;
							while (!executorService.isShutdown() || ObjectUtils.isNotEmpty(sndBlckQue)) {
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
									String tlgCtt;
									if (isTest) {
										tlgCtt = StringUtils.join("0020HDRREQPOLL", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")));
									} else if (Strings.CS.contains(propertyName, "_ENT_")) { // 회선시험
										msgNbr = StringUtils.join("99",
										LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmssSSS"))); // 11
										tlgCtt = EXUtils.newTestEnt(StringUtils.left(msgNbr, 11));
									} else if (Strings.CS.contains(propertyName, "_ATI_")) { // 회선시험
										msgNbr = StringUtils.join("05709",
										LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmssSSS"))); // 12
										tlgCtt = EXUtils.newTestAti(StringUtils.left(msgNbr, 12));
									} else if (Strings.CS.contains(propertyName, "_GRO_")) { // 회선시험
										msgNbr = StringUtils.join("0579",
										LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmssSSS"))); // 10
										tlgCtt = EXUtils.newTestGro(StringUtils.left(msgNbr, 10));
									} else {
										continue;
									}
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
								byte[] byteArray = StringUtils.getBytes(tlgCtt, "EUC-KR");
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////// 헤더보정 ///////////////////////////////////////
								byte[] tempArray = StringUtils.getBytes(StringUtils.leftPad(String.valueOf(byteArray.length - 4), 4, '0'), "EUC-KR");
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
						log.info("stop");
					});
//					int tryCnt = 0;
					atomicInteger.set(0);
					while (!executorService.isShutdown() || ObjectUtils.isNotEmpty(sndBlckQue)) {
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
						if (Strings.CS.startsWith(tlgCtt, "0020HDRRESPOLL") ||
							Strings.CS.contains(propertyName, "_ENT_") && Strings.CS.endsWith(StringUtils.left(tlgCtt, 31), msgNbr) ||
							Strings.CS.contains(propertyName, "_ATI_") && Strings.CS.endsWith(StringUtils.left(tlgCtt, 55), msgNbr) ||
							Strings.CS.contains(propertyName, "_GRO_") && Strings.CS.endsWith(StringUtils.left(tlgCtt, 49), msgNbr)) { // 회선시험
							log.trace("<{}]", tlgCtt);
							continue;
						}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						log.info("<{}]", tlgCtt);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 시뮬레이터전문강제보정 /////////////////////////////////
						if (isTest) {
							tlgCtt = StringUtils.join(StringUtils.left(tlgCtt, 4), "SIM",
							StringUtils.substring(tlgCtt, 7));
						}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						int i = Message.DEFAULT_PRIORITY;
						EXUtils.write(i, rcvBlckQueNm, tlgCtt);
						rcvBlckQue.put(Map.entry(tlgCtt, String.valueOf(i)));
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

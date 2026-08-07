package com.jpmc.kcg.ext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import javax.net.ServerSocketFactory;

import org.apache.commons.io.IOUtils;
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
public class ExtSRcv implements Runnable {

	private final Properties properties;
	private final ExecutorService executorService;
	private final Map<String, BlockingQueue<Entry<String, String>>> blckQueMap;
	private final Map<String, Entry<String, String>> ssnSttsMap;
	private final String propertyName;

	@Override
	public void run() {
		Entry<String, String> ssnStts = ssnSttsMap.get(propertyName);
		boolean isTest = Strings.CS.endsWithAny(propertyName, "_S", "_SS", "_SSS", "_SIM");
		int tryMax = isTest ? 5 : // 시뮬레이터타임아웃강제보정
		NumberUtils.toInt(properties.getProperty(StringUtils.join(StringUtils.left(Strings.CS.replace(propertyName, "SRCV_", "TRCV_"), 8), "_X")));
		String rcvBlckQueNm = StringUtils.left(Strings.CS.replace(propertyName, "SRCV_", "QRCV_"), 8);
		String sndBlckQueNm = StringUtils.left(Strings.CS.replace(propertyName, "SRCV_", isTest ? "SSND_" : "QSND_"), 8);
		String arsBlckQueNm = "QRCV_ARS";
		BlockingQueue<Entry<String, String>> rcvBlckQue = blckQueMap.get(rcvBlckQueNm);
		BlockingQueue<Entry<String, String>> sndBlckQue = blckQueMap.get(sndBlckQueNm);
		BlockingQueue<Entry<String, String>> arsBlckQue = blckQueMap.get(arsBlckQueNm);
		int port = NumberUtils.toInt(properties.getProperty(propertyName));
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		MDC.put("key", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")));
		Thread.currentThread().setName(propertyName);
		log.info("start");
		Socket socket0 = null;
		ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();
		while (!executorService.isShutdown()) {
			ssnStts.setValue("0");
			try (ServerSocket serverSocket = serverSocketFactory.createServerSocket(port)) {
				serverSocket.setReuseAddress(true);
				serverSocket.setSoTimeout(1000);
				while (!executorService.isShutdown()) {
					Socket socket;
					try {
						socket = serverSocket.accept();
						socket.setKeepAlive(true);
						socket.setReuseAddress(true);
						socket.setSoLinger(true, 1);
						socket.setSoTimeout(1000);
						socket.setTcpNoDelay(true);
						IOUtils.closeQuietly(socket0);
						socket0 = socket;
					} catch (SocketTimeoutException e) {
						continue;
					}
					log.info("{}", socket);
					ssnStts.setValue("1");
					executorService.execute(() -> {
						MDC.put("key", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")));
						Thread.currentThread().setName(propertyName);
						ssnStts.setValue("1");
						try (InputStream inputStream = socket.getInputStream();
							OutputStream outputStream = socket.getOutputStream()) {
							int tryCnt = 0;
							while (!executorService.isShutdown()) {
								ssnStts.setValue("1");
								byte[] byteArray = null;
								try {
									byteArray = IOUtils.toByteArray(inputStream, 7);
								} catch (SocketTimeoutException e) {
									tryCnt++;
									if ((tryMax + 9) < tryCnt) {
										throw e;
									}
									continue;
								}
								tryCnt = 0;
								String tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
								if (!StringUtils.isNumeric(StringUtils.left(tlgCtt, 4)) ||
									!Strings.CS.endsWith(tlgCtt, Strings.CS.contains(propertyName, "_ATI_") ? "ATI" : "HDR")) {
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
/////////////////////////////// 회선시험 ///////////////////////////////////////
								if (Strings.CS.startsWith(tlgCtt, "0020HDRREQPOLL")) {
									log.trace("<{}]", tlgCtt);
									StringBuilder sb = new StringBuilder(tlgCtt);
									sb.setCharAt(9, 'S');
									tlgCtt = sb.toString();
									log.trace(">{}]", tlgCtt);
									IOUtils.write(tlgCtt, outputStream, "EUC-KR");
									continue;
								}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
								log.info("<{}]", tlgCtt);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////// 전자금융관리전문보정 ///////////////////////////
								if (Strings.CS.contains(propertyName, "_HOF_") &&
									Strings.CS.endsWith(StringUtils.left(tlgCtt, 20), "ELB0800301000")) { // 회선시험
									StringBuilder sb = new StringBuilder(tlgCtt);
									sb.replace(10, 14, "0810");
									sb.replace(20, 21, "3");
									sb.replace(24, 27, "000");
									tlgCtt = sb.toString();
									int i = 9; // 우선순위높게
									EXUtils.write(i, sndBlckQueNm, tlgCtt);
									sndBlckQue.put(Map.entry(tlgCtt, String.valueOf(i)));
									continue;
								}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////// 시뮬레이터전문강제보정 /////////////////////////
								if (isTest) {
									tlgCtt = StringUtils.join(StringUtils.left(tlgCtt, 4), "SIM",
									StringUtils.substring(tlgCtt, 7));
								}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////// 금융공동망강제보정 /////////////////////////////
								if (Strings.CS.endsWith(StringUtils.left(tlgCtt, 10), "ARS")) {
									int i = Message.DEFAULT_PRIORITY;
									EXUtils.write(i, arsBlckQueNm, tlgCtt);
									arsBlckQue.put(Map.entry(tlgCtt, String.valueOf(i)));
									continue;
								}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
								int i = Message.DEFAULT_PRIORITY;
								EXUtils.write(i, rcvBlckQueNm, tlgCtt);
								rcvBlckQue.put(Map.entry(tlgCtt, String.valueOf(i)));
							}
						} catch (Throwable t) {
							log.error(ExceptionUtils.getRootCauseMessage(t), t);
						} finally {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////// 서버종료대기 ///////////////////////////////////////
							if (executorService.isShutdown()) {
								while (!serverSocket.isClosed()) {
									ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
								}
							}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
							IOUtils.closeQuietly(socket);
						}
						ssnStts.setValue("0");
					});
				}
			} catch (Throwable t) {
				log.error(ExceptionUtils.getRootCauseMessage(t), t);
			}
			ssnStts.setValue("0");
			ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
		}
		log.info("stop");
	}

}

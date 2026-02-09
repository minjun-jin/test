package com.jpmc.kcg.ext;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.slf4j.MDC;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ExtFCmn implements Runnable {

	private final Properties properties;
	private final ExecutorService executorService;
	private final Map<String, Entry<String, String>> ssnSttsMap;
	private final String propertyName;

	@Override
	public void run() {
		Entry<String, String> ssnStts = ssnSttsMap.get(propertyName);
		File alog = FileUtils.getFile(properties.getProperty("PATH_ALOG", "/home/ec2-user/apps/main/kcg_ext/logs"));
		File back = FileUtils.getFile(properties.getProperty("PATH_BACK", "/apps/kcg/shrd/back"));
		File recv = FileUtils.getFile(properties.getProperty("PATH_RECV", "/apps/kcg/shrd/recv"));
		File send = FileUtils.getFile(properties.getProperty("PATH_SEND", "/apps/kcg/shrd/send"));
		int port = NumberUtils.toInt(properties.getProperty(propertyName));
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		MDC.put("key", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")));
		Thread.currentThread().setName(propertyName);
		log.info("start");
		ServerSocketFactory serverSocketFactory = SSLServerSocketFactory.getDefault();
		while (!executorService.isShutdown()) {
			ssnStts.setValue("0");
//			try (ServerSocket serverSocket = EXUtils.newServerSocket(port)) {
			try (ServerSocket serverSocket = serverSocketFactory.createServerSocket(port)) {
				serverSocket.setReuseAddress(true);
				serverSocket.setSoTimeout(1000);
				while (!executorService.isShutdown()) {
					ssnStts.setValue("0");
					Socket socket;
					try {
						socket = serverSocket.accept();
						socket.setKeepAlive(true);
						socket.setReuseAddress(true);
						socket.setSoLinger(true, 1);
						socket.setSoTimeout(1000);
						socket.setTcpNoDelay(true);
					} catch (SocketTimeoutException e) {
						continue;
					}
					log.info("{}", socket);
					ssnStts.setValue("1");
					executorService.execute(() -> {
						MDC.put("key", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")));
						Thread.currentThread().setName(propertyName);
						log.trace("start");
						try (InputStream inputStream = socket.getInputStream();
							OutputStream outputStream = socket.getOutputStream()) {
							byte[] byteArray = IOUtils.toByteArray(inputStream, 7);
							String tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
							if (!StringUtils.isNumeric(StringUtils.left(tlgCtt, 4)) ||
								!Strings.CS.endsWith(tlgCtt, "HDR")) {
								throw new IOException(tlgCtt);
							}
							try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
								IOUtils.write(byteArray, byteArrayOutputStream);
								byteArray = IOUtils.toByteArray(inputStream, NumberUtils.toInt(StringUtils.left(tlgCtt, 4)) - 3);
								IOUtils.write(byteArray, byteArrayOutputStream);
								byteArray = byteArrayOutputStream.toByteArray();
							}
							tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
							if (Strings.CS.startsWith(tlgCtt, "0020HDRREQPOLL")) { // 회선시험
								log.trace("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								StringBuilder sb = new StringBuilder(tlgCtt);
								sb.setCharAt(9, 'S');
								tlgCtt = sb.toString();
								log.trace(">{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								IOUtils.write(tlgCtt, outputStream, "EUC-KR");
							} else if (Strings.CS.startsWith(tlgCtt, "0020HDRREQSTOP")) { // 정상종료
								log.debug("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								StringBuilder sb = new StringBuilder(tlgCtt);
								sb.setCharAt(9, 'S');
								tlgCtt = sb.toString();
								log.debug(">{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								IOUtils.write(tlgCtt, outputStream, "EUC-KR");
								executorService.shutdown();
							} else if (Strings.CS.startsWith(tlgCtt, "0020HDRREQKILL")) { // 강제종료
								log.debug("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								StringBuilder sb = new StringBuilder(tlgCtt);
								sb.setCharAt(9, 'S');
								tlgCtt = sb.toString();
								log.debug(">{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								IOUtils.write(tlgCtt, outputStream, "EUC-KR");
								EXUtils.exit(1);
							} else if (Strings.CS.startsWith(tlgCtt, "0020HDRREQSTTS")) { // 모니터링
								log.trace("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								StringBuilder sb = new StringBuilder(tlgCtt);
								sb.setCharAt(9, 'S');
								sb.append(StringUtils.leftPad(String.valueOf(ssnSttsMap.size()), 3, '0'));
								for (Entry<String, String> entry : ssnSttsMap.values()) {
									sb.append(StringUtils.rightPad(entry.getKey(), 20));
									sb.append(StringUtils.left(entry.getValue(), 1));
								}
								sb.replace(0, 4, StringUtils.leftPad(String.valueOf(sb.length() - 4), 4, '0'));
								tlgCtt = sb.toString();
								log.trace(">{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								IOUtils.write(tlgCtt, outputStream, "EUC-KR");
							} else if (Strings.CS.startsWith(tlgCtt, "0040HDRREQFDEL")) { // 파일삭제
								log.debug("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								StringBuilder sb = new StringBuilder(tlgCtt);
								sb.setCharAt(9, 'S');
								tlgCtt = sb.toString();
								log.debug(">{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								IOUtils.write(tlgCtt, outputStream, "EUC-KR");
								String fileNm = StringUtils.stripEnd(StringUtils.left(StringUtils.right(tlgCtt, 20), 8), StringUtils.SPACE);
								FileUtils.deleteQuietly(FileUtils.getFile(back, StringUtils.join(fileNm, "_",
								LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))));
								FileUtils.deleteQuietly(FileUtils.getFile(recv, fileNm));
								FileUtils.deleteQuietly(FileUtils.getFile(send, fileNm));
							} else if (Strings.CS.startsWith(tlgCtt, "0040HDRREQFSND")) { // 파일송신
								log.debug("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								StringBuilder sb = new StringBuilder(tlgCtt);
								sb.setCharAt(9, 'S');
								tlgCtt = sb.toString();
								log.debug(">{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								IOUtils.write(tlgCtt, outputStream, "EUC-KR");
								String fileNm = StringUtils.stripEnd(StringUtils.left(StringUtils.right(tlgCtt, 20), 8), StringUtils.SPACE);
								File file = FileUtils.getFile(back, StringUtils.join(fileNm, "_",
								LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))));
								try (FileOutputStream fileOutputStream = FileUtils.openOutputStream(file)) {
									long l = IOUtils.copyLarge(inputStream, fileOutputStream);
									log.info("<{}, {}", file, l);
								}
								Path path = Files.copy(file.toPath(), FileUtils.getFile(send, fileNm).toPath(),
								StandardCopyOption.REPLACE_EXISTING);
								log.info("copied {}, {}", file, path);
							} else if (Strings.CS.startsWith(tlgCtt, "0040HDRREQFRCV")) { // 파일수신
								log.debug("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								String fileNm = StringUtils.stripEnd(StringUtils.left(StringUtils.right(tlgCtt, 20), 8), StringUtils.SPACE);
								File file = FileUtils.getFile(recv, fileNm);
//								long fileSz = FileUtils.sizeOf(file);
								long fileSz = 0L;
								if (file != null &&
									file.exists()) {
									fileSz = FileUtils.sizeOf(file);
								}
								StringBuilder sb = new StringBuilder(tlgCtt);
								sb.setCharAt(9, 'S');
								sb.setLength(4 + 20 + 8);
								sb.append(StringUtils.leftPad(String.valueOf(fileSz), 12, '0'));
								tlgCtt = sb.toString();
								log.debug(">{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								IOUtils.write(tlgCtt, outputStream, "EUC-KR");
								if (!file.exists()) {
									return;
								}
								try (FileInputStream fileInputStream = FileUtils.openInputStream(file)) {
									long l = IOUtils.copyLarge(fileInputStream, outputStream);
									log.info(">{}, {}", file, l);
								}
							} else if (Strings.CS.startsWithAny(tlgCtt, "0020HDRREQLLST", "0040HDRREQLLST")) { // 로그목록
								log.debug("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								StringBuilder sb = new StringBuilder(tlgCtt);
								sb.setCharAt(9, 'S');
								sb.setLength(20 + 3);
								IOFileFilter ioFileFilter;
								if (Strings.CS.startsWithAny(tlgCtt, "0040HDRREQLLST")) {
									ioFileFilter = WildcardFileFilter.builder().setWildcards(StringUtils.join("*",
										StringUtils.stripEnd(StringUtils.left(StringUtils.right(tlgCtt, 20), 8), StringUtils.SPACE),
									"*")).get();
								} else {
									ioFileFilter = WildcardFileFilter.builder().setWildcards("*.log").get();
								}
								Collection<File> collection = FileUtils.listFiles(alog, ioFileFilter, FalseFileFilter.FALSE);
								sb.append(StringUtils.leftPad(String.valueOf(collection.size()), 3, '0'));
								for (File file : collection) {
//									long fileSz = FileUtils.sizeOf(file);
									long fileSz = 0L;
									if (file != null &&
										file.exists()) {
										fileSz = FileUtils.sizeOf(file);
									}
									String fileNm = file.getName();
//									sb.append(StringUtils.rightPad(fileNm, 88));
									byteArray = StringUtils.getBytes(fileNm, "EUC-KR");
									sb.append(fileNm).append(StringUtils.repeat(' ', 88 - byteArray.length));
									sb.append(StringUtils.leftPad(String.valueOf(fileSz), 12, '0'));
								}
								sb.replace(0, 4, StringUtils.leftPad(String.valueOf(sb.length() - 4), 4, '0'));
								tlgCtt = sb.toString();
								log.debug(">{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								IOUtils.write(tlgCtt, outputStream, "EUC-KR");
							} else if (Strings.CS.startsWith(tlgCtt, "0100HDRREQLRCV")) { // 로그수신
								log.debug("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								String fileNm = StringUtils.stripEnd(StringUtils.left(StringUtils.right(tlgCtt, 100), 88), StringUtils.SPACE);
								File file = FileUtils.getFile(alog, fileNm);
								long fileSz = 0L;
								if (file != null &&
									file.exists()) {
									fileSz = FileUtils.sizeOf(file);
								}
								StringBuilder sb = new StringBuilder(tlgCtt);
								sb.setCharAt(9, 'S');
								sb.setLength(4 + 20 + 88);
								sb.append(StringUtils.leftPad(String.valueOf(fileSz), 12, '0'));
								tlgCtt = sb.toString();
								log.debug(">{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								IOUtils.write(tlgCtt, outputStream, "EUC-KR");
								if (!file.exists()) {
									return;
								}
								try (FileInputStream fileInputStream = FileUtils.openInputStream(file)) {
									long l = IOUtils.copyLarge(fileInputStream, outputStream, 0L, fileSz);
									log.info(">{}, {}", file, l);
								}
							} else if (Strings.CS.startsWith(tlgCtt, "0100HDRREQRSLT")) { // 전송결과요구/통보
								log.debug("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								StringBuilder sb = new StringBuilder(tlgCtt);
								sb.setCharAt(9, 'S');
								tlgCtt = sb.toString();
								log.debug(">{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
								IOUtils.write(tlgCtt, outputStream, "EUC-KR");
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
								String apiTrxNo = StringUtils.stripEnd(StringUtils.left(StringUtils.right(tlgCtt, 80), 20), StringUtils.SPACE);
								String apiTrxDtm = StringUtils.stripEnd(StringUtils.left(StringUtils.right(tlgCtt, 60), 20), StringUtils.SPACE);
								String fileName = StringUtils.stripEnd(StringUtils.right(tlgCtt, 40), StringUtils.SPACE);
								String bodyStr = EXUtils.postTrnsfRslt(apiTrxNo, apiTrxDtm, fileName, properties);
								IOUtils.write(bodyStr, outputStream, "EUC-KR");
///////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
							}
						} catch (Throwable t) {
							log.error(ExceptionUtils.getRootCauseMessage(t));
						} finally {
							IOUtils.closeQuietly(socket);
						}
						log.trace("stop");
					});
				}
			} catch (Throwable t) {
				log.error(ExceptionUtils.getRootCauseMessage(t));
			}
			ssnStts.setValue("0");
			ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
		}
		log.info("stop");
	}

}

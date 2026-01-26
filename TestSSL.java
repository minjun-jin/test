package test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestSSL {

	@SneakyThrows
	public static void main(String[] args) {
//		keytool -genkeypair -alias test -keyalg RSA -keysize 2048 -keystore test.jks -validity 3650
		System.setProperty("javax.net.ssl.keyStore", "test.jks");
		System.setProperty("javax.net.ssl.keyStorePassword", "123456");
		System.setProperty("javax.net.ssl.trustStore", "test.jks");
		System.setProperty("javax.net.ssl.trustStorePassword", "123456");
		args = Arrays.asList("poll").toArray(String[]::new);
		int port = 6666;
		ExecutorService executorService = Executors.newCachedThreadPool();
		executorService.execute(() -> {
			ServerSocketFactory serverSocketFactory = SSLServerSocketFactory.getDefault();
			while (!executorService.isShutdown()) {
//				ssnStts.setValue("0");
				try (ServerSocket serverSocket = serverSocketFactory.createServerSocket(port)) {
					serverSocket.setReuseAddress(true);
					serverSocket.setSoTimeout(1000);
					while (!executorService.isShutdown()) {
//						ssnStts.setValue("0");
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
//						ssnStts.setValue("1");
						executorService.execute(() -> {
//							MDC.put("key", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")));
//							Thread.currentThread().setName(propertyName);
							log.debug("start");
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
									log.debug("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
									StringBuilder sb = new StringBuilder(tlgCtt);
									sb.setCharAt(9, 'S');
									tlgCtt = sb.toString();
									log.debug(">{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
									IOUtils.write(tlgCtt, outputStream, "EUC-KR");
								}
							} catch (Throwable t) {
								log.error(ExceptionUtils.getRootCauseMessage(t));
							} finally {
								IOUtils.closeQuietly(socket);
							}
							log.debug("stop");
						});
					}
				} catch (Throwable t) {
					log.error(ExceptionUtils.getRootCauseMessage(t));
				}
//				ssnStts.setValue("0");
				ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
			}
		});
		SocketFactory socketFactory = SSLSocketFactory.getDefault();
		try (Socket socket = socketFactory.createSocket("127.0.0.1", port)) {
			socket.setKeepAlive(true);
			socket.setReuseAddress(true);
			socket.setSoLinger(true, 1);
			socket.setSoTimeout(1000);
			socket.setTcpNoDelay(true);
			try (InputStream inputStream = socket.getInputStream();
				OutputStream outputStream = socket.getOutputStream()) {
				String tlgCtt = StringUtils.join("0020HDRREQ", StringUtils.upperCase(args[0]), LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")));
				log.info(">{}]", tlgCtt);
				IOUtils.write(tlgCtt, outputStream, "EUC-KR");
				byte[] byteArray = IOUtils.toByteArray(inputStream, 7);
				tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
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
				log.info("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
			}
		} catch (Throwable t) {
			log.error(ExceptionUtils.getRootCauseMessage(t));
		}
		executorService.shutdown();
		while (!executorService.awaitTermination(60000L, TimeUnit.MILLISECONDS)) {
		}
	}

}

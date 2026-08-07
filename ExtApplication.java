package com.jpmc.kcg.ext;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.SocketFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONObject;

import com.ibm.msg.client.jakarta.jms.JmsConnectionFactory;
import com.ibm.msg.client.jakarta.jms.JmsFactoryFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Slf4jLogger;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExtApplication {

	@SneakyThrows
	public static void main(String[] args) {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// JSch 강제설정 (로깅 및 금결원 알고리즘 제한) ///////////////////////////
		JSch.setLogger(new Slf4jLogger());
		JSch.setConfig("server_host_key", StringUtils.join(JSch.getConfig("server_host_key"), ",ssh-rsa"));
		JSch.setConfig("PubkeyAcceptedAlgorithms", StringUtils.join(JSch.getConfig("PubkeyAcceptedAlgorithms"), ",ssh-rsa"));
		JSch.setConfig("StrictHostKeyChecking", "no");
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		Properties properties = new Properties();
		String string = System.getProperty("config.location");
		if (StringUtils.isNotEmpty(string)) {
			try (InputStream inputStream = EXUtils.openInputStream(FileUtils.getFile(string))) {
				properties.load(inputStream);
			}
		} else {
			try (InputStream inputStream = ClassLoader.getSystemResourceAsStream("application.properties")) {
				properties.load(inputStream);
			}
		}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// 종료처리 ///////////////////////////////////////////////////////////////
		if (0 < args.length) {
//			if (Strings.CI.equalsAny(args[0], "poll", "stop", "kill")) {
			if (Strings.CI.equalsAny(args[0], "poll", "stop", "kill", "rslt")) {
				SocketFactory socketFactory = SocketFactory.getDefault();
				for (String propertyName : properties.stringPropertyNames().stream()
					.filter(propertyName -> Strings.CS.startsWith(propertyName, "FCMN_"))
						.collect(Collectors.toCollection(TreeSet::new))) {
					if (StringUtils.isEmpty(properties.getProperty(propertyName))) {
						continue;
					}
					int port = NumberUtils.toInt(properties.getProperty(propertyName));
					try (Socket socket = socketFactory.createSocket("127.0.0.1", port)) {
						socket.setKeepAlive(true);
						socket.setReuseAddress(true);
						socket.setSoLinger(true, 1);
						socket.setSoTimeout(1000);
						socket.setTcpNoDelay(true);
						try (InputStream inputStream = socket.getInputStream();
							OutputStream outputStream = socket.getOutputStream()) {
							if (Strings.CI.equalsAny(args[0], "rslt")) {
								String tlgCtt = StringUtils.join("0100HDRREQRSLT", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")),
								StringUtils.rightPad(StringUtils.left(StringUtils.defaultString(args[1]), 20), 20),
								StringUtils.rightPad(StringUtils.left(StringUtils.defaultString(args[2]), 20), 20),
								StringUtils.rightPad(StringUtils.left(StringUtils.defaultString(args[3]), 40), 40));
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
								log.info("<{}]", tlgCtt);
								byteArray = IOUtils.toByteArray(inputStream);
								String bodyStr = IOUtils.toString(byteArray, "EUC-KR");
								log.info("<{}", bodyStr);
								JSONObject jsonObject = new JSONObject(bodyStr);
								log.info("<{}", jsonObject.toString(2));
								return;
							}
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
							log.info("<{}]", tlgCtt);
						}
					}
				}
				return;
			}
			throw new UnsupportedOperationException(args[0]);		}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		ExecutorService executorService = Executors.newCachedThreadPool();
		JmsFactoryFactory jmsFactoryFactory = JmsFactoryFactory.getInstance(WMQConstants.JAKARTA_WMQ_PROVIDER);
		JmsConnectionFactory jmsConnectionFactory = jmsFactoryFactory.createConnectionFactory();
		jmsConnectionFactory.setIntProperty(WMQConstants.WMQ_SHARE_CONV_ALLOWED, NumberUtils.toInt(properties.getProperty(WMQConstants.WMQ_SHARE_CONV_ALLOWED, String.valueOf(WMQConstants.WMQ_SHARE_CONV_ALLOWED_NO))));
		jmsConnectionFactory.setStringProperty(WMQConstants.WMQ_APPLICATIONNAME, "kcg_ext");
		jmsConnectionFactory.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT);
		jmsConnectionFactory.setStringProperty(WMQConstants.WMQ_HOST_NAME, properties.getProperty(WMQConstants.WMQ_HOST_NAME, "43.200.241.167"));
		jmsConnectionFactory.setIntProperty(WMQConstants.WMQ_PORT, NumberUtils.toInt(properties.getProperty(WMQConstants.WMQ_PORT, "1414")));
		jmsConnectionFactory.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, properties.getProperty(WMQConstants.WMQ_QUEUE_MANAGER, "QM1"));
		jmsConnectionFactory.setStringProperty(WMQConstants.WMQ_CHANNEL, properties.getProperty(WMQConstants.WMQ_CHANNEL, "DEV.APP.SVRCONN"));
		jmsConnectionFactory.setStringProperty(WMQConstants.USERID, properties.getProperty(WMQConstants.USERID, "app"));
		jmsConnectionFactory.setStringProperty(WMQConstants.PASSWORD, properties.getProperty(WMQConstants.PASSWORD, "passw0rd"));
		if (StringUtils.isNotEmpty(System.getProperty("javax.net.ssl.keyStore"))) {
			jmsConnectionFactory.setStringProperty(WMQConstants.WMQ_SSL_CIPHER_SUITE, System.getProperty("javax.net.ssl.cipher.suite", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"));
		}
		Map<String, LocalDateTime> sndDttmMap = new ConcurrentHashMap<>();
		Map<String, BlockingQueue<Entry<String, String>>> blckQueMap = Stream.concat(Stream.concat(
			properties.stringPropertyNames().stream()
			.filter(propertyName -> Strings.CS.startsWithAny(propertyName, "QSND_", "QRCV_", "QCMN_BFT_")).map(
					propertyName -> StringUtils.left(propertyName, 8)),
			properties.stringPropertyNames().stream()
			.filter(propertyName -> Strings.CS.startsWithAny(propertyName, "QSND_")).map( // 시뮬레이터추가정의
					propertyName -> StringUtils.left(Strings.CS.replace(propertyName, "QSND_", "SSND_"), 8))),
			properties.stringPropertyNames().stream()
			.filter(propertyName -> Strings.CS.startsWithAny(propertyName, "SBMN_")).map( // 동기응답용추가정의
					propertyName -> StringUtils.left(Strings.CS.replace(propertyName, "SBMN_", "QSND2"), 8)))
		.collect(Collectors.toCollection(TreeSet::new)).stream()
		.collect(Collectors.toMap(
			Function.identity(),
			propertyName -> {
				return new PriorityBlockingQueue<>(11, (o1, o2) -> {
					return Strings.CS.compare(o2.getValue(), o1.getValue());
				});
			},
		ObjectUtils::getIfNull, TreeMap::new));
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		Map<String, String> crltnIdMap = new ConcurrentHashMap<>();
		Map<String, Entry<String, String>> ssnSttsMap = properties.stringPropertyNames().stream()
		.filter(propertyName -> Strings.CS.startsWithAny(propertyName, "FCMN_", "FRCV_", "FSND_", "QCMN_BFT_", "QRCV_", "QSND_", "SBMN_", "SCMN_", "SRCV_", "SSND_"))
		.collect(Collectors.toMap(
			Function.identity(),
			propertyName -> new SimpleEntry<>(propertyName, "0"),
		ObjectUtils::getIfNull, TreeMap::new));
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// 로그복구 ///////////////////////////////////////////////////////////////
		File blog = FileUtils.getFile(properties.getProperty("PATH_SHRD", FileUtils.getTempDirectoryPath()), "kcg_ext");
		try (BufferedReader bufferedReader = IOUtils.buffer(new InputStreamReader(EXUtils.openInputStream(blog), StandardCharsets.UTF_8))) {
			log.debug("restore {}", blog);
			Map<String, String> map = new TreeMap<>();
			bufferedReader.lines().forEach(s -> {
				if (Strings.CS.startsWith(s, "0")) {
					map.remove(StringUtils.substring(s, 1));
				} else {
					map.put(StringUtils.substring(s, 1), s);
				}
			});
			for (String s : map.values()) {
				log.info("[{}]", s);
				BlockingQueue<Entry<String, String>> blckQue = blckQueMap.get(StringUtils.right(StringUtils.left(s, 9), 8));
				if (null != blckQue) {
					blckQue.put(Map.entry(StringUtils.substring(s, 9), StringUtils.left(s, 1)));
				}
			}
		} catch (FileNotFoundException e) {
		}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// 로그생성 ///////////////////////////////////////////////////////////////
		EXUtils.reset(blog);
		log.debug("created {}", blog);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
				StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
				Strings.CS.startsWith(propertyName, "QCMN_BFT_"))
			.collect(Collectors.toCollection(TreeSet::new))) {
			executorService.execute(new ExtQCmn(properties, 
				executorService,
				jmsConnectionFactory,
				blckQueMap,
				ssnSttsMap,
			propertyName));
		}
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
				StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
				Strings.CS.startsWith(propertyName, "QSND_"))
			.collect(Collectors.toCollection(TreeSet::new))) {
			executorService.execute(new ExtQSnd(properties, 
				executorService,
				jmsConnectionFactory,
				blckQueMap,
				crltnIdMap,
				ssnSttsMap,
			propertyName));
		}
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
				StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
				Strings.CS.startsWith(propertyName, "QRCV_"))
			.collect(Collectors.toCollection(TreeSet::new))) {
			executorService.execute(new ExtQRcv(properties, 
				executorService,
				jmsConnectionFactory,
				blckQueMap,
				crltnIdMap,
				ssnSttsMap,
			propertyName));
		}
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
				StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
				Strings.CS.startsWith(propertyName, "SSND_"))
			.collect(Collectors.toCollection(TreeSet::new))) {
			executorService.execute(new ExtSSnd(properties,
				executorService,
				blckQueMap,
				ssnSttsMap,
				sndDttmMap,
			propertyName));
		}
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
				StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
				Strings.CS.startsWith(propertyName, "SRCV_"))
			.collect(Collectors.toCollection(TreeSet::new))) {
			executorService.execute(new ExtSRcv(properties,
				executorService,
				blckQueMap,
				ssnSttsMap,
			propertyName));
		}
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
				StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
				Strings.CS.startsWith(propertyName, "SCMN_"))
			.collect(Collectors.toCollection(TreeSet::new))) {
			executorService.execute(new ExtSCmn(properties,
				executorService,
				blckQueMap,
				ssnSttsMap,
			propertyName));
		}
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
				StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
				Strings.CS.startsWith(propertyName, "SBMN_"))
			.collect(Collectors.toCollection(TreeSet::new))) {
			executorService.execute(new ExtSBmn(properties,
				executorService,
				blckQueMap,
				ssnSttsMap,
			propertyName));
		}
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
				StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
				Strings.CS.startsWith(propertyName, "FSND_") && Strings.CS.endsWith(propertyName, "_1"))
			.collect(Collectors.toCollection(TreeSet::new))) {
			executorService.execute(new ExtFSnd1(properties,
				executorService,
				blckQueMap,
				ssnSttsMap,
			propertyName));
		}
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
				StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
				Strings.CS.startsWith(propertyName, "FSND_") && Strings.CS.endsWith(propertyName, "_2"))
			.collect(Collectors.toCollection(TreeSet::new))) {
			executorService.execute(new ExtFSnd2(properties,
				executorService,
				blckQueMap,
				ssnSttsMap,
			propertyName));
		}
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
				StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
				Strings.CS.startsWith(propertyName, "FRCV_") && Strings.CS.endsWith(propertyName, "_1"))
			.collect(Collectors.toCollection(TreeSet::new))) {
			executorService.execute(new ExtFRcv1(properties,
				executorService,
				blckQueMap,
				ssnSttsMap,
			propertyName));
		}
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
				StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
				Strings.CS.startsWith(propertyName, "FRCV_") && Strings.CS.endsWith(propertyName, "_2"))
			.collect(Collectors.toCollection(TreeSet::new))) {
			executorService.execute(new ExtFRcv2(properties,
				executorService,
				blckQueMap,
				ssnSttsMap,
			propertyName));
		}
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
				StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
				Strings.CS.startsWith(propertyName, "FCMN_"))
			.collect(Collectors.toCollection(TreeSet::new))) {
			executorService.execute(new ExtFCmn(properties,
				executorService,
				ssnSttsMap,
			propertyName));
		}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// 종료대기 ///////////////////////////////////////////////////////////////
		File back = FileUtils.getFile(properties.getProperty("PATH_BACK", "/home/ec2-user/ext/shrd/back"));
		File recv = FileUtils.getFile(properties.getProperty("PATH_RECV", "/home/ec2-user/ext/shrd/recv"));
		File send = FileUtils.getFile(properties.getProperty("PATH_SEND", "/home/ec2-user/ext/shrd/send"));
		while (!executorService.awaitTermination(60000L, TimeUnit.MILLISECONDS)) {
			LocalDateTime nowDttm = LocalDateTime.now();
			LocalDateTime tmpDttm = nowDttm.minusMinutes(1L);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
			sndDttmMap.entrySet().removeIf(e -> {
				return 0 < ObjectUtils.compare(tmpDttm, e.getValue());
			});
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 동기응답정리 ///////////////////////////////////////////////////////
			String crltnId = tmpDttm.format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
			crltnIdMap.entrySet().removeIf(e -> {
				return 0 < Strings.CS.compare(crltnId, e.getValue());
			});
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
			log.info("{}", executorService);
			for (Entry<String, BlockingQueue<Entry<String, String>>> entry : blckQueMap.entrySet()) {
				log.info("{} = {}", entry.getKey(), entry.getValue().size());
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 파일정리 ///////////////////////////////////////////////////////////
			if (23 == nowDttm.getHour() &&
				45 <= nowDttm.getMinute()) {
				for (File file : Stream.concat(
					FileUtils.listFiles(recv, TrueFileFilter.TRUE, FalseFileFilter.FALSE).stream(),
					FileUtils.listFiles(send, TrueFileFilter.TRUE, FalseFileFilter.FALSE).stream()
				).toList()) {
					String fileNm = file.getName();
					Path path = Files.move(file.toPath(), FileUtils.getFile(back, StringUtils.join(fileNm, "_",
					nowDttm.format(DateTimeFormatter.ofPattern("yyyyMMdd")))).toPath(),
					StandardCopyOption.REPLACE_EXISTING);
					log.info("moved {}, {}", file, path);
				}
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 로그리셋 ///////////////////////////////////////////////////////////
			EXUtils.reset(blog);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// 로그삭제 ///////////////////////////////////////////////////////////////
		FileUtils.deleteQuietly(blog);
		log.debug("deleted {}", blog);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
	}

}

package com.jpmc.kcg.frw;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FrwExtMngr implements InitializingBean {

	@Data
	public static class SsnStts {

		private String ssnNm;
		private int actvStts;

	}

	@Data
	public static class LogFile {

		private String fileNm;
		private long fileSz;

	}

	@Data
	public static class ExtRslt {

		private String rspCd;
		private String rspMsg;

	}

	@Autowired
	private SystemProperties systemProperties;
	private SocketFactory socketFactory;

	@Override
	public void afterPropertiesSet() throws Exception {
		socketFactory = SSLSocketFactory.getDefault();
	}

	/**
	 * 
	 */
	public Map<String, SsnStts> getSsnSttsMap() throws IOException {
		Map<String, SsnStts> ssnSttsMap = new TreeMap<>();
//		try (Socket socket = new Socket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
		try (Socket socket = socketFactory.createSocket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
			socket.setKeepAlive(true);
			socket.setReuseAddress(true);
			socket.setSoLinger(true, 1);
			socket.setSoTimeout(1000);
			socket.setTcpNoDelay(true);
			log.info("{}", socket);
			try (InputStream inputStream = socket.getInputStream();
				OutputStream outputStream = socket.getOutputStream()) {
				String tlgCtt = StringUtils.join("0020HDRREQSTTS", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")));
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
				try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArray)) {
					byteArray = IOUtils.toByteArray(byteArrayInputStream, 4 + 20 + 3);
					tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
					log.trace("={}]", tlgCtt);
					int nCnt = NumberUtils.toInt(StringUtils.right(tlgCtt, 3));
					for (int i = 0; i < nCnt; i++) {
						byteArray = IOUtils.toByteArray(byteArrayInputStream, 21);
						tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
						log.trace("{}", tlgCtt);
						SsnStts ssnStts = new SsnStts();
						ssnStts.setSsnNm(StringUtils.stripEnd(StringUtils.left(tlgCtt, 20), StringUtils.SPACE));
						ssnStts.setActvStts(NumberUtils.toInt(StringUtils.right(tlgCtt, 1)));
						log.trace("{}", ssnStts);
						ssnSttsMap.put(ssnStts.getSsnNm(), ssnStts);
					}
				}
			}
		}
		return ssnSttsMap;
	}

	/**
	 * 
	 */
	public Map<String, LogFile> getLogFileMap(String fileDt) throws IOException {
		Map<String, LogFile> logFileMap = new TreeMap<>();
//		try (Socket socket = new Socket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
		try (Socket socket = socketFactory.createSocket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
			socket.setKeepAlive(true);
			socket.setReuseAddress(true);
			socket.setSoLinger(true, 1);
			socket.setSoTimeout(1000);
			socket.setTcpNoDelay(true);
			log.info("{}", socket);
			try (InputStream inputStream = socket.getInputStream();
				OutputStream outputStream = socket.getOutputStream()) {
				String tlgCtt;
				if (StringUtils.isNotEmpty(fileDt)) {
					tlgCtt = StringUtils.join("0040HDRREQLLST", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")), StringUtils.rightPad(fileDt, 20));
				} else {
					tlgCtt = StringUtils.join("0020HDRREQLLST", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")));
				}
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
				try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArray)) {
					byteArray = IOUtils.toByteArray(byteArrayInputStream, 4 + 20 + 3);
					tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
					log.debug("={}]", tlgCtt);
					int nCnt = NumberUtils.toInt(StringUtils.right(tlgCtt, 3));
					for (int i = 0; i < nCnt; i++) {
						byteArray = IOUtils.toByteArray(byteArrayInputStream, 100);
						tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
						log.debug("{}", tlgCtt);
						LogFile logFile = new LogFile();
						logFile.setFileNm(StringUtils.stripEnd(StringUtils.left(tlgCtt, 88), StringUtils.SPACE));
						logFile.setFileSz(NumberUtils.toLong(StringUtils.right(tlgCtt, 12)));
						log.debug("{}", logFile);
						logFileMap.put(logFile.getFileNm(), logFile);
					}
				}
			}
		}
		return logFileMap;
	}

	/**
	 * 
	 */
	public Map<String, LogFile> getLogFileMap() throws IOException {
		return getLogFileMap(null);
	}

	/**
	 * 
	 */
	public File receiveLog(String fileNm) throws IOException {
		log.debug("{}", fileNm);
		File file = FileUtils.getFile(systemProperties.getShrd().getBack(), StringUtils.join(fileNm, ".",
		String.valueOf(System.currentTimeMillis())));
//		try (Socket socket = new Socket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
		try (Socket socket = socketFactory.createSocket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
			socket.setKeepAlive(true);
			socket.setReuseAddress(true);
			socket.setSoLinger(true, 1);
			socket.setSoTimeout(1000);
			socket.setTcpNoDelay(true);
			log.info("{}", socket);
			try (InputStream inputStream = socket.getInputStream();
				OutputStream outputStream = socket.getOutputStream()) {
				String tlgCtt = StringUtils.join("0120HDRREQLRCV", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")),
				StringUtils.rightPad(StringUtils.left(fileNm, 88), 88),
				StringUtils.repeat('0', 12));
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
				long fileSz = NumberUtils.toLong(StringUtils.right(tlgCtt, 12));
				if (0 >= fileSz) {
					throw new FileNotFoundException(fileNm);
				}
				try (FileOutputStream fileOutputStream = FileUtils.openOutputStream(file)) {
					long l = IOUtils.copyLarge(inputStream, fileOutputStream);
					log.info(">{}, {}", file, l);
				}
			}
		}
		return file;
	}

	/**
	 * 
	 */
	public void deleteExt(String fileNm) throws IOException {
		log.debug("{}", fileNm);
		FileUtils.deleteQuietly(FileUtils.getFile(systemProperties.getShrd().getBack(), StringUtils.join(fileNm, "_",
		LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))));
		FileUtils.deleteQuietly(FileUtils.getFile(systemProperties.getShrd().getRecv(), fileNm));
		FileUtils.deleteQuietly(FileUtils.getFile(systemProperties.getShrd().getSend(), fileNm));
//		try (Socket socket = new Socket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
		try (Socket socket = socketFactory.createSocket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
			socket.setKeepAlive(true);
			socket.setReuseAddress(true);
			socket.setSoLinger(true, 1);
			socket.setSoTimeout(1000);
			socket.setTcpNoDelay(true);
			log.info("{}", socket);
			try (InputStream inputStream = socket.getInputStream();
				OutputStream outputStream = socket.getOutputStream()) {
				String tlgCtt = StringUtils.join("0040HDRREQFDEL", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")),
				StringUtils.rightPad(StringUtils.left(fileNm, 8), 8),
				StringUtils.repeat('0', 12));
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

	/**
	 * 
	 */
	public void sendExt(String fileNm) throws IOException {
		log.debug("{}", fileNm);
		File file = FileUtils.getFile(systemProperties.getShrd().getSend(), fileNm);
		if (!file.exists()) {
			throw new FileNotFoundException(fileNm);
		}
//		try (Socket socket = new Socket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
		try (Socket socket = socketFactory.createSocket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
			socket.setKeepAlive(true);
			socket.setReuseAddress(true);
			socket.setSoLinger(true, 1);
			socket.setSoTimeout(1000);
			socket.setTcpNoDelay(true);
			log.info("{}", socket);
			try (InputStream inputStream = socket.getInputStream();
				OutputStream outputStream = socket.getOutputStream()) {
				long fileSz = 0L;
				if (file != null &&
					file.exists()) {
					fileSz = FileUtils.sizeOf(file);
				}
				String tlgCtt = StringUtils.join("0040HDRREQFSND", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")),
				StringUtils.rightPad(StringUtils.left(fileNm, 8), 8),
				StringUtils.leftPad(String.valueOf(fileSz), 12, '0'));
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
				try (FileInputStream fileInputStream = FileUtils.openInputStream(file)) {
					long l = IOUtils.copyLarge(fileInputStream, outputStream);
					log.info("<{}, {}", file, l);
				}
			}
		}
		Path path = Files.move(file.toPath(), FileUtils.getFile(systemProperties.getShrd().getBack(), StringUtils.join(fileNm, "_",
		LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))).toPath(),
		StandardCopyOption.REPLACE_EXISTING);
		log.info("moved {}, {}", file, path);
	}

	/**
	 * 
	 */
	public File receiveExt(String fileNm) throws IOException {
		log.debug("{}", fileNm);
		File file = FileUtils.getFile(systemProperties.getShrd().getBack(), StringUtils.join(fileNm, "_",
		LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))));
//		try (Socket socket = new Socket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
		try (Socket socket = socketFactory.createSocket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
			socket.setKeepAlive(true);
			socket.setReuseAddress(true);
			socket.setSoLinger(true, 1);
			socket.setSoTimeout(1000);
			socket.setTcpNoDelay(true);
			log.info("{}", socket);
			try (InputStream inputStream = socket.getInputStream();
				OutputStream outputStream = socket.getOutputStream()) {
				String tlgCtt = StringUtils.join("0040HDRREQFRCV", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")),
				StringUtils.rightPad(StringUtils.left(fileNm, 8), 8),
				StringUtils.repeat('0', 12));
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
				long fileSz = NumberUtils.toLong(StringUtils.right(tlgCtt, 12));
				if (0 >= fileSz) {
					throw new FileNotFoundException(fileNm);
				}
				try (FileOutputStream fileOutputStream = FileUtils.openOutputStream(file)) {
					long l = IOUtils.copyLarge(inputStream, fileOutputStream);
					log.info(">{}, {}", file, l);
				}
			}
		}
		Path path = Files.copy(file.toPath(), FileUtils.getFile(systemProperties.getShrd().getRecv(), fileNm).toPath(),
		StandardCopyOption.REPLACE_EXISTING);
		log.info("copied {}, {}", file, path);
		return path.toFile();
	}

	/**
	 * 
	 */
	public ExtRslt resultExt(String apiTrxNo, String apiTrxDtm, String fileNm) throws IOException {
		log.debug("{}, {}, {}", apiTrxNo, apiTrxDtm, fileNm);
		ExtRslt extRslt = new ExtRslt();
//		try (Socket socket = new Socket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
		try (Socket socket = socketFactory.createSocket(systemProperties.getExt().getHost(), systemProperties.getExt().getPort())) {
			socket.setKeepAlive(true);
			socket.setReuseAddress(true);
			socket.setSoLinger(true, 1);
			socket.setSoTimeout(31000);
			socket.setTcpNoDelay(true);
			log.info("{}", socket);
			try (InputStream inputStream = socket.getInputStream();
				OutputStream outputStream = socket.getOutputStream()) {
				String tlgCtt = StringUtils.join("0100HDRREQRSLT", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")),
				StringUtils.rightPad(StringUtils.left(StringUtils.defaultString(apiTrxNo), 20), 20),
				StringUtils.rightPad(StringUtils.left(StringUtils.defaultString(apiTrxDtm), 20), 20),
				StringUtils.rightPad(StringUtils.left(StringUtils.defaultString(fileNm), 40), 40));
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
				extRslt.setRspCd(jsonObject.optString("rsp_code"));
				extRslt.setRspMsg(jsonObject.optString("rsp_message"));
			}
		}
		log.debug("{}", extRslt);
		return extRslt;
	}

	/**
	 * 
	 */
	public OutputStream openOutputStream(String fileNm) throws IOException {
		File file = FileUtils.getFile(systemProperties.getShrd().getSend(), fileNm);
		return IOUtils.buffer(FileUtils.openOutputStream(file));
	}

	/**
	 * 
	 */
	public InputStream openInputStream(String fileNm) throws IOException {
		File file = FileUtils.getFile(systemProperties.getShrd().getRecv(), fileNm);
		return IOUtils.buffer(FileUtils.openInputStream(file));
	}

	/**
	 * 
	 */
	public void closeQuietly(Closeable closeable) {
		IOUtils.closeQuietly(closeable);
	}

	/**
	 * 
	 */
	public void closeQuietly(Closeable... closeables) {
		IOUtils.closeQuietly(closeables);
	}

	/**
	 * 
	 */
	public void deleteQuietly(String fileNm) {
		FileUtils.deleteQuietly(FileUtils.getFile(systemProperties.getShrd().getBack(), StringUtils.join(fileNm, "_",
		LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))));
		FileUtils.deleteQuietly(FileUtils.getFile(systemProperties.getShrd().getRecv(), fileNm));
		FileUtils.deleteQuietly(FileUtils.getFile(systemProperties.getShrd().getSend(), fileNm));
	}

	/**
	 * 
	 */
	public String write(OutputStream out, Vo v) throws IOException {
		try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
			v.write(byteArrayOutputStream);
			byte[] byteArray = byteArrayOutputStream.toByteArray();
			IOUtils.write(byteArray, out);
			return IOUtils.toString(byteArray, "EUC-KR");
		}
	}

	/**
	 * 
	 */
	public String read(InputStream in, int size) throws IOException {
		in.mark(size);
		try {
			return IOUtils.toString(IOUtils.toByteArray(in, size), "EUC-KR");
		} finally {
			in.reset();
		}
	}

	/**
	 * 
	 */
	public <VO extends Vo> VO read(InputStream in, Class<VO> voClass) throws IOException {
		if (0 < in.available()) {
			return VOUtils.toVo(in, voClass);
		}
		return null;
	}

}

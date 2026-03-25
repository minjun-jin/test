package com.jpmc.kcg.ext;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.json.JSONObject;
import org.slf4j.MDC;

import com.jpmc.kcg.ext.vo.Kft0300;
import com.jpmc.kcg.ext.vo.Kft0310;
import com.jpmc.kcg.ext.vo.Kft0320;
import com.jpmc.kcg.ext.vo.Kft0600;
import com.jpmc.kcg.ext.vo.Kft0610;
import com.jpmc.kcg.ext.vo.Kft0620;
import com.jpmc.kcg.ext.vo.Kft0630;
import com.jpmc.kcg.ext.vo.Kft0640;

import jakarta.jms.Message;
import lombok.Generated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Generated
@RequiredArgsConstructor
@Slf4j
public class ExtFRcv1 implements Runnable {

	private final Properties properties;
	private final ExecutorService executorService;
	private final Map<String, BlockingQueue<Entry<String, String>>> blckQueMap;
	private final Map<String, Entry<String, String>> ssnSttsMap;
	private final String propertyName;

	@Override
	public void run() {
		Entry<String, String> ssnStts = ssnSttsMap.get(propertyName);
		Entry<String, String> sndStts = ssnSttsMap.get(Strings.CS.replace(propertyName, "FRCV_", "FSND_"));
		String cmnBlckQueNm = "QCMN_BFT";
		BlockingQueue<Entry<String, String>> cmnBlckQue = blckQueMap.get(cmnBlckQueNm);
		File back = FileUtils.getFile(properties.getProperty("PATH_BACK", "/home/ec2-user/ext/shrd/back"));
		File recv = FileUtils.getFile(properties.getProperty("PATH_RECV", "/home/ec2-user/ext/shrd/recv"));
		String[] stringArray = StringUtils.split(properties.getProperty(propertyName), '@');
		String[] stringArray0 = StringUtils.split(stringArray[0], ':');
		String[] stringArray1 = StringUtils.split(stringArray[1], ':');
		String bizDvsnCd = StringUtils.upperCase(StringUtils.substringBetween(propertyName, "_"));
		String sndrNm    = stringArray0[0];
		String sndrPswrd = stringArray0[1];
		String host = stringArray1[0];
		int port = NumberUtils.toInt(stringArray1[1]);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		MDC.put("key", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")));
		Thread.currentThread().setName(propertyName);
		log.info("start");
		while (!executorService.isShutdown()) {
			ssnStts.setValue("0");
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 차단시간처리 ///////////////////////////////////////////////////////
			LocalTime localTime = LocalTime.now();
			if (23 == localTime.getHour() &&
				30 <= localTime.getMinute()) {
				ssnStts.setValue("0");
				for (int i = 0; i < 60 && !executorService.isShutdown(); i++) {
					ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
				}
				continue;
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 동시수행방지 ///////////////////////////////////////////////////////
			synchronized (ssnSttsMap) {
				ssnStts.setValue("1");
				if (Strings.CS.equals(sndStts.getValue(), "1")) {
					ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
					continue;
				}
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
			ssnStts.setValue("1");
			try (Socket socket = EXUtils.newSocket(host, port)) {
				socket.setKeepAlive(true);
				socket.setReuseAddress(true);
				socket.setSoLinger(true, 1);
				socket.setSoTimeout(60000);
				socket.setTcpNoDelay(true);
				log.info("{}", socket);
				try (InputStream inputStream = socket.getInputStream();
					OutputStream outputStream = socket.getOutputStream()) {
					Kft0300 kft0300 = null;
					Kft0310 kft0310 = null;
					Kft0320 kft0320 = null;
					Kft0600 kft0600 = null;
					Kft0610 kft0610 = null;
					Kft0620 kft0620 = null;
					Kft0630 kft0630 = null;
					Kft0640 kft0640 = null;
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////// 업무개시요구 ///////////////////////////////////////////////
					kft0600 = new Kft0600(); // 업무개시요구
					kft0600.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
					kft0600.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
					kft0600.setBnkCd("057"); // 3 송수신 은행 코드
					kft0600.setTlgKndDvsnCd("0600"); // 6 전문 종별 코드
					kft0600.setTrDvsnCd("S"); // 10 거래 구분 코드
					kft0600.setSndRcvTp("B"); // 11 송수신 FLAG
					kft0600.setFileNm(""); // 12 파일명
					kft0600.setRespCd("000"); // 20 응답코드
					kft0600.setTlgTrDttm(LocalDateTime.now()); // 23 전문 송신시간
					kft0600.setBizMngmInfo("001"); // 33 업무관리정보
					kft0600.setSndrNm(sndrNm); // 36 송신자명
					kft0600.setSndrPswrd(sndrPswrd); // 56 송신자 암호
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////// 송신자암호 /////////////////////////////////////////////////
					kft0600.setSndrPswrd(EXUtils.encSndrPswrd(
					kft0600.getBnkCd(),
					kft0600.getTlgTrDttm(),
					kft0600.getSndrNm(),
					kft0600.getSndrPswrd())); // 56 송신자 암호
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
					log.debug("{}", kft0600);
					byte[] byteArray = kft0600.toByteArray();
					String tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
					log.info(">{}]", tlgCtt);
					IOUtils.write(byteArray, outputStream);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////// 파일정보수신지시 | 업무개시통보 ////////////////////////////
					int tryCnt = 0;
					while (true) {
						try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
							byteArray = IOUtils.toByteArray(inputStream, 4);
							IOUtils.write(byteArray, byteArrayOutputStream);
							byteArray = IOUtils.toByteArray(inputStream, NumberUtils.toInt(IOUtils.toString(byteArray, "EUC-KR")));
							IOUtils.write(byteArray, byteArrayOutputStream);
							byteArray = byteArrayOutputStream.toByteArray();
						} catch (SocketTimeoutException e) {
							tryCnt++;
							if (4 < tryCnt) {
								throw e;
							}
							continue;
						}
						tryCnt = 0;
						tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
						log.info("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
						if (Strings.CS.endsWith(StringUtils.left(tlgCtt, 14), "0610")) { // 업무개시통보
							kft0610 = new Kft0610(byteArray);
							log.debug("{}", kft0610);
						}
						if (Strings.CS.endsWith(StringUtils.left(tlgCtt, 14), "0630")) { // 파일정보수신지시
							kft0630 = new Kft0630(byteArray);
							log.debug("{}", kft0630);
						}
						break;
					}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////// 파일정보수신지시 | 업무개시통보 | 업무종료지시 /////////////
					tryCnt = 0;
					while (true) {
						try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
							byteArray = IOUtils.toByteArray(inputStream, 4);
							IOUtils.write(byteArray, byteArrayOutputStream);
							byteArray = IOUtils.toByteArray(inputStream, NumberUtils.toInt(IOUtils.toString(byteArray, "EUC-KR")));
							IOUtils.write(byteArray, byteArrayOutputStream);
							byteArray = byteArrayOutputStream.toByteArray();
						} catch (SocketTimeoutException e) {
							tryCnt++;
							if (4 < tryCnt) {
								throw e;
							}
							continue;
						}
						tryCnt = 0;
						tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
						log.info("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
						if (Strings.CS.endsWith(StringUtils.left(tlgCtt, 14), "0600")) { // 업무종료지시
							kft0600 = new Kft0600(byteArray);
							log.debug("{}", kft0600);
							break;
						}
						if (Strings.CS.endsWith(StringUtils.left(tlgCtt, 14), "0610")) { // 업무개시통보
							kft0610 = new Kft0610(byteArray);
							log.debug("{}", kft0610);
						}
						if (Strings.CS.endsWith(StringUtils.left(tlgCtt, 14), "0630")) { // 파일정보수신지시
							kft0630 = new Kft0630(byteArray);
							log.debug("{}", kft0630);
						}
						String fileNm = StringUtils.upperCase(kft0630.getFileNm());
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////E
////////////////////////////////////////////////////////////////////////////////
						String propNm = fileNm;
						if (6 < StringUtils.length(propNm)) {
							propNm = StringUtils.left(propNm, 4);
						}
						int tlgBytLen = 4096;
						stringArray = StringUtils.splitPreserveAllTokens(properties.getProperty(propNm, ""), ',');
						if (0 < stringArray.length) {
							int recBytLen = NumberUtils.toInt(stringArray[0]);
							if (0 < recBytLen) {
								tlgBytLen = 34 + ((4096 - 34) / recBytLen * recBytLen);
							}
						}
//						useGZip = false;
//						if (1 < stringArray.length) {
//							useGZip = Strings.CI.equalsAny(stringArray[1], "true", "gzip", "gz");
//						}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						File file = FileUtils.getFile(recv, fileNm);
//						long fileSz = FileUtils.sizeOf(file);
						long fileSz = 0L;
						if (file != null &&
							file.exists()) {
							fileSz = FileUtils.sizeOf(file);
						}
						if (file != null &&
							file.exists() &&
							fileSz == kft0630.getFileInfoDtlFileSz()) { // 해당파일기수신완료시
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////// 파일정보수신보고 ///////////////////////////////////
							kft0640 = new Kft0640();
							kft0640.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
							kft0640.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
							kft0640.setBnkCd("057"); // 3 송수신 은행 코드
							kft0640.setTlgKndDvsnCd("0640"); // 6 전문 종별 코드
							kft0640.setTrDvsnCd("S"); // 10 거래 구분 코드
							kft0640.setSndRcvTp("B"); // 11 송수신 FLAG
							kft0640.setFileNm(fileNm); // 12 파일명
							kft0640.setRespCd("630"); // 20 응답코드
							kft0640.setFileInfoDtlFileNm(fileNm);
							kft0640.setFileInfoDtlFileSz(fileSz);
							kft0640.setFileInfoDtlTlgBytLen(tlgBytLen);
							log.debug("{}", kft0640);
							byteArray = kft0640.toByteArray();
							tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
							log.info(">{}]", tlgCtt);
							IOUtils.write(byteArray, outputStream);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////// 파일송신완료지시 ///////////////////////////////
								tryCnt = 0;
								while (true) {
									try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
										byteArray = IOUtils.toByteArray(inputStream, 4);
										IOUtils.write(byteArray, byteArrayOutputStream);
										byteArray = IOUtils.toByteArray(inputStream, NumberUtils.toInt(IOUtils.toString(byteArray, "EUC-KR")));
										IOUtils.write(byteArray, byteArrayOutputStream);
										byteArray = byteArrayOutputStream.toByteArray();
									} catch (SocketTimeoutException e) {
										tryCnt++;
										if (4 < tryCnt) {
											throw e;
										}
										continue;
									}
									tryCnt = 0;
									tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
									log.info("<{}]", tlgCtt);
									kft0600 = new Kft0600(byteArray); // 파일송신완료지시
									log.debug("{}", kft0600);
									break;
								}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						} else {
							file = FileUtils.getFile(back, StringUtils.join(fileNm, "_",
							LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))));
//							fileSz = FileUtils.sizeOf(file);
//							fileSz = 0L;
//							if (file != null &&
//								file.exists()) {
//								fileSz = FileUtils.sizeOf(file);
//							}
							fileSz = 0L; // 항상새로받기
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////// 파일정보수신보고 ///////////////////////////////////
							kft0640 = new Kft0640();
							kft0640.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
							kft0640.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
							kft0640.setBnkCd("057"); // 3 송수신 은행 코드
							kft0640.setTlgKndDvsnCd("0640"); // 6 전문 종별 코드
							kft0640.setTrDvsnCd("S"); // 10 거래 구분 코드
							kft0640.setSndRcvTp("B"); // 11 송수신 FLAG
							kft0640.setFileNm(fileNm); // 12 파일명
							kft0640.setRespCd("000"); // 20 응답코드
							kft0640.setFileInfoDtlFileNm(fileNm);
							kft0640.setFileInfoDtlFileSz(fileSz);
							kft0640.setFileInfoDtlTlgBytLen(tlgBytLen);
							log.debug("{}", kft0640);
							byteArray = kft0640.toByteArray();
							tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
							log.info(">{}]", tlgCtt);
							IOUtils.write(byteArray, outputStream);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////// 파일처리 ///////////////////////////////////////////
//							int tlgBytLen = NumberUtils.min(
//								kft0630.getFileInfoDtlTlgBytLen(),
//								kft0640.getFileInfoDtlTlgBytLen());
//							log.debug("{}", tlgBytLen);
							Kft0320[] kft0320Array = new Kft0320[100];
//							try (BufferedOutputStream bufferedOutputStream = IOUtils.buffer(FileUtils.newOutputStream(file, true))) {
							try (BufferedOutputStream bufferedOutputStream = IOUtils.buffer(FileUtils.newOutputStream(file, false))) { // 항상새로받기
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////// 파일송신완료지시 ///////////////////////////////
								tryCnt = 0;
								while (true) {
									try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
										byteArray = IOUtils.toByteArray(inputStream, 4);
										IOUtils.write(byteArray, byteArrayOutputStream);
										byteArray = IOUtils.toByteArray(inputStream, NumberUtils.toInt(IOUtils.toString(byteArray, "EUC-KR")));
										IOUtils.write(byteArray, byteArrayOutputStream);
										byteArray = byteArrayOutputStream.toByteArray();
									} catch (SocketTimeoutException e) {
										tryCnt++;
										if (4 < tryCnt) {
											throw e;
										}
										continue;
									}
									tryCnt = 0;
									tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
									log.info("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
									if (Strings.CS.endsWith(StringUtils.left(tlgCtt, 14), "0310")) { // 결번DATA송신
										kft0310 = new Kft0310(byteArray);
										log.debug("{}", kft0310);
										kft0320 = new Kft0320();
										kft0320.setTcpipSndByt(kft0310.getTcpipSndByt()); // TCP/IP 송수신 BYTE 수
										kft0320.setBizDvsnCd(kft0310.getBizDvsnCd()); // 0 System id = "FTS"
										kft0320.setBnkCd(kft0310.getBnkCd()); // 3 송수신 은행 코드
										kft0320.setTlgKndDvsnCd(kft0310.getTlgKndDvsnCd()); // 6 전문 종별 코드
										kft0320.setTrDvsnCd(kft0310.getTrDvsnCd()); // 10 거래 구분 코드
										kft0320.setSndRcvTp(kft0310.getSndRcvTp()); // 11 송수신 FLAG
										kft0320.setFileNm(kft0310.getFileNm()); // 12 파일명
										kft0320.setRespCd(kft0310.getRespCd()); // 20 응답코드
										kft0320.setBlckNo(kft0310.getBlckNo()); // 23 BLOCK NO
										kft0320.setSqncNo(kft0310.getSqncNo()); // 27 최종SEQ NO
										kft0320.setActlDatBytLen(kft0310.getActlDatBytLen()); // 30 RECORD 수
										kft0320.setFileDtl(kft0310.getFileDtl()); // 34 업무별 RECORD
										kft0320Array[kft0320.getSqncNo() - 1] = kft0320;
										continue;
									}
									if (Strings.CS.endsWith(StringUtils.left(tlgCtt, 14), "0320")) { // DATA송신
										kft0320 = new Kft0320(byteArray);
										kft0320Array[kft0320.getSqncNo() - 1] = kft0320;
										log.debug("{}", kft0320);
										continue;
									}
									if (Strings.CS.endsWith(StringUtils.left(tlgCtt, 14), "0620")) { // 결번확인지시
										kft0620 = new Kft0620(byteArray);
										log.debug("{}", kft0620);
										int msngNmbrCnt = 0;
										StringBuilder msngNmbrChck = new StringBuilder();
										for (int i = 0; i < kft0320.getSqncNo(); i++) {
											if (null == kft0320Array[i]) {
												msngNmbrCnt++;
												msngNmbrChck.append('0');
											} else {
												msngNmbrChck.append('1');
											}
										}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////// 결번확인보고 ///////////////////////////
										kft0300 = new Kft0300();
										kft0300.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
										kft0300.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
										kft0300.setBnkCd("057"); // 3 송수신 은행 코드
										kft0300.setTlgKndDvsnCd("0300"); // 6 전문 종별 코드
										kft0300.setTrDvsnCd("S"); // 10 거래 구분 코드
										kft0300.setSndRcvTp("B"); // 11 송수신 FLAG
										kft0300.setFileNm(fileNm); // 12 파일명
										kft0300.setRespCd("000"); // 20 응답코드
										kft0300.setBlckNo(kft0320.getBlckNo()); // 23 BLOCK NO
										kft0300.setSqncNo(kft0320.getSqncNo()); // 27 최종SEQ NO
										kft0300.setMsngNmbrCnt(msngNmbrCnt); // 30 결번 갯수
										kft0300.setMsngNmbrChck(String.valueOf(msngNmbrChck)); // 33 결번 확인
										log.debug("{}", kft0300);
										byteArray = kft0300.toByteArray();
										tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
										log.info(">{}]", tlgCtt);
										IOUtils.write(byteArray, outputStream);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
										if (0 < msngNmbrCnt) {
											continue;
										}
										for (int i = 0; i < kft0320.getSqncNo(); i++) {
											bufferedOutputStream.write(kft0320Array[i].getFileDtl(), 0, kft0320Array[i].getActlDatBytLen());
										}
										bufferedOutputStream.flush();
										kft0320Array = new Kft0320[100];
										continue;
									}
									kft0600 = new Kft0600(byteArray); // 파일송신완료지시
									log.debug("{}", kft0600);
									break;
								}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
							}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////// 파일송수신후속처리 /////////////////////////////////
//							fileSz = FileUtils.sizeOf(file);
							fileSz = 0L;
							if (file != null &&
								file.exists()) {
								fileSz = FileUtils.sizeOf(file);
							}
							Path path = Files.copy(file.toPath(), FileUtils.getFile(recv, fileNm).toPath(),
							StandardCopyOption.REPLACE_EXISTING);
							log.info("copied {}, {}", file, path);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////// 파일송수신정보전송 /////////////////////////////////
							String apiTrxNo = StringUtils.join(StringUtils.leftPad("057", 10, '0'),
							StringUtils.right(String.valueOf(System.currentTimeMillis()), 10));;
							String apiTrxDtm = kft0600.getTlgTrDttm().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
							JSONObject jsonObject = new JSONObject();
							jsonObject.put("api_trx_no", apiTrxNo);
							jsonObject.put("api_trx_dtm", apiTrxDtm);
							jsonObject.put("file_name", fileNm);
							jsonObject.put("file_size", fileSz);
							jsonObject.put("tr_dvsn_cd", "R");
							log.debug("{}", jsonObject.toString(2));
							String tmpCtt = String.valueOf(jsonObject);
							log.debug(">{}]", tmpCtt);
							int i = Message.DEFAULT_PRIORITY;
							EXUtils.write(i, cmnBlckQueNm, tmpCtt);
							cmnBlckQue.put(Map.entry(tmpCtt, String.valueOf(i)));
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 파일송신완료지시 ///////////////////////////////////////
//						tryCnt = 0;
//						while (!Strings.CS.endsWith(StringUtils.left(tlgCtt, 14), "0600")) { // 파일송신완료지시
//							try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
//								byteArray = IOUtils.toByteArray(inputStream, 4);
//								IOUtils.write(byteArray, byteArrayOutputStream);
//								byteArray = IOUtils.toByteArray(inputStream, NumberUtils.toInt(IOUtils.toString(byteArray, "EUC-KR")));
//								IOUtils.write(byteArray, byteArrayOutputStream);
//								byteArray = byteArrayOutputStream.toByteArray();
//							} catch (SocketTimeoutException e) {
//								tryCnt++;
//								if (4 < tryCnt) {
//									throw e;
//								}
//								continue;
//							}
//							tryCnt = 0;
//							tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
//							log.info("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
//							kft0600 = new Kft0600(byteArray); // 파일송신완료지시
//							log.debug("{}", kft0600);
//							break;
//						}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 파일송신완료보고 ///////////////////////////////////////
						kft0610 = new Kft0610();
						kft0610.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
						kft0610.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
						kft0610.setBnkCd("057"); // 3 송수신 은행 코드
						kft0610.setTlgKndDvsnCd("0610"); // 6 전문 종별 코드
						kft0610.setTrDvsnCd("S"); // 10 거래 구분 코드
						kft0610.setSndRcvTp("B"); // 11 송수신 FLAG
						kft0610.setFileNm(fileNm); // 12 파일명
						kft0610.setRespCd("000"); // 20 응답코드
						kft0610.setTlgTrDttm(LocalDateTime.now()); // 23 전문 송신시간
						kft0610.setBizMngmInfo(kft0600.getBizMngmInfo()); // 33 업무관리정보
						kft0610.setSndrNm(sndrNm); // 36 송신자명
						kft0610.setSndrPswrd(sndrPswrd); // 56 송신자 암호
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 송신자암호 /////////////////////////////////////////////
						kft0610.setSndrPswrd(EXUtils.encSndrPswrd(
						kft0610.getBnkCd(),
						kft0610.getTlgTrDttm(),
						kft0610.getSndrNm(),
						kft0610.getSndrPswrd())); // 56 송신자 암호
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						log.debug("{}", kft0610);
						byteArray = kft0610.toByteArray();
						tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
						log.info(">{}]", tlgCtt);
						IOUtils.write(byteArray, outputStream);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						if (Strings.CS.equals(kft0600.getBizMngmInfo(), "003")) { // 파일송수신완료(송신할파일없음)
							break;
						}
					}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////// 업무종료지시 ///////////////////////////////////////////////
					tryCnt = 0;
					while (!Strings.CS.equals(kft0600.getBizMngmInfo(), "004")) { // 업무종료상태가아닐때
						try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
							byteArray = IOUtils.toByteArray(inputStream, 4);
							IOUtils.write(byteArray, byteArrayOutputStream);
							byteArray = IOUtils.toByteArray(inputStream, NumberUtils.toInt(IOUtils.toString(byteArray, "EUC-KR")));
							IOUtils.write(byteArray, byteArrayOutputStream);
							byteArray = byteArrayOutputStream.toByteArray();
						} catch (SocketTimeoutException e) {
							tryCnt++;
							if (4 < tryCnt) {
								throw e;
							}
							continue;
						}
						tryCnt = 0;
						tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
						log.info("<{}]", MethodUtils.invokeExactMethod(StringUtils.defaultString(tlgCtt), "toString"));
						kft0600 = new Kft0600(byteArray);
						log.debug("{}", kft0600);
						break;
					}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////// 업무종료보고 ///////////////////////////////////////////////
					kft0610 = new Kft0610();
					kft0610.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
					kft0610.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
					kft0610.setBnkCd("057"); // 3 송수신 은행 코드
					kft0610.setTlgKndDvsnCd("0610"); // 6 전문 종별 코드
					kft0610.setTrDvsnCd("S"); // 10 거래 구분 코드
					kft0610.setSndRcvTp("B"); // 11 송수신 FLAG
					kft0610.setFileNm(""); // 12 파일명
					kft0610.setRespCd("000"); // 20 응답코드
					kft0610.setTlgTrDttm(LocalDateTime.now()); // 23 전문 송신시간
					kft0610.setBizMngmInfo("004"); // 33 업무관리정보
					kft0610.setSndrNm(sndrNm); // 36 송신자명
					kft0610.setSndrPswrd(sndrPswrd); // 56 송신자 암호
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////// 송신자암호 /////////////////////////////////////////////////
					kft0610.setSndrPswrd(EXUtils.encSndrPswrd(
					kft0610.getBnkCd(),
					kft0610.getTlgTrDttm(),
					kft0610.getSndrNm(),
					kft0610.getSndrPswrd())); // 56 송신자 암호
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
					log.debug("{}", kft0610);
					byteArray = kft0610.toByteArray();
					tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
					log.info(">{}]", tlgCtt);
					IOUtils.write(byteArray, outputStream);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
				}
			} catch (Throwable t) {
				if (t instanceof ConnectException) {
					log.error(ExceptionUtils.getRootCauseMessage(t));
				} else {
					log.error(ExceptionUtils.getRootCauseMessage(t), t);
				}
			}
			ssnStts.setValue("0");
			for (int i = 0; i < 600 && !executorService.isShutdown(); i++) {
				ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
			}
		}
		log.info("stop");
	}

}

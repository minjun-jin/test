package com.jpmc.kcg.ext;

import java.io.BufferedInputStream;
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
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import javax.net.SocketFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
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
public class ExtFSnd1 implements Runnable {

	private final Properties properties;
	private final ExecutorService executorService;
	private final Map<String, BlockingQueue<Entry<String, String>>> blckQueMap;
	private final Map<String, Entry<String, String>> ssnSttsMap;
	private final String propertyName;

	@Override
	public void run() {
		Entry<String, String> ssnStts = ssnSttsMap.get(propertyName);
		Entry<String, String> rcvStts = ssnSttsMap.get(Strings.CS.replace(propertyName, "FSND_", "FRCV_"));
		String cmnBlckQueNm = "QCMN_BFT";
		BlockingQueue<Entry<String, String>> cmnBlckQue = blckQueMap.get(cmnBlckQueNm);
		IOFileFilter ioFileFilter = new RegexFileFilter(properties.getProperty(Strings.CS.replace(propertyName, "FSND_", "FLTR_")));
		File back = FileUtils.getFile(properties.getProperty("PATH_BACK", "/home/ec2-user/ext/shrd/back"));
		File send = FileUtils.getFile(properties.getProperty("PATH_SEND", "/home/ec2-user/ext/shrd/send"));
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
		SocketFactory socketFactory = SocketFactory.getDefault();
		while (!executorService.isShutdown()) {
			ssnStts.setValue("0");
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 차단시간처리 ///////////////////////////////////////////////////////
			LocalTime localTime = LocalTime.now();
			if (23 == localTime.getHour() &&
				45 <= localTime.getMinute()) {
				ssnStts.setValue("0");
				for (int i = 0; i < 60 && !executorService.isShutdown(); i++) {
					ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
				}
				continue;
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 송신파일목록 ///////////////////////////////////////////////////////
			Collection<File> collection = FileUtils.listFiles(send, ioFileFilter, FalseFileFilter.FALSE);
			if (ObjectUtils.isEmpty(collection)) {
				ssnStts.setValue("0");
				for (int i = 0; i < 600 && !executorService.isShutdown(); i++) {
					ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
				}
				continue;
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 동시수행방지 ///////////////////////////////////////////////////////
			synchronized (ssnSttsMap) {
				ssnStts.setValue("1");
				if (Strings.CS.equals(rcvStts.getValue(), "1")) {
					ssnStts.setValue("0");
					ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
					continue;
				}
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
			ssnStts.setValue("1");
			try (Socket socket = socketFactory.createSocket(host, port)) {
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
					kft0600 = new Kft0600();
					kft0600.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
					kft0600.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
					kft0600.setBnkCd("057"); // 3 송수신 은행 코드
					kft0600.setTlgKndDvsnCd("0600"); // 6 전문 종별 코드
					kft0600.setTrDvsnCd("R"); // 10 거래 구분 코드
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
/////////////////// 업무개시통보 ///////////////////////////////////////////////
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
						log.info("<{}]", tlgCtt);
						kft0610 = new Kft0610(byteArray);
						log.debug("{}", kft0610);
						break;
					}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
					Iterator<File> iterator = collection.iterator();
					while (iterator.hasNext()) {
						File file = iterator.next();
//						long fileSz = FileUtils.sizeOf(file);
						long fileSz = 0L;
						if (file != null &&
							file.exists()) {
							fileSz = FileUtils.sizeOf(file);
						}
						String fileNm = StringUtils.upperCase(file.getName());
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
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
/////////////////////// 파일정보수신요구 ///////////////////////////////////////
						kft0630 = new Kft0630();
						kft0630.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
						kft0630.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
						kft0630.setBnkCd("057"); // 3 송수신 은행 코드
						kft0630.setTlgKndDvsnCd("0630"); // 6 전문 종별 코드
						kft0630.setTrDvsnCd("R"); // 10 거래 구분 코드
						kft0630.setSndRcvTp("B"); // 11 송수신 FLAG
						kft0630.setFileNm(fileNm); // 12 파일명
						kft0630.setRespCd("000"); // 20 응답코드
						kft0630.setFileInfoDtlFileNm(fileNm);
						kft0630.setFileInfoDtlFileSz(fileSz);
						kft0630.setFileInfoDtlTlgBytLen(tlgBytLen);
						log.debug("{}", kft0630);
						byteArray = kft0630.toByteArray();
						tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
						log.info(">{}]", tlgCtt);
						IOUtils.write(byteArray, outputStream);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 파일정보수신통보 ///////////////////////////////////////
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
							kft0640 = new Kft0640(byteArray);
							log.debug("{}", kft0640);
							break;
						}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 파일처리 ///////////////////////////////////////////////
						if (Strings.CS.equals(kft0640.getRespCd(), "000")) { // 정상
							tlgBytLen = NumberUtils.min(
								kft0630.getFileInfoDtlTlgBytLen(),
								kft0640.getFileInfoDtlTlgBytLen());
							log.debug("tlgBytLen = {}", tlgBytLen);
							int tlgDtlLen = (tlgBytLen - 34);
							log.debug("tlgDtlLen = {}", tlgDtlLen);
							int blckNo = (int) (kft0640.getFileInfoDtlFileSz() / (100 * tlgDtlLen));
							log.debug("blckNo = {}", blckNo);
							int sqncNo = (int) (kft0640.getFileInfoDtlFileSz() % (100 * tlgDtlLen)) / tlgDtlLen;
							log.debug("sqncNo = {}", sqncNo);
							Kft0320[] kft0320Array = new Kft0320[100];
							try (BufferedInputStream bufferedInputStream = IOUtils.buffer(FileUtils.openInputStream(file))) {
								IOUtils.skipFully(inputStream, ((100 * tlgDtlLen) * blckNo) + (tlgDtlLen * sqncNo));
								while (0 < bufferedInputStream.available()) {
									blckNo++;
									while (0 < bufferedInputStream.available() &&
										sqncNo < 100) {
										sqncNo++;
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////// DATA송신 ///////////////////////////////
										kft0320 = new Kft0320();
										kft0320.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
										kft0320.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
										kft0320.setBnkCd("057"); // 3 송수신 은행 코드
										kft0320.setTlgKndDvsnCd("0320"); // 6 전문 종별 코드
										kft0320.setTrDvsnCd("R"); // 10 거래 구분 코드
										kft0320.setSndRcvTp("B"); // 11 송수신 FLAG
										kft0320.setFileNm(fileNm); // 12 파일명
										kft0320.setRespCd("000"); // 20 응답코드
										kft0320.setBlckNo(blckNo); // 23 BLOCK NO
										kft0320.setSqncNo(sqncNo); // 27 최종SEQ NO
										byteArray = IOUtils.byteArray(tlgDtlLen);
										int datBytLen = IOUtils.read(bufferedInputStream, byteArray);
										if (datBytLen < tlgDtlLen) {
											byteArray = ArrayUtils.subarray(byteArray, 0, datBytLen);
										}
										kft0320.setActlDatBytLen(datBytLen); // 30 RECORD 수
										kft0320.setFileDtl(byteArray); // 34 업무별 RECORD
										kft0320Array[sqncNo - 1] = kft0320;
										log.debug("{}", kft0320);
										byteArray = kft0320.toByteArray();
										tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
										log.info(">{}]", tlgCtt);
										IOUtils.write(byteArray, outputStream);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
									}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////// 결번처리 ///////////////////////////////////
									tryCnt = 0;
									while (true) {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////// 결번확인요구 ///////////////////////////
										kft0620 = new Kft0620();
										kft0620.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
										kft0620.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
										kft0620.setBnkCd("057"); // 3 송수신 은행 코드
										kft0620.setTlgKndDvsnCd("0620"); // 6 전문 종별 코드
										kft0620.setTrDvsnCd("R"); // 10 거래 구분 코드
										kft0620.setSndRcvTp("B"); // 11 송수신 FLAG
										kft0620.setFileNm(fileNm); // 12 파일명
										kft0620.setRespCd("000"); // 20 응답코드
										kft0620.setBlckNo(blckNo); // 23 BLOCK NO
										kft0620.setSqncNo(sqncNo); // 27 최종SEQ NO
										log.debug("{}", kft0620);
										byteArray = kft0620.toByteArray();
										tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
										log.info(">{}]", tlgCtt);
										IOUtils.write(byteArray, outputStream);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////// 결번확인통보 ///////////////////////////
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
										kft0300 = new Kft0300(byteArray);
										log.debug("{}", kft0300);
										if (0 == kft0300.getMsngNmbrCnt()) { // 결번없음
											break;
										}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////// 결번DATA송신 ///////////////////////////
										sqncNo = 0;
										String msngNmbrChck = kft0300.getMsngNmbrChck();
										while (sqncNo < StringUtils.length(msngNmbrChck)) {
											if ('1' == msngNmbrChck.charAt(sqncNo)) { // 수신완료
												sqncNo++;
												continue;
											}
											kft0320 = kft0320Array[sqncNo];
											sqncNo++;
											kft0310 = new Kft0310();
											kft0310.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
											kft0310.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
											kft0310.setBnkCd("057"); // 3 송수신 은행 코드
											kft0310.setTlgKndDvsnCd("0310"); // 6 전문 종별 코드
											kft0310.setTrDvsnCd("R"); // 10 거래 구분 코드
											kft0310.setSndRcvTp("B"); // 11 송수신 FLAG
											kft0310.setFileNm(fileNm); // 12 파일명
											kft0310.setRespCd("000"); // 20 응답코드
											kft0310.setBlckNo(blckNo); // 23 BLOCK NO
											kft0310.setSqncNo(sqncNo); // 27 최종SEQ NO
											kft0310.setActlDatBytLen(kft0320.getActlDatBytLen()); // 30 RECORD 수
											kft0310.setFileDtl(kft0320.getFileDtl()); // 34 업무별 RECORD
											log.debug("{}", kft0310);
											byteArray = kft0310.toByteArray();
											tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
											log.info(">{}]", tlgCtt);
											IOUtils.write(byteArray, outputStream);
										}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
									}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
									sqncNo = 0;
								}
							}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////// 파일송수신후속처리 /////////////////////////////////
							Path path = Files.move(file.toPath(), FileUtils.getFile(back, StringUtils.join(fileNm, "_",
							LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))).toPath(),
							StandardCopyOption.REPLACE_EXISTING);
							log.info("moved {}, {}", file, path);
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
							jsonObject.put("tr_dvsn_cd", "S");
							log.debug("{}", jsonObject.toString(2));
							String tmpCtt = String.valueOf(jsonObject);
							log.debug(">{}]", tmpCtt);
							int i = Message.DEFAULT_PRIORITY;
							EXUtils.write(i, cmnBlckQueNm, tmpCtt);
							cmnBlckQue.put(Map.entry(tmpCtt, String.valueOf(i)));
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						} else { // 오류
							log.error("{}:{}", kft0640.getRespCd(), EXUtils.newRespMsg(kft0640.getRespCd()));
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////// 파일송수신실패처리 /////////////////////////////////
							Path path = Files.move(file.toPath(), FileUtils.getFile(back, StringUtils.join(fileNm, "_",
							LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))).toPath(),
							StandardCopyOption.REPLACE_EXISTING);
							log.info("moved {}, {}", file, path);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 파일송신완료요구 ///////////////////////////////////////
						kft0600 = new Kft0600();
						kft0600.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
						kft0600.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
						kft0600.setBnkCd("057"); // 3 송수신 은행 코드
						kft0600.setTlgKndDvsnCd("0600"); // 6 전문 종별 코드
						kft0600.setTrDvsnCd("R"); // 10 거래 구분 코드
						kft0600.setSndRcvTp("B"); // 11 송수신 FLAG
						kft0600.setFileNm(fileNm); // 12 파일명
						kft0600.setRespCd("000"); // 20 응답코드
						kft0600.setTlgTrDttm(LocalDateTime.now()); // 23 전문 송신시간
						kft0600.setBizMngmInfo(iterator.hasNext() ? "002" : "003"); // 33 업무관리정보
						if (executorService.isShutdown()) { // 종료요청강제보정
							kft0600.setBizMngmInfo("003");
						}
						kft0600.setSndrNm(sndrNm); // 36 송신자명
						kft0600.setSndrPswrd(sndrPswrd); // 56 송신자 암호
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 송신자암호 /////////////////////////////////////////////
						kft0600.setSndrPswrd(EXUtils.encSndrPswrd(
						kft0600.getBnkCd(),
						kft0600.getTlgTrDttm(),
						kft0600.getSndrNm(),
						kft0600.getSndrPswrd())); // 56 송신자 암호
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						log.debug("{}", kft0600);
						byteArray = kft0600.toByteArray();
						tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
						log.info(">{}]", tlgCtt);
						IOUtils.write(byteArray, outputStream);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 파일송신완료통보 ///////////////////////////////////////
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
							kft0610 = new Kft0610(byteArray);
							log.debug("{}", kft0610);
							break;
						}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						if (Strings.CS.equals(kft0600.getBizMngmInfo(), "003")) { // 파일송수신완료(송신할파일없음)
							break;
						}
					}
					tryCnt = 0;
					while (true) {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 업무종료요구 ///////////////////////////////////////////
						kft0600 = new Kft0600();
						kft0600.setTcpipSndByt(0); // TCP/IP 송수신 BYTE 수
						kft0600.setBizDvsnCd(bizDvsnCd); // 0 System id = "FTS"
						kft0600.setBnkCd("057"); // 3 송수신 은행 코드
						kft0600.setTlgKndDvsnCd("0600"); // 6 전문 종별 코드
						kft0600.setTrDvsnCd("R"); // 10 거래 구분 코드
						kft0600.setSndRcvTp("B"); // 11 송수신 FLAG
						kft0600.setFileNm(""); // 12 파일명
						kft0600.setRespCd("000"); // 20 응답코드
						kft0600.setTlgTrDttm(LocalDateTime.now()); // 23 전문 송신시간
						kft0600.setBizMngmInfo("004"); // 33 업무관리정보
						kft0600.setSndrNm(sndrNm); // 36 송신자명
						kft0600.setSndrPswrd(sndrPswrd); // 56 송신자 암호
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 송신자암호 /////////////////////////////////////////////
						kft0600.setSndrPswrd(EXUtils.encSndrPswrd(
						kft0600.getBnkCd(),
						kft0600.getTlgTrDttm(),
						kft0600.getSndrNm(),
						kft0600.getSndrPswrd())); // 56 송신자 암호
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						log.debug("{}", kft0600);
						byteArray = kft0600.toByteArray();
						tlgCtt = IOUtils.toString(byteArray, "EUC-KR");
						log.info(">{}]", tlgCtt);
						IOUtils.write(byteArray, outputStream);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 업무종료통보 ///////////////////////////////////////////
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
						kft0610 = new Kft0610(byteArray);
						log.debug("{}", kft0610);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						break;
					}
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

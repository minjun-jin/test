package com.jpmc.kcg.ext;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONObject;
import org.slf4j.MDC;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import com.jcraft.jsch.SftpProgressMonitorImpl;

import jakarta.jms.Message;
import lombok.Generated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Generated
@RequiredArgsConstructor
@Slf4j
public class ExtFRcv2 implements Runnable {

	private final Properties properties;
	private final ExecutorService executorService;
	private final Map<String, BlockingQueue<Entry<String, String>>> blckQueMap;
	private final Map<String, Entry<String, String>> ssnSttsMap;
	private final String propertyName;

	@Override
	public void run() {
		Entry<String, String> ssnStts = ssnSttsMap.get(propertyName);
//		Entry<String, String> sndStts = ssnSttsMap.get(Strings.CS.replace(propertyName, "FRCV_", "FSND_"));
		String cmnBlckQueNm = "QCMN_BFT";
		BlockingQueue<Entry<String, String>> cmnBlckQue = blckQueMap.get(cmnBlckQueNm);
		File shrd = FileUtils.getFile(properties.getProperty("PATH_SHRD", "/home/ec2-user/ext/shrd"));
		File back = FileUtils.getFile(properties.getProperty("PATH_BACK", "/home/ec2-user/ext/shrd/back"));
		File recv = FileUtils.getFile(properties.getProperty("PATH_RECV", "/home/ec2-user/ext/shrd/recv"));
		File auth = FileUtils.getFile(shrd, StringUtils.join("kcg_", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")), ".token"));
		File temp = FileUtils.getFile(shrd, StringUtils.join("kcg_", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")), ".recv2"));
		boolean useGZip = false;
		boolean useTemp = false;
		String[] stringArray = StringUtils.split(properties.getProperty(propertyName), '@');
		String http = stringArray[0];
		stringArray = StringUtils.split(stringArray[1], ':');
		String host = stringArray[0];
		int port = NumberUtils.toInt(stringArray[1]);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// 인증정보 ///////////////////////////////////////////////////////////////
		stringArray = StringUtils.split(properties.getProperty(Strings.CS.replace(propertyName, "FRCV_", "AUTH_")), ':');
		String clientId = stringArray[0];
		String clientSecret = stringArray[1];
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
				45 <= localTime.getMinute()) {
				ssnStts.setValue("0");
				for (int i = 0; i < 60 && !executorService.isShutdown(); i++) {
					ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
				}
				continue;
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
			ssnStts.setValue("1");
			try {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////// 토큰발급 ///////////////////////////////////////////////////////
				String bodyStr = null;
				JSONObject jsonObject = null;
				HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
				synchronized (ssnSttsMap) {
					if (System.currentTimeMillis() >
						Duration.ofDays(90 - 1).toMillis() + auth.lastModified()) {
						int tryCnt = 0;
						while (true) {
							bodyStr = StringUtils.join(
								"client_id=", clientId, "&",
								"client_secret=", clientSecret, "&",
								"grant_type=client_credentials"
							);
							log.debug(">{}", bodyStr);
							HttpResponse<String> httpResponse;
							try {
								HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(StringUtils.join(http, "/oauth/2.0/token"))).headers(
									"Content-Type", "application/x-www-form-urlencoded; charset=UTF-8"
								).POST(BodyPublishers.ofString(bodyStr)).timeout(Duration.ofSeconds(30L)).build();
								log.trace(">{}", httpRequest);
								httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
								log.trace("<{}", httpResponse);
							} catch (HttpTimeoutException e) {
								log.error(ExceptionUtils.getRootCauseMessage(e));
								tryCnt++;
								if (2 < tryCnt) {
									throw e;
								}
								ThreadUtils.sleepQuietly(Duration.ofSeconds(30L));
								continue;
							}
							tryCnt = 0;
							bodyStr = httpResponse.body();
							log.debug("<{}", bodyStr);
							jsonObject = new JSONObject(bodyStr);
							log.debug("<{}", jsonObject.toString(2));
							break;
						}
						String rspCode = jsonObject.optString("rsp_code");
						if (StringUtils.isNotEmpty(rspCode)) {
							String rspMessage = jsonObject.optString("rsp_message");
							log.error("{}:{}", rspCode, rspMessage);
							ssnStts.setValue("0");
							for (int i = 0; i < 600 && !executorService.isShutdown(); i++) {
								ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
							}
							continue;
						}
						FileUtils.write(auth, bodyStr, StandardCharsets.UTF_8);
					} else {
						bodyStr = FileUtils.readFileToString(auth, StandardCharsets.UTF_8);
						log.debug("!{}", bodyStr);
						jsonObject = new JSONObject(bodyStr);
						log.debug("!{}", jsonObject.toString(2));
					}
				}
				String accessToken = jsonObject.optString("access_token");
				String tokenType = jsonObject.optString("token_type");
//				long expiresIn = jsonObject.optLong("expires_in");
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
				String resultCode = "R0000"; // 처리 정상
				while (!executorService.isShutdown() && Strings.CS.equals(resultCode, "R0000")) {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////// 수신개시요구/통보 //////////////////////////////////////////
					useTemp = temp.exists();
					if (useTemp) {
						bodyStr = FileUtils.readFileToString(temp, StandardCharsets.UTF_8);
						log.debug("!{}", bodyStr);
						jsonObject = new JSONObject(bodyStr);
						log.debug("!{}", jsonObject.toString(2));
					} else {
						jsonObject = new JSONObject();
						jsonObject.put("file_name", "");
						jsonObject.put("compressed_file_name", "");
						log.debug(">{}", jsonObject.toString(2));
						bodyStr = String.valueOf(jsonObject);
						log.debug(">{}", bodyStr);
						String apiTrxNo = StringUtils.join(StringUtils.leftPad("057", 10, '0'),
						StringUtils.right(String.valueOf(System.currentTimeMillis()), 10));
						log.debug("apiTrxNo  = {}", apiTrxNo);
						String apiTrxDtm = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
						log.debug("apiTrxDtm = {}", apiTrxDtm);
						int statusCode = HttpURLConnection.HTTP_OK;
						int tryCnt = 0;
						while (true) {
							HttpResponse<String> httpResponse;
							try {
								HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(StringUtils.join(http, "/transfer/recvinit"))).headers(
									"Content-Type", "application/json; charset=UTF-8",
									"Authorization", StringUtils.join(tokenType, " ", accessToken),
									"api_trx_no", apiTrxNo,
									"api_trx_dtm", apiTrxDtm
								).POST(BodyPublishers.ofString(bodyStr)).timeout(Duration.ofSeconds(30L)).build();
								log.trace(">{}", httpRequest);
								httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
								log.trace("<{}", httpResponse);
							} catch (HttpTimeoutException e) {
								log.error(ExceptionUtils.getRootCauseMessage(e));
								tryCnt++;
								if (2 < tryCnt) {
									throw e;
								}
								ThreadUtils.sleepQuietly(Duration.ofSeconds(30L));
								continue;
							}
							tryCnt = 0;
							bodyStr = httpResponse.body();
							log.debug("<{}", bodyStr);
							jsonObject = new JSONObject(bodyStr);
							log.debug("<{}", jsonObject.toString(2));
							statusCode = httpResponse.statusCode();
							break;
						}
						if (HttpURLConnection.HTTP_UNAUTHORIZED == statusCode) {
							FileUtils.deleteQuietly(auth);
							break;
						}
						if (HttpURLConnection.HTTP_OK           != statusCode) {
							break;
						}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 복구정보추가설정 ///////////////////////////////////////
						jsonObject.put("api_trx_no", apiTrxNo);
						jsonObject.put("api_trx_dtm", apiTrxDtm);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
					}
					String apiTrxNo = jsonObject.optString("api_trx_no");
					String apiTrxDtm = jsonObject.optString("api_trx_dtm");
					String rspCode = jsonObject.optString("rsp_code");
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
					resultCode = "R0006"; // 기타 알수 없는 오류 발생
					if (Strings.CS.equals(rspCode, "A0000")) { // 정상 완료
						String sha1Value = jsonObject.optString("sha1_value");
						String fileName = jsonObject.optString("file_name");
						long fileSize = jsonObject.optLong("file_size");
//						String compressedfileName = null;
//						long compressedfileSize = 0L;
						String gzipName = null;
						File gzip = null;
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						String propNm = fileName;
						if (6 < StringUtils.length(propNm)) {
							propNm = StringUtils.left(propNm, 4);
						}
//						int tlgBytLen = 4096;
						stringArray = StringUtils.splitPreserveAllTokens(properties.getProperty(propNm, ""), ',');
//						if (0 < stringArray.length) {
//							int recBytLen = NumberUtils.toInt(stringArray[0]);
//							if (0 < recBytLen) {
//								tlgBytLen = 34 + ((4096 - 34) / recBytLen * recBytLen);
//							}
//						}
						useGZip = false;
						if (1 < stringArray.length) {
							useGZip = Strings.CI.equalsAny(stringArray[1], "true", "gzip", "gz");
						}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
						if (useGZip) {
//							compressedfileName = jsonObject.optString("compressed_file_name");
//							compressedfileSize = jsonObject.optLong("compressed_file_size");
//							fileName = compressedfileName;
//							fileSize = compressedfileSize;
							gzipName = StringUtils.join(fileName, "_",
							LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), ".gz");
							gzip = FileUtils.getFile(back, gzipName);
						}
						String sftpOneTimeId = jsonObject.optString("sftp_one_time_id");
						String sftpOneTimePasswd = jsonObject.optString("sftp_one_time_passwd");
						File file = FileUtils.getFile(back, StringUtils.join(fileName, "_",
						LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))));
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 파일수신 ///////////////////////////////////////////////
						FileUtils.write(temp, String.valueOf(jsonObject), StandardCharsets.UTF_8);
						JSch jsch = new JSch();
						try {
							Session session = jsch.getSession(sftpOneTimeId, host, port);
							session.setPassword(StringUtils.getBytes(sftpOneTimePasswd, StandardCharsets.UTF_8));
							session.setTimeout(30000);
							session.connect   (30000);
							resultCode = "R0002"; // 파일 송수신중 오류가 발생하여 전송 미완료
							try {
								ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
								channelSftp.connect();
								try {
									SftpProgressMonitor sftpProgressMonitor = new SftpProgressMonitorImpl();
									if (useGZip) {
										if (useTemp) {
											channelSftp.get(StringUtils.join(fileName, ".gz"), gzip.getAbsolutePath(), sftpProgressMonitor, ChannelSftp.RESUME);
										} else {
											channelSftp.get(StringUtils.join(fileName, ".gz"), gzip.getAbsolutePath(), sftpProgressMonitor);
										}
										try (InputStream inputStream = new GZIPInputStream(FileUtils.openInputStream(gzip))) {
											try (OutputStream outputStream = FileUtils.openOutputStream(file)) {
												IOUtils.copyLarge(inputStream, outputStream);
											}
										} catch (Throwable t) {
											throw new ZipException(ExceptionUtils.getRootCauseMessage(t));
										}
									} else {
										if (useTemp) {
											channelSftp.get(fileName, file.getAbsolutePath(), sftpProgressMonitor, ChannelSftp.RESUME);
										} else {
											channelSftp.get(fileName, file.getAbsolutePath(), sftpProgressMonitor);
										}
									}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////// 파일 해시값 검증 ///////////////////////////
									try (InputStream inputStream = FileUtils.openInputStream(file)) {
										if (Strings.CI.equals(sha1Value, DigestUtils.sha1Hex(inputStream))) {
											Path path = Files.copy(file.toPath(), FileUtils.getFile(recv, fileName).toPath(),
											StandardCopyOption.REPLACE_EXISTING);
											log.info("copied {}, {}", file, path);
											resultCode = "R0000"; // 처리 정상
										} else { // 검증 오류
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////// 재수신을 위해 파일 삭제 ////////////
											if (useGZip) {
												FileUtils.deleteQuietly(gzip);
											}
											FileUtils.deleteQuietly(file);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
											resultCode = "R0005"; // 파일 해시값 검증 오류
										}
									}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
								} catch (ZipException e) {
									log.error(ExceptionUtils.getRootCauseMessage(e));
									resultCode = "R0003"; // 압축파일 해제 오류
								} catch (SftpException e) {
									log.error(ExceptionUtils.getRootCauseMessage(e));
									resultCode = "R0002"; // 파일 송수신중 오류가 발생하여 전송 미완료
								} finally {
									channelSftp.disconnect();
								}
							} catch (JSchException e) {
								log.error(ExceptionUtils.getRootCauseMessage(e));
								resultCode = "R0007"; // SFTP 접속오류
							} finally {
								session.disconnect();
							}
						} catch (Throwable t) {
							log.error(ExceptionUtils.getRootCauseMessage(t), t);
							resultCode = "R0006"; // 기타 알수 없는 오류 발생
						}
						FileUtils.deleteQuietly(temp);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 전일자파일보정 /////////////////////////////////////////
						if (Strings.CS.equals(fileName, "") &&
							17 >= LocalTime.now().getHour()) {
							FileUtils.deleteQuietly(FileUtils.getFile(recv, fileName));
							if (useGZip) {
								Path path = Files.move(gzip.toPath(), FileUtils.getFile(back, StringUtils.join(fileName, "_",
								LocalDate.now().minusDays(1L).format(DateTimeFormatter.ofPattern("yyyyMMdd")), ".gz")).toPath(),
								StandardCopyOption.REPLACE_EXISTING);
								log.info("moved {}, {}", gzip, path);
							}
							Path path = Files.move(file.toPath(), FileUtils.getFile(back, StringUtils.join(fileName, "_",
							LocalDate.now().minusDays(1L).format(DateTimeFormatter.ofPattern("yyyyMMdd")))).toPath(),
							StandardCopyOption.REPLACE_EXISTING);
							log.info("moved {}, {}", file, path);
						}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////// 파일송수신정보전송 /////////////////////////////////////
						if (Strings.CS.equals(resultCode, "R0000")) {
							jsonObject = new JSONObject();
							jsonObject.put("api_trx_no", apiTrxNo);
							jsonObject.put("api_trx_dtm", apiTrxDtm);
							jsonObject.put("file_name", fileName);
							jsonObject.put("file_size", fileSize);
							jsonObject.put("tr_dvsn_cd", "R");
							log.debug(">{}", jsonObject.toString(2));
							bodyStr = String.valueOf(jsonObject);
							log.debug(">{}", bodyStr);
							int i = Message.DEFAULT_PRIORITY;
							EXUtils.write(i, cmnBlckQueNm, bodyStr);
							cmnBlckQue.put(Map.entry(bodyStr, String.valueOf(i)));
						}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
					} else {
						String rspMessage = jsonObject.optString("rsp_message");
						log.error("{}:{}", rspCode, rspMessage);
						resultCode = "R0001"; // 파일 미전송
					}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////// 종료요구/통보 //////////////////////////////////////////////
					jsonObject = new JSONObject();
					jsonObject.put("org_api_trx_no", apiTrxNo);
					jsonObject.put("org_api_trx_dtm", apiTrxDtm);
					jsonObject.put("result_code", resultCode);
					log.debug(">{}", jsonObject.toString(2));
					bodyStr = String.valueOf(jsonObject);
					log.debug(">{}", bodyStr);
					apiTrxNo = StringUtils.join(StringUtils.leftPad("057", 10, '0'),
					StringUtils.right(String.valueOf(System.currentTimeMillis()), 10));
					log.debug("apiTrxNo  = {}", apiTrxNo);
					apiTrxDtm = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
					log.debug("apiTrxDtm = {}", apiTrxDtm);
					int tryCnt = 0;
					while (true) {
						HttpResponse<String> httpResponse;
						try {
							HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(StringUtils.join(http, "/transfer/completion"))).headers(
								"Content-Type", "application/json; charset=UTF-8",
								"Authorization", StringUtils.join(tokenType, " ", accessToken),
								"api_trx_no", apiTrxNo,
								"api_trx_dtm", apiTrxDtm
							).POST(BodyPublishers.ofString(bodyStr)).timeout(Duration.ofSeconds(30L)).build();
							log.trace(">{}", httpRequest);
							httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
							log.trace("<{}", httpResponse);
						} catch (Exception e) {
							log.error(ExceptionUtils.getRootCauseMessage(e));
							tryCnt++;
							if (2 < tryCnt) {
								throw e;
							}
							ThreadUtils.sleepQuietly(Duration.ofSeconds(30L));
							continue;
						}
						tryCnt = 0;
						bodyStr = httpResponse.body();
						log.debug("<{}", bodyStr);
						jsonObject = new JSONObject(bodyStr);
						log.debug("<{}", jsonObject.toString(2));
						break;
					}
					rspCode = jsonObject.optString("rsp_code");
					if (Strings.CS.equals(rspCode, "A0000")) { // 정상 완료
					} else {
						String rspMessage = jsonObject.optString("rsp_message");
						log.error("{}:{}", rspCode, rspMessage);
					}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
				}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
			} catch (Throwable t) {
				log.error(ExceptionUtils.getRootCauseMessage(t), t);
			}
			ssnStts.setValue("0");
			for (int i = 0; i < 600 && !executorService.isShutdown(); i++) {
				ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
			}
		}
		log.info("stop");
	}

}

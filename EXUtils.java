package com.jpmc.kcg.ext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;

import lombok.Generated;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EXUtils {

	public static String encSndrPswrd(String bnkCd, LocalDateTime tlgTrDttm, String sndrNm, String plnPswrd) {
		StringBuilder sb = new StringBuilder();
		String key = StringUtils.join(StringUtils.right(bnkCd, 2),
			tlgTrDttm.format(DateTimeFormatter.ofPattern("yyMMdd")),
			StringUtils.rightPad(StringUtils.left(sndrNm, 8), 8, 'Z'));
		key = StringUtils.upperCase(key);
		String ext = plnPswrd;
		while (16 > StringUtils.length(ext)) {
			ext = StringUtils.join(ext, plnPswrd);
		}
		ext = StringUtils.left(ext, 16);
		String str = StringUtils.reverse("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
		for (int i = 0; i < 16; i++) {
			int p1 = StringUtils.indexOf(str, ext.charAt(i));
			int k1 = StringUtils.indexOf(str, key.charAt(i));
			int c1 = (k1 + p1) % 36;
			sb.append(str.charAt(c1));
		}
		return sb.toString();
	}

	@Generated
	public static void exit(int status) {
		System.exit(status);
	}

	@Generated
	public static String postTrnsfRslt(String apiTrxNo, String apiTrxDtm, String fileName, Properties properties) throws IOException, InterruptedException {
		String bodyStr = "{}";
		JSONObject jsonObject = null;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
		for (String propertyName : properties.stringPropertyNames().stream()
			.filter(propertyName ->
			StringUtils.isNotEmpty(properties.getProperty(propertyName)) &&
			Strings.CS.startsWith(propertyName, "FLTR_") && Strings.CS.endsWith(propertyName, "_2")
		).collect(Collectors.toCollection(TreeSet::new))) {
			if (Pattern.matches(properties.getProperty(propertyName), fileName)) {
				File shrd = FileUtils.getFile(properties.getProperty("PATH_SHRD", "/home/ec2-user/ext/shrd"));
				File auth = FileUtils.getFile(shrd, StringUtils.join("kcg_", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")), ".token"));
				String[] stringArray = StringUtils.split(properties.getProperty(Strings.CS.replace(propertyName, "FLTR_", "FSND_")), '@');
				String http = stringArray[0];
				bodyStr = FileUtils.readFileToString(auth, StandardCharsets.UTF_8);
				log.debug("!{}", bodyStr);
				jsonObject = new JSONObject(bodyStr);
				log.debug("!{}", jsonObject.toString(2));
				String accessToken = jsonObject.optString("access_token");
				String tokenType = jsonObject.optString("token_type");
//				long expiresIn = jsonObject.optLong("expires_in");
				jsonObject = new JSONObject();
				jsonObject.put("org_api_trx_no", apiTrxNo);
				jsonObject.put("org_api_trx_dtm", apiTrxDtm);
				jsonObject.put("file_name", fileName);
				log.debug(">{}", jsonObject.toString(2));
				bodyStr = String.valueOf(jsonObject);
				log.debug(">{}", bodyStr);
				apiTrxNo = StringUtils.join(StringUtils.leftPad("057", 10, '0'),
				StringUtils.right(String.valueOf(System.currentTimeMillis()), 10));
				log.debug("apiTrxNo  = {}", apiTrxNo);
				apiTrxDtm = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
				log.debug("apiTrxDtm = {}", apiTrxDtm);
				HttpResponse<String> httpResponse;
				try {
					HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(StringUtils.join(http, "/transfer/result"))).headers(
						"Content-Type", "application/json; charset=UTF-8",
						"Authorization", StringUtils.join(tokenType, " ", accessToken),
						"api_trx_no", apiTrxNo,
						"api_trx_dtm", apiTrxDtm
					).POST(BodyPublishers.ofString(bodyStr)).timeout(Duration.ofSeconds(30L)).build();
					log.trace(">{}", httpRequest);
					httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
					log.trace("<{}", httpResponse);
					bodyStr = httpResponse.body();
					log.debug("<{}", bodyStr);
					jsonObject = new JSONObject(bodyStr);
					log.debug("<{}", jsonObject.toString(2));
				} catch (HttpTimeoutException e) {
					log.error(ExceptionUtils.getRootCauseMessage(e));
				}
				break;
			}
		}
		return bodyStr;
	}

	public static String newRespMsg(String respCd) {
		if (Strings.CS.equals(respCd, "000")) {
			return StringUtils.join(respCd, ":", "정상");
		}
		if (Strings.CS.equals(respCd, "090")) {
			return StringUtils.join(respCd, ":", "시스템 장애");
		}
		if (Strings.CS.equals(respCd, "310")) {
			return StringUtils.join(respCd, ":", "송신자명 오류");
		}
		if (Strings.CS.equals(respCd, "320")) {
			return StringUtils.join(respCd, ":", "송신자암호 오류");
		}
		if (Strings.CS.equals(respCd, "630")) {
			return StringUtils.join(respCd, ":", "기전송 완료");
		}
		if (Strings.CS.equals(respCd, "631")) {
			return StringUtils.join(respCd, ":", "해당 은행 미등록 업무");
		}
		if (Strings.CS.equals(respCd, "632")) {
			return StringUtils.join(respCd, ":", "비정상 파일명");
		}
		if (Strings.CS.equals(respCd, "633")) {
			return StringUtils.join(respCd, ":", "비정상 전문 BYTE 수");
		}
		if (Strings.CS.equals(respCd, "634")) {
			return StringUtils.join(respCd, ":", "파일 처리 완료");
		}
		if (Strings.CS.equals(respCd, "800")) {
			return StringUtils.join(respCd, ":", "FORMAT 오류");
		}
		return respCd;
	}

	public static String newTractId(String appNm, String tlgKndDvsnCd, String tlgBizDvsnCd, String msgNbr) {
		StringBuilder sb = new StringBuilder(tlgKndDvsnCd);
		if (CharUtils.isAsciiNumeric(sb.charAt(0))) {
			sb.setCharAt(0, '0');
			char c = sb.charAt(2);
			if ('1' == c) {
				sb.setCharAt(2, '0');
			} else if ('3' == c) {
				sb.setCharAt(2, '2');
			}
			sb.setCharAt(3, '0');
		}
		return StringUtils.join(appNm, sb.toString(), tlgBizDvsnCd, msgNbr);
	}

	public static String newTractId(String tlgCtt) {
		String str = StringUtils.left(tlgCtt, 7);
		if (Strings.CS.endsWith(str, "ATI")) { // ATI
			String tlgKndDvsnCd = StringUtils.substring(tlgCtt, 10, 14); // 전문종별구분코드
			String tlgBizDvsnCd = StringUtils.substring(tlgCtt, 14, 20); // 업무구분코드
			return newTractId("ATI", tlgKndDvsnCd, tlgBizDvsnCd, StringUtils.substring(tlgCtt, 43, 55)); // 전문관리번호
		} else if (Strings.CS.endsWith(str, "EBS")) { // ENT
			String tlgKndDvsnCd = StringUtils.substring(tlgCtt, 10, 14); // 전문종별구분코드
			String tlgBizDvsnCd = StringUtils.substring(tlgCtt, 14, 20); // 업무구분코드
			return newTractId("ENT", tlgKndDvsnCd, tlgBizDvsnCd, StringUtils.substring(tlgCtt, 32, 45)); // 거래고유번호
		} else if (Strings.CS.endsWith(str, "GRO")) { // GRO
			String tlgKndDvsnCd = StringUtils.substring(tlgCtt, 10, 14); // 전문종별구분코드
			String tlgBizDvsnCd = StringUtils.substring(tlgCtt, 14, 20); // 업무구분코드
			return newTractId("GRO", tlgKndDvsnCd, tlgBizDvsnCd, StringUtils.substring(tlgCtt, 39, 49)); // 발생기관전문관리번호
		}
		str = StringUtils.left(tlgCtt, 10);
		if (Strings.CS.endsWith(str, "ARS")) { // ARS
			String tlgKndDvsnCd = StringUtils.substring(tlgCtt, 13, 17); // 전문종별구분코드
			String tlgBizDvsnCd = StringUtils.substring(tlgCtt, 17, 23); // 업무구분코드
			return newTractId("ARS", tlgKndDvsnCd, tlgBizDvsnCd, StringUtils.substring(tlgCtt, 39, 51)); // 센터전문관리번호
		} else if (Strings.CS.endsWith(str, "ELB")) { // HOF
			String tlgKndDvsnCd = StringUtils.substring(tlgCtt, 10, 14); // 전문종별구분코드
			String tlgBizDvsnCd = StringUtils.substring(tlgCtt, 14, 20); // 업무구분코드
		if (Strings.CS.startsWithAny(tlgKndDvsnCd, "07", "08")) { // 관리전문
			return newTractId("HOF", tlgKndDvsnCd, tlgBizDvsnCd, StringUtils.substring(tlgCtt, 41, 49)); // 전문추적번호
		} else { // 업무처리전문
			return newTractId("HOF", tlgKndDvsnCd, tlgBizDvsnCd, StringUtils.substring(tlgCtt, 57, 70)); // 거래고유번호
		}
		} else if (Strings.CS.endsWith(str, "LCS")) { // 순이체한도
			String tlgKndDvsnCd = StringUtils.substring(tlgCtt, 13, 17); // 전문종별구분코드
			String tlgBizDvsnCd = StringUtils.substring(tlgCtt, 17, 23); // 업무구분코드
			return newTractId("LCR", tlgKndDvsnCd, tlgBizDvsnCd, StringUtils.substring(tlgCtt, 44, 54)); // 전문추적번호
		} else if (Strings.CS.endsWith(str, "RET")) { // RPR
			String tlgKndDvsnCd = StringUtils.substring(tlgCtt, 10, 14); // 전문종별구분코드
			String tlgBizDvsnCd = StringUtils.substring(tlgCtt, 14, 20); // 업무구분코드
		if (Strings.CS.startsWithAny(tlgKndDvsnCd, "07", "08")) { // 관리전문
			return newTractId("RPR", tlgKndDvsnCd, tlgBizDvsnCd, StringUtils.substring(tlgCtt, 41, 49)); // 전문추적번호
		} else { // 업무처리전문
			return newTractId("RPR", tlgKndDvsnCd, tlgBizDvsnCd, StringUtils.substring(tlgCtt, 77, 90)); // 거래고유번호
		}
		} else { // IFT
			String tlgKndDvsnCd = StringUtils.substring(tlgCtt, 12, 16); // 전문종별구분코드
			String tlgBizDvsnCd = StringUtils.rightPad(StringUtils.substring(tlgCtt, 16, 19), 6, '0'); // 업무구분코드
		if (Strings.CS.startsWithAny(tlgKndDvsnCd, "07", "08")) { // 관리전문
			return newTractId("IFT", tlgKndDvsnCd, tlgBizDvsnCd, StringUtils.EMPTY);
		} else if (Strings.CS.startsWith(tlgKndDvsnCd, "02") && Strings.CS.startsWith(tlgBizDvsnCd, "500")) { // 수취조회
			return newTractId("IFT", tlgKndDvsnCd, tlgBizDvsnCd, StringUtils.substring(tlgCtt, 94, 107)); // 수취조회번호
		} else { // 업무처리전문
			return newTractId("IFT", tlgKndDvsnCd, tlgBizDvsnCd, StringUtils.substring(tlgCtt, 23, 36)); // 취급전문관리번호
		}
		}
	}

	public static String newTestEnt(String msgNbr) {
		String unqNbr = StringUtils.join("057", "ENT", "2", "9", String.valueOf(System.currentTimeMillis() / 1000 % 100000));
		String tlgCtt = StringUtils.join("0103", // TCP/IP HEADER
			"EBS", // 시스템-ID
			"057", // 은행코드
			"0800", // 전문종별코드
			"000007", // 거래구분코드
			msgNbr, // 전문추적번호 11
			"B", // 송수신FLAG
			unqNbr, // 거래고유번호 13
			"000", // STATUS
			"   ", // 응답코드1
			"                    ", // 응답코드2
			LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")), // 전문전송일시
			"          ", // 관리기관통계코드
			"057", // 은행코드
			"000", // 장애(회복)은행코드
			"0000", // 지급제시통보연장시각
		"00"); // 연장사유
		return tlgCtt;
	}

	public static String newTestAti(String msgNbr) {
		String tlgCtt = StringUtils.join("0070", // TCP/IPHeader
			"ATI", // 시스템-ID
			"099", // 기관코드
			"0800", // 전문종별코드
			"000301", // 업무구분코드
			"000", // 상태코드
			"B", // 송수신FLAG
			" ", // 응답코드구분
			"    ", // 응답코드
			LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")), // 전문 전송 일시
			msgNbr, // 전문 관리 번호 12
		"                   "); // FILLER
		return tlgCtt;
	}

	public static String newTestGro(String msgNbr) {
		String tlgCtt = StringUtils.join("0065", // 전문길이
			"GRO", // 업무구분
			"057", // 금융기관/센터코드
			"0800", // 전문종별코드
			"000301", // 거래구분코드
			"000", // 상태코드
			"B", // 송수신FLAG
			"   ", // 응답코드
			LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss")), // 전송일시
			msgNbr, // 발생기관전문관리번호 10
			"0000000000", // 센터전문관리번호 (원거래전문관리번호) 10
			"0000000", // 이용기관지로번호
		"057"); // 금융기관코드
		return tlgCtt;
	}

	public static InputStream openInputStream(File file) throws IOException {
		return FileUtils.openInputStream(file);
	}

	private static File f;
	private static LocalDate tmpDt;
	private static Writer w;
	private static final StringBuilder sb = new StringBuilder();

	public static void reset(File file) throws IOException {
		LocalDate nowDt = LocalDate.now();
		synchronized (sb) {
			f = file;
			if (0 != ObjectUtils.compare(nowDt, tmpDt)) {
				tmpDt = nowDt;
				w = new OutputStreamWriter(FileUtils.openOutputStream(f), StandardCharsets.UTF_8);
			}
		}
	}

	public static void write(int priority, String blckQueNm, String tlgCtt) throws IOException {
		LocalDate nowDt = LocalDate.now();
		synchronized (sb) {
			if (0 != ObjectUtils.compare(nowDt, tmpDt)) {
				w = new OutputStreamWriter(FileUtils.openOutputStream(f), StandardCharsets.UTF_8);
				tmpDt = nowDt;
			}
			sb.setLength(0);
			sb.append(priority);
			sb.append(blckQueNm);
			sb.append(tlgCtt);
			sb.append(System.lineSeparator());
			w.write(sb.toString());
			w.flush();
		}
	}

}

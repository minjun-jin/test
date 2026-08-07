package com.jpmc.kcg.ext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;

import com.ibm.msg.client.jakarta.jms.JmsConnectionFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;

import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ExtQSnd implements Runnable {

	private final Properties properties;
	private final ExecutorService executorService;
	private final JmsConnectionFactory jmsConnectionFactory;
	private final Map<String, BlockingQueue<Entry<String, String>>> blckQueMap;
	private final Map<String, String> crltnIdMap;
	private final Map<String, Entry<String, String>> ssnSttsMap;
	private final String propertyName;

	@Override
	public void run() {
		Entry<String, String> ssnStts = ssnSttsMap.get(propertyName);
		String sndBlckQueNm = StringUtils.left(propertyName, 8);
		String simBlckQueNm = StringUtils.left(Strings.CS.replace(propertyName, "QSND_", "SSND_"), 8);
		String tmpBlckQueNm = StringUtils.left(Strings.CS.replace(propertyName, "QSND_", "QSND2"), 8);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// 금융공동망강제보정 /////////////////////////////////////////////////////
		if (Strings.CS.contains(propertyName, "_ARS_")) {
			sndBlckQueNm = Strings.CS.replace(sndBlckQueNm, "_ARS", "_HOF");
			simBlckQueNm = Strings.CS.replace(simBlckQueNm, "_ARS", "_HOF");
			tmpBlckQueNm = Strings.CS.replace(tmpBlckQueNm, "_ARS", "_HOF");
		}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		BlockingQueue<Entry<String, String>> sndBlckQue = blckQueMap.get(sndBlckQueNm);
		BlockingQueue<Entry<String, String>> simBlckQue = blckQueMap.get(simBlckQueNm);
		BlockingQueue<Entry<String, String>> tmpBlckQue = blckQueMap.get(tmpBlckQueNm);
		String sndQueueName = properties.getProperty(propertyName);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		MDC.put("key", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")));
		Thread.currentThread().setName(propertyName);
		log.info("start");
		while (!executorService.isShutdown()) {
			ssnStts.setValue("0");
			try (JMSContext jmsContext = jmsConnectionFactory.createContext()) {
				ssnStts.setValue("1");
				Queue sndQueue = jmsContext.createQueue(StringUtils.join(WMQConstants.QUEUE_PREFIX, "/", sndQueueName));
				try (JMSConsumer jmsConsumer = jmsContext.createConsumer(sndQueue)) {
					while (!executorService.isShutdown()) {
						Message message = jmsConsumer.receive(1000L);
						if (message instanceof TextMessage textMessage) {
							log.debug("{}", textMessage);
							String tlgCtt = textMessage.getText();
							log.info("<{}]", tlgCtt);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////// 동기응답처리 ///////////////////////////////////////
							String crltnId = textMessage.getJMSCorrelationID();
							if (StringUtils.isNotEmpty(crltnId)) {
								log.debug("crltnId = {}", crltnId);
								String tractId = EXUtils.newTractId(tlgCtt);
								log.debug("tractId = {}", tractId);
								crltnId = StringUtils.join(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss")),
								crltnId); // 일시추가
								crltnIdMap.put(tractId, crltnId);
							}
							int i = textMessage.getJMSPriority();
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////// 시뮬레이터처리 /////////////////////////////////////
							if (Strings.CS.equals(textMessage.getStringProperty("USRX_SIM_YN"), "Y")) {
								EXUtils.write(i, simBlckQueNm, tlgCtt);
								simBlckQue.put(Map.entry(tlgCtt, String.valueOf(i)));
								continue;
							}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////////////// 동기응답강제보정 ///////////////////////////////////
							if (Strings.CS.contains(propertyName, "_GRO_")) {
								String tlgKndDvsnCd = StringUtils.substring(tlgCtt, 10, 14); // 전문종별구분코드
//								String tlgBizDvsnCd = StringUtils.substring(tlgCtt, 14, 20); // 업무구분코드
								if (Strings.CS.startsWithAny(tlgKndDvsnCd, "9") ||
									Strings.CS.endsWithAny(StringUtils.left(tlgKndDvsnCd, 3), "1", "3")) { // 응답
									EXUtils.write(i, tmpBlckQueNm, tlgCtt);
									tmpBlckQue.put(Map.entry(tlgCtt, String.valueOf(i)));
									continue;
								}
							}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
							EXUtils.write(i, sndBlckQueNm, tlgCtt);
							sndBlckQue.put(Map.entry(tlgCtt, String.valueOf(i)));
						}
					}
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

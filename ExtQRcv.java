package com.jpmc.kcg.ext;

import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;

import com.ibm.msg.client.jakarta.jms.JmsConnectionFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;

import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ExtQRcv implements Runnable {

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
		String rcvBlckQueNm = StringUtils.left(propertyName, 8);
		BlockingQueue<Entry<String, String>> rcvBlckQue = blckQueMap.get(rcvBlckQueNm);
		String cmnQueueName = properties.getProperty("QCMN_FRW_X");
		String rcvQueueName = properties.getProperty(propertyName);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		MDC.put("key", StringUtils.lowerCase(StringUtils.substringBetween(propertyName, "_")));
		Thread.currentThread().setName(propertyName);
		log.info("start");
		while (!executorService.isShutdown()) {
			ssnStts.setValue("0");
			Entry<String, String> entry = null;
			try (JMSContext jmsContext = jmsConnectionFactory.createContext()) {
				ssnStts.setValue("1");
				Queue cmnQueue = jmsContext.createQueue(StringUtils.join(WMQConstants.QUEUE_PREFIX, "/", cmnQueueName));
				Queue rcvQueue = jmsContext.createQueue(StringUtils.join(WMQConstants.QUEUE_PREFIX, "/", rcvQueueName));
				JMSProducer jmsProducer = jmsContext.createProducer();
				while (!executorService.isShutdown() || ObjectUtils.isNotEmpty(rcvBlckQue)) {
					entry = rcvBlckQue.poll(1000L, TimeUnit.MILLISECONDS);
					if (null == entry) {
						continue;
					}
					String tmpCtt = entry.getKey();
					String tlgCtt = tmpCtt;
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////// 시뮬레이터전문강제보정 /////////////////////////////////////
					if (Strings.CS.endsWith(StringUtils.left(tmpCtt, 7), "SIM")) {
						if (Strings.CS.contains(propertyName, "_ENT_")) {
							tlgCtt = StringUtils.join(StringUtils.left(tmpCtt, 4), "EBS",
							StringUtils.substring(tmpCtt, 7));
						} else if (Strings.CS.contains(propertyName, "_ATI_")) {
							tlgCtt = StringUtils.join(StringUtils.left(tmpCtt, 4), "ATI",
							StringUtils.substring(tmpCtt, 7));
						} else if (Strings.CS.contains(propertyName, "_GRO_")) {
							tlgCtt = StringUtils.join(StringUtils.left(tmpCtt, 4), "GRO",
							StringUtils.substring(tmpCtt, 7));
						} else {
							tlgCtt = StringUtils.join(StringUtils.left(tmpCtt, 4), "HDR",
							StringUtils.substring(tmpCtt, 7));
						}
					}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
					log.info(">{}]", tlgCtt);
					TextMessage textMessage = jmsContext.createTextMessage(tlgCtt);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////////// 동기응답처리 ///////////////////////////////////////////////
					String tractId = EXUtils.newTractId(tlgCtt);
					log.debug("tractId = {}", tractId);
					String crltnId = crltnIdMap.remove(tractId);
					if (StringUtils.isNotEmpty(crltnId)) {
						crltnId = StringUtils.substring(crltnId, 12); // 일시제거
						log.debug("crltnId = {}", crltnId);
						textMessage.setJMSCorrelationID(crltnId);
						log.debug("{}", textMessage);
						jmsProducer.setTimeToLive(60000L); // 만료설정
						jmsProducer.send(cmnQueue, textMessage);
						jmsProducer.setTimeToLive(0L);
						EXUtils.write(0, rcvBlckQueNm, tmpCtt);
						entry = null;
						continue;
					}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
					log.debug("{}", textMessage);
					jmsProducer.send(rcvQueue, textMessage);
					EXUtils.write(0, rcvBlckQueNm, tmpCtt);
					entry = null;
				}
			} catch (Throwable t) {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////// 장애전문복원 ///////////////////////////////////////////////////
				if (null != entry) {
					rcvBlckQue.add(Map.entry(entry.getKey(), String.valueOf(9)));
				}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
				log.error(ExceptionUtils.getRootCauseMessage(t), t);
			}
			ssnStts.setValue("0");
			ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
		}
		log.info("stop");
	}

}

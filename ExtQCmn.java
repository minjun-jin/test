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
public class ExtQCmn implements Runnable {

	private final Properties properties;
	private final ExecutorService executorService;
	private final JmsConnectionFactory jmsConnectionFactory;
	private final Map<String, BlockingQueue<Entry<String, String>>> blckQueMap;
	private final Map<String, Entry<String, String>> ssnSttsMap;
	private final String propertyName;

	@Override
	public void run() {
		Entry<String, String> ssnStts = ssnSttsMap.get(propertyName);
		String cmnBlckQueNm = StringUtils.left(propertyName, 8);
		BlockingQueue<Entry<String, String>> cmnBlckQue = blckQueMap.get(cmnBlckQueNm);
		String cmnQueueName = properties.getProperty(propertyName);
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
				JMSProducer jmsProducer = jmsContext.createProducer();
				while (!executorService.isShutdown() || ObjectUtils.isNotEmpty(cmnBlckQue)) {
					entry = cmnBlckQue.poll(1000L, TimeUnit.MILLISECONDS);
					if (null == entry) {
						continue;
					}
					String tmpCtt = entry.getKey();
					String tlgCtt = tmpCtt;
					log.info(">{}]", tlgCtt);
					TextMessage textMessage = jmsContext.createTextMessage(tlgCtt);
					log.debug("{}", textMessage);
					jmsProducer.send(cmnQueue, textMessage);
					EXUtils.write(0, cmnBlckQueNm, tmpCtt);
					entry = null;
				}
			} catch (Throwable t) {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////////// 장애전문복원 ///////////////////////////////////////////////////
				if (null != entry) {
					cmnBlckQue.add(Map.entry(entry.getKey(), String.valueOf(9)));
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

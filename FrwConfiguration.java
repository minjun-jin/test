package com.jpmc.kcg.frw;

import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.jms.autoconfigure.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.boot.jms.autoconfigure.JmsProperties;
import org.springframework.boot.jms.autoconfigure.JmsProperties.DeliveryMode;
import org.springframework.boot.jms.autoconfigure.JmsProperties.Template;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.support.JmsUtils;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.destination.DestinationResolver;

import com.ibm.msg.client.jakarta.wmq.WMQConstants;

import io.micrometer.observation.ObservationRegistry;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "ibm.mq", name = "autoConfigure", matchIfMissing=true)
public class FrwConfiguration {

	@Data
	public class ThreadValue {
		int deliveryMode;
		int priority;
		long timeToLive;
		long receiveTimeout;
	}

	@Bean
	JmsTemplate jmsTemplate(SystemProperties systemProperties,
		JmsProperties properties,
		ObjectProvider<DestinationResolver> destinationResolver,
		ObjectProvider<MessageConverter> messageConverter,
		ObjectProvider<ObservationRegistry> observationRegistry,
		ConnectionFactory connectionFactory) {
		PropertyMapper map = PropertyMapper.get();
		JmsTemplate template = new JmsTemplate(connectionFactory) {

			private ThreadLocal<ThreadValue> threadLocal = new ThreadLocal<>();

			@Override
			public void setExplicitQosEnabled(boolean explicitQosEnabled) {
				threadLocal.remove();
				if (explicitQosEnabled) {
					ThreadValue threadValue = new ThreadValue();
					threadValue.setDeliveryMode(super.getDeliveryMode());
					threadValue.setPriority(super.getPriority());
					threadValue.setTimeToLive(super.getTimeToLive());
					threadValue.setReceiveTimeout(super.getReceiveTimeout());
					threadLocal.set(threadValue);
					return;
				}
				super.setExplicitQosEnabled(explicitQosEnabled);
			}

			@Override
			public boolean isExplicitQosEnabled() {
				ThreadValue threadValue = threadLocal.get();
				if (null != threadValue) {
					return true;
				}
				return super.isExplicitQosEnabled();
			}

			@Override
			public void setReceiveTimeout(long receiveTimeout) {
				ThreadValue threadValue = threadLocal.get();
				if (null != threadValue) {
					threadValue.setReceiveTimeout(receiveTimeout);
					return;
				}
				super.setReceiveTimeout(receiveTimeout);
			}

			@Override
			public long getReceiveTimeout() {
				ThreadValue threadValue = threadLocal.get();
				if (null != threadValue) {
					return threadValue.getReceiveTimeout();
				}
				return super.getReceiveTimeout();
			}

			@Override
			public void setDeliveryMode(int deliveryMode) {
				ThreadValue threadValue = threadLocal.get();
				if (null != threadValue) {
					threadValue.setDeliveryMode(deliveryMode);
					return;
				}
				super.setDeliveryMode(deliveryMode);
			}

			@Override
			public int getDeliveryMode() {
				ThreadValue threadValue = threadLocal.get();
				if (null != threadValue) {
					return threadValue.getDeliveryMode();
				}
				return super.getDeliveryMode();
			}

			@Override
			public void setPriority(int priority) {
				ThreadValue threadValue = threadLocal.get();
				if (null != threadValue) {
					threadValue.setPriority(priority);
					return;
				}
				super.setPriority(priority);
			}

			@Override
			public int getPriority() {
				ThreadValue threadValue = threadLocal.get();
				if (null != threadValue) {
					return threadValue.getPriority();
				}
				return super.getPriority();
			}

			@Override
			public void setTimeToLive(long timeToLive) {
				ThreadValue threadValue = threadLocal.get();
				if (null != threadValue) {
					threadValue.setTimeToLive(timeToLive);
					return;
				}
				super.setTimeToLive(timeToLive);
			}

			@Override
			public long getTimeToLive() {
				ThreadValue threadValue = threadLocal.get();
				if (null != threadValue) {
					return threadValue.getTimeToLive();
				}
				return super.getTimeToLive();
			}

			@Override
			protected Message doSendAndReceive(Session session, Destination destination, MessageCreator messageCreator) throws JMSException {
				Destination responseQueue = null;
				MessageProducer producer = null;
				MessageConsumer consumer = null;
				try {
					Message requestMessage = messageCreator.createMessage(session);
					responseQueue = resolveDestinationName(session, systemProperties.getKcg().getFrw().getCmn());
					producer = session.createProducer(destination);
					consumer = session.createConsumer(responseQueue);
					requestMessage.setJMSReplyTo(responseQueue);
					logger.debug("Sending created message: " + requestMessage);
					doSend(producer, requestMessage);
					return receiveSelected(responseQueue, StringUtils.join(WMQConstants.JMS_CORRELATIONID, " = '", requestMessage.getJMSCorrelationID(), "'"));
				}
				finally {
					JmsUtils.closeMessageConsumer(consumer);
					JmsUtils.closeMessageProducer(producer);
				}
			}

		};
		template.setPubSubDomain(properties.isPubSubDomain());
		map.from(destinationResolver::getIfUnique).to(template::setDestinationResolver);
		map.from(messageConverter::getIfUnique).to(template::setMessageConverter);
		map.from(observationRegistry::getIfUnique).to(template::setObservationRegistry);
		map = PropertyMapper.get();
		mapTemplateProperties(properties.getTemplate(), template);
		return template;
	}

	private void mapTemplateProperties(Template properties, JmsTemplate template) {
		PropertyMapper map = PropertyMapper.get();
		map.from(properties.getSession().getAcknowledgeMode()::getMode).to(template::setSessionAcknowledgeMode);
		map.from(properties.getSession()::isTransacted).to(template::setSessionTransacted);
		map.from(properties::getDefaultDestination).to(template::setDefaultDestinationName);
		map.from(properties::getDeliveryDelay).as(Duration::toMillis).to(template::setDeliveryDelay);
		map.from(properties::determineQosEnabled).to(template::setExplicitQosEnabled);
		map.from(properties::getDeliveryMode).as(DeliveryMode::getValue).to(template::setDeliveryMode);
		map.from(properties::getPriority).to(template::setPriority);
		map.from(properties::getTimeToLive).as(Duration::toMillis).to(template::setTimeToLive);
		map.from(properties::getReceiveTimeout).as(Duration::toMillis).to(template::setReceiveTimeout);
	}

	@Bean
	DefaultJmsListenerContainerFactory jmsListenerContainerFactory(DefaultJmsListenerContainerFactoryConfigurer configurer,
		ConnectionFactory connectionFactory) {
		DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
		configurer.configure(factory, connectionFactory);
		factory.setErrorHandler(t -> {
			log.error("handleError", t);
//			ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
		});
		return factory;
	}

}

package com.jpmc.kcg.frw;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.scheduling.ScheduledTasksEndpoint;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health.Builder;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.stereotype.Component;

@Component
public class SchedulingHealthIndicator extends AbstractHealthIndicator {

	private ScheduledTasksEndpoint scheduledTasksEndpoint;

	public SchedulingHealthIndicator(ObjectProvider<ScheduledTaskHolder> holders) {
		scheduledTasksEndpoint = new ScheduledTasksEndpoint(holders.orderedStream().toList());
	}

	@Override
	protected void doHealthCheck(Builder builder) throws Exception {
		builder.up().withDetail("scheduledTasks", scheduledTasksEndpoint.scheduledTasks()).build();
	}

}

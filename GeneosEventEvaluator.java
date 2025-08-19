package ch.qos.logback.core.boolex;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Strings;

import ch.qos.logback.classic.spi.ILoggingEvent;

public class GeneosEventEvaluator extends EventEvaluatorBase<ILoggingEvent> {

	private String[] strings = ArrayUtils.EMPTY_STRING_ARRAY;

	@Override
	public boolean evaluate(ILoggingEvent event) throws NullPointerException, EvaluationException {
		for (String string : strings) {
			if (Strings.CS.contains(event.getMessage(), string)) {
				return true;
			}
		}
		return false;
	}

	public void setString(String string) {
		strings = ArrayUtils.add(strings, string);
	}

}

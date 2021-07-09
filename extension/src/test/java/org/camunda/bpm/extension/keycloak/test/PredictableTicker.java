package org.camunda.bpm.extension.keycloak.test;

import com.github.benmanes.caffeine.cache.Ticker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * To be used in tests to control the passage of time in a predictable manner
 */
public class PredictableTicker implements Ticker {

	private static final AtomicLong TICK = new AtomicLong();

	@Override
	public long read() {
		return TICK.get();
	}

	public static void moveTimeForwardByMinutes(long offsetMinutes) {
		TICK.set(TICK.get() + TimeUnit.NANOSECONDS.convert(offsetMinutes, TimeUnit.MINUTES));
	}

	public static void reset() {
		TICK.set(0);
	}
}

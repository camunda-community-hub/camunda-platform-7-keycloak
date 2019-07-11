package org.camunda.bpm.extension.keycloak.showcase.test.util;

import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions;

/**
 * Assert methods for process tests.
 */
public abstract class ProcessTestAssertions {

	/** Empty private constructor. */
	private ProcessTestAssertions() {
		// No instances will be made
	}

	/**
	 * Assert object offering ProcessInstance specific wait assertions. Useful especially in case of asynchronous continuations.
	 */
	public static class WaitAssert {

		private static final int DEFAULT_TIMEOUT_MILLIS = 5000;
		private static final int DEFAULT_INTERVAL_MILLIS = 100;

		protected long timeOutMillis = DEFAULT_TIMEOUT_MILLIS;
		protected long intervalMillis = DEFAULT_INTERVAL_MILLIS;

		/** ProcessInstance under test. */
		protected ProcessInstance actual;

		/**
		 * Constructs a new {@link WaitAssert}.
		 * 
		 * @param actual ProcessInstance under test
		 */
		protected WaitAssert(ProcessInstance actual) {
			this.actual = actual;
		}

		/**
		 * Waits until a process instance with asynchronous continuations has passed a specific activity. Default timeout is 5 seconds.
		 * 
		 * @param activityId the id's of the activities expected to have been passed
		 * @return this {@link WaitAssert}
		 */
		public WaitAssert hasPassed(String activityId) {
			waitForRunWithoutAssertionError(() -> ProcessEngineAssertions.assertThat(actual).hasPassed(activityId));
			return this;
		}

		/**
		 * Waits until a process instance with asynchronous continuations has reached a specific intermediate message catch event. Default
		 * timeout is 5 seconds.
		 * 
		 * @param messageNames the names of the message the process instance is expected to be waiting for
		 * @return this {@link WaitAssert}
		 */
		public WaitAssert isWaitingFor(String... messageNames) {
			waitForRunWithoutAssertionError(() -> ProcessEngineAssertions.assertThat(actual).isWaitingFor(messageNames));
			return this;
		}

		/**
		 * Enter into a chained task assert inspecting the one and mostly one task of the specified task definition key currently available
		 * in the context of the process instance under test of this ProcessInstanceAssert.
		 *
		 * @param activityId id narrowing down the search for jobs
		 * @return {@link JobAssert} inspecting the retrieved job.
		 */
		public JobAssert job(String activityId) {
			return new JobAssert(this, activityId);
		}

		/**
		 * Waits until a process instance with asynchronous continuations has started a specified subprocess.
		 * 
		 * @param processDefinitionKey the process definition key of the expected subprocess
		 * @return this {@link WaitAssert}
		 */
		public WaitAssert hasStartedSubProcess(String processDefinitionKey) {
			waitForRunWithoutAssertionError(
					() -> ProcessEngineAssertions.assertThat(actual).calledProcessInstance(processDefinitionKey).isActive());
			return this;
		}

		/**
		 * Waits until a process instance has ended.
		 * 
		 * @return this {@link WaitAssert}
		 */
		public WaitAssert isEnded() {
			waitForRunWithoutAssertionError(() -> ProcessEngineAssertions.assertThat(actual).isEnded());
			return this;
		}

		/**
		 * Sets the timeout in milliseconds of this {@link WaitAssert}.
		 * <p>
		 * Use at the beginning of the assertion:<br/>
		 * {@code waitUntil(pi).withTimeOut(10000) ...}
		 * 
		 * @param timeOutMillis timeout in milliseconds
		 * @return this {@link WaitAssert}
		 */
		public WaitAssert withTimeOutMillis(long timeOutMillis) {
			this.timeOutMillis = timeOutMillis;
			return this;
		}

		/**
		 * Sets the check interval in milliseconds of this {@link WaitAssert}.
		 * <p>
		 * Use at the beginning of the assertion:<br/>
		 * {@code waitUntil(pi).withInterval(1000) ...}
		 * 
		 * @param intervalMillis check interval in milliseconds
		 * @return this {@link WaitAssert}
		 */
		public WaitAssert withIntervalMillis(long intervalMillis) {
			this.intervalMillis = intervalMillis;
			return this;
		}

		/**
		 * Waits until a {@link Runnable} throws no assertion error.
		 * @param runnable the assertion to execute
		 */
		public void hasNoAssertionErrorFor(Runnable runnable) {
			waitForRunWithoutAssertionError(runnable);
		}
		
		/**
		 * Periodically (with intervalMillis as interval time) executes the runnable until it finishes wihout throwing an AssertionError or
		 * the total wait time duration timeOutMillis has been reached.
		 * 
		 * @param runnable the Runnable to execute
		 */
		protected void waitForRunWithoutAssertionError(Runnable runnable) { // NOPMD
			final long maxWait = System.currentTimeMillis() + timeOutMillis;
			boolean finished = false;
			while (!finished) {
				try {
					runnable.run();
					finished = true;
				} catch (AssertionError ex) {
					if (System.currentTimeMillis() > maxWait) {
						throw ex;
					}
					try {
						// check every 100 ms
						Thread.sleep(intervalMillis);
					} catch (Exception slex) { // NOSONAR
					}
				}
			}
		}
	}

	/**
	 * Enhanced assert object offering ProcessInstance specific job assertions. Use {@code WaitAssert.job(String activityId)} for creating
	 * this specific assert.
	 */
	public static final class JobAssert extends ProcessTestAssertions.WaitAssert {

		private String jobActivityId;

		/**
		 * Constructs a new JobAssert.
		 * 
		 * @param waitAssert the original waitAssert
		 * @param jobActivityId the activityId of the job to test against
		 */
		private JobAssert(WaitAssert waitAssert, String jobActivityId) {
			super(waitAssert.actual);
			this.timeOutMillis = waitAssert.timeOutMillis;
			this.intervalMillis = waitAssert.intervalMillis;
			this.jobActivityId = jobActivityId;
		}

		/**
		 * Waits until a process instance with asynchronous continuations has reached the given number of retries.
		 * 
		 * @param expectedRetries the expected number of retries
		 * @return this {@link WaitAssert}
		 */
		public JobAssert hasRetries(int expectedRetries) {
			waitForRunWithoutAssertionError(
					() -> ProcessEngineAssertions.assertThat(actual).job(jobActivityId).hasRetries(expectedRetries));
			return this;
		}
	}

	/**
	 * Wait until... the given ProcessInstance meets your expectations.
	 * 
	 * @param actual ProcessInstance under test
	 * @return Assert object offering ProcessInstance specific wait assertions.
	 */
	public static WaitAssert waitUntil(ProcessInstance actual) {
		return new WaitAssert(actual);
	}
}

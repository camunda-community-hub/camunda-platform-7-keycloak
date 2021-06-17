package org.camunda.bpm.extension.keycloak.test;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple RestTemplate interceptor which counts the number of http requests made.
 * To be used in tests to assert caching behaviour.
 */
public class CountingHttpRequestInterceptor implements ClientHttpRequestInterceptor {

	private static final AtomicInteger httpRequestCount = new AtomicInteger();

	@Override
	public ClientHttpResponse intercept(HttpRequest httpRequest, byte[] bytes, ClientHttpRequestExecution execution) throws IOException {
		if (!httpRequest.getURI().getPath().contains("/protocol/openid-connect/token")) {
			httpRequestCount.incrementAndGet();
		}
		return execution.execute(httpRequest, bytes);
	}

	public static int getHttpRequestCount() {
		return httpRequestCount.get();
	}

	public static void resetCount() {
		httpRequestCount.set(0);
	}
}

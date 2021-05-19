package org.camunda.bpm.extension.keycloak.rest;

import org.camunda.bpm.extension.keycloak.KeycloakContextProvider;
import org.camunda.bpm.extension.keycloak.util.KeycloakPluginLogger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Keycloak specific RestTemplate taking care of authentication and a single retry in case of HTTP 401.
 */
public class KeycloakRestTemplate extends org.springframework.web.client.RestTemplate {
	
	/** Access to the Keycloak context. */
	private KeycloakContextProvider keycloakContextProvider;
	
	/**
	 * Execute the HTTP method to the given URI template (using a Keycloak default request entity to the request) and returns the response as ResponseEntity. 
	 * URI Template variables are expanded using the given URI variables, if any.
 	 * @param url the URL
	 * @param method the HTTP method (GET, POST, etc)
	 * @param responseType the type to convert the response to, or {@code Void.class} for no body
	 * @param uriVariables the variables to expand in the template
	 * @return the response as entity
	 * @throws RestClientException in case of any errors
	 */
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method,
			Class<T> responseType, Object... uriVariables) throws RestClientException {
		return exchange(url, method, keycloakContextProvider.createApiRequestEntity(), responseType, uriVariables);
	}
	
	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
			Class<T> responseType, Object... uriVariables) throws RestClientException {
		try {
			return super.exchange(url, method, requestEntity, responseType, uriVariables);
		} catch (HttpClientErrorException.Unauthorized u) {
			// retry once in case of HTTP 401
			KeycloakPluginLogger.INSTANCE.requestFailedUnauthorized(url);
			keycloakContextProvider.invalidateToken();
			return super.exchange(url, method, keycloakContextProvider.createApiRequestEntity(), responseType, uriVariables);
		}
	}

	/**
	 * Registers the Keycloak Context Provider.
	 * @param keycloakContextProvider the context provider
	 */
	public void registerKeycloakContextProvider(KeycloakContextProvider keycloakContextProvider) {
		this.keycloakContextProvider = keycloakContextProvider;
	}
	
}

package org.camunda.bpm.extension.keycloak;

import static org.camunda.bpm.extension.keycloak.json.JsonUtil.*;

import org.camunda.bpm.engine.impl.identity.IdentityProviderException;
import org.camunda.bpm.extension.keycloak.json.JsonException;
import org.camunda.bpm.extension.keycloak.util.ContentType;
import org.camunda.bpm.extension.keycloak.util.KeycloakPluginLogger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.google.gson.JsonObject;

/**
 * Keycloak context provider. 
 * <p>
 * Manages access tokens for then Keycloak REST API.
 */
public class KeycloakContextProvider {

	private final static KeycloakPluginLogger LOG = KeycloakPluginLogger.INSTANCE;

	protected KeycloakConfiguration keycloakConfiguration;
	protected RestTemplate restTemplate;

	protected KeycloakContext context;
	
	/**
	 * Creates a new Keycloak context provider
	 * @param keycloakConfiguration the Keycloak configuration
	 * @param restTemplate REST template
	 */
	public KeycloakContextProvider(KeycloakConfiguration keycloakConfiguration, RestTemplate restTemplate) {
		this.keycloakConfiguration = keycloakConfiguration;
		this.restTemplate = restTemplate;
	}
	
	/**
	 * Requests an access token for the configured Keycloak client.
	 * @return new Keycloak context holding the access token
	 */
	private KeycloakContext openAuthorizationContext() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED + ";charset=" + keycloakConfiguration.getCharset());
		HttpEntity<String> request = new HttpEntity<String>(
	    		"client_id=" + keycloakConfiguration.getClientId()
	    		+ "&client_secret=" + keycloakConfiguration.getClientSecret()
	    		+ "&grant_type=client_credentials",
				headers);

		try {
			ResponseEntity<String> response = restTemplate
					.postForEntity(keycloakConfiguration.getKeycloakIssuerUrl() + "/protocol/openid-connect/token", request, String.class);
			if (!response.getStatusCode().equals(HttpStatus.OK)) {
				throw new IdentityProviderException("Could not connect to " + keycloakConfiguration.getKeycloakIssuerUrl()
						+ ": HTTP status code " + response.getStatusCodeValue());
			}

			JsonObject json = parseAsJsonObject(response.getBody());
			String accessToken = getJsonString(json, "access_token");
			String tokenType = getJsonString(json, "token_type");
			String refreshToken = getJsonString(json, "refresh_token");
			long expiresInMillis = getJsonLong(json, "expires_in") * 1000;
			return new KeycloakContext(accessToken, tokenType, expiresInMillis, refreshToken, keycloakConfiguration.getCharset());

		} catch (RestClientException rce) {
			LOG.requestTokenFailed(rce);
			throw new IdentityProviderException("Unable to get access token from Keycloak server", rce);
		} catch (JsonException je) {
			LOG.requestTokenFailed(je);
			throw new IdentityProviderException("Unable to get access token from Keycloak server", je);
		}
	}

	/**
	 * Refreshs an access token for the configured Keycloak client.
	 * @return the refreshed Keycloak context holding the access token
	 */
	private KeycloakContext refreshToken() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED + ";charset=" + keycloakConfiguration.getCharset());
		HttpEntity<String> request = new HttpEntity<String>(
	    		"client_id=" + keycloakConfiguration.getClientId()
	    		+ "&client_secret=" + keycloakConfiguration.getClientSecret()
	    		+ "&refresh_token=" + context.getRefreshToken()
	    		+ "&grant_type=refresh_token",
				headers);

		try {
			ResponseEntity<String> response = restTemplate
					.postForEntity(keycloakConfiguration.getKeycloakIssuerUrl() + "/protocol/openid-connect/token", request, String.class);
			if (!response.getStatusCode().equals(HttpStatus.OK)) {
				throw new IdentityProviderException("Could not connect to " + keycloakConfiguration.getKeycloakIssuerUrl()
						+ ": HTTP status code " + response.getStatusCodeValue());
			}

			JsonObject json = parseAsJsonObject(response.getBody());
			String accessToken = getJsonString(json, "access_token");
			String tokenType = getJsonString(json, "token_type");
			String refreshToken = getJsonString(json, "refresh_token");
			long expiresInMillis = getJsonLong(json, "expires_in") * 1000;
			return new KeycloakContext(accessToken, tokenType, expiresInMillis, refreshToken, keycloakConfiguration.getCharset());

		} catch (RestClientException rce) {
			LOG.refreshTokenFailed(rce);
			throw new IdentityProviderException("Unable to refresh access token from Keycloak server", rce);
		} catch (JsonException je) {
			LOG.refreshTokenFailed(je);
			throw new IdentityProviderException("Unable to refresh access token from Keycloak server", je);
		}
	}

	/**
	 * Creates a valid request entity for the Keycloak management API.
	 * @return request entity with authorization header / access token set
	 */
	public HttpEntity<String> createApiRequestEntity() {
		if (context == null) {
			context = openAuthorizationContext();
		} else if (context.needsRefresh()) {
			try {
				context = refreshToken();
			} catch (IdentityProviderException ipe) {
				context = openAuthorizationContext();
			}
		}
		return context.createHttpRequestEntity();
	}
	
}

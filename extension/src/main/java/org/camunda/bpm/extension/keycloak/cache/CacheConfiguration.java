package org.camunda.bpm.extension.keycloak.cache;

import org.camunda.bpm.extension.keycloak.KeycloakConfiguration;

import java.time.Duration;

/**
 * Query/Login Cache Configuration as parsed from KeycloakConfiguration.
 */
public class CacheConfiguration {

	private final boolean enabled;
	private final int maxSize;
	private final Duration expirationTimeout;

	private CacheConfiguration(boolean enabled, int maxSize, Duration expirationTimeout) {
		this.enabled = enabled;
		this.maxSize = maxSize;
		this.expirationTimeout = expirationTimeout;
	}

	/**
	 * Creates a new query cache configuration out of the overal Keycloak configuration.
	 * @param keycloakConfiguration the Keycloak Identity Provider configuration.
	 * @return the resulting query cache configuration
	 */
	public static CacheConfiguration from(KeycloakConfiguration keycloakConfiguration) {
		return new CacheConfiguration(keycloakConfiguration.isCacheEnabled(),
						keycloakConfiguration.getMaxCacheSize(), Duration.ofMinutes(keycloakConfiguration.getCacheExpirationTimeoutMin()));
	}

	/**
	 * Creates a new login cache configuration out of the overal Keycloak configuration.
	 * @param keycloakConfiguration the Keycloak Identity Provider configuration.
	 * @return the resulting login cache configuration
	 */
	public static CacheConfiguration fromLoginConfigOf(KeycloakConfiguration keycloakConfiguration) {
		return new CacheConfiguration(keycloakConfiguration.isLoginCacheEnabled(), keycloakConfiguration.getLoginCacheSize(), 
				Duration.ofMinutes(keycloakConfiguration.getLoginCacheExpirationTimeoutMin()));
	}
	
	public boolean isEnabled() {
		return enabled;
	}

	public int getMaxSize() {
		return maxSize;
	}

	public Duration getExpirationTimeout() {
		return expirationTimeout;
	}
}

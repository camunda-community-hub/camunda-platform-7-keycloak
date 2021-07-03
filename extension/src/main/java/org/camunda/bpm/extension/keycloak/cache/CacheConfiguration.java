package org.camunda.bpm.extension.keycloak.cache;

import org.camunda.bpm.extension.keycloak.KeycloakConfiguration;

import java.time.Duration;

/**
 * <p>Query Cache Configuration parsed from KeycloakConfiguration</p>
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

	public static CacheConfiguration from(KeycloakConfiguration keycloakConfiguration) {
		return new CacheConfiguration(keycloakConfiguration.isCacheEnabled(),
						keycloakConfiguration.getMaxCacheSize(), Duration.ofMinutes(keycloakConfiguration.getCacheExpirationTimeoutMin()));
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

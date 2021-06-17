package org.camunda.bpm.extension.keycloak.cache;

import java.time.Duration;

/**
 * <p>Java Bean holding Query Cache Configuration</p>
 */
public class CacheConfiguration {

  /**
   * determines if queries to keycloak are cached. default: false
   */
  private boolean enabled;

  /**
   * maximum size of the cache. least used entries are evicted when this limit is reached. default: 500
   * for more details on this eviction behaviour, please check the documentation of the 
   * QueryCache implementation. The default QueryCache implementation is CaffeineCache.
   */
  private int maxSize = 500;

  /**
   * time after which a cached entry is evicted. default: 15 minutes
   */
  private Duration expirationTimeout = Duration.ofMinutes(15);

  //-------------------------------------------------------------------------
  // Getters / Setters
  //-------------------------------------------------------------------------

  /**
   * @return boolean indicating if caching is enabled
   */
  public boolean isEnabled() {
    return this.enabled;
  }

  /**
   * @return the maximum size of the query cache
   */
  public int getMaxSize() {
    return this.maxSize;
  }

  /**
   * @return the expiry timeout for cached entries
   */
  public Duration getExpirationTimeout() {
    return this.expirationTimeout;
  }

  /**
   * @param enabled boolean indicating whether or not caching is enabled
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * @param maxSize the maximum size of the query cache
   */
  public void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }

  /**
   * @param expirationTimeout the expiry timeout for cached entries
   */
  public void setExpirationTimeout(Duration expirationTimeout) {
    this.expirationTimeout = expirationTimeout;
  }
}

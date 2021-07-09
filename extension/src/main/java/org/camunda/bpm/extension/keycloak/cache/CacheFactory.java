package org.camunda.bpm.extension.keycloak.cache;

/**
 * Factory for creating a QueryCache.
 */
public class CacheFactory {

  /**
   * Creates implementations of QueryCache based on the provided configuration.
   * 
   * @param configuration the configuration defining the caching behavior
   * @return The created QueryCache implementation
   */
  public static <K, V> QueryCache<K, V> create(CacheConfiguration configuration) {
    if (configuration.isEnabled()) {
      return new CaffeineCache<>(configuration);
    } else {
      return new PassThroughCache<>();
    }
  }
}

package org.camunda.bpm.extension.keycloak.cache;

/**
 * Creates implementations of QueryCache based on provided configuration
 **/
public class CacheFactory {

  /**
   * @param configuration the configuration defining the caching behaviour
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

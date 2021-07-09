package org.camunda.bpm.extension.keycloak.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * An implementation of QueryCache backed by Caffeine
 */
public class CaffeineCache<K, V> implements QueryCache<K, V> {

  private final Cache<K, V> cache;

  public CaffeineCache(CacheConfiguration config) {
    this(config, Ticker.systemTicker());
  }

  /**
   * @param config the cache configuration
   * @param ticker ticker to be used by the cache to measure durations 
   */
  public CaffeineCache(CacheConfiguration config, Ticker ticker) {
    this.cache = Caffeine.newBuilder()
            .ticker(ticker)
            .maximumSize(config.getMaxSize())
            .expireAfterWrite(config.getExpirationTimeout())
            .build();
  }

  @Override
  public V getOrCompute(K key, Function<K, V> computation) {
    return this.cache.get(key, computation);
  }

  @Override
  public void clear() {
    this.cache.invalidateAll();
  }

  /**
   * to be used in tests to trigger any pending eviction tasks.
   * this is required since caffeine performs these asynchronously.
   * to make tests predictable and deterministic, this method should be called after every potential cacheable action
   */
  public void cleanUp() {
    this.cache.cleanUp();
  }

  /**
   * returns cached entries as a map.
   * useful for asserting entries in tests.
   */
  public ConcurrentMap<K, V> asMap() {
    return this.cache.asMap();
  }
}

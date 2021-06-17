package org.camunda.bpm.extension.keycloak.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.function.Function;

/**
 * An implementation of QueryCache backed by Caffeine
 */
public class CaffeineCache<K, V> implements QueryCache<K, V> {

  private final Cache<K, V> cache;

  public CaffeineCache(CacheConfiguration config) {
    this.cache = Caffeine.newBuilder()
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
}

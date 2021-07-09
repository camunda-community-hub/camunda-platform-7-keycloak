package org.camunda.bpm.extension.keycloak.cache;

import java.util.function.Function;

/**
 * The interface for implementations of query cache
 *
 * @param <K> The type of key used to lookup the cache
 * @param <V> The cached or computed value corresponding to the provided key
 */
public interface QueryCache<K, V> {

  /**
   * Gets the cached value if present or computes, stores and returns the computed value.
   *
   * @param key         The key to lookup the cache with
   * @param computation the computation to perform if no entries are present for requested key.
   * @return the value corresponding to the provided key.
   */
  V getOrCompute(K key, Function<K, V> computation);

  /**
   * clear/invalidate all entries in cache
   */
  void clear();
}

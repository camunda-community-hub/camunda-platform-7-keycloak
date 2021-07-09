package org.camunda.bpm.extension.keycloak.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * An implementation of QueryCache backed by Caffeine.
 */
public class CaffeineCache<K, V> implements QueryCache<K, V> {

	/** The cache. */
	private final Cache<K, V> cache;

	/**
	 * Creates a new Caffeine backed cache.
	 * @param config the cach configuration
	 */
	public CaffeineCache(CacheConfiguration config) {
		this(config, Ticker.systemTicker());
	}

	/**
	 * Creates a new Caffeine backed cache.
	 * @param config the cache configuration
	 * @param ticker ticker to be used by the cache to measure durations
	 */
	public CaffeineCache(CacheConfiguration config, Ticker ticker) {
		this.cache = Caffeine.newBuilder().ticker(ticker).maximumSize(config.getMaxSize())
				.expireAfterWrite(config.getExpirationTimeout()).build();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V getOrCompute(K key, Function<K, V> computation) {
		return this.cache.get(key, computation);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		this.cache.invalidateAll();
	}

	/**
	 * To be used in tests to trigger any pending eviction tasks. This is required
	 * since caffeine performs these asynchronously. To make tests predictable and
	 * deterministic, this method should be called after every potential cacheable
	 * action.
	 */
	public void cleanUp() {
		this.cache.cleanUp();
	}

	/**
	 * Returns cached entries as a map. Useful for asserting entries in tests.
	 */
	public ConcurrentMap<K, V> asMap() {
		return this.cache.asMap();
	}
}

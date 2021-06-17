package org.camunda.bpm.extension.keycloak.cache;

import java.util.function.Function;

/**
 * A no-op implementation of QueryCache.
 */
public class PassThroughCache<K, V> implements QueryCache<K, V> {
	@Override
	public V getOrCompute(K key, Function<K, V> computation) {
		return computation.apply(key);
	}

	@Override
	public void clear() {
		// no-op
	}
}

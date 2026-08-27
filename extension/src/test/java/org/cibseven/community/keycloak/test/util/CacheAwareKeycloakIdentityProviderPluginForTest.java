package org.cibseven.community.keycloak.test.util;

import java.util.List;

import org.cibseven.bpm.engine.identity.Group;
import org.cibseven.bpm.engine.identity.User;
import org.cibseven.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.cibseven.community.keycloak.CacheableKeycloakCheckPasswordCall;
import org.cibseven.community.keycloak.CacheableKeycloakGroupQuery;
import org.cibseven.community.keycloak.CacheableKeycloakUserQuery;
import org.cibseven.community.keycloak.KeycloakIdentityProviderFactory;
import org.cibseven.community.keycloak.cache.CacheConfiguration;
import org.cibseven.community.keycloak.cache.CaffeineCache;
import org.cibseven.community.keycloak.plugin.KeycloakIdentityProviderPlugin;

public class CacheAwareKeycloakIdentityProviderPluginForTest extends KeycloakIdentityProviderPlugin {

	public static CaffeineCache<CacheableKeycloakUserQuery, List<User>> userQueryCache;
	public static CaffeineCache<CacheableKeycloakGroupQuery, List<Group>> groupQueryCache;
	public static CaffeineCache<CacheableKeycloakCheckPasswordCall, Boolean> checkPasswordCache;

	@Override
	public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
		super.preInit(processEngineConfiguration);
		KeycloakIdentityProviderFactory factory =
						(KeycloakIdentityProviderFactory) processEngineConfiguration.getIdentityProviderSessionFactory();

		PredictableTicker ticker = new PredictableTicker();
		CacheConfiguration cacheConfiguration = CacheConfiguration.from(this);
		CacheConfiguration loginCacheConfiguration = CacheConfiguration.fromLoginConfigOf(this);

		// instantiate with ticker that can be controlled in tests
		userQueryCache = new CaffeineCache<>(cacheConfiguration, ticker);
		groupQueryCache = new CaffeineCache<>(cacheConfiguration, ticker);
		checkPasswordCache = new CaffeineCache<>(loginCacheConfiguration, ticker);

		factory.setUserQueryCache(userQueryCache);
		factory.setGroupQueryCache(groupQueryCache);
		factory.setCheckPasswordCache(checkPasswordCache);
	}
}

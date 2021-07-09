package org.camunda.bpm.extension.keycloak.test;

import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.extension.keycloak.CacheableKeycloakGroupQuery;
import org.camunda.bpm.extension.keycloak.CacheableKeycloakUserQuery;
import org.camunda.bpm.extension.keycloak.KeycloakIdentityProviderFactory;
import org.camunda.bpm.extension.keycloak.cache.CacheConfiguration;
import org.camunda.bpm.extension.keycloak.cache.CaffeineCache;
import org.camunda.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;

import java.util.List;

public class CacheAwareKeycloakIdentityProviderPluginForTest extends KeycloakIdentityProviderPlugin {

	public static CaffeineCache<CacheableKeycloakUserQuery, List<User>> userQueryCache;
	public static CaffeineCache<CacheableKeycloakGroupQuery, List<Group>> groupQueryCache;

	@Override
	public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
		super.preInit(processEngineConfiguration);
		KeycloakIdentityProviderFactory factory =
						(KeycloakIdentityProviderFactory) processEngineConfiguration.getIdentityProviderSessionFactory();

		PredictableTicker ticker = new PredictableTicker();
		CacheConfiguration cacheConfiguration = CacheConfiguration.from(this);

		// instantiate with ticker that can be controlled in tests
		userQueryCache = new CaffeineCache<>(cacheConfiguration, ticker);
		groupQueryCache = new CaffeineCache<>(cacheConfiguration, ticker);

		factory.setUserQueryCache(userQueryCache);
		factory.setGroupQueryCache(groupQueryCache);
	}
}

package org.camunda.bpm.extension.keycloak.test;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.identity.UserQuery;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.extension.keycloak.CacheableKeycloakUserQuery;
import org.camunda.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User query test for the Keycloak identity provider with caching enabled and cache duration configured
 */
public class KeycloakUserQueryTestWithCachingAndCustomCacheExpiry extends AbstractKeycloakIdentityProviderTest {

	public static Test suite() {
		return new TestSetup(new TestSuite(KeycloakUserQueryTestWithCachingAndCustomCacheExpiry.class)) {

			// @BeforeClass
			protected void setUp() throws Exception {
				ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
								.createProcessEngineConfigurationFromResource("camunda.enableCachingAndConfigureCacheDuration.cfg.xml");
				configureKeycloakIdentityProviderPlugin(config);
				PluggableProcessEngineTestCase.cachedProcessEngine = config.buildProcessEngine();
			}

			// @AfterClass
			protected void tearDown() throws Exception {
				PluggableProcessEngineTestCase.cachedProcessEngine.close();
				PluggableProcessEngineTestCase.cachedProcessEngine = null;
			}
		};
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		// delete all created authorizations
		processEngine.getAuthorizationService().createAuthorizationQuery().list().forEach(a -> {
			processEngine.getAuthorizationService().deleteAuthorization(a.getId());
		});
		this.clearCache();
		PredictableTicker.reset();
		CountingHttpRequestInterceptor.resetCount();
	}

	/**
	 * clears the query cache so each test can start with a clean slate
	 */
	private void clearCache() {
		processEngineConfiguration.getProcessEnginePlugins()
						.stream()
						.filter(KeycloakIdentityProviderPlugin.class::isInstance)
						.map(KeycloakIdentityProviderPlugin.class::cast)
						.forEach(KeycloakIdentityProviderPlugin::clearCache);
	}

	// ------------------------------------------------------------------------
	// Test configuration
	// ------------------------------------------------------------------------

	public void testCacheEntriesEvictedWhenCacheTimeoutIsReached() {

		UserQuery query = identityService.createUserQuery();

		// query Admin at time = 0
		assertEquals("Admin", queryUser(query, "Admin").getFirstName());

		// cache contains only Admin at this point
		assertEquals(Collections.singletonList("Admin"), getCacheEntries());

		// move clock by 2 minutes
		PredictableTicker.moveTimeForwardByMinutes(2);

		// query Identity after 2 minutes
		assertEquals("Identity", queryUser(query, "Identity").getFirstName());

		// cache contains Identity and Admin
		assertEquals(Arrays.asList("Admin", "Identity"), getCacheEntries());

		// move clock by another 2 minutes
		PredictableTicker.moveTimeForwardByMinutes(2);

		// cache still contains Identity and Admin
		assertEquals(Arrays.asList("Admin", "Identity"), getCacheEntries());

		// move clock by another 2 minutes
		PredictableTicker.moveTimeForwardByMinutes(2);

		// Admin was evicted because eviction timeout (5 minutes) has been breached 
		// it's been 6 minutes since Admin was inserted into cache
		assertEquals(Collections.singletonList("Identity"), getCacheEntries());

		// move clock by another 5 minutes
		PredictableTicker.moveTimeForwardByMinutes(5);

		// cache is empty. Identity has also been evicted
		assertEquals(Collections.emptyList(), getCacheEntries());
	}

	private static User queryUser(UserQuery query, String firstName) {
		User user = query.userFirstName(firstName).singleResult();
		processPendingCacheEvictions();
		return user;
	}

	private static List<String> getCacheEntries() {
		processPendingCacheEvictions();
		return CacheAwareKeycloakIdentityProviderPluginForTest.userQueryCache
						.asMap()
						.keySet()
						.stream()
						.map(CacheableKeycloakUserQuery::getFirstName)
						.sorted()
						.collect(Collectors.toList());
	}

	private static void processPendingCacheEvictions() {
		CacheAwareKeycloakIdentityProviderPluginForTest.userQueryCache.cleanUp();
	}

}

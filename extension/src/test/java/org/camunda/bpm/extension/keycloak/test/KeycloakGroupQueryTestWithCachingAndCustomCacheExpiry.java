package org.camunda.bpm.extension.keycloak.test;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.extension.keycloak.CacheableKeycloakGroupQuery;
import org.camunda.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;
import org.camunda.bpm.extension.keycloak.test.util.CacheAwareKeycloakIdentityProviderPluginForTest;
import org.camunda.bpm.extension.keycloak.test.util.CountingHttpRequestInterceptor;
import org.camunda.bpm.extension.keycloak.test.util.PredictableTicker;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests group queries with caching enabled and cache duration configured
 */
public class KeycloakGroupQueryTestWithCachingAndCustomCacheExpiry extends AbstractKeycloakIdentityProviderTest {

	public static Test suite() {
		return new TestSetup(new TestSuite(KeycloakGroupQueryTestWithCachingAndCustomCacheExpiry.class)) {

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

		GroupQuery query = identityService.createGroupQuery();

		// query camunda-admin at time = 0
		assertEquals("camunda-admin", queryGroup(query, "camunda-admin").getName());

		// cache contains only camunda-admin at this point
		assertEquals(Collections.singletonList("camunda-admin"), getCacheEntries());

		// move clock by 2 minutes
		PredictableTicker.moveTimeForwardByMinutes(2);

		// query cam-read-only after 2 minutes
		assertEquals("cam-read-only", queryGroup(query, "cam-read-only").getName());

		// cache contains cam-read-only and camunda-admin
		assertEquals(Arrays.asList("cam-read-only", "camunda-admin"), getCacheEntries());

		// move clock by another 2 minutes
		PredictableTicker.moveTimeForwardByMinutes(2);

		// cache still contains cam-read-only and camunda-admin
		assertEquals(Arrays.asList("cam-read-only", "camunda-admin"), getCacheEntries());

		// move clock by another 2 minutes
		PredictableTicker.moveTimeForwardByMinutes(2);

		// camunda-admin was evicted because eviction timeout (5 minutes) has been breached 
		// it's been 6 minutes since camunda-admin was inserted into cache
		assertEquals(Collections.singletonList("cam-read-only"), getCacheEntries());

		// move clock by another 5 minutes
		PredictableTicker.moveTimeForwardByMinutes(5);

		// cache is empty. cam-read-only has also been evicted
		assertEquals(Collections.emptyList(), getCacheEntries());
	}

	private static Group queryGroup(GroupQuery query, String groupName) {
		Group group = query.groupName(groupName).singleResult();
		processPendingCacheEvictions();
		return group;
	}

	private static List<String> getCacheEntries() {
		processPendingCacheEvictions();
		return CacheAwareKeycloakIdentityProviderPluginForTest.groupQueryCache
						.asMap()
						.keySet()
						.stream()
						.map(CacheableKeycloakGroupQuery::getName)
						.sorted()
						.collect(Collectors.toList());
	}

	private static void processPendingCacheEvictions() {
		CacheAwareKeycloakIdentityProviderPluginForTest.groupQueryCache.cleanUp();
	}
}

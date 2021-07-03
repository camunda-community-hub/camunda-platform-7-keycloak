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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests group queries with caching enabled and max size configured
 */
public class KeycloakGroupQueryTestWithCachingAndMaxSize extends AbstractKeycloakIdentityProviderTest {

	public static Test suite() {
		return new TestSetup(new TestSuite(KeycloakGroupQueryTestWithCachingAndMaxSize.class)) {

			// @BeforeClass
			protected void setUp() throws Exception {
				ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
								.createProcessEngineConfigurationFromResource("camunda.enableCachingAndConfigureMaxCacheSize.cfg.xml");
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

	public void testCacheEntriesEvictedWhenMaxSizeIsReached() {

		GroupQuery query = identityService.createGroupQuery();

		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		assertEquals(0, countBefore);

		assertEquals("camunda-admin", queryGroup(query, "camunda-admin").getName());

		// camunda-admin has not been queried before so http call count should increase by 1
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		// cache contains only camunda-admin at this point
		assertEquals(Collections.singletonList("camunda-admin"), getCacheEntries());

		assertEquals("cam-read-only", queryGroup(query, "cam-read-only").getName());

		// cam-read-only has not been queried before so http call count should increase by 1
		assertEquals(countBefore + 2, CountingHttpRequestInterceptor.getHttpRequestCount());

		// cache contains cam-read-only and camunda-admin
		assertEquals(Arrays.asList("cam-read-only", "camunda-admin"), getCacheEntries());

		assertEquals("camunda-admin", queryGroup(query, "camunda-admin").getName());

		// camunda-admin has already been queried and is still in the cache so count stays same
		assertEquals(countBefore + 2, CountingHttpRequestInterceptor.getHttpRequestCount());

		// cache still contains cam-read-only and camunda-admin
		assertEquals(Arrays.asList("cam-read-only", "camunda-admin"), getCacheEntries());

		assertEquals("manager", queryGroup(query, "manager").getName());

		// manager has not been queried before so http call count should increase by 1
		assertEquals(countBefore + 3, CountingHttpRequestInterceptor.getHttpRequestCount());

		// cam-read-only was evicted because maxSize(2) was breached and it was used fewer times than camunda-admin
		assertEquals(Arrays.asList("camunda-admin", "manager"), getCacheEntries());

		// query cam-read-only again
		assertEquals("cam-read-only", queryGroup(query, "cam-read-only").getName());

		// count should increase because cam-read-only was removed from cache before the query
		assertEquals(countBefore + 4, CountingHttpRequestInterceptor.getHttpRequestCount());

		// manager was evicted because it was used fewer times than camunda-admin
		assertEquals(Arrays.asList("cam-read-only", "camunda-admin"), getCacheEntries());
	}

	private static Group queryGroup(GroupQuery query, String groupName) {
		Group group = query.groupName(groupName).singleResult();
		CacheAwareKeycloakIdentityProviderPluginForTest.groupQueryCache.cleanUp();
		return group;
	}

	private static List<String> getCacheEntries() {
		return CacheAwareKeycloakIdentityProviderPluginForTest.groupQueryCache
						.asMap()
						.keySet()
						.stream()
						.map(CacheableKeycloakGroupQuery::getName)
						.sorted()
						.collect(Collectors.toList());
	}
}

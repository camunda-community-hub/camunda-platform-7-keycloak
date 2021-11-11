package org.camunda.bpm.extension.keycloak.test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.extension.keycloak.CacheableKeycloakCheckPasswordCall;
import org.camunda.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;
import org.camunda.bpm.extension.keycloak.test.util.CacheAwareKeycloakIdentityProviderPluginForTest;
import org.camunda.bpm.extension.keycloak.test.util.CountingHttpRequestInterceptor;
import org.camunda.bpm.extension.keycloak.test.util.PredictableTicker;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Keycloak login test with login cache enabled.
 */
public class KeycloakLoginTestWithCaching extends AbstractKeycloakIdentityProviderTest {
  
	public static Test suite() {
		return new TestSetup(new TestSuite(KeycloakLoginTestWithCaching.class)) {

			// @BeforeClass
			protected void setUp() throws Exception {
				ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
								.createProcessEngineConfigurationFromResource("camunda.enableLoginCaching.cfg.xml");
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
		this.clearCache();
		CountingHttpRequestInterceptor.resetCount();
	}

	/**
	 * clears the query cache so each test can start with a clean state
	 */
	private void clearCache() {
		processEngineConfiguration.getProcessEnginePlugins()
						.stream()
						.filter(KeycloakIdentityProviderPlugin.class::isInstance)
						.map(KeycloakIdentityProviderPlugin.class::cast)
						.forEach(KeycloakIdentityProviderPlugin::clearCache);
	}

	// ------------------------------------------------------------------------
	// Test methods
	// ------------------------------------------------------------------------
	
	public void testCacheEnabledKeycloakLoginSuccess() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		// first login
		assertTrue(identityService.checkPassword("camunda@accso.de", "camunda1!"));
		// non cached query. http request count should have increased
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());
		
		// second login
		assertTrue(identityService.checkPassword("camunda@accso.de", "camunda1!"));
		// request count should be same as before
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());
	}

	public void testCacheEnabledKeycloakLoginCapitalization() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		// first login
		assertTrue(identityService.checkPassword("Camunda@Accso.de", "camunda1!"));
		// non cached query. http request count should have increased
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		// second login
		assertTrue(identityService.checkPassword("Camunda@Accso.de", "camunda1!"));
		// request count should be same as before
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());
	}

	public void testCacheEnabledKeycloakLoginFailure() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		// failing logins
		assertFalse(identityService.checkPassword("camunda@accso.de", "c"));
		assertFalse(identityService.checkPassword("non-existing", "camunda1!"));
		// non cached query. http requests count should have increased
		assertEquals(countBefore + 2, CountingHttpRequestInterceptor.getHttpRequestCount());

		// second failing logins
		assertFalse(identityService.checkPassword("camunda@accso.de", "c"));
		assertFalse(identityService.checkPassword("non-existing", "camunda1!"));
		// request count should be same as before
		assertEquals(countBefore + 2, CountingHttpRequestInterceptor.getHttpRequestCount());
		
		// third now successful login
		assertTrue(identityService.checkPassword("camunda@accso.de", "camunda1!"));
		// request count should have increased due to the different password
		assertEquals(countBefore + 3, CountingHttpRequestInterceptor.getHttpRequestCount());
	}

	public void testCacheEnabledKeycloakLoginNullValues() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		// failing logins
		assertFalse(identityService.checkPassword(null, "camunda1!"));
		assertFalse(identityService.checkPassword("camunda@accso.de", null));
		assertFalse(identityService.checkPassword(null, null));
		// no http requests: missing userId or password - we do not support anonymous logins
		assertEquals(countBefore, CountingHttpRequestInterceptor.getHttpRequestCount());
	}

	public void testCacheEnabledKeycloakLoginEmptyValues() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		// first login
		assertFalse(identityService.checkPassword("", "camunda1!"));
		assertFalse(identityService.checkPassword("camunda@accso.de", ""));
		assertFalse(identityService.checkPassword("", ""));
		// no http request: empty user Id or passwords - we do not support anonymous logins
		assertEquals(countBefore, CountingHttpRequestInterceptor.getHttpRequestCount());
	}
	
	public void testCacheEnabledKeycloakLoginSpecialCharacterPassword() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		// first logins
		assertTrue(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€"));
		assertTrue(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ"));
		// non cached query. http requests count should have increased
		assertEquals(countBefore + 2, CountingHttpRequestInterceptor.getHttpRequestCount());

		// second logins
		assertTrue(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€"));
		assertTrue(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ"));
		// request count should be same as before
		assertEquals(countBefore + 2, CountingHttpRequestInterceptor.getHttpRequestCount());	
	}

	public void testLoginCacheSize() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		// first logins
		assertTrue(identityService.checkPassword("camunda@accso.de", "camunda1!"));
		assertTrue(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€"));
		// non cached query. http requests count should have increased
		assertEquals(countBefore + 2, CountingHttpRequestInterceptor.getHttpRequestCount());
		// check cache entries
		assertEquals(Arrays.asList("camunda@accso.de", "johnfoo@gmail.com"), getCacheEntries());

		// call cache entry
		assertTrue(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€"));
		// request count should be same as before, cache size as well
		assertEquals(countBefore + 2, CountingHttpRequestInterceptor.getHttpRequestCount());
		assertEquals(2, getCacheEntries().size());
		
		// next login
		assertTrue(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ"));
		// non cached query. http requests count should have increased
		assertEquals(countBefore + 3, CountingHttpRequestInterceptor.getHttpRequestCount());
		// check cache entries: one has been evicted, new one is part of the cache
		assertTrue(getCacheEntries().contains("hans.mustermann@tradermail.info"));
		assertEquals(2, getCacheEntries().size());

		// call cache entry
		assertTrue(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ"));
		// request count should be same as before, cache size as well
		assertEquals(countBefore + 3, CountingHttpRequestInterceptor.getHttpRequestCount());
		assertEquals(2, getCacheEntries().size());	
	}
	
	public void testLoginCustomCacheTimeout() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		// first logins
		assertTrue(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€"));
		assertTrue(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ"));
		// non cached query. http requests count should have increased
		assertEquals(countBefore + 2, CountingHttpRequestInterceptor.getHttpRequestCount());

		// move clock by 2 minutes
		PredictableTicker.moveTimeForwardByMinutes(2);

		// next logins
		assertTrue(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€"));
		assertTrue(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ"));
		// request count should be same as before
		assertEquals(countBefore + 2, CountingHttpRequestInterceptor.getHttpRequestCount());

		// move clock by another 2 minutes
		PredictableTicker.moveTimeForwardByMinutes(2);
	
		// next logins
		assertTrue(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€"));
		assertTrue(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ"));
		assertTrue(identityService.checkPassword("camunda@accso.de", "camunda1!"));
		// request count should be increased by new login only
		assertEquals(countBefore + 3, CountingHttpRequestInterceptor.getHttpRequestCount());

		// move clock by another 2 minutes: now 6 minutes are gone, configured timeout has been 5 minutes
		PredictableTicker.moveTimeForwardByMinutes(2);
	
		// check cache: only youngest login (with age 2 minutes) should have survived
		assertEquals(1, getCacheEntries().size());
		assertTrue(getCacheEntries().contains("camunda@accso.de"));
		
		// logins after timeout
		assertTrue(identityService.checkPassword("camunda@accso.de", "camunda1!"));
		assertTrue(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€"));
		assertTrue(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ"));
		// only youngest login cached, others are timed out. http requests count should have increased
		assertEquals(countBefore + 5, CountingHttpRequestInterceptor.getHttpRequestCount());
	}
	
	// ------------------------------------------------------------------------
	// Helper methods
	// ------------------------------------------------------------------------

	private static List<String> getCacheEntries() {
		return CacheAwareKeycloakIdentityProviderPluginForTest.checkPasswordCache
						.asMap()
						.keySet()
						.stream()
						.map(CacheableKeycloakCheckPasswordCall::getUserId)
						.sorted()
						.collect(Collectors.toList());
	}
	
}

package org.camunda.bpm.extension.keycloak.test;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;

import java.util.List;

/**
 * User query test for the Keycloak identity provider with caching enabled.
 */
public class KeycloakUserQueryTestWithCaching extends AbstractKeycloakIdentityProviderTest {

	public static Test suite() {
		return new TestSetup(new TestSuite(KeycloakUserQueryTestWithCaching.class)) {

			// @BeforeClass
			protected void setUp() throws Exception {
				ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
								.createProcessEngineConfigurationFromResource("camunda.enableCaching.cfg.xml");
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

	public void testCacheEnabledQueryWithNoFilter() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		List<User> result = identityService.createUserQuery().list();

		assertEquals(5, result.size());

		// non cached query. http request count should have increased
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		// run query again
		assertEquals(5, identityService.createUserQuery().list().size());

		// request count should be same as before
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());
	}

	public void testCacheEnabledQueryFilterByUserId() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		User user = identityService.createUserQuery().userId("camunda@accso.de").singleResult();
		assertNotNull(user);

		// validate user
		assertEquals("camunda@accso.de", user.getId());
		assertEquals("Admin", user.getFirstName());
		assertEquals("Camunda", user.getLastName());
		assertEquals("camunda@accso.de", user.getEmail());

		// non cached query. http request count should have increased
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		// run query again
		assertEquals(user, identityService.createUserQuery().userId("camunda@accso.de").singleResult());

		// request count should be same as before
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		user = identityService.createUserQuery().userId("non-existing").singleResult();
		assertNull(user);
	}

	public void testCacheEnabledQueryWithPaging() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		// First page
		List<User> result = identityService.createUserQuery().listPage(0, 2);
		assertEquals(2, result.size());

		// non cached query. http request count should have increased
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		// run query again
		assertEquals(2, identityService.createUserQuery().listPage(0, 2).size());

		// request count should be same as before
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		// Next page
		List<User> resultNext = identityService.createUserQuery().listPage(2, 10);
		assertEquals(3, resultNext.size());

		// unique results
		assertEquals(0, result.stream().filter(user -> resultNext.contains(user)).count());
	}

	public void testCacheEnabledQueryOrderByUserId() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		List<User> result = identityService.createUserQuery().orderByUserId().desc().list();
		assertEquals(5, result.size());
		assertTrue(result.get(0).getId().compareTo(result.get(1).getId()) > 0);
		assertTrue(result.get(1).getId().compareTo(result.get(2).getId()) > 0);

		// non cached query. http request count should have increased
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		// run query again
		assertEquals(5, identityService.createUserQuery().orderByUserId().desc().list().size());

		// request count should be same as before
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());
	}

	public void testAuthenticatedUserCanQueryOwnGroupsWhenCacheIsEnabled() {
		try {
			processEngineConfiguration.setAuthorizationEnabled(true);

			identityService.setAuthenticatedUserId("non-existing");
			assertEquals(0, identityService.createUserQuery().count());

			identityService.setAuthenticatedUserId("camunda@accso.de");
			assertEquals(1, identityService.createUserQuery().count());

			// auth should not be cached and should be performed 
			// on every query regardless of caching being enabled or not 
			identityService.setAuthenticatedUserId("non-existing");
			assertEquals(0, identityService.createUserQuery().count());

		} finally {
			processEngineConfiguration.setAuthorizationEnabled(false);
			identityService.clearAuthentication();
		}
	}

}

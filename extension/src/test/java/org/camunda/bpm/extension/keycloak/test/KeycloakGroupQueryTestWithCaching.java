package org.camunda.bpm.extension.keycloak.test;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;
import org.camunda.bpm.extension.keycloak.test.util.CountingHttpRequestInterceptor;

import java.util.List;

/**
 * Tests group queries with caching enabled.
 */
public class KeycloakGroupQueryTestWithCaching extends AbstractKeycloakIdentityProviderTest {

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

		List<Group> groupList = identityService.createGroupQuery().list();
		assertEquals(9, groupList.size());

		// non cached query. http request count should have increased
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		// run query again
		assertEquals(9, identityService.createGroupQuery().list().size());

		// request count should be same as before
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());
	}

	public void testCacheEnabledQueryFilterByGroupId() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		Group group = identityService.createGroupQuery().groupId(GROUP_ID_ADMIN).singleResult();
		assertNotNull(group);

		// validate result
		assertEquals(GROUP_ID_ADMIN, group.getId());
		assertEquals("camunda-admin", group.getName());
		assertEquals("SYSTEM", group.getType());

		// non cached query. http request count should have increased
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		// run query again
		assertEquals(group, identityService.createGroupQuery().groupId(GROUP_ID_ADMIN).singleResult());

		// request count should be same as before
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		group = identityService.createGroupQuery().groupId("whatever").singleResult();
		assertNull(group);
	}

	public void testCacheEnabledQueryWithPaging() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		// First page
		List<Group> result = identityService.createGroupQuery().listPage(0, 3);
		assertEquals(3, result.size());

		// non cached query. http request count should have increased
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		// run query again
		assertEquals(3, identityService.createGroupQuery().listPage(0, 3).size());

		// request count should be same as before
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		// Next page
		List<Group> resultNext = identityService.createGroupQuery().listPage(3, 3);
		assertEquals(3, resultNext.size());

		// Next page
		List<Group> resultLast = identityService.createGroupQuery().listPage(6, 10);
		assertEquals(3, resultLast.size());

		// unique results
		assertEquals(0, result.stream().filter(group -> resultNext.contains(group)).count());
		assertEquals(0, result.stream().filter(group -> resultLast.contains(group)).count());
	}

	public void testCacheEnabledQueryOrderByGroupId() {
		int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

		List<Group> groupList = identityService.createGroupQuery().orderByGroupId().desc().list();
		assertEquals(9, groupList.size());
		assertTrue(groupList.get(0).getId().compareTo(groupList.get(1).getId()) > 0);
		assertTrue(groupList.get(1).getId().compareTo(groupList.get(2).getId()) > 0);
		assertTrue(groupList.get(2).getId().compareTo(groupList.get(3).getId()) > 0);
		assertTrue(groupList.get(5).getId().compareTo(groupList.get(6).getId()) > 0);
		assertTrue(groupList.get(6).getId().compareTo(groupList.get(7).getId()) > 0);

		// non cached query. http request count should have increased
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

		// run query again
		assertEquals(9, identityService.createGroupQuery().orderByGroupId().desc().list().size());

		// request count should be same as before
		assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());
	}

	public void testAuthenticatedUserCanQueryOwnGroupsWhenCacheIsEnabled() {
		try {
			processEngineConfiguration.setAuthorizationEnabled(true);
			identityService.setAuthenticatedUserId("johnfoo@gmail.com");

			assertEquals(0, identityService.createGroupQuery().groupMember("camunda@accso.de").count());
			assertEquals(2, identityService.createGroupQuery().groupMember("johnfoo@gmail.com").count());

			// auth should not be cached and should be performed 
			// on every query regardless of caching being enabled or not 
			assertEquals(0, identityService.createGroupQuery().groupMember("camunda@accso.de").count());
		} finally {
			processEngineConfiguration.setAuthorizationEnabled(false);
			identityService.clearAuthentication();
		}
	}

}

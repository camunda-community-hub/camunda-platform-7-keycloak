package org.camunda.bpm.extension.keycloak.test;

import java.util.List;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * User query test for the Keycloak identity provider.
 * Flags useEmailAsCamundaUserId and useUsernameAsCamundaUserId disabled.
 */
public class KeycloakUseKeycloakIdAsUserIdQueryTest extends AbstractKeycloakIdentityProviderTest {

	public static Test suite() {
	    return new TestSetup(new TestSuite(KeycloakUseKeycloakIdAsUserIdQueryTest.class)) {

	    	// @BeforeClass
	        protected void setUp() throws Exception {
	    		ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
	    				.createProcessEngineConfigurationFromResource("camunda.useKeycloakIdAsCamundaUserId.cfg.xml");
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

	// ------------------------------------------------------------------------
	// Authorization tests
	// ------------------------------------------------------------------------
	
	public void testKeycloakLoginSuccess() {
		assertTrue(identityService.checkPassword(USER_ID_CAMUNDA_ADMIN, "camunda1!"));
	}

	// ------------------------------------------------------------------------
	// User Query tests
	// ------------------------------------------------------------------------
	
	public void testUserQueryFilterByUserId() {
		User user = identityService.createUserQuery().userId(USER_ID_TEAMLEAD).singleResult();
		assertNotNull(user);

		user = identityService.createUserQuery().userId(USER_ID_CAMUNDA_ADMIN).singleResult();
		assertNotNull(user);

		// validate user
		assertEquals(USER_ID_CAMUNDA_ADMIN, user.getId());
		assertEquals("Admin", user.getFirstName());
		assertEquals("Camunda", user.getLastName());
		assertEquals("camunda@accso.de", user.getEmail());

		user = identityService.createUserQuery().userId("non-existing").singleResult();
		assertNull(user);
	}

	public void testUserQueryFilterByUserIdIn() {
		List<User> users = identityService.createUserQuery().userIdIn(USER_ID_CAMUNDA_ADMIN, USER_ID_TEAMLEAD).list();
		assertNotNull(users);
		assertEquals(2, users.size());

		users = identityService.createUserQuery().userIdIn(USER_ID_CAMUNDA_ADMIN, "non-existing").list();
		assertNotNull(users);
		assertEquals(1, users.size());
	}

	public void testUserQueryFilterByEmail() {
		User user = identityService.createUserQuery().userEmail("camunda@accso.de").singleResult();
		assertNotNull(user);

		// validate user
		assertEquals(USER_ID_CAMUNDA_ADMIN, user.getId());
		assertEquals("Admin", user.getFirstName());
		assertEquals("Camunda", user.getLastName());
		assertEquals("camunda@accso.de", user.getEmail());

		user = identityService.createUserQuery().userEmail("non-exist*").singleResult();
		assertNull(user);
	}

	public void testUserQueryFilterByGroupIdAndId() {
		List<User> result = identityService.createUserQuery()
				.memberOfGroup(GROUP_ID_ADMIN)
				.userId(USER_ID_CAMUNDA_ADMIN)
				.list();
		assertEquals(1, result.size());

		result = identityService.createUserQuery()
				.memberOfGroup(GROUP_ID_ADMIN)
				.userId("non-exist")
				.list();
		assertEquals(0, result.size());

		result = identityService.createUserQuery()
				.memberOfGroup("non-exist")
				.userId(USER_ID_CAMUNDA_ADMIN)
				.list();
		assertEquals(0, result.size());
		
	}

	public void testAuthenticatedUserSeesHimself() {
		try {
			processEngineConfiguration.setAuthorizationEnabled(true);

			identityService.setAuthenticatedUserId("non-existing");
			assertEquals(0, identityService.createUserQuery().count());

			identityService.setAuthenticatedUserId(USER_ID_CAMUNDA_ADMIN);
			assertEquals(1, identityService.createUserQuery().count());

		} finally {
			processEngineConfiguration.setAuthorizationEnabled(false);
			identityService.clearAuthentication();
		}
	}

	// ------------------------------------------------------------------------
	// Group query tests
	// ------------------------------------------------------------------------

	public void testGroupQueryFilterByUserId() {
		List<Group> result = identityService.createGroupQuery().groupMember(USER_ID_CAMUNDA_ADMIN).list();
		assertEquals(1, result.size());

		result = identityService.createGroupQuery().groupMember("non-exist").list();
		assertEquals(0, result.size());
	}

	public void testFilterByGroupIdAndUserId() {
		Group group = identityService.createGroupQuery()
				.groupId(GROUP_ID_ADMIN)
				.groupMember(USER_ID_CAMUNDA_ADMIN)
				.singleResult();
		assertNotNull(group);
		assertEquals("camunda-admin", group.getName());

		group = identityService.createGroupQuery()
				.groupId("non-exist")
				.groupMember(USER_ID_CAMUNDA_ADMIN)
				.singleResult();
		assertNull(group);

		group = identityService.createGroupQuery()
				.groupId(GROUP_ID_ADMIN)
				.groupMember("non-exist")
				.singleResult();
		assertNull(group);
	}
	
	public void testFilterByGroupIdInAndUserId() {
		Group group = identityService.createGroupQuery()
				.groupIdIn(GROUP_ID_ADMIN, GROUP_ID_TEAMLEAD)
				.groupMember(USER_ID_CAMUNDA_ADMIN)
				.singleResult();
		assertNotNull(group);
		assertEquals("camunda-admin", group.getName());

		group = identityService.createGroupQuery()
				.groupIdIn(GROUP_ID_ADMIN, GROUP_ID_TEAMLEAD)
				.groupMember("non-exist")
				.singleResult();
		assertNull(group);
	}
	
}

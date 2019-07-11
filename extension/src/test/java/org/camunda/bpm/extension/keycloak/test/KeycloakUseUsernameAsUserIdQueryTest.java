package org.camunda.bpm.extension.keycloak.test;

import java.util.List;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;

/**
 * User query test for the Keycloak identity provider.
 * Flag useUsernameAsCamundaUserId enabled.
 */
public class KeycloakUseUsernameAsUserIdQueryTest extends AbstractKeycloakIdentityProviderTest {

	@Override
	protected void initializeProcessEngine() {
		ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
				.createProcessEngineConfigurationFromResource("camunda.useUsernameAsCamundaUserId.cfg.xml");
		config.getProcessEnginePlugins().forEach(p -> {
			if (p instanceof KeycloakIdentityProviderPlugin) {
				((KeycloakIdentityProviderPlugin) p).setClientSecret(CLIENT_SECRET);
			}
		});
		processEngine = config.buildProcessEngine();
	}

	@Override
	protected void closeDownProcessEngine() {
		super.closeDownProcessEngine();
		processEngine.close();
		processEngine = null;
	}
	
	// ------------------------------------------------------------------------
	// Authorization tests
	// ------------------------------------------------------------------------
	
	public void testKeycloakLoginSuccess() {
		assertTrue(identityService.checkPassword("camunda", "camunda1!"));
	}

	// ------------------------------------------------------------------------
	// User Query tests
	// ------------------------------------------------------------------------
	
	public void testUserQueryFilterByUserId() {
		User user = identityService.createUserQuery().userId("hans.mustermann").singleResult();
		assertNotNull(user);

		user = identityService.createUserQuery().userId("camunda").singleResult();
		assertNotNull(user);

		// validate user
		assertEquals("camunda", user.getId());
		assertEquals("Admin", user.getFirstName());
		assertEquals("Camunda", user.getLastName());
		assertEquals("camunda@accso.de", user.getEmail());

		user = identityService.createUserQuery().userId("non-existing").singleResult();
		assertNull(user);
	}

	public void testUserQueryFilterByUserIdIn() {
		List<User> users = identityService.createUserQuery().userIdIn("camunda", "hans.mustermann").list();
		assertNotNull(users);
		assertEquals(2, users.size());

		users = identityService.createUserQuery().userIdIn("camunda", "non-existing").list();
		assertNotNull(users);
		assertEquals(1, users.size());
	}

	public void testUserQueryFilterByEmail() {
		User user = identityService.createUserQuery().userEmail("camunda@accso.de").singleResult();
		assertNotNull(user);

		// validate user
		assertEquals("camunda", user.getId());
		assertEquals("Admin", user.getFirstName());
		assertEquals("Camunda", user.getLastName());
		assertEquals("camunda@accso.de", user.getEmail());

		user = identityService.createUserQuery().userEmail("non-exist*").singleResult();
		assertNull(user);
	}

	public void testUserQueryFilterByGroupIdAndId() {
		List<User> result = identityService.createUserQuery()
				.memberOfGroup(GROUP_ID_ADMIN)
				.userId("camunda")
				.list();
		assertEquals(1, result.size());

		result = identityService.createUserQuery()
				.memberOfGroup(GROUP_ID_ADMIN)
				.userId("non-exist")
				.list();
		assertEquals(0, result.size());

		result = identityService.createUserQuery()
				.memberOfGroup("non-exist")
				.userId("camunda")
				.list();
		assertEquals(0, result.size());
		
	}

	public void testAuthenticatedUserSeesHimself() {
		try {
			processEngineConfiguration.setAuthorizationEnabled(true);

			identityService.setAuthenticatedUserId("non-existing");
			assertEquals(0, identityService.createUserQuery().count());

			identityService.setAuthenticatedUserId("camunda");
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
		List<Group> result = identityService.createGroupQuery().groupMember("camunda").list();
		assertEquals(1, result.size());

		result = identityService.createGroupQuery().groupMember("non-exist").list();
		assertEquals(0, result.size());
	}

	public void testFilterByGroupIdAndUserId() {
		Group group = identityService.createGroupQuery()
				.groupId(GROUP_ID_ADMIN)
				.groupMember("camunda")
				.singleResult();
		assertNotNull(group);
		assertEquals("camunda-admin", group.getName());

		group = identityService.createGroupQuery()
				.groupId("non-exist")
				.groupMember("camunda")
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
				.groupMember("camunda")
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

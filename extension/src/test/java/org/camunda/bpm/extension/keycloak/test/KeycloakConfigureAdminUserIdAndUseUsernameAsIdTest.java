package org.camunda.bpm.extension.keycloak.test;

import java.util.List;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;

/**
 * Admin user configuration test for the Keycloak identity provider.
 * Use Keycloak internal ID as administratorUserId and flag useUsernameAsCamundaUserId enabled.
 */
public class KeycloakConfigureAdminUserIdAndUseUsernameAsIdTest extends AbstractKeycloakIdentityProviderTest {

	@Override
	protected void initializeProcessEngine() {
		ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
				.createProcessEngineConfigurationFromResource("camunda.configureAdminUserIdAndUseUsernameAsId.cfg.xml");
		config.getProcessEnginePlugins().forEach(p -> {
			if (p instanceof KeycloakIdentityProviderPlugin) {
				KeycloakIdentityProviderPlugin kcp = (KeycloakIdentityProviderPlugin) p;
				kcp.setClientSecret(CLIENT_SECRET);
				kcp.setAdministratorUserId(USER_ID_CAMUNDA_ADMIN);
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
	
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		// delete all created authorizations
		processEngine.getAuthorizationService().createAuthorizationQuery().list().forEach(a -> {
			processEngine.getAuthorizationService().deleteAuthorization(a.getId());
		});
	}
	
	// ------------------------------------------------------------------------
	// Test configuration
	// ------------------------------------------------------------------------
	

	public void testAdminUserConfiguration() {
		// check engine configuration
		List<String> camundaAdminUsers = ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getAdminUsers();
		assertEquals(1, camundaAdminUsers.size());
		String adminUserId = camundaAdminUsers.get(0);
		assertEquals("camunda", adminUserId);
		
		// check that authorizations have been created
		assertTrue(processEngine.getAuthorizationService().createAuthorizationQuery()
				.userIdIn(adminUserId).count() > 0);
		
		// check sample authorization for applications
		assertEquals(1, processEngine.getAuthorizationService().createAuthorizationQuery()
				.userIdIn(adminUserId)
				.resourceType(Resources.APPLICATION)
				.resourceId(Authorization.ANY)
				.hasPermission(Permissions.ALL)
				.count());

		// query user data
		User user = processEngine.getIdentityService().createUserQuery().userId(adminUserId).singleResult();
		assertNotNull(user);
		assertEquals("camunda", user.getId());
		assertEquals("camunda@accso.de", user.getEmail());
		
		// query groups
		Group group = processEngine.getIdentityService().createGroupQuery().groupMember(adminUserId).singleResult();
		assertNotNull(group);
		assertEquals("camunda-admin", group.getName());
	}

}

package de.vonderbeck.bpm.identity.keycloak.plugin;

import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.camunda.bpm.engine.authorization.Permissions.ALL;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.impl.persistence.entity.AuthorizationEntity;
import org.springframework.util.StringUtils;

import de.vonderbeck.bpm.identity.keycloak.KeycloakConfiguration;
import de.vonderbeck.bpm.identity.keycloak.KeycloakIdentityProviderFactory;
import de.vonderbeck.bpm.identity.keycloak.util.KeycloakPluginLogger;

/**
 * <p>{@link ProcessEnginePlugin} providing Keycloak Identity Provider support</p>
 *
 * <p>This class extends {@link KeycloakConfiguration} such that the configuration properties
 * can be set directly on this class vie the <code>&lt;properties .../&gt;</code> element
 * in bpm-platform.xml / processes.xml</p>
 */
public class KeycloakIdentityProviderPlugin extends KeycloakConfiguration implements ProcessEnginePlugin {

	private final static KeycloakPluginLogger LOG = KeycloakPluginLogger.INSTANCE;
	
	private boolean authorizationEnabled;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
		checkMandatoryConfigurationParameters(processEngineConfiguration);
		
		authorizationEnabled = processEngineConfiguration.isAuthorizationEnabled();

		if (!StringUtils.isEmpty(administratorGroupName)) {
			if (processEngineConfiguration.getAdminGroups() == null) {
				processEngineConfiguration.setAdminGroups(new ArrayList<String>());
			}
			processEngineConfiguration.getAdminGroups().add(administratorGroupName);
		}
		if (!StringUtils.isEmpty(administratorUserName)) {
			if (processEngineConfiguration.getAdminUsers() == null) {
				processEngineConfiguration.setAdminUsers(new ArrayList<String>());
			}
			processEngineConfiguration.getAdminUsers().add(administratorUserName);
		}

		KeycloakIdentityProviderFactory keycloakIdentityProviderFactory = new KeycloakIdentityProviderFactory(this);
		processEngineConfiguration.setIdentityProviderSessionFactory(keycloakIdentityProviderFactory);

		LOG.pluginActivated(getClass().getSimpleName(), processEngineConfiguration.getProcessEngineName());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void postInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
		// nothing to do
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void postProcessEngineBuild(ProcessEngine processEngine) {
		if(!authorizationEnabled) {
			return;
		}

		final AuthorizationService authorizationService = processEngine.getAuthorizationService();

		if (!StringUtils.isEmpty(administratorGroupName)) {
			// query the real group ID
			Group administratorGroup = processEngine.getIdentityService().createGroupQuery().groupName(administratorGroupName).singleResult();
			String administratorGroupId = administratorGroup != null ? administratorGroup.getId() : administratorGroupName;
			// create ADMIN authorizations on all built-in resources for configured admin group
			for (Resource resource : Resources.values()) {
				if(authorizationService.createAuthorizationQuery().groupIdIn(administratorGroupId).resourceType(resource).resourceId(ANY).count() == 0) {
					AuthorizationEntity adminGroupAuth = new AuthorizationEntity(AUTH_TYPE_GRANT);
					adminGroupAuth.setGroupId(administratorGroupId);
					adminGroupAuth.setResource(resource);
					adminGroupAuth.setResourceId(ANY);
					adminGroupAuth.addPermission(ALL);
					authorizationService.saveAuthorization(adminGroupAuth);
					LOG.grantGroupPermissions(administratorGroupName, administratorGroupId, resource.resourceName());
				}
			}
		}

		if (!StringUtils.isEmpty(administratorUserName)) {
			// create ADMIN authorizations on all built-in resources for configured admin user
			for (Resource resource : Resources.values()) {
				if(authorizationService.createAuthorizationQuery().userIdIn(administratorUserName).resourceType(resource).resourceId(ANY).count() == 0) {
					AuthorizationEntity adminUserAuth = new AuthorizationEntity(AUTH_TYPE_GRANT);
					adminUserAuth.setUserId(administratorUserName);
					adminUserAuth.setResource(resource);
					adminUserAuth.setResourceId(ANY);
					adminUserAuth.addPermission(ALL);
					authorizationService.saveAuthorization(adminUserAuth);
					LOG.grantUserPermissions(administratorUserName, resource.resourceName());
				}
			}
		}
	}

	/**
	 * Checks mandatory configuration parameters.
	 * @param processEngineConfiguration the process engine configuration
	 */
	private void checkMandatoryConfigurationParameters(ProcessEngineConfigurationImpl processEngineConfiguration) {
		List<String> missing = new ArrayList<>();
		if (StringUtils.isEmpty(keycloakIssuerUrl)) {
			LOG.missingConfigurationParameter("keycloakIssuerUrl");
			missing.add("keycloakIssuerUrl");
		}
		if (StringUtils.isEmpty(keycloakAdminUrl)) {
			LOG.missingConfigurationParameter("keycloakAdminUrl");
			missing.add("keycloakAdminUrl");
		}
		if (StringUtils.isEmpty(clientId)) {
			LOG.missingConfigurationParameter("clientId");
			missing.add("clientId");
		}
		if (StringUtils.isEmpty(clientSecret)) {
			LOG.missingConfigurationParameter("clientSecret");
			missing.add("clientSecret");
		}
		if (missing.size() > 0) {
			LOG.activationError(getClass().getSimpleName(), processEngineConfiguration.getProcessEngineName(),
					"missing mandatory configuration parameters " + missing.toString());
			throw new IllegalStateException("Unable to initialize plugin "
											+ getClass().getSimpleName() 
											+ ": - missing mandatory configuration parameters: " 
											+ missing.toString());
		}
	}
	
}

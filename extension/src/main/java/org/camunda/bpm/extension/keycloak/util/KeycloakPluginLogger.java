package org.camunda.bpm.extension.keycloak.util;

import org.camunda.commons.logging.BaseLogger;

/**
 * Keycloak plugin logger including message IDs.
 */
public class KeycloakPluginLogger extends BaseLogger {

	public static final String PROJECT_CODE = "KEYCLOAK";

	public static final KeycloakPluginLogger INSTANCE = BaseLogger.createLogger(
			KeycloakPluginLogger.class, PROJECT_CODE, "org.camunda.bpm.extension.keycloak", "01");

	public void pluginActivated(String pluginClassName, String engineName) {
		logInfo("001", "PLUGIN {} activated on process engine {}", pluginClassName, engineName);
	}

	public void grantGroupPermissions(String administratorGroupName, String administratorGroupId, String resourceName) {
		logInfo("002", "GRANT group {}[{}] ALL permissions on resource {}.", administratorGroupName , administratorGroupId, resourceName);
	}

	public void grantUserPermissions(String administratorUserName, String resourceName) {
		logInfo("003", "GRANT user {} ALL permissions on resource {}.", administratorUserName , resourceName);
	}

	public void missingConfigurationParameter(String parameter) {
		logError("004", "CONFIG parameter {} is mandatory, but hasn't been found", parameter);
	}
	
	public void activationError(String pluginClassName, String engineName, String errorMessage) {
		logError("005", "PLUGIN {} could not be activated on process engine {}: {}", pluginClassName, engineName, errorMessage);
	}

	public void requestTokenFailed(Exception exception) {
		logError("011", "TOKEN request failed: {}", exception.getMessage());
	}

	public void refreshTokenFailed(Exception exception) {
		logError("012", "TOKEN refresh failed: {}", exception.getMessage());
	}
	
	public void userNotFound(String userId, Exception exception) {
		logWarn("020", "FIND userId {} failed: {}", userId, exception.getMessage());
	}
	
	public void userQueryFilter(String filter) {
		logDebug("021", "FIND user with query {}", filter);
	}
	
	public void groupQueryFilter(String filter) {
		logDebug("022", "FIND group with query {}", filter);
	}

	public void groupQueryResult(String summary) {
		// log sensitive data only on FINE
		logDebug("050", summary);
	}

	public void userQueryResult(String summary) {
		// log sensitive data only on FINE
		logDebug("051", summary);
	}
}

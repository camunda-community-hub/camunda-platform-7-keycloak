package de.vonderbeck.bpm.identity.keycloak;

/**
 * <p>Java Bean holding Keycloak configuration</p>
 */
public class KeycloakConfiguration {

	/** Keycloak issuer URL including realm name {@code https://<mykeyclaokserver>/auth/realms/master}. */
	protected String keycloakIssuerUrl;

	/** Keycloak admin REST api base URL including realm name {@code https://<mykeyclaokserver>/auth/admin/realms/master}. */
	protected String keycloakAdminUrl;

	// Client must have access type confidential, service accounts enabled, 
	// service account roles must include realm roles for query-users, query-groups, view-users
	/** The client ID. */
	protected String clientId;
	/** The client secret. */
	protected String clientSecret;

	/**
	 * Whether to use the email attribute as Camunda user ID. Activate this when not using SSO.
	 */
	protected boolean useEmailAsCamundaUserId = false;
	
	/** The name of the administrator group.
	 *
	 * If this name is set to a non-null and non-empty value,
	 * the plugin will create group-level Administrator authorizations
	 * on all built-in resources. */
	protected String administratorGroupName;

	/** The name of the administrator user.
	 *
	 * If this name is set to a non-null and non-empty value,
	 * the plugin will create user-level Administrator authorizations
	 * on all built-in resources. */
	protected String administratorUserName;
	
	protected boolean authorizationCheckEnabled = true;

	protected boolean disableSSLCertificateValidation = false;
	
	protected int maxHttpConnections = 200;
	
	//-------------------------------------------------------------------------
	// Getters / Setters
	//-------------------------------------------------------------------------

	/**
	 * @return the keycloakIssuerUrl
	 */
	public String getKeycloakIssuerUrl() {
		return keycloakIssuerUrl;
	}

	/**
	 * @param keycloakIssuerUrl the keycloakIssuerUrl to set
	 */
	public void setKeycloakIssuerUrl(String keycloakIssuerUrl) {
		this.keycloakIssuerUrl = unifyUrl(keycloakIssuerUrl);
	}

	/**
	 * @return the keycloakAdminUrl
	 */
	public String getKeycloakAdminUrl() {
		return keycloakAdminUrl;
	}

	/**
	 * @param keycloakAdminUrl the keycloakAdminUrl to set
	 */
	public void setKeycloakAdminUrl(String keycloakAdminUrl) {
		this.keycloakAdminUrl = unifyUrl(keycloakAdminUrl);
	}

	/**
	 * @return the clientId
	 */
	public String getClientId() {
		return clientId;
	}

	/**
	 * @param clientId the clientId to set
	 */
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	/**
	 * @return the clientSecret
	 */
	public String getClientSecret() {
		return clientSecret;
	}

	/**
	 * @param clientSecret the clientSecret to set
	 */
	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	/**
	 * @return the useEmailAsCamundaUserId
	 */
	public boolean isUseEmailAsCamundaUserId() {
		return useEmailAsCamundaUserId;
	}

	/**
	 * @param useEmailAsCamundaUserId the useEmailAsCamundaUserId to set
	 */
	public void setUseEmailAsCamundaUserId(boolean useEmailAsCamundaUserId) {
		this.useEmailAsCamundaUserId = useEmailAsCamundaUserId;
	}

	/**
	 * @return the administratorGroupName
	 */
	public String getAdministratorGroupName() {
		return administratorGroupName;
	}

	/**
	 * @param administratorGroupName the administratorGroupName to set
	 */
	public void setAdministratorGroupName(String administratorGroupName) {
		this.administratorGroupName = administratorGroupName;
	}

	/**
	 * @return the administratorUserName
	 */
	public String getAdministratorUserName() {
		return administratorUserName;
	}

	/**
	 * @param administratorUserName the administratorUserName to set
	 */
	public void setAdministratorUserName(String administratorUserName) {
		this.administratorUserName = administratorUserName;
	}

	/**
	 * @return the authorizationCheckEnabled
	 */
	public boolean isAuthorizationCheckEnabled() {
		return authorizationCheckEnabled;
	}

	/**
	 * @param authorizationCheckEnabled the authorizationCheckEnabled to set
	 */
	public void setAuthorizationCheckEnabled(boolean authorizationCheckEnabled) {
		this.authorizationCheckEnabled = authorizationCheckEnabled;
	}

	/**
	 * @return the disableSSLCertificateValidation
	 */
	public boolean isDisableSSLCertificateValidation() {
		return disableSSLCertificateValidation;
	}

	/**
	 * @param disableSSLCertificateValidation the disableSSLCertificateValidation to set
	 */
	public void setDisableSSLCertificateValidation(boolean disableSSLCertificateValidation) {
		this.disableSSLCertificateValidation = disableSSLCertificateValidation;
	}

	/**
	 * @return the maxHttpConnections
	 */
	public int getMaxHttpConnections() {
		return maxHttpConnections;
	}

	/**
	 * @param maxHttpConnections the maxHttpConnections to set
	 */
	public void setMaxHttpConnections(int maxHttpConnections) {
		this.maxHttpConnections = maxHttpConnections;
	}

	//-------------------------------------------------------------------------
	// Helpers
	//-------------------------------------------------------------------------

	/**
	 * Provides a unified format for setting URLs.
	 * @param url the URL as configured
	 * @return unified format of this URL
	 */
	private String unifyUrl(String url) {
		if (url.endsWith("/")) {
			return url.substring(0, url.length() - 1);
		}
		return url;
	}
}

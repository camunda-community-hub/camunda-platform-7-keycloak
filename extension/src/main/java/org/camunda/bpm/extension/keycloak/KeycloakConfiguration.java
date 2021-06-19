package org.camunda.bpm.extension.keycloak;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * <p>Java Bean holding Keycloak configuration</p>
 */
public class KeycloakConfiguration {

	/** Keycloak issuer URL including realm name, e.g. {@code https://<mykeyclaokserver>/auth/realms/master}. */
	protected String keycloakIssuerUrl;

	/** Keycloak admin REST api base URL including realm name, e.g. {@code https://<mykeyclaokserver>/auth/admin/realms/master}. */
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

	/**
	 * Whether to use the username attribute as Camunda user ID. Set keycloak.principal-attribute=preferred_username*
	 */
	protected boolean useUsernameAsCamundaUserId = false;

	/**
	 * Whether to use the group's path as Camunda group ID. Makes sense in case you want to have human readable group IDs
	 * and e.g. use them in Camunda's authorization configuration.
	 */
	protected boolean useGroupPathAsCamundaGroupId = false;

	/** The name of the administrator group.
	 *
	 * If this name is set to a non-null and non-empty value,
	 * the plugin will create group-level Administrator authorizations
	 * on all built-in resources. */
	protected String administratorGroupName;

	/** The ID of the administrator user.
	 *
	 * If this ID is set to a non-null and non-empty value,
	 * the plugin will create user-level Administrator authorizations
	 * on all built-in resources. */
	protected String administratorUserId;

	/** Whether to enable Camunda authorization checks for groups and users. */
	protected boolean authorizationCheckEnabled = true;

	/** Disables SSL certificate validation. Useful for testing. */
	protected boolean disableSSLCertificateValidation = false;

	/** Maximum number of HTTP connections of the Keycloak specific connection pool. */
	protected int maxHttpConnections = 50;

	/** Charset to use for REST communication with Keycloak. Leave at UTF-8 for standard installation. */
	protected String charset = StandardCharsets.UTF_8.name();

	/** Maximum result size for Keycloak user queries */
	protected Integer maxResultSize = 250;

	protected String proxyUri = null;

	protected String proxyUser = null;

	protected String proxyPassword = null;

	/** determines if queries to keycloak are cached. default: false */
	private boolean cacheEnabled;

	/**
	 * maximum size of the cache. least used entries are evicted when this limit is reached. default: 500
	 * for more details on this eviction behaviour, please check the documentation of the 
	 * QueryCache implementation. The default QueryCache implementation is CaffeineCache.
	 */
	private int maxCacheSize = 500;

	/** time after which a cached entry is evicted. default: 15 minutes	 */
	private Duration cacheExpirationTimeout = Duration.ofMinutes(15);
	
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
	 * @return the useUsernameAsCamundaUserId
	 */
	public boolean isUseUsernameAsCamundaUserId() {
		return useUsernameAsCamundaUserId;
	}

	/**
	 * @param useUsernameAsCamundaUserId the useUsernameAsCamundaUserId to set
	 */
	public void setUseUsernameAsCamundaUserId(boolean useUsernameAsCamundaUserId) {
		this.useUsernameAsCamundaUserId = useUsernameAsCamundaUserId;
	}

	/**
	 * @return the useGroupPathAsCamundaGroupId
	 */
	public boolean isUseGroupPathAsCamundaGroupId() {
		return useGroupPathAsCamundaGroupId;
	}

	/**
	 * @param useGroupPathAsCamundaGroupId the useGroupPathAsCamundaGroupId to set
	 */
	public void setUseGroupPathAsCamundaGroupId(boolean useGroupPathAsCamundaGroupId) {
		this.useGroupPathAsCamundaGroupId = useGroupPathAsCamundaGroupId;
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
	 * @return the administratorUserId
	 */
	public String getAdministratorUserId() {
		return administratorUserId;
	}

	/**
	 * @param administratorUserId the administratorUserId to set
	 */
	public void setAdministratorUserId(String administratorUserId) {
		this.administratorUserId = administratorUserId;
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

	/**
	 * @return the charset
	 */
	public String getCharset() {
		return charset;
	}

	/**
	 * @param charset the charset to set
	 */
	public void setCharset(String charset) {
		this.charset = charset;
	}

	/**
	 * @return the maxResultSize
	 */
	public Integer getMaxResultSize() {
		return maxResultSize;
	}

	/**
	 * @param maxResultSize the maxResultSize to set
	 */
	public void setMaxResultSize(Integer maxResultSize) {
		this.maxResultSize = maxResultSize;
	}

	public String getProxyUri() {
		return proxyUri;
	}

	public void setProxyUri(String proxyUri) {
		this.proxyUri = proxyUri;
	}

	public String getProxyUser() {
		return proxyUser;
	}

	public void setProxyUser(String proxyUser) {
		this.proxyUser = proxyUser;
	}

	public String getProxyPassword() {
		return proxyPassword;
	}

	public void setProxyPassword(String proxyPassword) {
		this.proxyPassword = proxyPassword;
	}

	/**
	 * @return boolean indicating if caching is enabled
	 */
	public boolean isCacheEnabled() {
		return this.cacheEnabled;
	}

	/**
	 * @return the maximum size of the query cache
	 */
	public int getMaxCacheSize() {
		return this.maxCacheSize;
	}

	/**
	 * @return the expiry timeout for cached entries
	 */
	public Duration getCacheExpirationTimeout() {
		return this.cacheExpirationTimeout;
	}

	/**
	 * @param cacheEnabled boolean indicating whether or not caching is enabled
	 */
	public void setCacheEnabled(boolean cacheEnabled) {
		this.cacheEnabled = cacheEnabled;
	}

	/**
	 * @param maxCacheSize the maximum size of the query cache
	 */
	public void setMaxCacheSize(int maxCacheSize) {
		this.maxCacheSize = maxCacheSize;
	}

	/**
	 * @param cacheExpirationTimeout the expiry timeout for cached entries
	 */
	public void setCacheExpirationTimeout(Duration cacheExpirationTimeout) {
		this.cacheExpirationTimeout = cacheExpirationTimeout;
	}

	/**
	 * @param cacheExpirationTimeout the textual representation of the expiry timeout for cached entries
	 */
	public void setCacheExpirationTimeout(String cacheExpirationTimeout) {
		this.cacheExpirationTimeout = Duration.parse(cacheExpirationTimeout);
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

package org.camunda.bpm.extension.keycloak.test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ResourceBundle;

import javax.net.ssl.SSLContext;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.ssl.TrustStrategy;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.impl.test.TestLogger;
import org.camunda.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;
import org.camunda.commons.logging.BaseLogger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Super class for all Identity Provider Tests.
 */
public abstract class AbstractKeycloakIdentityProviderTest extends PluggableProcessEngineTestCase {

	// Keycloak configuration
	// - in your maven build set as environment variables in order to override defaults
	// - if not available defaults will be taken from keycloak-default.properties
	private static final String KEYCLOAK_URL; // expected format "https://<myhost:myport>/auth
	private static final String KEYCLOAK_ADMIN_USER;
	private static final String KEYCLOAK_ADMIN_PASSWORD;

	// ------------------------------------------------------------------------
	
	private final static Logger LOG = BaseLogger.createLogger(
		      TestLogger.class, "KEYCLOAK", "org.camunda.bpm.extension.keycloak", "42").getLogger();
	
	protected static String GROUP_ID_TEAMLEAD;
	protected static String GROUP_ID_MANAGER;
	protected static String GROUP_ID_ADMIN;
	protected static String GROUP_ID_SYSTEM_READONLY;

	protected static String USER_ID_CAMUNDA_ADMIN;
	protected static String USER_ID_TEAMLEAD;
	protected static String USER_ID_MANAGER;

	protected static String GROUP_ID_HIERARCHY_ROOT;
	protected static String GROUP_ID_HIERARCHY_CHILD1;
	protected static String GROUP_ID_HIERARCHY_CHILD2;
	protected static String GROUP_ID_HIERARCHY_SUBCHILD1;

	protected static String USER_ID_HIERARCHY;
	
	private static final RestTemplate restTemplate = new RestTemplate();
	
	protected static String CLIENT_SECRET = null;
	
	// creates Keycloak setup only once per test run
	static {
		// read keycloak configuration
		ResourceBundle defaults = ResourceBundle.getBundle("keycloak-default");
		KEYCLOAK_URL = getConfigValue(defaults, "keycloak.url");
		KEYCLOAK_ADMIN_USER = getConfigValue(defaults, "keycloak.admin.user");
		KEYCLOAK_ADMIN_PASSWORD = getConfigValue(defaults, "keycloak.admin.password");
		
		// setup 
		try {
			setupRestTemplate();
			setupKeycloak();
		} catch (Exception e) {
			throw new RuntimeException("Unable to setup keycloak test realm", e);
		}
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				try {
					tearDownKeycloak();
				} catch (JSONException e) {
					throw new RuntimeException("Error tearing down keycloak test realm", e);
				}
			}
		});
	}

	/**
	 * Helper class for reading configuration values from environment variables and system properties.
	 * 
	 * @param defaults the default configuration
	 * @param key the key
	 * @return the value of the key - falls back to default in case environment variable hasn't been set
	 */
	private static String getConfigValue(ResourceBundle defaults, String key) {
		String val = null;
		boolean useDefault = false;
		String envVarName = key.toUpperCase().replace('.', '_');
		// 1.) check for environment variable
		val = System.getenv(envVarName);
		if (StringUtils.isEmpty(val)) {
			// 2.) check for system property
			val = System.getProperty(envVarName);
			if (StringUtils.isEmpty(val)) { 
				// 3.) fall back to keycloak-default.properties
				useDefault = true;
				val = defaults.getString(key);
			}
		}
		LOG.info("Configuration {}: '{}' {}", envVarName, val, 
				useDefault ? "[Environment variable not set - using default]" : "");
		return val;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void initializeProcessEngine() {
		processEngine = getOrInitializeCachedProcessEngine();
	}

	/**
	 * Initializes the process engine using standard configuration camunda.cfg.xml, but
	 * replaces the KeyCloakProvider's client secret with the actual test setup.
	 * Furthermore it uses a new database for each test class.
	 * @return the process engine
	 */
	private ProcessEngine getOrInitializeCachedProcessEngine() {
		if (cachedProcessEngine == null) {
			ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
					.createProcessEngineConfigurationFromResource("camunda.cfg.xml");
			config.getProcessEnginePlugins().forEach(p -> {
				if (p instanceof KeycloakIdentityProviderPlugin) {
					((KeycloakIdentityProviderPlugin) p).setClientSecret(CLIENT_SECRET);
				}
			});
			config.setJdbcUrl(config.getJdbcUrl().replace("KeycloakIdentityServiceTest", getClass().getSimpleName()));
			cachedProcessEngine = config.buildProcessEngine();
		}
		return cachedProcessEngine;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void setUp() throws Exception {
		super.setUp();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	// ------------------------------------------------------------------------
	// Test setup
	// ------------------------------------------------------------------------
	
	/**
	 * Setup Keycloak test realm, client, users and groups.
	 * @throws JSONException in case of errors
	 */
	private static void setupKeycloak() throws JSONException {
		LOG.info("Setting up Keycloak Test Realm");
		
		// Authenticate Admin
		HttpHeaders headers = authenticateKeycloakAdmin();
		
	    // Create test realm
		String realm = "test";
		createRealm(headers, realm);
	    
	    // Create Client
		CLIENT_SECRET = createClient(headers, realm, "camunda-identity-service", "http://localhost:8080/login");
	    
	    // Create groups
		GROUP_ID_ADMIN = createGroup(headers, realm, "camunda-admin", true);
		GROUP_ID_TEAMLEAD = createGroup(headers, realm, "teamlead", false);
		GROUP_ID_MANAGER = createGroup(headers, realm, "manager", false);
		GROUP_ID_SYSTEM_READONLY = createGroup(headers, realm, "cam-read-only", true);
		
		// Create Users
		USER_ID_CAMUNDA_ADMIN = createUser(headers, realm, "camunda", "Admin", "Camunda", "camunda@accso.de", "camunda1!");
		assignUserGroup(headers, realm, USER_ID_CAMUNDA_ADMIN, GROUP_ID_ADMIN);
		
		USER_ID_TEAMLEAD = createUser(headers, realm, "hans.mustermann","Hans", "Mustermann", "hans.mustermann@tradermail.info", "äöüÄÖÜ");
		assignUserGroup(headers, realm, USER_ID_TEAMLEAD, GROUP_ID_TEAMLEAD);
		
		USER_ID_MANAGER = createUser(headers, realm, "gunnar.von-der-beck@accso.de", "Gunnar", "von der Beck", "gunnar.von-der-beck@accso.de", null);
		assignUserGroup(headers, realm, USER_ID_MANAGER, GROUP_ID_MANAGER);
		assignUserGroup(headers, realm, USER_ID_MANAGER, GROUP_ID_TEAMLEAD);

		// Create additional group hierarchy
		GROUP_ID_HIERARCHY_ROOT = createGroup(headers, realm, "root", false);
		GROUP_ID_HIERARCHY_CHILD1 = createGroup(headers, realm, "child1", false, GROUP_ID_HIERARCHY_ROOT);
		GROUP_ID_HIERARCHY_CHILD2 = createGroup(headers, realm, "child2", false, GROUP_ID_HIERARCHY_ROOT);
		GROUP_ID_HIERARCHY_SUBCHILD1 = createGroup(headers, realm, "subchild1", false, GROUP_ID_HIERARCHY_CHILD1);

		// Create user with access to parts of hierarchy
		USER_ID_HIERARCHY = createUser(headers, realm, "johnfoo", "John", "Foo", "johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€");
		assignUserGroup(headers, realm, USER_ID_HIERARCHY, GROUP_ID_HIERARCHY_CHILD2);
		assignUserGroup(headers, realm, USER_ID_HIERARCHY, GROUP_ID_HIERARCHY_SUBCHILD1);
	}
	
	/**
	 * Deletes Keycloak test realm
	 * @throws JSONException in case of errors
	 */
	public static void tearDownKeycloak() throws JSONException {
		LOG.info("Cleaning up Keycloak Test Realm");

		// Delete test realm
		HttpHeaders headers = authenticateKeycloakAdmin();
		deleteRealm(headers, "test");
	}

	// ------------------------------------------------------------------------
	// Helper methods
	// ------------------------------------------------------------------------
	
	/**
	 * Rest template setup including a disabled SSL certificate validation.
	 * @throws Exception in case of errors
	 */
	private static void setupRestTemplate() throws Exception {
		final TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
	    final SSLContext sslContext = org.apache.http.ssl.SSLContexts.custom()
                .loadTrustMaterial(null, acceptingTrustStrategy)
                .build();
		final HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		final HttpClient httpClient = HttpClientBuilder.create()
	    		.setRedirectStrategy(new LaxRedirectStrategy())
	    		.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext))
	    		.build();
		factory.setHttpClient(httpClient);
		restTemplate.setRequestFactory(factory);		

		for (int i = 0; i < restTemplate.getMessageConverters().size(); i++) {
			if (restTemplate.getMessageConverters().get(i) instanceof StringHttpMessageConverter) {
				restTemplate.getMessageConverters().set(i, new StringHttpMessageConverter(StandardCharsets.UTF_8));
				break;
			}
		}
	}
	
	/**
	 * Authenticates towards the admin REST interface as Keycloak Admin using username/password.
	 * @return HttpHeaders including the Authorization header / acces token
	 * @throws JSONException in case of errors
	 */
	private static HttpHeaders authenticateKeycloakAdmin() throws JSONException {
		// Authenticate Admin
		HttpHeaders headers = new HttpHeaders();
	    headers.add(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.toString());
	    HttpEntity<String> request = new HttpEntity<>(
	    		"client_id=admin-cli"
	    		+ "&username=" + KEYCLOAK_ADMIN_USER + "&password=" + KEYCLOAK_ADMIN_PASSWORD
	    		+ "&grant_type=password",
				headers);
	    ResponseEntity<String> response = restTemplate.postForEntity(KEYCLOAK_URL + "/realms/master/protocol/openid-connect/token", request, String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
	    JSONObject json = new JSONObject(response.getBody());
		String accessToken = json.getString("access_token");
		String tokenType = json.getString("token_type");

		// Create REST request header
		headers = new HttpHeaders();
	    headers.add(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString() + ";charset="+StandardCharsets.UTF_8.name());
		headers.add(HttpHeaders.AUTHORIZATION, tokenType + " " + accessToken);

		return headers;
	}
	
	/**
	 * Creates a new Keycloak realm.
	 * @param headers HttpHeaders including the Authorization header / acces token
	 * @param realm the realm name
	 */
	private static void createRealm(HttpHeaders headers, String realm) {
	    String realmData = "{\"id\":null,\"realm\":\"" + realm + "\",\"notBefore\":0,\"revokeRefreshToken\":false,\"refreshTokenMaxReuse\":0,\"accessTokenLifespan\":300,\"accessTokenLifespanForImplicitFlow\":900,\"ssoSessionIdleTimeout\":1800,\"ssoSessionMaxLifespan\":36000,\"ssoSessionIdleTimeoutRememberMe\":0,\"ssoSessionMaxLifespanRememberMe\":0,\"offlineSessionIdleTimeout\":2592000,\"offlineSessionMaxLifespanEnabled\":false,\"offlineSessionMaxLifespan\":5184000,\"accessCodeLifespan\":60,\"accessCodeLifespanUserAction\":300,\"accessCodeLifespanLogin\":1800,\"actionTokenGeneratedByAdminLifespan\":43200,\"actionTokenGeneratedByUserLifespan\":300,\"enabled\":true,\"sslRequired\":\"external\",\"registrationAllowed\":false,\"registrationEmailAsUsername\":false,\"rememberMe\":false,\"verifyEmail\":false,\"loginWithEmailAllowed\":true,\"duplicateEmailsAllowed\":false,\"resetPasswordAllowed\":false,\"editUsernameAllowed\":false,\"bruteForceProtected\":false,\"permanentLockout\":false,\"maxFailureWaitSeconds\":900,\"minimumQuickLoginWaitSeconds\":60,\"waitIncrementSeconds\":60,\"quickLoginCheckMilliSeconds\":1000,\"maxDeltaTimeSeconds\":43200,\"failureFactor\":30,\"defaultRoles\":[\"uma_authorization\",\"offline_access\"],\"requiredCredentials\":[\"password\"],\"otpPolicyType\":\"totp\",\"otpPolicyAlgorithm\":\"HmacSHA1\",\"otpPolicyInitialCounter\":0,\"otpPolicyDigits\":6,\"otpPolicyLookAheadWindow\":1,\"otpPolicyPeriod\":30,\"otpSupportedApplications\":[\"FreeOTP\",\"Google Authenticator\"],\"browserSecurityHeaders\":{\"contentSecurityPolicyReportOnly\":\"\",\"xContentTypeOptions\":\"nosniff\",\"xRobotsTag\":\"none\",\"xFrameOptions\":\"SAMEORIGIN\",\"xXSSProtection\":\"1; mode=block\",\"contentSecurityPolicy\":\"frame-src 'self'; frame-ancestors 'self'; object-src 'none';\",\"strictTransportSecurity\":\"max-age=31536000; includeSubDomains\"},\"smtpServer\":{},\"eventsEnabled\":false,\"eventsListeners\":[\"jboss-logging\"],\"enabledEventTypes\":[],\"adminEventsEnabled\":false,\"adminEventsDetailsEnabled\":false,\"internationalizationEnabled\":false,\"supportedLocales\":[],\"browserFlow\":\"browser\",\"registrationFlow\":\"registration\",\"directGrantFlow\":\"direct grant\",\"resetCredentialsFlow\":\"reset credentials\",\"clientAuthenticationFlow\":\"clients\",\"dockerAuthenticationFlow\":\"docker auth\",\"attributes\":{\"_browser_header.xXSSProtection\":\"1; mode=block\",\"_browser_header.xFrameOptions\":\"SAMEORIGIN\",\"_browser_header.strictTransportSecurity\":\"max-age=31536000; includeSubDomains\",\"permanentLockout\":\"false\",\"quickLoginCheckMilliSeconds\":\"1000\",\"_browser_header.xRobotsTag\":\"none\",\"maxFailureWaitSeconds\":\"900\",\"minimumQuickLoginWaitSeconds\":\"60\",\"failureFactor\":\"30\",\"actionTokenGeneratedByUserLifespan\":\"300\",\"maxDeltaTimeSeconds\":\"43200\",\"_browser_header.xContentTypeOptions\":\"nosniff\",\"offlineSessionMaxLifespan\":\"5184000\",\"actionTokenGeneratedByAdminLifespan\":\"43200\",\"_browser_header.contentSecurityPolicyReportOnly\":\"\",\"bruteForceProtected\":\"false\",\"_browser_header.contentSecurityPolicy\":\"frame-src 'self'; frame-ancestors 'self'; object-src 'none';\",\"waitIncrementSeconds\":\"60\",\"offlineSessionMaxLifespanEnabled\":\"false\"},\"userManagedAccessAllowed\":false}";
	    HttpEntity<String> request = new HttpEntity<>(realmData, headers);
	    ResponseEntity<String> response = restTemplate.postForEntity(KEYCLOAK_URL + "/admin/realms", request, String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.CREATED));
	    LOG.info("Created realm " + realm);
	}
	
	/**
	 * Deletes a Keycloak realm.
	 * @param headers HttpHeaders including the Authorization header / acces token
	 * @param realm the realm name
	 */
	private static void deleteRealm(HttpHeaders headers, String realm) {
	    ResponseEntity<String>response = restTemplate.exchange(KEYCLOAK_URL + "/admin/realms/" + realm, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.NO_CONTENT));
	    LOG.info("Deleted realm " + realm);
	}
	
	/**
	 * Creates a new Keycloak client including access rights for querying users and groups using the REST API.
	 * @param headers HttpHeaders including the Authorization header / acces token
	 * @param realm the realm name
	 * @param clientId the client ID
	 * @param redirectUri valid redirect URI
	 * @return the client secret required for authentication
	 * @throws JSONException in case of errors
	 */
	private static String createClient(HttpHeaders headers, String realm, String clientId, String redirectUri) throws JSONException {
	    // Create Client
	    String clientData = "{\"id\":null,\"clientId\":\"" + clientId + "\",\"surrogateAuthRequired\":false,\"enabled\":true,\"clientAuthenticatorType\":\"client-secret\",\"redirectUris\":[\"" + redirectUri + "\"],\"webOrigins\":[],\"notBefore\":0,\"bearerOnly\":false,\"consentRequired\":false,\"standardFlowEnabled\":true,\"implicitFlowEnabled\":false,\"directAccessGrantsEnabled\":true,\"serviceAccountsEnabled\":true,\"publicClient\":false,\"frontchannelLogout\":false,\"protocol\":\"openid-connect\",\"attributes\":{\"saml.assertion.signature\":\"false\",\"saml.force.post.binding\":\"false\",\"saml.multivalued.roles\":\"false\",\"saml.encrypt\":\"false\",\"saml.server.signature\":\"false\",\"saml.server.signature.keyinfo.ext\":\"false\",\"exclude.session.state.from.auth.response\":\"false\",\"saml_force_name_id_format\":\"false\",\"saml.client.signature\":\"false\",\"tls.client.certificate.bound.access.tokens\":\"false\",\"saml.authnstatement\":\"false\",\"display.on.consent.screen\":\"false\",\"saml.onetimeuse.condition\":\"false\"},\"authenticationFlowBindingOverrides\":{},\"fullScopeAllowed\":true,\"nodeReRegistrationTimeout\":-1,\"protocolMappers\":[{\"id\":null,\"name\":\"Client Host\",\"protocol\":\"openid-connect\",\"protocolMapper\":\"oidc-usersessionmodel-note-mapper\",\"consentRequired\":false,\"config\":{\"user.session.note\":\"clientHost\",\"userinfo.token.claim\":\"true\",\"id.token.claim\":\"true\",\"access.token.claim\":\"true\",\"claim.name\":\"clientHost\",\"jsonType.label\":\"String\"}},{\"id\":null,\"name\":\"Client IP Address\",\"protocol\":\"openid-connect\",\"protocolMapper\":\"oidc-usersessionmodel-note-mapper\",\"consentRequired\":false,\"config\":{\"user.session.note\":\"clientAddress\",\"userinfo.token.claim\":\"true\",\"id.token.claim\":\"true\",\"access.token.claim\":\"true\",\"claim.name\":\"clientAddress\",\"jsonType.label\":\"String\"}},{\"id\":null,\"name\":\"Client ID\",\"protocol\":\"openid-connect\",\"protocolMapper\":\"oidc-usersessionmodel-note-mapper\",\"consentRequired\":false,\"config\":{\"user.session.note\":\"clientId\",\"userinfo.token.claim\":\"true\",\"id.token.claim\":\"true\",\"access.token.claim\":\"true\",\"claim.name\":\"clientId\",\"jsonType.label\":\"String\"}}],\"defaultClientScopes\":[\"web-origins\",\"role_list\",\"profile\",\"roles\",\"email\"],\"optionalClientScopes\":[\"address\",\"phone\",\"offline_access\"],\"access\":{\"view\":true,\"configure\":true,\"manage\":true}}";
	    HttpEntity<String>request = new HttpEntity<>(clientData, headers);
	    ResponseEntity<String>response = restTemplate.postForEntity(KEYCLOAK_URL + "/admin/realms/" + realm + "/clients", request, String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.CREATED));
	    
	    // Get the Client secret
	    String clientSecret = null;
	    response = restTemplate.exchange(KEYCLOAK_URL + "/admin/realms/" + realm + "/clients?clientId=" + clientId,
				HttpMethod.GET, new HttpEntity<>(headers), String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
	    String internalClientId =  new JSONArray(response.getBody()).getJSONObject(0).getString("id");
	    response = restTemplate.exchange(KEYCLOAK_URL + "/admin/realms/" + realm + "/clients/" + internalClientId + "/client-secret",
	    		HttpMethod.GET, new HttpEntity<>(headers), String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
	    clientSecret = new JSONObject(response.getBody()).getString("value");
	    assertThat(clientSecret, notNullValue());
	    
	    // Get the Client's service account
	    response = restTemplate.exchange(KEYCLOAK_URL + "/admin/realms/" + realm + "/users?username=service-account-" + clientId, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
		String serviceAccountId = new JSONArray(response.getBody()).getJSONObject(0).getString("id");

	    // Get user and group roles of realm-management client
	    String queryUserRoleId = null;
	    String queryGroupRoleId = null;
	    String viewUserRoleId = null;
	    response = restTemplate.exchange(KEYCLOAK_URL + "/admin/realms/" + realm + "/clients?clientId=realm-management",
				HttpMethod.GET, new HttpEntity<>(headers), String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
	    String realmManagementId =  new JSONArray(response.getBody()).getJSONObject(0).getString("id");
		response = restTemplate.exchange(KEYCLOAK_URL + "/admin/realms/" + realm + "/users/" + serviceAccountId + "/role-mappings/clients/" + realmManagementId + "/available",
				HttpMethod.GET, new HttpEntity<>(headers), String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
	    JSONArray roleList = new JSONArray(response.getBody());
	    for (int i = 0; i < roleList.length(); i++) {
	    	JSONObject role = roleList.getJSONObject(i);
	    	String roleName = role.getString("name");
	    	if (roleName.equals("query-users")) {
	    		queryUserRoleId = role.getString("id");
	    	} else if (roleName.equals("query-groups")) {
	    		queryGroupRoleId = role.getString("id");
	    	} else if (roleName.equals("view-users")) {
	    		viewUserRoleId = role.getString("id");
	    	}
	    	if (queryUserRoleId != null && queryGroupRoleId != null && viewUserRoleId != null) break;
	    }
	    assertThat(queryUserRoleId, notNullValue());
	    assertThat(queryGroupRoleId, notNullValue());
	    assertThat(viewUserRoleId, notNullValue());

	    // Add service account client roles query-user, query-group, view-user for realm management: this allows using the management REST API via this newly created client
	    String roleMapping ="["
	    		+ "{\"clientRole\":true,\"composite\":false,\"containerId\":\"" + realmManagementId + "\",\"description\":\"${role_query-groups}\",\"id\":\"" + queryGroupRoleId + "\",\"name\":\"query-groups\"},"
	    		+ "{\"clientRole\":true,\"composite\":false,\"containerId\":\"" + realmManagementId + "\",\"description\":\"${role_query-users}\",\"id\":\"" + queryUserRoleId + "\",\"name\":\"query-users\"},"
	    		+ "{\"clientRole\":true,\"composite\":true,\"containerId\":\"" + realmManagementId + "\",\"description\":\"${role_view-users}\",\"id\":\"" + viewUserRoleId + "\",\"name\":\"view-users\"}"
	    		+ "]";
	    request = new HttpEntity<>(roleMapping, headers);
	    response = restTemplate.postForEntity(KEYCLOAK_URL + "/admin/realms/" + realm + "/users/" + serviceAccountId + "/role-mappings/clients/" + realmManagementId, request, String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.NO_CONTENT));
	    
	    LOG.info("Created client " + clientId);
	    return clientSecret;
	}
	
	/**
	 * Creates a user.
	 * @param headers HttpHeaders including the Authorization header / acces token
	 * @param realm the realm name
	 * @param userName the username
	 * @param firstName the first name
	 * @param lastName the last name
	 * @param email the email
	 * @param password the password (optional, can be {@code null} in case no authentication is planned/required)
	 * @return the user ID
	 * @throws JSONException in case of errors
	 */
	private static String createUser(HttpHeaders headers, String realm, String userName, String firstName, String lastName, String email, String password) throws JSONException {
		// create user
	    String userData = "{\"id\":null,\"username\":\""+ userName + "\",\"enabled\":true,\"totp\":false,\"emailVerified\":false,\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName + "\",\"email\":\"" + email + "\",\"disableableCredentialTypes\":[\"password\"],\"requiredActions\":[],\"federatedIdentities\":[],\"notBefore\":0,\"access\":{\"manageGroupMembership\":true,\"view\":true,\"mapRoles\":true,\"impersonate\":true,\"manage\":true}}";
	    HttpEntity<String> request = new HttpEntity<>(userData, headers);
	    ResponseEntity<String> response = restTemplate.postForEntity(KEYCLOAK_URL + "/admin/realms/" + realm + "/users", request, String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.CREATED));
	    // get the user ID
	    response = restTemplate.exchange(KEYCLOAK_URL + "/admin/realms/" + realm + "/users?username="+userName, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
	    String userId =  new JSONArray(response.getBody()).getJSONObject(0).getString("id");
	    // set optional password
	    if (!StringUtils.isEmpty(password)) {
    	    String pwd = "{\"type\":\"password\",\"value\":\"" + password + "\"}";
    	    response = restTemplate.exchange(KEYCLOAK_URL + "/admin/realms/" + realm +"/users/" + userId + "/reset-password",
    				HttpMethod.PUT, new HttpEntity<>(pwd, headers), String.class);
    	    assertThat(response.getStatusCode(), equalTo(HttpStatus.NO_CONTENT));
	    }
		LOG.info("Created user {}", userName);
	    return userId;
	}
	
	/**
	 * Creates a group.
	 * @param headers HttpHeaders including the Authorization header / acces token
	 * @param realm the realm name
	 * @param groupName the name of the group
	 * @param isSystemGroup {@code true} in case of system groups, {@code false} for workflow groups
	 * @return the group ID
	 * @throws JSONException in case of errors
	 */
	private static String createGroup(HttpHeaders headers, String realm, String groupName, boolean isSystemGroup) throws JSONException {
		// create group without parent
		return createGroup(headers, realm, groupName, isSystemGroup, null);
	}
	
	/**
	 * Creates a child group
	 * @param headers HttpHeaders including the Authorization header / acces token
	 * @param realm the realm name
	 * @param groupName the name of the group
	 * @param isSystemGroup {@code true} in case of system groups, {@code false} for workflow groups
	 * @param parentGroupId the group ID of the parent group
	 * @return the group ID
	 * @throws JSONException in case of errors
	 */
	private static String createGroup(HttpHeaders headers, String realm, String groupName, boolean isSystemGroup, String parentGroupId) throws JSONException {
		// create group
	    String camundaAdmin = "{\"id\":null,\"name\":\"" + groupName + "\",\"attributes\":{" + (isSystemGroup ? "\"type\":[\"SYSTEM\"]" : "") + 
	    		"},\"realmRoles\":[],\"clientRoles\":{},\"subGroups\":[],\"access\":{\"view\":true,\"manage\":true,\"manageMembership\":true}}";
	    HttpEntity<String> request = new HttpEntity<>(camundaAdmin, headers);
	    ResponseEntity<String> response = restTemplate.postForEntity(KEYCLOAK_URL + "/admin/realms/" + realm + "/groups" + (parentGroupId != null ? "/" + parentGroupId + "/children" : ""), request, String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.CREATED));
	    // get the group id
	    response = restTemplate.exchange(KEYCLOAK_URL + "/admin/realms/" + realm + "/groups?search="+groupName, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
	    JSONArray groups = new JSONArray(response.getBody());
	    JSONObject group = findGroupInHierarchy(groups, groupName);
	    if (group != null) {
	    	return group.getString("id");
	    }
	    throw new IllegalStateException("Error creating group " + groupName);
	}

	private static JSONObject findGroupInHierarchy(JSONArray groups, String groupName) throws JSONException {
	    for (int i = 0; i < groups.length(); i++) {
	    	JSONObject group = groups.getJSONObject(i);
	    	if (group.getString("name").equals(groupName)) {
	    		LOG.info("Created group {}", groupName);
	    		return group;
	    	}
	    	JSONObject subGroup = findGroupInHierarchy(group.getJSONArray("subGroups"), groupName);
	    	if (subGroup != null) {
	    		return subGroup;
	    	}
	    }
	    return null;
	}
	
	/**
	 * Assigns a user to a group.
	 * @param headers HttpHeaders including the Authorization header / acces token
	 * @param realm the realm name
	 * @param userId the user ID
	 * @param groupId the group ID
	 */
	private static void assignUserGroup(HttpHeaders headers, String realm, String userId, String groupId) {
		ResponseEntity<String>response = restTemplate.exchange(KEYCLOAK_URL + "/admin/realms/" + realm + "/users/" + userId + "/groups/" + groupId, 
				HttpMethod.PUT, new HttpEntity<>(headers), String.class);
	    assertThat(response.getStatusCode(), equalTo(HttpStatus.NO_CONTENT));
	}
}

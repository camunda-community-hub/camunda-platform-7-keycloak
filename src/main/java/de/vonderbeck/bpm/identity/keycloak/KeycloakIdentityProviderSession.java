package de.vonderbeck.bpm.identity.keycloak;

import static org.camunda.bpm.engine.authorization.Permissions.READ;
import static org.camunda.bpm.engine.authorization.Resources.GROUP;
import static org.camunda.bpm.engine.authorization.Resources.USER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.identity.NativeUserQuery;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.TenantQuery;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.identity.UserQuery;
import org.camunda.bpm.engine.impl.UserQueryImpl;
import org.camunda.bpm.engine.impl.identity.IdentityProviderException;
import org.camunda.bpm.engine.impl.identity.ReadOnlyIdentityProvider;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.GroupEntity;
import org.camunda.bpm.engine.impl.persistence.entity.UserEntity;
import org.camunda.bpm.engine.impl.util.json.JSONArray;
import org.camunda.bpm.engine.impl.util.json.JSONException;
import org.camunda.bpm.engine.impl.util.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.vonderbeck.bpm.identity.keycloak.util.ContentType;
import de.vonderbeck.bpm.identity.keycloak.util.KeycloakPluginLogger;

/**
 * Keycloak {@link ReadOnlyIdentityProvider}.
 */
public class KeycloakIdentityProviderSession implements ReadOnlyIdentityProvider {

	protected KeycloakConfiguration keycloakConfiguration;
	protected RestTemplate restTemplate;
	protected KeycloakContextProvider keycloakContextProvider;
	
	/**
	 * Creates a new session.
	 * @param keycloakConfiguration the Keycloak configuration
	 * @param restTemplate REST template
	 * @param keycloakContextProvider Keycloak context provider
	 */
	public KeycloakIdentityProviderSession(KeycloakConfiguration keycloakConfiguration, RestTemplate restTemplate, KeycloakContextProvider keycloakContextProvider) {
		this.keycloakConfiguration = keycloakConfiguration;
		this.restTemplate = restTemplate;
		this.keycloakContextProvider = keycloakContextProvider;
	}
	
	@Override
	public void flush() {
		// nothing to do
	}

	@Override
	public void close() {
		// nothing to do
	}

	//-------------------------------------------------------------------------
	// Users
	//-------------------------------------------------------------------------
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public User findUserById(String userId) {
		return createUserQuery(org.camunda.bpm.engine.impl.context.Context.getCommandContext()).userId(userId)
				.singleResult();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UserQuery createUserQuery() {
		return new KeycloakUserQuery(org.camunda.bpm.engine.impl.context.Context.getProcessEngineConfiguration()
				.getCommandExecutorTxRequired());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UserQueryImpl createUserQuery(CommandContext commandContext) {
		return new KeycloakUserQuery();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NativeUserQuery createNativeUserQuery() {
		throw new BadUserRequestException("Native user queries are not supported for Keycloak identity service provider.");
	}

	/**
	 * find the number of users meeting given user query criteria.
	 * @param userQuery the user query
	 * @return number of matching users
	 */
	protected long findUserCountByQueryCriteria(KeycloakUserQuery userQuery) {
		return findUserByQueryCriteria(userQuery).size();
	}

	/**
	 * find users meeting given user query criteria.
	 * @param userQuery the user query
	 * @return list of matching users
	 */
	protected List<User> findUserByQueryCriteria(KeycloakUserQuery userQuery) {
		if (!StringUtils.isEmpty(userQuery.getGroupId())) {
			// search within the members of a single group
			return requestUsersByGroupId(userQuery);
		} else {
			return requestUsersWithoutGroupId(userQuery);
		}
	}

	//------------------- user query implementation ---------------------------
	
	protected List<User> requestUsersByGroupId(KeycloakUserQuery query) {
		String groupId = query.getGroupId();
		List<User> userList = new ArrayList<>();

		StringBuilder resultLogger = new StringBuilder();
		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("Keycloak user query results: [");
		}

		try {
			// get members of this group
			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + "/groups/" + groupId + "/members", HttpMethod.GET,
					keycloakContextProvider.createApiRequestEntity(), String.class);
			if (!response.getStatusCode().equals(HttpStatus.OK)) {
				throw new IdentityProviderException(
						"Unable to read group members from " + keycloakConfiguration.getKeycloakAdminUrl()
								+ ": HTTP status code " + response.getStatusCodeValue());
			}

			JSONArray searchResult = new JSONArray(response.getBody());
			for (int i = 0; i < searchResult.length(); i++) {
				JSONObject keycloakUser = searchResult.getJSONObject(i);
				if (keycloakConfiguration.isUseEmailAsCamundaUserId() && 
						StringUtils.isEmpty(getStringValue(keycloakUser, "email"))) {
					continue;
				}
				UserEntity user = transformUser(keycloakUser);

				// client side check of further query filters
				if (!matches(query.getId(), user.getId())) continue;
				if (!matches(query.getIds(), user.getId())) continue;
				if (!matches(query.getEmail(), user.getEmail())) continue;
				if (!matchesLike(query.getEmailLike(), user.getEmail())) continue;
				if (!matches(query.getFirstName(), user.getFirstName())) continue;
				if (!matchesLike(query.getFirstNameLike(), user.getFirstName())) continue;
				if (!matches(query.getLastName(), user.getLastName())) continue;
				if (!matchesLike(query.getLastNameLike(), user.getLastName())) continue;
				
				if(isAuthenticatedUser(user) || isAuthorized(READ, USER, user.getId())) {
					userList.add(user);
	
					if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
						resultLogger.append(user);
						resultLogger.append(" based on ");
						resultLogger.append(keycloakUser.toString());
						resultLogger.append(", ");
					}
				}
			}

		} catch (HttpClientErrorException hcee) {
			// if groupID is unknown server answers with HTTP 404 not found
			if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				return userList;
			}
			throw hcee;
		} catch (RestClientException rce) {
			throw new IdentityProviderException("Unable to query members of group " + groupId, rce);
		} catch (JSONException je) {
			throw new IdentityProviderException("Unable to query members of group " + groupId, je);
		}

		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("]");
			KeycloakPluginLogger.INSTANCE.userQueryResult(resultLogger.toString());
		}

		return userList;
	}

	protected List<User> requestUsersWithoutGroupId(KeycloakUserQuery query) {
		List<User> userList = new ArrayList<>();

		StringBuilder resultLogger = new StringBuilder();
		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("Keycloak user query results: [");
		}

		try {
			// get members of this group
			ResponseEntity<String> response = null;

			boolean idSearch = false;
			if (!StringUtils.isEmpty(query.getId())) {
				response = requestUserById(query.getId());
				idSearch = true;
			} else {
				// Create user search filter
				String userFilter = createUserSearchFilter(query);
				response = restTemplate.exchange(keycloakConfiguration.getKeycloakAdminUrl() + "/users" + userFilter, HttpMethod.GET,
						keycloakContextProvider.createApiRequestEntity(), String.class);
			}
			if (!response.getStatusCode().equals(HttpStatus.OK)) {
				throw new IdentityProviderException(
						"Unable to read users from " + keycloakConfiguration.getKeycloakAdminUrl()
								+ ": HTTP status code " + response.getStatusCodeValue());
			}

			JSONArray searchResult = new JSONArray(response.getBody());
			for (int i = 0; i < searchResult.length(); i++) {
				JSONObject keycloakUser = searchResult.getJSONObject(i);
				if (keycloakConfiguration.isUseEmailAsCamundaUserId() && 
						StringUtils.isEmpty(getStringValue(keycloakUser, "email"))) {
					continue;
				}
				UserEntity user = transformUser(keycloakUser);

				// client side check of further query filters
				if (idSearch) {
					if (!matches(query.getEmail(), user.getEmail())) continue;
					if (!matches(query.getFirstName(), user.getFirstName())) continue;
					if (!matches(query.getLastName(), user.getLastName())) continue;
				}
				if (!matches(query.getIds(), user.getId())) continue;
				if (!matchesLike(query.getEmailLike(), user.getEmail())) continue;
				if (!matchesLike(query.getFirstNameLike(), user.getFirstName())) continue;
				if (!matchesLike(query.getLastNameLike(), user.getLastName())) continue;
				
				if(isAuthenticatedUser(user) || isAuthorized(READ, USER, user.getId())) {
					userList.add(user);
	
					if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
						resultLogger.append(user);
						resultLogger.append(" based on ");
						resultLogger.append(keycloakUser.toString());
						resultLogger.append(", ");
					}
				}
			}

		} catch (RestClientException rce) {
			throw new IdentityProviderException("Unable to query users", rce);
		} catch (JSONException je) {
			throw new IdentityProviderException("Unable to query users", je);
		}

		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("]");
			KeycloakPluginLogger.INSTANCE.userQueryResult(resultLogger.toString());
		}

		return userList;
	}

	/**
	 * Creates an Keycloak user search filter query
	 * @param query the user query
	 * @return request query
	 */
	protected String createUserSearchFilter(KeycloakUserQuery query) {
		StringBuilder filter = new StringBuilder();
		if (!StringUtils.isEmpty(query.getEmail())) {
			addArgument(filter, "email", query.getEmail());
		}
		if (!StringUtils.isEmpty(query.getEmailLike())) {
			addArgument(filter, "search", query.getEmailLike().replaceAll("[%,\\*]", ""));
		}
		if (!StringUtils.isEmpty(query.getFirstName())) {
			addArgument(filter, "firstName", query.getFirstName());
		}
		if (!StringUtils.isEmpty(query.getFirstNameLike())) {
			addArgument(filter, "search", query.getFirstNameLike().replaceAll("[%,\\*]", ""));
		}
		if (!StringUtils.isEmpty(query.getLastName())) {
			addArgument(filter, "lastName", query.getLastName());
		}
		if (!StringUtils.isEmpty(query.getLastNameLike())) {
			addArgument(filter, "search", query.getLastNameLike().replaceAll("[%,\\*]", ""));
		}
		if (filter.length() > 0) {
			filter.insert(0, "?");
			String result = filter.toString();
			KeycloakPluginLogger.INSTANCE.userQueryFilter(result);
			return result;
		}
		return "";
	}
	
	/**
	 * Adds a single argument to search filter
	 * @param filter the current filter
	 * @param name the name of the attribute
	 * @param value the value to search
	 */
	protected void addArgument(StringBuilder filter, String name, String value) {
		if (filter.length() > 0) {
			filter.append("&");
		}
		filter.append(name).append('=').append(value);
	}
	
	protected ResponseEntity<String> requestUserById(String userId) throws RestClientException {
		try {
			String userSearch = keycloakConfiguration.isUseEmailAsCamundaUserId()
					? "/users?email=" + userId
					: "/users/" + userId;
			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + userSearch, HttpMethod.GET,
					keycloakContextProvider.createApiRequestEntity(), String.class);
			String result = keycloakConfiguration.isUseEmailAsCamundaUserId()
					? response.getBody()
					: "[" + response.getBody() + "]";
			return new ResponseEntity<String>(result, response.getHeaders(), response.getStatusCode());
		} catch (HttpClientErrorException hcee) {
			if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				String result = "[]";
				return new ResponseEntity<String>(result, HttpStatus.OK);
			}
			throw hcee;
		}
	}

	protected String getKeycloakUserID(String userId) throws KeycloakUserNotFoundException, RestClientException {
		if (! keycloakConfiguration.isUseEmailAsCamundaUserId()) {
			return userId;
		}
		try {
			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + "/users?email=" + userId, HttpMethod.GET,
					keycloakContextProvider.createApiRequestEntity(), String.class);
			return new JSONArray(response.getBody()).getJSONObject(0).getString("id");
		} catch (JSONException je) {
			throw new KeycloakUserNotFoundException(userId + " not found - email unknown", je);
		}
	}

	protected UserEntity transformUser(JSONObject result) throws JSONException {
		UserEntity user = new UserEntity();
		if (keycloakConfiguration.isUseEmailAsCamundaUserId()) {
			user.setId(getStringValue(result, "email"));
		} else {
			user.setId(result.getString("id"));
		}
		user.setFirstName(getStringValue(result, "firstName"));
		user.setLastName(getStringValue(result, "lastName"));
		if (StringUtils.isEmpty(user.getFirstName())) {
			user.setFirstName(getStringValue(result, "username"));
		}
		user.setEmail(getStringValue(result, "email"));
		return user;
	}
	
	//-------------------------------------------------------------------------
	// Login / Password check
	//-------------------------------------------------------------------------
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean checkPassword(String userId, String password) {

		// engine can't work without users
		if (StringUtils.isEmpty(userId)) {
			return false;
		}

		// prevent missing/empty passwords - we do not support anonymous login
		if (StringUtils.isEmpty(password)) {
			return false;
		}
		
		// Get Keycloak username for authentication
		String userName;
		try {
			userName = getKeycloakUsername(userId);
		} catch (KeycloakUserNotFoundException aunfe) {
			KeycloakPluginLogger.INSTANCE.userNotFound(userId, aunfe);
			return false;
		}
			
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.add(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED);
			HttpEntity<String> request = new HttpEntity<>(
		    		"client_id=" + keycloakConfiguration.getClientId()
    	    		+ "&client_secret=" + keycloakConfiguration.getClientSecret()
    	    		+ "&username=" + userName
    	    		+ "&password=" + password
    	    		+ "&grant_type=password",
    	    		headers);
			restTemplate.postForEntity(keycloakConfiguration.getKeycloakIssuerUrl() + "/protocol/openid-connect/token", request, String.class);
			return true;
		} catch (HttpClientErrorException hcee) {
			if (hcee.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
				return false;
			}
			throw new IdentityProviderException("Unable to authenticate user at " + keycloakConfiguration.getKeycloakIssuerUrl(),
					hcee);
		} catch (RestClientException rce) {
			throw new IdentityProviderException("Unable to authenticate user at " + keycloakConfiguration.getKeycloakIssuerUrl(),
					rce);
		}

	}

	protected String getKeycloakUsername(String userId) throws KeycloakUserNotFoundException, RestClientException {
		try {
			if (keycloakConfiguration.isUseEmailAsCamundaUserId()) {
				ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + "/users?email=" + userId, HttpMethod.GET,
					keycloakContextProvider.createApiRequestEntity(), String.class);
				return new JSONArray(response.getBody()).getJSONObject(0).getString("username");
			} else {
				ResponseEntity<String> response = restTemplate.exchange(
						keycloakConfiguration.getKeycloakAdminUrl() + "/users/" + userId, HttpMethod.GET,
						keycloakContextProvider.createApiRequestEntity(), String.class);
				return new JSONObject(response.getBody()).getString("username");
			}
		} catch (JSONException je) {
			throw new KeycloakUserNotFoundException(userId + " not found - email unknown", je);
		} catch (HttpClientErrorException hcee) {
			if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				throw new KeycloakUserNotFoundException(userId + " not found", hcee);
			}
			throw hcee;
		}
	}
	
	//-------------------------------------------------------------------------
	// Groups
	//-------------------------------------------------------------------------
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Group findGroupById(String groupId) {
		return createGroupQuery(org.camunda.bpm.engine.impl.context.Context.getCommandContext()).groupId(groupId)
				.singleResult();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public GroupQuery createGroupQuery() {
		return new KeycloakGroupQuery(org.camunda.bpm.engine.impl.context.Context.getProcessEngineConfiguration()
				.getCommandExecutorTxRequired());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public GroupQuery createGroupQuery(CommandContext commandContext) {
		return new KeycloakGroupQuery();
	}

	/**
	 * find the number of groups meeting given group query criteria.
	 * @param groupQuery the group query
	 * @return number of matching groups
	 */
	protected long findGroupCountByQueryCriteria(KeycloakGroupQuery groupQuery) {
		return findGroupByQueryCriteria(groupQuery).size();
	}

	/**
	 * find groups meeting given group query criteria.
	 * @param groupQuery the group query
	 * @return list of matching groups
	 */
	protected List<Group> findGroupByQueryCriteria(KeycloakGroupQuery groupQuery) {
		if (!StringUtils.isEmpty(groupQuery.getUserId())) {
			// if restriction on userId is provided, we're searching within the groups of a single user
			return requestGroupsByUserId(groupQuery);
		} else {
			return requestGroupsWithoutUserId(groupQuery);
		}
	}

	//------------------- group query implementation --------------------------

	protected List<Group> requestGroupsByUserId(KeycloakGroupQuery query) {
		String userId = query.getUserId();
		List<Group> groupList = new ArrayList<>();

		StringBuilder resultLogger = new StringBuilder();
		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("Keycloak group query results: [");
		}

		try {
			//  get Keycloak specific userID
			String keyCloakID;
			try {
				keyCloakID = getKeycloakUserID(userId);
			} catch (KeycloakUserNotFoundException e) {
				// user not found: empty search result
				return groupList;
			}

			// get members of this group
			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + "/users/" + keyCloakID + "/groups", HttpMethod.GET,
					keycloakContextProvider.createApiRequestEntity(), String.class);
			if (!response.getStatusCode().equals(HttpStatus.OK)) {
				throw new IdentityProviderException(
						"Unable to read user groups from " + keycloakConfiguration.getKeycloakAdminUrl()
								+ ": HTTP status code " + response.getStatusCodeValue());
			}

			JSONArray searchResult = new JSONArray(response.getBody());
			for (int i = 0; i < searchResult.length(); i++) {
				JSONObject keycloakGroup = searchResult.getJSONObject(i);
				Group group = transformGroup(keycloakGroup);

				// client side check of further query filters
				if (!matches(query.getId(), group.getId())) continue;
				if (!matches(query.getIds(), group.getId())) continue;
				if (!matches(query.getName(), group.getName())) continue;
				if (!matchesLike(query.getNameLike(), group.getName())) continue;
				if (!matches(query.getType(), group.getType())) continue;

				if (isAuthorized(READ, GROUP, group.getId())) {
					groupList.add(group);

					if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
						resultLogger.append(group);
						resultLogger.append(" based on ");
						resultLogger.append(keycloakGroup.toString());
						resultLogger.append(", ");
					}
				}
			}

		} catch (HttpClientErrorException hcee) {
			// if userID is unknown server answers with HTTP 404 not found
			if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				return groupList;
			}
			throw hcee;
		} catch (RestClientException rce) {
			throw new IdentityProviderException("Unable to query groups of user " + userId, rce);
		} catch (JSONException je) {
			throw new IdentityProviderException("Unable to query groups of user " + userId, je);
		}

		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("]");
			KeycloakPluginLogger.INSTANCE.groupQueryResult(resultLogger.toString());
		}

		return groupList;
	}
	
	protected List<Group> requestGroupsWithoutUserId(KeycloakGroupQuery query) {
		List<Group> groupList = new ArrayList<>();

		StringBuilder resultLogger = new StringBuilder();
		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("Keycloak group query results: [");
		}

		try {
			// get members of this group
			ResponseEntity<String> response = null;

			if (!StringUtils.isEmpty(query.getId())) {
				response = requestGroupById(query.getId());
			} else {
				response = restTemplate.exchange(keycloakConfiguration.getKeycloakAdminUrl() + "/groups", HttpMethod.GET,
						keycloakContextProvider.createApiRequestEntity(), String.class);
			}
			if (!response.getStatusCode().equals(HttpStatus.OK)) {
				throw new IdentityProviderException(
						"Unable to read groups from " + keycloakConfiguration.getKeycloakAdminUrl()
								+ ": HTTP status code " + response.getStatusCodeValue());
			}

			JSONArray searchResult = new JSONArray(response.getBody());
			for (int i = 0; i < searchResult.length(); i++) {
				JSONObject keycloakGroup = searchResult.getJSONObject(i);
				Group group = transformGroup(keycloakGroup);
				
				// client side check of further query filters
				if (!matches(query.getIds(), group.getId())) continue;
				if (!matches(query.getName(), group.getName())) continue;
				if (!matchesLike(query.getNameLike(), group.getName())) continue;
				if (!matches(query.getType(), group.getType())) continue;
				
				if (isAuthorized(READ, GROUP, group.getId())) {
					groupList.add(group);
	
					if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
						resultLogger.append(group);
						resultLogger.append(" based on ");
						resultLogger.append(keycloakGroup.toString());
						resultLogger.append(", ");
					}
				}
			}

		} catch (RestClientException rce) {
			throw new IdentityProviderException("Unable to query groups", rce);
		} catch (JSONException je) {
			throw new IdentityProviderException("Unable to query groups", je);
		}

		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("]");
			KeycloakPluginLogger.INSTANCE.groupQueryResult(resultLogger.toString());
		}

		return groupList;
	}

	protected ResponseEntity<String> requestGroupById(String groupId) throws RestClientException {
		try {
			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + "/groups/" + groupId, HttpMethod.GET,
					keycloakContextProvider.createApiRequestEntity(), String.class);
			String result = "[" + response.getBody() + "]}";
			return new ResponseEntity<String>(result, response.getHeaders(), response.getStatusCode());
		} catch (HttpClientErrorException hcee) {
			if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				String result = "[]";
				return new ResponseEntity<String>(result, HttpStatus.OK);
			}
			throw hcee;
		}
	}
	
	protected GroupEntity transformGroup(JSONObject result) throws JSONException {
		GroupEntity group = new GroupEntity();
		group.setId(result.getString("id"));
		group.setName(result.getString("name"));
		if (isSystemGroup(result)) {
			group.setType(Groups.GROUP_TYPE_SYSTEM);
		} else {
			group.setType(Groups.GROUP_TYPE_WORKFLOW);
		}
		return group;
	}

	private boolean isSystemGroup(JSONObject result) {
		String name = result.getString("name");
		if (Groups.CAMUNDA_ADMIN.equals(name) || 
				name.equals(keycloakConfiguration.getAdministratorGroupName())) {
			return true;
		}
		try {
			JSONArray types = result.getJSONObject("attributes").getJSONArray("type");
			for (int i = 0; i < types.length(); i++) {
				if (Groups.GROUP_TYPE_SYSTEM.equals(types.getString(i).toLowerCase())) {
					return true;
				}
			}
		} catch (JSONException ex) {
			return false;
		}
		return false;
	}
	
	//-------------------------------------------------------------------------
	// Tenants
	//-------------------------------------------------------------------------
	
	@Override
	public TenantQuery createTenantQuery() {
		return new KeycloakTenantQuery(org.camunda.bpm.engine.impl.context.Context.getProcessEngineConfiguration()
				.getCommandExecutorTxRequired());
	}

	@Override
	public TenantQuery createTenantQuery(CommandContext commandContext) {
		return new KeycloakTenantQuery();
	}

	@Override
	public Tenant findTenantById(String id) {
		// since multi-tenancy is currently not supported for the Keycloak plugin, always return null
		return null;
	}

	//-------------------------------------------------------------------------
	// Helpers
	//-------------------------------------------------------------------------
	
	/**
	 * Returns the value mapped by name if it exists, coercing it if necessary.
	 * 
	 * @param result the result object
	 * @param name   the attribute to read
	 * @return the string value or {@code null} if not such attribute exists.
	 */
	protected String getStringValue(JSONObject result, String name) {
		try {
			return result.getString(name);
		} catch (JSONException e) {
			return null;
		}
	}

	/**
	 * Checks whether a filter applies.
	 * @param queryParameter the queryParameter
	 * @param attribute the corresponding attribute value
	 * @return {@code true} if the query parameter is not set at all or if both are equal.
	 */
	protected boolean matches(Object queryParameter, Object attribute) {
		return queryParameter == null || queryParameter.equals(attribute);
	}
	
	/**
	 * Checks whether a filter applies.
	 * @param queryParameter the queryParameter list
	 * @param attribute the corresponding attribute value
	 * @return {@code true} if the query parameter is not set at all or if one of the query parameter matches the attribute.
	 */
	protected boolean matches(Object[] queryParameter, Object attribute) {
		return queryParameter == null || queryParameter.length == 0 ||
				Arrays.asList(queryParameter).contains(attribute);
	}

	/**
	 * Checks whether a like filter applies.
	 * @param queryParameter the queryParameter
	 * @param attribute the corresponding attribute value
	 * @return {@code true} if the query parameter is not set at all or if the attribute is like the query parameters.
	 */
	protected boolean matchesLike(String queryParameter, String attribute) {
		return queryParameter == null || attribute.matches(queryParameter.replaceAll("[%\\*]", ".*"));
	}

	/**
	 * @return true if the passed-in user is currently authenticated
	 */
	protected boolean isAuthenticatedUser(UserEntity user) {
		if (user.getId() == null) {
			return false;
		}
		return user.getId().equalsIgnoreCase(
				org.camunda.bpm.engine.impl.context.Context.getCommandContext().getAuthenticatedUserId());
	}

	/**
	 * Checks if the current is user is authorized to access a specific resource
	 * @param permission the permission, e.g. READ
	 * @param resource the resource type, e.g. GROUP
	 * @param resourceId the ID of the concrete resource to check
	 * @return {@code true} if the current user is authorized to access the given resourceId
	 */
	protected boolean isAuthorized(Permission permission, Resource resource, String resourceId) {
		return !keycloakConfiguration.isAuthorizationCheckEnabled() || org.camunda.bpm.engine.impl.context.Context
				.getCommandContext().getAuthorizationManager().isAuthorized(permission, resource, resourceId);
	}

}

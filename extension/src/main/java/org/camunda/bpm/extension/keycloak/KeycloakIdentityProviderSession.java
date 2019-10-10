package org.camunda.bpm.extension.keycloak;

import static org.camunda.bpm.engine.authorization.Permissions.READ;
import static org.camunda.bpm.engine.authorization.Resources.GROUP;
import static org.camunda.bpm.engine.authorization.Resources.USER;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import org.camunda.bpm.engine.impl.Direction;
import org.camunda.bpm.engine.impl.GroupQueryProperty;
import org.camunda.bpm.engine.impl.QueryOrderingProperty;
import org.camunda.bpm.engine.impl.UserQueryImpl;
import org.camunda.bpm.engine.impl.UserQueryProperty;
import org.camunda.bpm.engine.impl.identity.IdentityProviderException;
import org.camunda.bpm.engine.impl.identity.ReadOnlyIdentityProvider;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.GroupEntity;
import org.camunda.bpm.engine.impl.persistence.entity.UserEntity;
import org.camunda.bpm.extension.keycloak.json.JSONArray;
import org.camunda.bpm.extension.keycloak.json.JSONException;
import org.camunda.bpm.extension.keycloak.json.JSONObject;
import org.camunda.bpm.extension.keycloak.util.ContentType;
import org.camunda.bpm.extension.keycloak.util.KeycloakPluginLogger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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
	
	/**
	 * Requests users of a specific group.
	 * @param query the user query - including a groupId criteria
	 * @return list of matching users
	 */
	protected List<User> requestUsersByGroupId(KeycloakUserQuery query) {
		String groupId = query.getGroupId();
		List<User> userList = new ArrayList<>();

		StringBuilder resultLogger = new StringBuilder();
		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("Keycloak user query results: [");
		}

		try {
			//  get Keycloak specific groupID
			String keyCloakID;
			try {
				keyCloakID = getKeycloakGroupID(groupId);
			} catch (KeycloakGroupNotFoundException e) {
				// group not found: empty search result
				return userList;
			}

			// get members of this group
			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + "/groups/" + keyCloakID + "/members", HttpMethod.GET,
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
				if (keycloakConfiguration.isUseUsernameAsCamundaUserId() &&
						StringUtils.isEmpty(getStringValue(keycloakUser, "username"))) {
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

		if (query.getOrderingProperties().size() > 0) {
			userList.sort(new UserComparator(query.getOrderingProperties()));
		}
		
		return userList;
	}

	/**
	 * Requests users.
	 * @param query the user query - not including a groupId criteria
	 * @return list of matching users
	 */
	protected List<User> requestUsersWithoutGroupId(KeycloakUserQuery query) {
		List<User> userList = new ArrayList<>();

		StringBuilder resultLogger = new StringBuilder();
		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("Keycloak user query results: [");
		}

		try {
			// get members of this group
			ResponseEntity<String> response = null;

			if (!StringUtils.isEmpty(query.getId())) {
				response = requestUserById(query.getId());
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
				if (keycloakConfiguration.isUseUsernameAsCamundaUserId() &&
						StringUtils.isEmpty(getStringValue(keycloakUser, "username"))) {
					continue;
				}

				UserEntity user = transformUser(keycloakUser);

				// client side check of further query filters
				// beware: looks like most attributes are treated as 'like' queries on Keycloak
				//         and must therefore be seen as a sort of pre-filter only
				if (!matches(query.getId(), user.getId())) continue;
				if (!matches(query.getEmail(), user.getEmail())) continue;
				if (!matches(query.getFirstName(), user.getFirstName())) continue;
				if (!matches(query.getLastName(), user.getLastName())) continue;
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

		if (query.getOrderingProperties().size() > 0) {
			userList.sort(new UserComparator(query.getOrderingProperties()));
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
			addArgument(filter, "email", query.getEmailLike().replaceAll("[%,\\*]", ""));
		}
		if (!StringUtils.isEmpty(query.getFirstName())) {
			addArgument(filter, "firstName", query.getFirstName());
		}
		if (!StringUtils.isEmpty(query.getFirstNameLike())) {
			addArgument(filter, "firstName", query.getFirstNameLike().replaceAll("[%,\\*]", ""));
		}
		if (!StringUtils.isEmpty(query.getLastName())) {
			addArgument(filter, "lastName", query.getLastName());
		}
		if (!StringUtils.isEmpty(query.getLastNameLike())) {
			addArgument(filter, "lastName", query.getLastNameLike().replaceAll("[%,\\*]", ""));
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
	 * Requests a user by its userId.
	 * @param userId the userId
	 * @return response consisting of a list containing the one user
	 * @throws RestClientException
	 */
	protected ResponseEntity<String> requestUserById(String userId) throws RestClientException {
		try {
			String userSearch;
			if (keycloakConfiguration.isUseEmailAsCamundaUserId()) {
				userSearch="/users?email=" + userId;
			} else if (keycloakConfiguration.isUseUsernameAsCamundaUserId()) {
				userSearch="/users?username=" + userId;
			} else {
				userSearch= "/users/" + userId;
			}

			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + userSearch, HttpMethod.GET,
					keycloakContextProvider.createApiRequestEntity(), String.class);
			String result = (keycloakConfiguration.isUseEmailAsCamundaUserId() || keycloakConfiguration.isUseUsernameAsCamundaUserId())
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

	/**
	 * Gets the Keycloak internal ID of an user.
	 * @param userId the userId as sent by the client
	 * @return the Keycloak internal ID
	 * @throws KeycloakUserNotFoundException in case the user cannot be found
	 * @throws RestClientException in case of technical errors
	 */
	protected String getKeycloakUserID(String userId) throws KeycloakUserNotFoundException, RestClientException {
		String userSearch;
		if (keycloakConfiguration.isUseEmailAsCamundaUserId()) {
			userSearch="/users?email=" + userId;
		} else if (keycloakConfiguration.isUseUsernameAsCamundaUserId()) {
			userSearch="/users?username=" + userId;
		} else {
			return userId;
		}
		
		try {
			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + userSearch, HttpMethod.GET,
					keycloakContextProvider.createApiRequestEntity(), String.class);
			return new JSONArray(response.getBody()).getJSONObject(0).getString("id");
		} catch (JSONException je) {
			throw new KeycloakUserNotFoundException(userId + 
					(keycloakConfiguration.isUseEmailAsCamundaUserId() 
					? " not found - email unknown" 
					: " not found - username unknown"), je);
		}
	}

	/**
	 * Get the user ID of the configured admin user. Enable configuration using username / email as well.
	 * This prevents common configuration pitfalls and makes it consistent to other configuration options
	 * like the flags 'useUsernameAsCamundaUserId' and 'useEmailAsCamundaUserId'.
	 * 
	 * @param configuredAdminUserId the originally configured admin user ID
	 * @return the corresponding keycloak user ID to use: either internal keycloak ID, username or email, depending on config
	 */
	public String getKeycloakAdminUserId(String configuredAdminUserId) {
		try {
			// check whether configured admin user ID can be resolved as a real keycloak user ID
			try {
				ResponseEntity<String> response = restTemplate.exchange(
						keycloakConfiguration.getKeycloakAdminUrl() + "/users/" + configuredAdminUserId, HttpMethod.GET,
						keycloakContextProvider.createApiRequestEntity(), String.class);
				if (keycloakConfiguration.isUseEmailAsCamundaUserId()) {
					return new JSONObject(response.getBody()).getString("email");
				}
				if (keycloakConfiguration.isUseUsernameAsCamundaUserId()) {
					return new JSONObject(response.getBody()).getString("username");
				}
				return new JSONObject(response.getBody()).getString("id");
			} catch (RestClientException | JSONException ex) {
				// user ID not found: fall through
			}
			// check whether configured admin user ID can be resolved as email address
			if (keycloakConfiguration.isUseEmailAsCamundaUserId() && configuredAdminUserId.contains("@")) {
				try {
					getKeycloakUserID(configuredAdminUserId);
					return configuredAdminUserId;
				} catch (KeycloakUserNotFoundException e) {
					// email not found: fall through
				}
			}
			// check whether configured admin user ID can be resolved as username
			try {
				ResponseEntity<String> response = restTemplate.exchange(
						keycloakConfiguration.getKeycloakAdminUrl() + "/users?username=" + configuredAdminUserId, HttpMethod.GET,
						keycloakContextProvider.createApiRequestEntity(), String.class);
				if (keycloakConfiguration.isUseEmailAsCamundaUserId()) {
					return new JSONArray(response.getBody()).getJSONObject(0).getString("email");
				}
				if (keycloakConfiguration.isUseUsernameAsCamundaUserId()) {
					return new JSONArray(response.getBody()).getJSONObject(0).getString("username");
				}
				return new JSONArray(response.getBody()).getJSONObject(0).getString("id");
			} catch (JSONException je) {
				// username not found: fall through
			}
			// keycloak admin user does not exist :-(
			throw new IdentityProviderException("Configured administratorUserId " + configuredAdminUserId + " does not exist.");
		} catch (RestClientException rce) {
			throw new IdentityProviderException("Unable to read data of configured administratorUserId " + configuredAdminUserId, rce);
		}
	}
	
	/**
	 * Maps a Keycloak JSON result to a User object
	 * @param result the Keycloak JSON result
	 * @return the User object
	 * @throws JSONException in case of errors
	 */
	protected UserEntity transformUser(JSONObject result) throws JSONException {
		UserEntity user = new UserEntity();
		if (keycloakConfiguration.isUseEmailAsCamundaUserId()) {
			user.setId(getStringValue(result, "email"));
		} else if (keycloakConfiguration.isUseUsernameAsCamundaUserId()) {
			user.setId(getStringValue(result, "username"));
		} else {
			user.setId(result.getString("id"));
		}
		user.setFirstName(getStringValue(result, "firstName"));
		user.setLastName(getStringValue(result, "lastName"));
		if (StringUtils.isEmpty(user.getFirstName()) && StringUtils.isEmpty(user.getLastName())) {
			user.setFirstName(getStringValue(result, "username"));
		}
		user.setEmail(getStringValue(result, "email"));
		return user;
	}

	/**
	 * Helper for client side user ordering.
	 */
	private static class UserComparator implements Comparator<User> {
		private final static int USER_ID = 0;
		private final static int EMAIL = 1;
		private final static int FIRST_NAME = 2;
		private final static int LAST_NAME = 3;
		private int[] order;
		private boolean[] desc;
		public UserComparator(List<QueryOrderingProperty> orderList) {
			// Prepare query ordering
			this.order = new int[orderList.size()];
			this.desc = new boolean[orderList.size()];
			for (int i = 0; i< orderList.size(); i++) {
				QueryOrderingProperty qop = orderList.get(i);
				if (qop.getQueryProperty().equals(UserQueryProperty.USER_ID)) {
					order[i] = USER_ID;
				} else if (qop.getQueryProperty().equals(UserQueryProperty.EMAIL)) {
					order[i] = EMAIL;
				} else if (qop.getQueryProperty().equals(UserQueryProperty.FIRST_NAME)) {
					order[i] = FIRST_NAME;
				} else if (qop.getQueryProperty().equals(UserQueryProperty.LAST_NAME)) {
					order[i] = LAST_NAME;
				} else {
					order[i] = -1;
				}
				desc[i] = Direction.DESCENDING.equals(qop.getDirection());
			}
		}
		@Override
		public int compare(User u1, User u2) {
			int c = 0;
			for (int i = 0; i < order.length; i ++) {
				switch (order[i]) {
					case USER_ID:
						c = KeycloakIdentityProviderSession.compare(u1.getId(), u2.getId());
						break;
					case EMAIL:
						c = KeycloakIdentityProviderSession.compare(u1.getEmail(), u2.getEmail());
						break;
					case FIRST_NAME:
						c = KeycloakIdentityProviderSession.compare(u1.getFirstName(), u2.getFirstName());
						break;
					case LAST_NAME:
						c = KeycloakIdentityProviderSession.compare(u1.getLastName(), u2.getLastName());
						break;
					default:
						// do nothing
				}
				if (c != 0) {
					return desc[i] ? -c : c;
				}
			}
			return c;
		}
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
			headers.add(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED + ";charset=" + keycloakConfiguration.getCharset());
			HttpEntity<String> request = new HttpEntity<>(
		    		"client_id=" + keycloakConfiguration.getClientId()
    	    		+ "&client_secret=" + keycloakConfiguration.getClientSecret()
    	    		+ "&username=" + userName
    	    		+ "&password=" + URLEncoder.encode(password, keycloakConfiguration.getCharset())
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
		} catch (UnsupportedEncodingException uee) {
			throw new IdentityProviderException("Unable to authenticate user at " + keycloakConfiguration.getKeycloakIssuerUrl(),
					uee);
		}

	}

	/**
	 * Gets the Keycloak internal username of an user.
	 * @param userId the userId as sent by the client (when checking password)
	 * @return the Keycloak internal username
	 * @throws KeycloakUserNotFoundException in case the user cannot be found
	 * @throws RestClientException in case of technical errors
	 */
	protected String getKeycloakUsername(String userId) throws KeycloakUserNotFoundException, RestClientException {
		if (keycloakConfiguration.isUseUsernameAsCamundaUserId()) {
			return userId;
		}
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
			throw new KeycloakUserNotFoundException(userId + 
					(keycloakConfiguration.isUseEmailAsCamundaUserId() 
					? " not found - email unknown" 
					: " not found - ID unknown"), je);
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

	/**
	 * Requests groups of a specific user.
	 * @param query the group query - including a userId criteria
	 * @return list of matching groups
	 */
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

			// get groups of this user
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

		// sort groups according to query criteria
		if (query.getOrderingProperties().size() > 0) {
			groupList.sort(new GroupComparator(query.getOrderingProperties()));
		}

		return groupList;
	}
	
	/**
	 * Requests groups.
	 * @param query the group query - not including a userId criteria
	 * @return list of matching groups
	 */
	protected List<Group> requestGroupsWithoutUserId(KeycloakGroupQuery query) {
		List<Group> groupList = new ArrayList<>();

		StringBuilder resultLogger = new StringBuilder();
		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("Keycloak group query results: [");
		}

		try {
			// get groups according to search criteria
			ResponseEntity<String> response = null;

			if (!StringUtils.isEmpty(query.getId())) {
				response = requestGroupById(query.getId());
			} else {
				String groupFilter = createGroupSearchFilter(query); // only pre-filter of names possible
				response = restTemplate.exchange(keycloakConfiguration.getKeycloakAdminUrl() + "/groups" + groupFilter, HttpMethod.GET,
						keycloakContextProvider.createApiRequestEntity(), String.class);
			}
			if (!response.getStatusCode().equals(HttpStatus.OK)) {
				throw new IdentityProviderException(
						"Unable to read groups from " + keycloakConfiguration.getKeycloakAdminUrl()
								+ ": HTTP status code " + response.getStatusCodeValue());
			}

			JSONArray searchResult = null;
			if (!StringUtils.isEmpty(query.getId())) {
				searchResult = new JSONArray(response.getBody());
			} else {
				// for non ID queries search in subgroups as well
				searchResult = flattenSubGroups(new JSONArray(response.getBody()), new JSONArray());
			}
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

		// sort groups according to query criteria
		if (query.getOrderingProperties().size() > 0) {
			groupList.sort(new GroupComparator(query.getOrderingProperties()));
		}
		
		return groupList;
	}

	/**
	 * Creates an Keycloak group search filter query
	 * @param query the group query
	 * @return request query
	 */
	protected String createGroupSearchFilter(KeycloakGroupQuery query) {
		StringBuilder filter = new StringBuilder();
		if (!StringUtils.isEmpty(query.getName())) {
			addArgument(filter, "search", query.getName());
		}
		if (!StringUtils.isEmpty(query.getNameLike())) {
			addArgument(filter, "search", query.getNameLike().replaceAll("[%,\\*]", ""));
		}
		if (filter.length() > 0) {
			filter.insert(0, "?");
			String result = filter.toString();
			KeycloakPluginLogger.INSTANCE.groupQueryFilter(result);
			return result;
		}
		return "";
	}

	/**
	 * Converts a result consisting of a potential hierarchy of groups into a flattened list of groups.
	 * @param groups the original structured hierarchy of groups
	 * @param result recursive result
	 * @return flattened list of all groups in this hierarchy
	 * @throws JSONException in case of errors
	 */
	private JSONArray flattenSubGroups(JSONArray groups, JSONArray result) throws JSONException {
		if (groups == null) return result;
	    for (int i = 0; i < groups.length(); i++) {
	    	JSONObject group = groups.getJSONObject(i);
	    	JSONArray subGroups;
			try {
				subGroups = group.getJSONArray("subGroups");
		    	group.remove("subGroups");
		    	result.put(group);
		    	flattenSubGroups(subGroups, result);
			} catch (JSONException e) {
				result.put(group);
			}
	    }
	    return result;
	}
	
	/**
	 * Requests data of single group.
	 * @param groupId the ID of the requested group
	 * @return response consisting of a list containing the one group
	 * @throws RestClientException
	 */
	protected ResponseEntity<String> requestGroupById(String groupId) throws RestClientException {
		try {
			String groupSearch;
			if (keycloakConfiguration.isUseGroupPathAsCamundaGroupId()) {
				groupSearch = "/group-by-path/" + groupId;
			} else {
				groupSearch = "/groups/" + groupId;
			}

			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + groupSearch, HttpMethod.GET,
					keycloakContextProvider.createApiRequestEntity(), String.class);
			String result = "[" + response.getBody() + "]";
			return new ResponseEntity<String>(result, response.getHeaders(), response.getStatusCode());
		} catch (HttpClientErrorException hcee) {
			if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				String result = "[]";
				return new ResponseEntity<String>(result, HttpStatus.OK);
			}
			throw hcee;
		}
	}

	/**
	 * Gets the Keycloak internal ID of a group.
	 * @param groupId the userId as sent by the client
	 * @return the Keycloak internal ID
	 * @throws KeycloakGroupNotFoundException in case the group cannot be found
	 * @throws RestClientException in case of technical errors
	 */
	protected String getKeycloakGroupID(String groupId) throws KeycloakGroupNotFoundException, RestClientException {
		String groupSearch;
		if (keycloakConfiguration.isUseGroupPathAsCamundaGroupId()) {
			groupSearch = "/group-by-path/" + groupId;
		} else {
			return groupId;
		}
		
		try {
			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + groupSearch, HttpMethod.GET,
					keycloakContextProvider.createApiRequestEntity(), String.class);
			return new JSONObject(response.getBody()).getString("id");
		} catch (JSONException je) {
			throw new KeycloakGroupNotFoundException(groupId + " not found - path unknown", je);
		}
	}

	/**
	 * Get the group ID of the configured admin group. Enable configuration using group path as well.
	 * This prevents common configuration pitfalls and makes it consistent to other configuration options
	 * like the flag 'useGroupPathAsCamundaGroupId'.
	 * 
	 * @param configuredAdminGroupName the originally configured admin group name
	 * @return the corresponding keycloak group ID to use: either internal keycloak ID or path, depending on config
	 */
	public String getKeycloakAdminGroupId(String configuredAdminGroupName) {
		try {
			// check whether configured admin group can be resolved as path
			try {
				ResponseEntity<String> response = restTemplate.exchange(
						keycloakConfiguration.getKeycloakAdminUrl() + "/group-by-path/" + configuredAdminGroupName, HttpMethod.GET,
						keycloakContextProvider.createApiRequestEntity(), String.class);
				if (keycloakConfiguration.isUseGroupPathAsCamundaGroupId()) {
					return new JSONObject(response.getBody()).getString("path").substring(1); // remove trailing '/'
				}
				return new JSONObject(response.getBody()).getString("id");
			} catch (RestClientException | JSONException ex) {
				// group not found: fall through
			}
			
			// check whether configured admin group can be resolved as group name
			try {
				ResponseEntity<String> response = restTemplate.exchange(
						keycloakConfiguration.getKeycloakAdminUrl() + "/groups?search=" + configuredAdminGroupName, HttpMethod.GET,
						keycloakContextProvider.createApiRequestEntity(), String.class);
				// filter search result for exact group name, including subgroups
				JSONArray result = flattenSubGroups(new JSONArray(response.getBody()), new JSONArray());
				JSONArray groups = new JSONArray();
				for (int i = 0; i < result.length(); i++) {
					JSONObject keycloakGroup = result.getJSONObject(i);
					if (keycloakGroup.getString("name").equals(configuredAdminGroupName)) {
						groups.put(keycloakGroup);
					}
				}
				if (groups.length() == 1) {
					if (keycloakConfiguration.isUseGroupPathAsCamundaGroupId()) {
						return groups.getJSONObject(0).getString("path").substring(1); // remove trailing '/'
					}
					return groups.getJSONObject(0).getString("id");
				} else if (groups.length() > 0) {
					throw new IdentityProviderException("Configured administratorGroupName " + configuredAdminGroupName + " is not unique. Please configure exact group path.");
				}
				// groups size == 0: fall through
			} catch (JSONException je) {
				// group not found: fall through
			}

			// keycloak admin group does not exist :-(
			throw new IdentityProviderException("Configured administratorGroupName " + configuredAdminGroupName + " does not exist.");
		} catch (RestClientException rce) {
			throw new IdentityProviderException("Unable to read data of configured administratorGroupName " + configuredAdminGroupName, rce);
		}
	}
	
	/**
	 * Maps a Keycloak JSON result to a Group object
	 * @param result the Keycloak JSON result
	 * @return the Group object
	 * @throws JSONException in case of errors
	 */
	protected GroupEntity transformGroup(JSONObject result) throws JSONException {
		GroupEntity group = new GroupEntity();
		if (keycloakConfiguration.isUseGroupPathAsCamundaGroupId()) {
			group.setId(result.getString("path").substring(1)); // remove trailing '/'
		} else {
			group.setId(result.getString("id"));
		}
		group.setName(result.getString("name"));
		if (isSystemGroup(result)) {
			group.setType(Groups.GROUP_TYPE_SYSTEM);
		} else {
			group.setType(Groups.GROUP_TYPE_WORKFLOW);
		}
		return group;
	}

	/**
	 * Checks whether a Keycloak JSON result represents a SYSTEM group.
	 * @param result the Keycloak JSON result
	 * @return {@code true} in case the result is a SYSTEM group.
	 */
	private boolean isSystemGroup(JSONObject result) {
		String name = result.getString("name");
		if (Groups.CAMUNDA_ADMIN.equals(name) || 
				name.equals(keycloakConfiguration.getAdministratorGroupName())) {
			return true;
		}
		try {
			JSONArray types = result.getJSONObject("attributes").getJSONArray("type");
			for (int i = 0; i < types.length(); i++) {
				if (Groups.GROUP_TYPE_SYSTEM.equals(types.getString(i).toUpperCase())) {
					return true;
				}
			}
		} catch (JSONException ex) {
			return false;
		}
		return false;
	}
	
	/**
	 * Helper for client side group ordering.
	 */
	private static class GroupComparator implements Comparator<Group> {
		private final static int GROUP_ID = 0;
		private final static int NAME = 1;
		private final static int TYPE = 2;
		private int[] order;
		private boolean[] desc;
		public GroupComparator(List<QueryOrderingProperty> orderList) {
			// Prepare query ordering
			this.order = new int[orderList.size()];
			this.desc = new boolean[orderList.size()];
			for (int i = 0; i< orderList.size(); i++) {
				QueryOrderingProperty qop = orderList.get(i);
				if (qop.getQueryProperty().equals(GroupQueryProperty.GROUP_ID)) {
					order[i] = GROUP_ID;
				} else if (qop.getQueryProperty().equals(GroupQueryProperty.NAME)) {
					order[i] = NAME;
				} else if (qop.getQueryProperty().equals(GroupQueryProperty.TYPE)) {
					order[i] = TYPE;
				} else {
					order[i] = -1;
				}
				desc[i] = Direction.DESCENDING.equals(qop.getDirection());
			}
		}

		@Override
		public int compare(Group g1, Group g2) {
			int c = 0;
			for (int i = 0; i < order.length; i ++) {
				switch (order[i]) {
					case GROUP_ID:
						c = KeycloakIdentityProviderSession.compare(g1.getId(), g2.getId());
						break;
					case NAME:
						c = KeycloakIdentityProviderSession.compare(g1.getName(), g2.getName());
						break;
					case TYPE:
						c = KeycloakIdentityProviderSession.compare(g1.getType(), g2.getType());
						break;
					default:
						// do nothing
				}
				if (c != 0) {
					return desc[i] ? -c : c;
				}
			}
			return c;
		}
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

	/**
	 * Null safe compare of two strings.
	 * @param str1 string 1
	 * @param str2 string 2
	 * @return 0 if both strings are equal; -1 if string 1 is less, +1 if string 1 is greater than string 2
	 */
	protected static int compare(final String str1, final String str2) {
		if (str1 == str2) {
			return 0;
		}
		if (str1 == null) {
			return -1;
		}
		if (str2 == null) {
			return 1;
		}
		return str1.compareTo(str2);
	}

}

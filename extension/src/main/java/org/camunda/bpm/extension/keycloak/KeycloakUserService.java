package org.camunda.bpm.extension.keycloak;

import static org.camunda.bpm.engine.authorization.Permissions.READ;
import static org.camunda.bpm.engine.authorization.Resources.USER;
import static org.camunda.bpm.extension.keycloak.json.JsonUtil.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.Direction;
import org.camunda.bpm.engine.impl.QueryOrderingProperty;
import org.camunda.bpm.engine.impl.UserQueryProperty;
import org.camunda.bpm.engine.impl.identity.IdentityProviderException;
import org.camunda.bpm.engine.impl.persistence.entity.UserEntity;
import org.camunda.bpm.extension.keycloak.json.JsonException;
import org.camunda.bpm.extension.keycloak.util.KeycloakPluginLogger;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Implementation of user queries against Keycloak's REST API.
 */
public class KeycloakUserService extends KeycloakServiceBase {

	/**
	 * Default constructor.
	 * 
	 * @param keycloakConfiguration the Keycloak configuration
	 * @param restTemplate REST template
	 * @param keycloakContextProvider Keycloak context provider
	 */
	public KeycloakUserService(KeycloakConfiguration keycloakConfiguration, RestTemplate restTemplate,
			KeycloakContextProvider keycloakContextProvider) {
		super(keycloakConfiguration, restTemplate, keycloakContextProvider);
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
					return parseAsJsonObjectAndGetMemberAsString(response.getBody(), "email");
				}
				if (keycloakConfiguration.isUseUsernameAsCamundaUserId()) {
					return parseAsJsonObjectAndGetMemberAsString(response.getBody(), "username");
				}
				return parseAsJsonObjectAndGetMemberAsString(response.getBody(), "id");
			} catch (RestClientException | JsonException ex) {
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
				JsonObject user = findFirst(parseAsJsonArray(response.getBody()), "username", configuredAdminUserId);
				if (user != null) {
					if (keycloakConfiguration.isUseEmailAsCamundaUserId()) {
						return getJsonString(user, "email");
					}
					if (keycloakConfiguration.isUseUsernameAsCamundaUserId()) {
						return getJsonString(user, "username");
					}
					return getJsonString(user, "id");
				}
			} catch (JsonException je) {
				// username not found: fall through
			}
			// keycloak admin user does not exist :-(
			throw new IdentityProviderException("Configured administratorUserId " + configuredAdminUserId + " does not exist.");
		} catch (RestClientException rce) {
			throw new IdentityProviderException("Unable to read data of configured administratorUserId " + configuredAdminUserId, rce);
		}
	}

	/**
	 * Requests users of a specific group.
	 * @param query the user query - including a groupId criteria
	 * @return list of matching users
	 */
	public List<User> requestUsersByGroupId(KeycloakUserQuery query) {
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
					keycloakConfiguration.getKeycloakAdminUrl() + "/groups/" + keyCloakID + "/members?max=" + getMaxQueryResultSize(), 
					HttpMethod.GET, keycloakContextProvider.createApiRequestEntity(), String.class);
			if (!response.getStatusCode().equals(HttpStatus.OK)) {
				throw new IdentityProviderException(
						"Unable to read group members from " + keycloakConfiguration.getKeycloakAdminUrl()
								+ ": HTTP status code " + response.getStatusCodeValue());
			}

			JsonArray searchResult = parseAsJsonArray(response.getBody());
			for (int i = 0; i < searchResult.size(); i++) {
				JsonObject keycloakUser = getJsonObjectAtIndex(searchResult, i);
				if (keycloakConfiguration.isUseEmailAsCamundaUserId() && 
						StringUtils.isEmpty(getJsonString(keycloakUser, "email"))) {
					continue;
				}
				if (keycloakConfiguration.isUseUsernameAsCamundaUserId() &&
						StringUtils.isEmpty(getJsonString(keycloakUser, "username"))) {
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
		} catch (JsonException je) {
			throw new IdentityProviderException("Unable to query members of group " + groupId, je);
		}

		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("]");
			KeycloakPluginLogger.INSTANCE.userQueryResult(resultLogger.toString());
		}

		// sort users according to query criteria
		if (query.getOrderingProperties().size() > 0) {
			userList.sort(new UserComparator(query.getOrderingProperties()));
		}

		// paging
		if ((query.getFirstResult() > 0) || (query.getMaxResults() < Integer.MAX_VALUE)) {
			userList = userList.subList(query.getFirstResult(), 
					Math.min(userList.size(), query.getFirstResult() + query.getMaxResults()));
		}
		
		return userList;
	}

	/**
	 * Requests users.
	 * @param query the user query - not including a groupId criteria
	 * @return list of matching users
	 */
	public List<User> requestUsersWithoutGroupId(KeycloakUserQuery query) {
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

			JsonArray searchResult = parseAsJsonArray(response.getBody());
			for (int i = 0; i < searchResult.size(); i++) {
				JsonObject keycloakUser = getJsonObjectAtIndex(searchResult, i);
				if (keycloakConfiguration.isUseEmailAsCamundaUserId() && 
						StringUtils.isEmpty(getJsonString(keycloakUser, "email"))) {
					continue;
				}
				if (keycloakConfiguration.isUseUsernameAsCamundaUserId() &&
						StringUtils.isEmpty(getJsonString(keycloakUser, "username"))) {
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
		} catch (JsonException je) {
			throw new IdentityProviderException("Unable to query users", je);
		}

		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("]");
			KeycloakPluginLogger.INSTANCE.userQueryResult(resultLogger.toString());
		}

		// sort users according to query criteria
		if (query.getOrderingProperties().size() > 0) {
			userList.sort(new UserComparator(query.getOrderingProperties()));
		}
		
		// paging
		if ((query.getFirstResult() > 0) || (query.getMaxResults() < Integer.MAX_VALUE)) {
			userList = userList.subList(query.getFirstResult(), 
					Math.min(userList.size(), query.getFirstResult() + query.getMaxResults()));
		}
		
		return userList;
	}

	/**
	 * Creates an Keycloak user search filter query
	 * @param query the user query
	 * @return request query
	 */
	private String createUserSearchFilter(KeycloakUserQuery query) {
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
		addArgument(filter, "max", getMaxQueryResultSize());
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
	private ResponseEntity<String> requestUserById(String userId) throws RestClientException {
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
	 * Maps a Keycloak JSON result to a User object
	 * @param result the Keycloak JSON result
	 * @return the User object
	 * @throws JsonException in case of errors
	 */
	private UserEntity transformUser(JsonObject result) throws JsonException {
		UserEntity user = new UserEntity();
		if (keycloakConfiguration.isUseEmailAsCamundaUserId()) {
			user.setId(getJsonString(result, "email"));
		} else if (keycloakConfiguration.isUseUsernameAsCamundaUserId()) {
			user.setId(getJsonString(result, "username"));
		} else {
			user.setId(getJsonString(result, "id"));
		}
		user.setFirstName(getJsonString(result, "firstName"));
		user.setLastName(getJsonString(result, "lastName"));
		if (StringUtils.isEmpty(user.getFirstName()) && StringUtils.isEmpty(user.getLastName())) {
			user.setFirstName(getJsonString(result, "username"));
		}
		user.setEmail(getJsonString(result, "email"));
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
						c = KeycloakServiceBase.compare(u1.getId(), u2.getId());
						break;
					case EMAIL:
						c = KeycloakServiceBase.compare(u1.getEmail(), u2.getEmail());
						break;
					case FIRST_NAME:
						c = KeycloakServiceBase.compare(u1.getFirstName(), u2.getFirstName());
						break;
					case LAST_NAME:
						c = KeycloakServiceBase.compare(u1.getLastName(), u2.getLastName());
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

}
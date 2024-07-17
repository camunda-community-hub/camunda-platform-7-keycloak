package org.camunda.bpm.extension.keycloak;

import static org.camunda.bpm.engine.authorization.Permissions.READ;
import static org.camunda.bpm.engine.authorization.Resources.USER;
import static org.camunda.bpm.extension.keycloak.json.JsonUtil.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.Direction;
import org.camunda.bpm.engine.impl.QueryOrderingProperty;
import org.camunda.bpm.engine.impl.UserQueryProperty;
import org.camunda.bpm.engine.impl.identity.IdentityProviderException;
import org.camunda.bpm.engine.impl.persistence.entity.UserEntity;
import org.camunda.bpm.extension.keycloak.json.JsonException;
import org.camunda.bpm.extension.keycloak.rest.KeycloakRestTemplate;
import org.camunda.bpm.extension.keycloak.util.KeycloakPluginLogger;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

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
	public KeycloakUserService(KeycloakConfiguration keycloakConfiguration, KeycloakRestTemplate restTemplate,
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
						keycloakConfiguration.getKeycloakAdminUrl() + "/users/" + configuredAdminUserId, HttpMethod.GET, String.class);
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
						keycloakConfiguration.getKeycloakAdminUrl() + "/users?exact=true&username=" + configuredAdminUserId, HttpMethod.GET, String.class);
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
	public List<User> requestUsersByGroupId(CacheableKeycloakUserQuery query) {
		String groupId = query.getGroupId();
		List<User> userList = new ArrayList<>();

		try {
			//  get Keycloak specific groupID
			String keyCloakID;
			try {
				keyCloakID = getKeycloakGroupID(groupId);
			} catch (KeycloakGroupNotFoundException e) {
				// group not found: empty search result
				return Collections.emptyList();
			}

			// get members of this group
			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + "/groups/" + keyCloakID + "/members?max=" + getMaxQueryResultSize(), 
					HttpMethod.GET, String.class);
			if (!response.getStatusCode().equals(HttpStatus.OK)) {
				throw new IdentityProviderException(
						"Unable to read group members from " + keycloakConfiguration.getKeycloakAdminUrl()
								+ ": HTTP status code " + response.getStatusCodeValue());
			}

			JsonArray searchResult = parseAsJsonArray(response.getBody());
			for (int i = 0; i < searchResult.size(); i++) {
				JsonObject keycloakUser = getJsonObjectAtIndex(searchResult, i);
				if (keycloakConfiguration.isUseEmailAsCamundaUserId() && 
						!StringUtils.hasLength(getJsonString(keycloakUser, "email"))) {
					continue;
				}
				if (keycloakConfiguration.isUseUsernameAsCamundaUserId() &&
						!StringUtils.hasLength(getJsonString(keycloakUser, "username"))) {
					continue;
				}
				userList.add(transformUser(keycloakUser));
			}

		} catch (HttpClientErrorException hcee) {
			// if groupID is unknown server answers with HTTP 404 not found
			if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				return Collections.emptyList();
			}
			throw hcee;
		} catch (RestClientException | JsonException rce) {
			throw new IdentityProviderException("Unable to query members of group " + groupId, rce);
		}

		return userList;
	}

	/**
	 * Requests users.
	 * @param query the user query - not including a groupId criteria
	 * @return list of matching users
	 */
	public List<User> requestUsersWithoutGroupId(CacheableKeycloakUserQuery query) {
		List<User> userList = new ArrayList<>();

		try {
			// get members of this group
			ResponseEntity<String> response = null;

			if (StringUtils.hasLength(query.getId())) {
				response = requestUserById(query.getId());
			} else if (query.getIds() != null && query.getIds().length == 1) {
				response = requestUserById(query.getIds()[0]);
			} else {
				// Create user search filter
				String userFilter = createUserSearchFilter(query);
				response = restTemplate.exchange(keycloakConfiguration.getKeycloakAdminUrl() + "/users" + userFilter, HttpMethod.GET, String.class);
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
						!StringUtils.hasLength(getJsonString(keycloakUser, "email"))) {
					continue;
				}
				if (keycloakConfiguration.isUseUsernameAsCamundaUserId() &&
						!StringUtils.hasLength(getJsonString(keycloakUser, "username"))) {
					continue;
				}

				userList.add(transformUser(keycloakUser));
			}

		} catch (RestClientException | JsonException rce) {
			throw new IdentityProviderException("Unable to query users", rce);
		}

		return userList;
	}

	/**
	 * Post processes a Keycloak query result.
	 * @param query the original query
	 * @param userList the full list of results returned from Keycloak without client side filters
	 * @param resultLogger the log accumulator
	 * @return final result with the client side filtered, sorted and paginated list of users
	 */
	public List<User> postProcessResults(KeycloakUserQuery query, List<User> userList, StringBuilder resultLogger) {
		// apply client side filtering
		Stream<User> processed = userList.stream().filter(user -> isValid(query, user, resultLogger));

		// sort users according to query criteria
		if (query.getOrderingProperties().size() > 0) {
			processed = processed.sorted(new UserComparator(query.getOrderingProperties()));
		}

		// paging
		if ((query.getFirstResult() > 0) || (query.getMaxResults() < Integer.MAX_VALUE)) {
			processed = processed.skip(query.getFirstResult()).limit(query.getMaxResults());
		}

		return processed.collect(Collectors.toList());
	}

	/**
	 * Post processing query filter. Checks if a single user is valid.
	 * @param query the original query
	 * @param user the user to validate
	 * @param resultLogger the log accumulator
	 * @return a boolean indicating if the user is valid for current query
	 */
	private boolean isValid(KeycloakUserQuery query, User user, StringBuilder resultLogger) {
		// client side check of further query filters
		// beware: looks like most attributes are treated as 'like' queries on Keycloak
		//         and must therefore be seen as a sort of pre-filter only
		if (!matches(query.getId(), user.getId())) return false;
		if (!matches(query.getIds(), user.getId())) return false;
		if (!matches(query.getEmail(), user.getEmail())) return false;
		if (!matchesLike(query.getEmailLike(), user.getEmail())) return false;
		if (!matches(query.getFirstName(), user.getFirstName())) return false;
		if (!matchesLike(query.getFirstNameLike(), user.getFirstName())) return false;
		if (!matches(query.getLastName(), user.getLastName())) return false;
		if (!matchesLike(query.getLastNameLike(), user.getLastName())) return false;

		if(isAuthenticatedUser(user.getId()) || isAuthorized(READ, USER, user.getId())) {
			if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
				resultLogger.append(user);
				resultLogger.append(", ");
			}
			return true;
		}

		return false;
	}

	/**
	 * Creates an Keycloak user search filter query
	 * @param query the user query
	 * @return request query
	 */
	private String createUserSearchFilter(CacheableKeycloakUserQuery query) {
		StringBuilder filter = new StringBuilder();
		if (StringUtils.hasLength(query.getEmail())) {
			addArgument(filter, "email", query.getEmail());
		}
		if (StringUtils.hasLength(query.getEmailLike())) {
			addArgument(filter, "email", query.getEmailLike().replaceAll("[%,\\*]", ""));
		}
		if (StringUtils.hasLength(query.getFirstName())) {
			addArgument(filter, "firstName", query.getFirstName());
		}
		if (StringUtils.hasLength(query.getFirstNameLike())) {
			addArgument(filter, "firstName", query.getFirstNameLike().replaceAll("[%,\\*]", ""));
		}
		if (StringUtils.hasLength(query.getLastName())) {
			addArgument(filter, "lastName", query.getLastName());
		}
		if (StringUtils.hasLength(query.getLastNameLike())) {
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
				userSearch="/users?exact=true&email=" + userId;
			} else if (keycloakConfiguration.isUseUsernameAsCamundaUserId()) {
				userSearch="/users?exact=true&username=" + userId;
			} else {
				userSearch= "/users/" + userId;
			}

			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + userSearch, HttpMethod.GET, String.class);
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
		if (!StringUtils.hasLength(user.getFirstName()) && !StringUtils.hasLength(user.getLastName())) {
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

		private final int[] order;
		private final boolean[] desc;

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
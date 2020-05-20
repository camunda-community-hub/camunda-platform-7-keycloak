package org.camunda.bpm.extension.keycloak;

import java.util.Arrays;
import java.util.List;

import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.impl.persistence.entity.UserEntity;
import org.camunda.bpm.extension.keycloak.json.JSONArray;
import org.camunda.bpm.extension.keycloak.json.JSONException;
import org.camunda.bpm.extension.keycloak.json.JSONObject;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Base class for services implementing user / group queries against Keycloak.
 * Provides general helper methods.
 */
public abstract class KeycloakServiceBase {

	protected KeycloakConfiguration keycloakConfiguration;
	protected RestTemplate restTemplate;
	protected KeycloakContextProvider keycloakContextProvider;

	/**
	 * Default constructor.
	 * 
	 * @param keycloakConfiguration the Keycloak configuration
	 * @param restTemplate REST template
	 * @param keycloakContextProvider Keycloak context provider
	 */
	public KeycloakServiceBase(KeycloakConfiguration keycloakConfiguration,
			RestTemplate restTemplate, KeycloakContextProvider keycloakContextProvider) {
		this.keycloakConfiguration = keycloakConfiguration;
		this.restTemplate = restTemplate;
		this.keycloakContextProvider = keycloakContextProvider;
	}

	//-------------------------------------------------------------------------
	// User and Group ID Mappings dependent on configuration settings
	//-------------------------------------------------------------------------

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
			JSONArray resultList = new JSONArray(response.getBody());
			JSONObject result = findFirst(resultList,
					keycloakConfiguration.isUseUsernameAsCamundaUserId() ? "username" : "email",
					userId);
			if (result != null) {
				return result.getString("id");
			}
			throw new KeycloakUserNotFoundException(userId + 
					(keycloakConfiguration.isUseEmailAsCamundaUserId() 
					? " not found - email unknown" 
					: " not found - username unknown"));
		} catch (JSONException je) {
			throw new KeycloakUserNotFoundException(userId + 
					(keycloakConfiguration.isUseEmailAsCamundaUserId() 
					? " not found - email unknown" 
					: " not found - username unknown"), je);
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
	
	//-------------------------------------------------------------------------
	// General helper methods
	//-------------------------------------------------------------------------

	/**
	 * Return the maximum result size of Keycloak queries as String.
	 * @return maximum results for Keycloak search requests
	 */
	protected String getMaxQueryResultSize() {
		return Integer.toString(keycloakConfiguration.getMaxResultSize());
	}
	
	/**
	 * Truncates a list to a given maximum size.
	 * @param <T> element type of list
	 * @param list the original list
	 * @param maxSize the maximum size
	 * @return the truncated list
	 */
	protected <T> List<T> truncate(List<T> list, int maxSize) {
		if (list == null) return list;
		int actualSize = list.size();
		if (actualSize <=  maxSize) return list;
		return list.subList(0, maxSize);
	}
	
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
	 * Finds the first element in a list, where a given attribute matches a given name.
	 * @param list the list
	 * @param attributeToMatch the name of the attribute to match against
	 * @param attributeValue the value of the attribute
	 * @return matching element if found, {@code null} otherwise
	 */
	protected JSONObject findFirst(JSONArray list, String attributeToMatch, String attributeValue) {
		for (int i=0; i < list.length(); i++) {
			JSONObject result = list.getJSONObject(i);
			if (attributeValue.equalsIgnoreCase(result.optString(attributeToMatch))) {
				return result;
			}
		}
		return null;
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

	/**
	 * @return true if the passed-in user is currently authenticated
	 */
	protected boolean isAuthenticatedUser(UserEntity user) {
		return isAuthenticatedUser(user.getId());
	}

	/**
	 * @return true if the passed-in userId matches the currently authenticated user
	 */
	protected boolean isAuthenticatedUser(String userId) {
		if (userId == null) {
			return false;
		}
		return userId.equalsIgnoreCase(
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

package org.camunda.bpm.extension.keycloak;

import static org.camunda.bpm.engine.authorization.Permissions.READ;
import static org.camunda.bpm.engine.authorization.Resources.GROUP;
import static org.camunda.bpm.extension.keycloak.json.JsonUtil.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.impl.Direction;
import org.camunda.bpm.engine.impl.GroupQueryProperty;
import org.camunda.bpm.engine.impl.QueryOrderingProperty;
import org.camunda.bpm.engine.impl.identity.IdentityProviderException;
import org.camunda.bpm.engine.impl.persistence.entity.GroupEntity;
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

import camundajar.impl.scala.Array;

/**
 * Implementation of group queries against Keycloak's REST API.
 */
public class KeycloakGroupService extends KeycloakServiceBase {

	/**
	 * Default constructor.
	 * 
	 * @param keycloakConfiguration the Keycloak configuration
	 * @param restTemplate REST template
	 * @param keycloakContextProvider Keycloak context provider
	 */
	public KeycloakGroupService(KeycloakConfiguration keycloakConfiguration,
			RestTemplate restTemplate, KeycloakContextProvider keycloakContextProvider) {
		super(keycloakConfiguration, restTemplate, keycloakContextProvider);
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
					return parseAsJsonObjectAndGetMemberAsString(response.getBody(), "path").substring(1); // remove trailing '/'
				}
				return parseAsJsonObjectAndGetMemberAsString(response.getBody(), "id");
			} catch (RestClientException | JsonException ex) {
				// group not found: fall through
			}
			
			// check whether configured admin group can be resolved as group name
			try {
				ResponseEntity<String> response = restTemplate.exchange(
						keycloakConfiguration.getKeycloakAdminUrl() + "/groups?search=" + configuredAdminGroupName, HttpMethod.GET,
						keycloakContextProvider.createApiRequestEntity(), String.class);
				// filter search result for exact group name, including subgroups
				JsonArray result = flattenSubGroups(parseAsJsonArray(response.getBody()), new JsonArray());
				JsonArray groups = new JsonArray();
				for (int i = 0; i < result.size(); i++) {
					JsonObject keycloakGroup = getJsonObjectAtIndex(result, i);
					if (getOptJsonString(keycloakGroup, "name").equals(configuredAdminGroupName)) {
						groups.add(keycloakGroup);
					}
				}
				if (groups.size() == 1) {
					if (keycloakConfiguration.isUseGroupPathAsCamundaGroupId()) {
						return getJsonString(getJsonObjectAtIndex(groups, 0), "path").substring(1); // remove trailing '/'
					}
					return getJsonString(getJsonObjectAtIndex(groups, 0), "id");
				} else if (groups.size() > 0) {
					throw new IdentityProviderException("Configured administratorGroupName " + configuredAdminGroupName + " is not unique. Please configure exact group path.");
				}
				// groups size == 0: fall through
			} catch (JsonException je) {
				// group not found: fall through
			}

			// keycloak admin group does not exist :-(
			throw new IdentityProviderException("Configured administratorGroupName " + configuredAdminGroupName + " does not exist.");
		} catch (RestClientException rce) {
			throw new IdentityProviderException("Unable to read data of configured administratorGroupName " + configuredAdminGroupName, rce);
		}
	}

	/**
	 * Requests groups of a specific user.
	 * @param query the group query - including a userId criteria
	 * @return list of matching groups
	 */
	public List<Group> requestGroupsByUserId(KeycloakGroupQuery query) {
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
					keycloakConfiguration.getKeycloakAdminUrl() + "/users/" + keyCloakID + "/groups?max=" + getMaxQueryResultSize(), 
					HttpMethod.GET, keycloakContextProvider.createApiRequestEntity(), String.class);
			if (!response.getStatusCode().equals(HttpStatus.OK)) {
				throw new IdentityProviderException(
						"Unable to read user groups from " + keycloakConfiguration.getKeycloakAdminUrl()
								+ ": HTTP status code " + response.getStatusCodeValue());
			}

			JsonArray searchResult = parseAsJsonArray(response.getBody());

			//build whitelist
			ArrayList<String> whitelistedGroups = buildGroupWhitelist();
			
			for (int i = 0; i < searchResult.size(); i++) {
				JsonObject keycloakGroup = getJsonObjectAtIndex(searchResult, i);
				Group group = transformGroup(keycloakGroup);

				//perform whitelist check
				if (!whitelistedGroups.isEmpty()) {
					//check if this group is one of the whitelisted groups
					if(!isGroupWhitelisted(group.getName(),whitelistedGroups)) {
						if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
							resultLogger.append(group);
							resultLogger.append(" based on ");
							resultLogger.append(keycloakGroup.toString());
							resultLogger.append(" because it is not whitelisted, ");
						}
						continue;						
					}
				}
				
				//Prefix-Filter set ? Apply only if whitelisting is not active
				if ((!StringUtils.isEmpty(this.keycloakConfiguration.getKeycloakGroupFilterPrefix())) && (whitelistedGroups.isEmpty())){
					if (!matchesGroupPrefix(group.getName())) {
						if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
							resultLogger.append("skip ");
							resultLogger.append(group);
							resultLogger.append(" based on ");
							resultLogger.append(keycloakGroup.toString());
							resultLogger.append(" because group-prefix does not match , ");
						}
						continue;
					}
				}

				// client side check of further query filters
				if (!matches(query.getId(), group.getId())) continue;
				if (!matches(query.getIds(), group.getId())) continue;
				if (!matches(query.getName(), group.getName())) continue;
				if (!matchesLike(query.getNameLike(), group.getName())) continue;
				if (!matches(query.getType(), group.getType())) continue;

				// authenticated user is always allowed to query his own groups
				// otherwise READ authentication is required
				boolean isAuthenticatedUser = isAuthenticatedUser(userId);
				if (isAuthenticatedUser || isAuthorized(READ, GROUP, group.getId())) {
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
		} catch (JsonException je) {
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

		// paging
		if ((query.getFirstResult() > 0) || (query.getMaxResults() < Integer.MAX_VALUE)) {
			groupList = groupList.subList(query.getFirstResult(), 
					Math.min(groupList.size(), query.getFirstResult() + query.getMaxResults()));
		}

		// group queries in Keycloak do not consider the max attribute within the search request
		return truncate(groupList, keycloakConfiguration.getMaxResultSize());
	}
	
	/**
	 * Requests groups.
	 * @param query the group query - not including a userId criteria
	 * @return list of matching groups
	 */
	public List<Group> requestGroupsWithoutUserId(KeycloakGroupQuery query) {
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

			JsonArray searchResult = null;
			if (!StringUtils.isEmpty(query.getId())) {
				searchResult = parseAsJsonArray(response.getBody());
			} else {
				// for non ID queries search in subgroups as well
				searchResult = flattenSubGroups(parseAsJsonArray(response.getBody()), new JsonArray());
			}

			//build whitelist
			ArrayList<String> whitelistedGroups = buildGroupWhitelist();
			
			for (int i = 0; i < searchResult.size(); i++) {
				JsonObject keycloakGroup = getJsonObjectAtIndex(searchResult, i);
				Group group = transformGroup(keycloakGroup);
				
				//perform whitelist check
				if (!whitelistedGroups.isEmpty()) {
					//check if this group is one of the whitelisted groups
					if(!isGroupWhitelisted(group.getName(),whitelistedGroups)) {
						if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
							resultLogger.append("skip ");
							resultLogger.append(group);
							resultLogger.append(" based on ");
							resultLogger.append(keycloakGroup.toString());
							resultLogger.append(" because it is not whitelisted, ");
						}
						continue;						
					}
				}
				
				//Prefix-Filter set ? Apply only if whitelisting is not active
				if ((!StringUtils.isEmpty(this.keycloakConfiguration.getKeycloakGroupFilterPrefix())) && (whitelistedGroups.isEmpty())){
					if (!matchesGroupPrefix(group.getName())) {
						if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
							resultLogger.append("skip ");
							resultLogger.append(group);
							resultLogger.append(" based on ");
							resultLogger.append(keycloakGroup.toString());
							resultLogger.append(" because group-prefix does not match , ");
						}
						continue;
					}
				}
				
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
		} catch (JsonException je) {
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

		// paging
		if ((query.getFirstResult() > 0) || (query.getMaxResults() < Integer.MAX_VALUE)) {
			groupList = groupList.subList(query.getFirstResult(), 
					Math.min(groupList.size(), query.getFirstResult() + query.getMaxResults()));
		}

		// group queries in Keycloak do not consider the max attribute within the search request
		return truncate(groupList, keycloakConfiguration.getMaxResultSize());
	}

	/**
	 * Creates an Keycloak group search filter query
	 * @param query the group query
	 * @return request query
	 */
	private String createGroupSearchFilter(KeycloakGroupQuery query) {
		StringBuilder filter = new StringBuilder();
		if (!StringUtils.isEmpty(query.getName())) {
			addArgument(filter, "search", query.getName());
		}
		if (!StringUtils.isEmpty(query.getNameLike())) {
			addArgument(filter, "search", query.getNameLike().replaceAll("[%,\\*]", ""));
		}
		addArgument(filter, "max", getMaxQueryResultSize());
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
	 * @throws JsonException in case of errors
	 */
	private JsonArray flattenSubGroups(JsonArray groups, JsonArray result) throws JsonException {
		if (groups == null) return result;
	    for (int i = 0; i < groups.size(); i++) {
	    	JsonObject group = getJsonObjectAtIndex(groups, i);
	    	JsonArray subGroups;
			try {
				subGroups = getJsonArray(group, "subGroups");
		    	group.remove("subGroups");
		    	result.add(group);
		    	flattenSubGroups(subGroups, result);
			} catch (JsonException e) {
				result.add(group);
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
	private ResponseEntity<String> requestGroupById(String groupId) throws RestClientException {
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
	 * Maps a Keycloak JSON result to a Group object
	 * @param result the Keycloak JSON result
	 * @return the Group object
	 * @throws JsonException in case of errors
	 */
	private GroupEntity transformGroup(JsonObject result) throws JsonException {
		GroupEntity group = new GroupEntity();
		if (keycloakConfiguration.isUseGroupPathAsCamundaGroupId()) {
			group.setId(getJsonString(result, "path").substring(1)); // remove trailing '/'
		} else {
			group.setId(getJsonString(result, "id"));
		}
		group.setName(getJsonString(result, "name"));
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
	 * @throws JsonException in case of errors
	 */
	private boolean isSystemGroup(JsonObject result) throws JsonException {
		String name = getJsonString(result, "name");
		if (Groups.CAMUNDA_ADMIN.equals(name) || 
				name.equals(keycloakConfiguration.getAdministratorGroupName())) {
			return true;
		}
		try {
			JsonArray types = getJsonArray(getJsonObject(result, "attributes"), "type");
			for (int i = 0; i < types.size(); i++) {
				if (Groups.GROUP_TYPE_SYSTEM.equals(getJsonStringAtIndex(types, i).toUpperCase())) {
					return true;
				}
			}
		} catch (JsonException ex) {
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
						c = KeycloakServiceBase.compare(g1.getId(), g2.getId());
						break;
					case NAME:
						c = KeycloakServiceBase.compare(g1.getName(), g2.getName());
						break;
					case TYPE:
						c = KeycloakServiceBase.compare(g1.getType(), g2.getType());
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

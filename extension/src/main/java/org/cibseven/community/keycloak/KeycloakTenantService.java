package org.cibseven.community.keycloak;

import static org.cibseven.bpm.engine.authorization.Permissions.READ;
import static org.cibseven.bpm.engine.authorization.Resources.TENANT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.cibseven.bpm.engine.identity.Tenant;
import org.cibseven.bpm.engine.impl.Direction;
import org.cibseven.bpm.engine.impl.QueryOrderingProperty;
import org.cibseven.bpm.engine.impl.TenantQueryImpl;
import org.cibseven.bpm.engine.impl.TenantQueryProperty;
import org.cibseven.bpm.engine.impl.identity.IdentityProviderException;
import org.cibseven.bpm.engine.impl.persistence.entity.TenantEntity;
import org.cibseven.community.keycloak.json.JsonException;
import org.cibseven.community.keycloak.rest.KeycloakRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import static org.cibseven.community.keycloak.json.JsonUtil.*;

public class KeycloakTenantService extends KeycloakServiceBase {

	/**
	 * Default constructor.
	 *
	 * @param keycloakConfiguration the Keycloak configuration
	 * @param restTemplate REST template
	 * @param keycloakContextProvider Keycloak context provider
	 */
	public KeycloakTenantService(KeycloakConfiguration keycloakConfiguration,
			KeycloakRestTemplate restTemplate, KeycloakContextProvider keycloakContextProvider) {
		super(keycloakConfiguration, restTemplate, keycloakContextProvider);
	}

	/**
	 * Requests all tenants (Keycloak organizations).
	 * Requires {@link KeycloakConfiguration#isOrganizationsExtendedQueriesEnabled()}.
	 * @return list of all tenants, empty if organizations are not enabled
	 */
	public List<Tenant> getOrganizationsforTenants() {
		if (!keycloakConfiguration.isOrganizationsExtendedQueriesEnabled()) {
			return Collections.emptyList();
		}
		List<Tenant> tenantList = new ArrayList<>();
		try {
			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + "/organizations?first=0&max=" + getMaxQueryResultSize(),
					HttpMethod.GET, String.class);

			JsonArray searchResult = parseAsJsonArray(response.getBody());
			for (int i = 0; i < searchResult.size(); i++) {
				tenantList.add(transformTenant(getJsonObjectAtIndex(searchResult, i)));
			}
		} catch (HttpClientErrorException hcee) {
			if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				return Collections.emptyList();
			}
			throw new IdentityProviderException("Unable to query organizations", hcee);
		} catch (RestClientException | JsonException ex) {
			throw new IdentityProviderException("Unable to query organizations", ex);
		}
		return tenantList;
	}

	/**
	 * Requests tenants (Keycloak organizations) of a specific user.
	 * Requires {@link KeycloakConfiguration#isOrganizationsEnabled()}.
	 * @param userId the CIB seven user ID
	 * @return list of matching tenants, empty if organizations are not enabled
	 */
	public List<Tenant> requestTenantsByUserId(String userId) {
		if (!keycloakConfiguration.isOrganizationsEnabled()) {
			return Collections.emptyList();
		}
		List<Tenant> tenantList = new ArrayList<>();
		try {
			String keyCloakID;
			try {
				keyCloakID = getKeycloakUserID(userId);
			} catch (KeycloakUserNotFoundException e) {
				return Collections.emptyList();
			}
			ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + "/organizations/members/" + keyCloakID + "/organizations",
					HttpMethod.GET, String.class);

			JsonArray searchResult = parseAsJsonArray(response.getBody());
			for (int i = 0; i < searchResult.size(); i++) {
				tenantList.add(transformTenant(getJsonObjectAtIndex(searchResult, i)));
			}
		} catch (HttpClientErrorException hcee) {
			if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				return Collections.emptyList();
			}
			throw new IdentityProviderException("Unable to query organizations of user " + userId, hcee);
		} catch (RestClientException | JsonException ex) {
			throw new IdentityProviderException("Unable to query organizations of user " + userId, ex);
		}
		return tenantList;
	}

	/**
	 * Requests a single tenant (Keycloak organization) by its ID.
	 * Requires {@link KeycloakConfiguration#isOrganizationsExtendedQueriesEnabled()}.
	 * <p>
	 * No authorization check is done here: this method is called from the cached, user independent
	 * query path. Authorization is applied per caller in {@link #postProcessResults(TenantQueryImpl, List)}.
	 * @param tenantId the tenant ID
	 * @return the tenant, or {@code null} if not found or organizations are not enabled
	 */
	public TenantEntity findTenant(String tenantId) {
		if (!keycloakConfiguration.isOrganizationsExtendedQueriesEnabled()) {
			return null;
		}
		try {
			ResponseEntity<String> response = restTemplate.exchange(
                keycloakConfiguration.getKeycloakAdminUrl() + "/organizations/" + tenantId,
                HttpMethod.GET, String.class);
			return transformTenant(parseAsJsonObject(response.getBody()));
		} catch (HttpClientErrorException hcee) {
        	if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
            	return null;
        	}
			throw new IdentityProviderException("Unable to query organization " + tenantId, hcee);
		} catch (RestClientException | JsonException ex) {
        	throw new IdentityProviderException("Unable to query organization " + tenantId, ex);
		}
	}

	/**
	 * Post processes a Keycloak query result.
	 * @param query the original query
	 * @param tenantList the full list of results returned from Keycloak without client side filters
	 * @return final result with client side filtered, sorted and paginated list of tenants
	 */
	public List<Tenant> postProcessResults(TenantQueryImpl query, List<Tenant> tenantList) {
		Stream<Tenant> processed = tenantList.stream().filter(tenant -> isValid(query, tenant));
		if (query.getOrderingProperties().size() > 0) {
			processed = processed.sorted(new TenantComparator(query.getOrderingProperties()));
		}
		if ((query.getFirstResult() > 0) || (query.getMaxResults() < Integer.MAX_VALUE)) {
			processed = processed.skip(query.getFirstResult()).limit(query.getMaxResults());
		}

		return processed.limit(keycloakConfiguration.getMaxResultSize()).collect(Collectors.toList());
	}

	/**
	 * Post processing query filter. Checks if a single tenant is valid.
	 * @param query the original query
	 * @param tenant the tenant to validate
	 * @return a boolean indicating if the tenant is valid for current query
	 */
	private boolean isValid(TenantQueryImpl query, Tenant tenant) {
		if (!matches(query.getId(), tenant.getId())) return false;
		if (!matches(query.getIds(), tenant.getId())) return false;
		if (!matches(query.getName(), tenant.getName())) return false;
		if (!matchesLike(query.getNameLike(), tenant.getName())) return false;

		return isAuthenticatedUser(query.getUserId()) || isAuthorized(READ, TENANT, tenant.getId());
	}

	private TenantEntity transformTenant(JsonObject result) throws JsonException {
		TenantEntity tenant = new TenantEntity();
		tenant.setId(getJsonString(result, "id"));
		tenant.setName(getJsonString(result, "name"));
		return tenant;
	}

	/**
	 * Helper for client side tenant ordering.
	 */
	private static class TenantComparator implements Comparator<Tenant> {
		private final static int TENANT_ID = 0;
		private final static int NAME = 1;

		private final int[] order;
		private final boolean[] desc;

		public TenantComparator(List<QueryOrderingProperty> orderList) {
			this.order = new int[orderList.size()];
			this.desc = new boolean[orderList.size()];
			for (int i = 0; i < orderList.size(); i++) {
				QueryOrderingProperty qop = orderList.get(i);
				if (qop.getQueryProperty().equals(TenantQueryProperty.GROUP_ID)) {
					order[i] = TENANT_ID;
				} else if (qop.getQueryProperty().equals(TenantQueryProperty.NAME)) {
					order[i] = NAME;
				} else {
					order[i] = -1;
				}
				desc[i] = Direction.DESCENDING.equals(qop.getDirection());
			}
		}

		@Override
		public int compare(Tenant t1, Tenant t2) {
			int c = 0;
			for (int i = 0; i < order.length; i++) {
				switch (order[i]) {
					case TENANT_ID:
						c = KeycloakServiceBase.compare(t1.getId(), t2.getId());
						break;
					case NAME:
						c = KeycloakServiceBase.compare(t1.getName(), t2.getName());
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

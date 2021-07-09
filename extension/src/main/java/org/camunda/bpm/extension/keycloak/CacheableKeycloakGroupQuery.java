package org.camunda.bpm.extension.keycloak;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable wrapper over KeycloakGroupQuery that can be used as a cache key.
 * Note: keep equals/hashcode in sync with the list of fields
 */
public class CacheableKeycloakGroupQuery {

	private final String id;
	private final String[] ids;
	private final String name;
	private final String nameLike;
	private final String type;
	private final String userId;
	private final String tenantId;

	private CacheableKeycloakGroupQuery(KeycloakGroupQuery delegate) {
		this.id = delegate.getId();
		this.ids = delegate.getIds();
		this.name = delegate.getName();
		this.nameLike = delegate.getNameLike();
		this.type = delegate.getType();
		this.userId = delegate.getUserId();
		this.tenantId = delegate.getTenantId();
	}

	public static CacheableKeycloakGroupQuery of(KeycloakGroupQuery groupQuery) {
		return new CacheableKeycloakGroupQuery(groupQuery);
	}

	public String getId() {
		return id;
	}

	public String[] getIds() {
		return ids;
	}

	public String getName() {
		return name;
	}

	public String getNameLike() {
		return nameLike;
	}

	public String getType() {
		return type;
	}

	public String getUserId() {
		return userId;
	}

	public String getTenantId() {
		return tenantId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CacheableKeycloakGroupQuery that = (CacheableKeycloakGroupQuery) o;
		return Objects.equals(id, that.id) &&
						Arrays.equals(ids, that.ids) &&
						Objects.equals(name, that.name) &&
						Objects.equals(nameLike, that.nameLike) &&
						Objects.equals(type, that.type) &&
						Objects.equals(userId, that.userId) &&
						Objects.equals(tenantId, that.tenantId);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(id, name, nameLike, type, userId, tenantId);
		result = 31 * result + Arrays.hashCode(ids);
		return result;
	}
}

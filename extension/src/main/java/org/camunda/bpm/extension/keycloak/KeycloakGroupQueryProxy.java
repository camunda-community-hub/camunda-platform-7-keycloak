package org.camunda.bpm.extension.keycloak;

import lombok.Value;

/**
 * Immutable wrapper over KeycloakGroupQuery that can be used as a cache key.
 */
@Value
public class KeycloakGroupQueryProxy {

	private final String id;
	private final String[] ids;
	private final String name;
	private final String nameLike;
	private final String type;
	private final String userId;
	private final String tenantId;

	private KeycloakGroupQueryProxy(KeycloakGroupQuery delegate) {
		this.id = delegate.getId();
		this.ids = delegate.getIds();
		this.name = delegate.getName();
		this.nameLike = delegate.getNameLike();
		this.type = delegate.getType();
		this.userId = delegate.getUserId();
		this.tenantId = delegate.getTenantId();
	}

	public static KeycloakGroupQueryProxy of(KeycloakGroupQuery groupQuery) {
		return new KeycloakGroupQueryProxy(groupQuery);
	}
}

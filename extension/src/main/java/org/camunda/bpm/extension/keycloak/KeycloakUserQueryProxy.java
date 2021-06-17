package org.camunda.bpm.extension.keycloak;

import lombok.Value;

/**
 * Immutable wrapper over KeycloakUserQuery that can be used as a cache key.
 */
@Value
public class KeycloakUserQueryProxy {

	private final String id;
	private final String[] ids;
	private final String firstName;
	private final String firstNameLike;
	private final String lastName;
	private final String lastNameLike;
	private final String email;
	private final String emailLike;
	private final String groupId;

	private KeycloakUserQueryProxy(KeycloakUserQuery delegate) {
		this.id = delegate.getId();
		this.ids = delegate.getIds();
		this.firstName = delegate.getFirstName();
		this.firstNameLike = delegate.getFirstNameLike();
		this.lastName = delegate.getLastName();
		this.lastNameLike = delegate.getLastNameLike();
		this.email = delegate.getEmail();
		this.emailLike = delegate.getEmailLike();
		this.groupId = delegate.getGroupId();
	}

	public static KeycloakUserQueryProxy of(KeycloakUserQuery userQuery) {
		return new KeycloakUserQueryProxy(userQuery);
	}
}

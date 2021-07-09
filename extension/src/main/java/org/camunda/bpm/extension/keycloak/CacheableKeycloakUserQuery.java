package org.camunda.bpm.extension.keycloak;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable wrapper over KeycloakUserQuery that can be used as a cache key.
 * Note: keep equals/hashcode in sync with the list of fields
 */
public class CacheableKeycloakUserQuery {

	private final String id;
	private final String[] ids;
	private final String firstName;
	private final String firstNameLike;
	private final String lastName;
	private final String lastNameLike;
	private final String email;
	private final String emailLike;
	private final String groupId;

	private CacheableKeycloakUserQuery(KeycloakUserQuery delegate) {
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

	public static CacheableKeycloakUserQuery of(KeycloakUserQuery userQuery) {
		return new CacheableKeycloakUserQuery(userQuery);
	}

	public String getId() {
		return id;
	}

	public String[] getIds() {
		return ids;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getFirstNameLike() {
		return firstNameLike;
	}

	public String getLastName() {
		return lastName;
	}

	public String getLastNameLike() {
		return lastNameLike;
	}

	public String getEmail() {
		return email;
	}

	public String getEmailLike() {
		return emailLike;
	}

	public String getGroupId() {
		return groupId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CacheableKeycloakUserQuery that = (CacheableKeycloakUserQuery) o;
		return Objects.equals(id, that.id) && 
						Arrays.equals(ids, that.ids) && 
						Objects.equals(firstName, that.firstName) && 
						Objects.equals(firstNameLike, that.firstNameLike) && 
						Objects.equals(lastName, that.lastName) && 
						Objects.equals(lastNameLike, that.lastNameLike) && 
						Objects.equals(email, that.email) && 
						Objects.equals(emailLike, that.emailLike) && 
						Objects.equals(groupId, that.groupId);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(id, firstName, firstNameLike, lastName, lastNameLike, email, emailLike, groupId);
		result = 31 * result + Arrays.hashCode(ids);
		return result;
	}
}

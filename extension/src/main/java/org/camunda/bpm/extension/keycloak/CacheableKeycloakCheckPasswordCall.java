package org.camunda.bpm.extension.keycloak;

import java.util.Objects;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * Immutable wrapper for the checkPassword result that can be used as a cache key.
 */
public class CacheableKeycloakCheckPasswordCall {

	/** The userId. */
	private final String userId;
	/** Login hash. */
	private final String hash;

	/**
	 * @param userId
	 * @param pwdHash
	 */
	public CacheableKeycloakCheckPasswordCall(String userId, String password) {
		super();
		this.userId = userId;
		this.hash = password != null ? DigestUtils.sha256Hex(password) : null;
	}
	
	/**
	 * @return the userId
	 */
	public String getUserId() {
		return userId;
	}

	/**
	 * @return the hash
	 */
	public String getHash() {
		return hash;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hash(hash, userId);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CacheableKeycloakCheckPasswordCall other = (CacheableKeycloakCheckPasswordCall) obj;
		return Objects.equals(hash, other.hash) && Objects.equals(userId, other.userId);
	}
	
}

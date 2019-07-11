package org.camunda.bpm.extension.keycloak;

/**
 * Thrown in case a query for a unique group fails.
 */
public class KeycloakGroupNotFoundException extends Exception {

	/** This class' serial version UID. */
	private static final long serialVersionUID = 4368608195497046998L;

	/**
	 * Creates a new KeycloakGroupNotFoundException.
	 * @param message the message
	 * @param cause the original cause
	 */
	public KeycloakGroupNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

}

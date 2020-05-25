package org.camunda.bpm.extension.keycloak.json;

/**
 * Exception thrown in case of any JSON errors.
 */
public class JsonException extends Exception {

	/** This class' serial version UID. */
	private static final long serialVersionUID = -3207252773395186866L;

	/** 
	 * Constructs a new JsonException.
	 * @param message the message
	 * @param cause the original cause
	 */
	public JsonException(String message, Throwable cause) {
		super(message, cause);
	}

}

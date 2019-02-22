package de.vonderbeck.bpm.identity.keycloak;

import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;

/**
 * Super class for all Identity Provider Tests.
 */
public abstract class KeycloakIdentityProviderTest extends PluggableProcessEngineTestCase {

	protected final static String GROUP_ID_TEAMLEAD = "2d72716d-d269-479a-bc94-1bc9bb3f29a1";
	protected final static String GROUP_ID_MANAGEMENT = "ec513624-5b34-41c4-b81a-8340694d9c78";
	protected final static String GROUP_ID_ADMIN = "6b8f4ee5-db87-4530-8daa-0c557645f046";
	
	protected final static String USER_ID_CAMUNDA_ADMIN = "7ede1f9c-bae4-4a99-ae3f-322a2c2b544c";
	protected final static String USER_ID_OTHER = "1b7bfaca-2c88-4776-a639-9188ef2ff608";

	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}


}

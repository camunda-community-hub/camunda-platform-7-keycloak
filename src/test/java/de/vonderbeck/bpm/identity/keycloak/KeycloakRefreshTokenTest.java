package de.vonderbeck.bpm.identity.keycloak;

import java.lang.reflect.Field;

import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

/**
 * Refresh access token test.
 */
public class KeycloakRefreshTokenTest extends AbstractKeycloakIdentityProviderTest {

	/**
	 * Tests refreshing access token.
	 * @throws Exception in case of errors
	 */
	public void testRefreshToken() throws Exception {
		// access Keycloak
	    assertEquals(3, identityService.createUserQuery().count());

	    // expire current token (the dirty way)
	    KeycloakIdentityProviderFactory sessionFacory = (KeycloakIdentityProviderFactory) 
	    		((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getIdentityProviderSessionFactory();
	    KeycloakContext ctx = sessionFacory.keycloakContextProvider.context;
	    Field expiresField = KeycloakContext.class.getDeclaredField("expiresAt");
	    expiresField.setAccessible(true);
	    expiresField.set(ctx, 0);
	    assertTrue(ctx.needsRefresh());
	    
		// access Keycloak again
	    assertEquals(3, identityService.createUserQuery().count());
	    
	}
}

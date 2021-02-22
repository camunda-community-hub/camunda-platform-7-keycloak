package org.camunda.bpm.extension.keycloak.test;

import java.util.List;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Group query test for the Keycloak identity provider.
 * Flag useGroupPathAsCamundaGroupId enabled.
 */
public class KeycloakGroupFilterWhitelistTest extends AbstractKeycloakIdentityProviderTest {

	public static Test suite() {
	    return new TestSetup(new TestSuite(KeycloakGroupFilterWhitelistTest.class)) {

	    	// @BeforeClass
	        protected void setUp() throws Exception {
	    		ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
	    				.createProcessEngineConfigurationFromResource("camunda.groupFilterWhitelist.cfg.xml");
	    		configureKeycloakIdentityProviderPlugin(config);
	    		PluggableProcessEngineTestCase.cachedProcessEngine = config.buildProcessEngine();
	        }
	        
	        // @AfterClass
	        protected void tearDown() throws Exception {
	    		PluggableProcessEngineTestCase.cachedProcessEngine.close();
	    		PluggableProcessEngineTestCase.cachedProcessEngine = null;
	        }
	    };
	}
	
	// ------------------------------------------------------------------------
	// Group Query tests
	// ------------------------------------------------------------------------
	
	public void testFilterByGroupId() {
		//admin group will be auto-whitelisted
		Group group = identityService.createGroupQuery().groupId("camunda-admin").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals("camunda-admin", group.getId());
		assertEquals("camunda-admin", group.getName());
		assertEquals("SYSTEM", group.getType());

		group = identityService.createGroupQuery().groupId("manager").singleResult();
		assertNull(group);
	}

	public void testWhitelistFilter() {
		List<Group> groups = identityService.createGroupQuery().list();
		assertEquals(3, groups.size());
		
	}	
	
}

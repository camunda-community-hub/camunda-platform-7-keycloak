package org.camunda.bpm.extension.keycloak.test;

import java.util.List;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.impl.test.TestLogger;
import org.camunda.commons.logging.BaseLogger;
import org.slf4j.Logger;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Group query test for the Keycloak identity provider.
 * Flag useGroupPathAsCamundaGroupId enabled.
 */
public class KeycloakGroupFilterPrefixTest extends AbstractKeycloakIdentityProviderTest {
	

	public static Test suite() {
	    return new TestSetup(new TestSuite(KeycloakGroupFilterPrefixTest.class)) {

	    	// @BeforeClass
	        protected void setUp() throws Exception {
	    		ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
	    				.createProcessEngineConfigurationFromResource("camunda.groupFilterPrefix.cfg.xml");
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
		//admin group will be always allowed 
		Group group = identityService.createGroupQuery().groupId("camunda-admin").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals("camunda-admin", group.getId());
		assertEquals("camunda-admin", group.getName());
		assertEquals("SYSTEM", group.getType());

		group = identityService.createGroupQuery().groupId("whatever").singleResult();
		assertNull(group);
	}

	public void testPrefixFilter() {
		List<Group> groups = identityService.createGroupQuery().list();
		
		assertEquals(2, groups.size());
		
		for (Group group : groups) {
			if (!group.getId().equals("camunda-admin") && !group.getId().equals("camunda-identity-service")) {
				fail();
			}
		}
	}	
	
}

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
public class KeycloakUseGroupPathAsGroupIdQueryTest extends AbstractKeycloakIdentityProviderTest {

	public static Test suite() {
	    return new TestSetup(new TestSuite(KeycloakUseGroupPathAsGroupIdQueryTest.class)) {

	    	// @BeforeClass
	        protected void setUp() {
	    		ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
	    				.createProcessEngineConfigurationFromResource("camunda.useGroupPathAsCamundaGroupId.cfg.xml");
	    		configureKeycloakIdentityProviderPlugin(config);
	    		PluggableProcessEngineTestCase.cachedProcessEngine = config.buildProcessEngine();
	        }
	        
	        // @AfterClass
	        protected void tearDown() {
	    		PluggableProcessEngineTestCase.cachedProcessEngine.close();
	    		PluggableProcessEngineTestCase.cachedProcessEngine = null;
	        }
	    };
	}
	
	// ------------------------------------------------------------------------
	// Group Query tests
	// ------------------------------------------------------------------------
	
	public void testFilterByGroupId() {
		Group group = identityService.createGroupQuery().groupId("camunda-admin").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals("camunda-admin", group.getId());
		assertEquals("camunda-admin", group.getName());
		assertEquals("SYSTEM", group.getType());

		group = identityService.createGroupQuery().groupId("whatever").singleResult();
		assertNull(group);
	}

	public void testFilterByChildGroupId() {
		Group group = identityService.createGroupQuery().groupId("root/child1").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals("root/child1", group.getId());
		assertEquals("child1", group.getName());
	}

	public void testFilterByUserId() {
		List<Group> result = identityService.createGroupQuery().groupMember("camunda@accso.de").list();
		assertEquals(1, result.size());
	}
	
	public void testFilterByUserIdMemberOfChildGroups() {
		List<Group> groups = identityService.createGroupQuery().groupMember("johnfoo@gmail.com").list();
		assertEquals(2, groups.size());
		
		for (Group group : groups) {
			if (!group.getId().equals("root/child1/subchild1") && !group.getId().equals("root/child2")) {
				fail();
			}
		}
		
	}

	public void testFilterByGroupTypeAndGroupId() {
		Group group = identityService.createGroupQuery().groupType("SYSTEM").groupId("cam-read-only").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals("cam-read-only", group.getId());
		assertEquals("cam-read-only", group.getName());
		assertEquals("SYSTEM", group.getType());
	}
	
	
	public void testFilterByGroupIdIn() {
		List<Group> groups = identityService.createGroupQuery()
				.groupIdIn("camunda-admin", "manager")
				.list();

		assertEquals(2, groups.size());
		for (Group group : groups) {
			if (!group.getName().equals("camunda-admin") && !group.getName().equals("manager")) {
				fail();
			}
		}
	}

	public void testFilterByChildGroupIdIn() {
		List<Group> groups = identityService.createGroupQuery()
				.groupIdIn("root/child1/subchild1", "root/child2")
				.list();

		assertEquals(2, groups.size());
		for (Group group : groups) {
			if (!group.getId().equals("root/child1/subchild1") && !group.getId().equals("root/child2")) {
				fail();
			}
		}
	}

	public void testFilterByGroupIdInAndType() {
		Group group = identityService.createGroupQuery()
				.groupIdIn("camunda-admin", "manager")
				.groupType("WORKFLOW")
				.singleResult();
		assertNotNull(group);
		assertEquals("manager", group.getName());
		
		group = identityService.createGroupQuery()
				.groupIdIn("camunda-admin", "manager")
				.groupType("SYSTEM")
				.singleResult();
		assertNotNull(group);
		assertEquals("camunda-admin", group.getName());
	}

	public void testFilterByGroupIdInAndUserId() {
		Group group = identityService.createGroupQuery()
				.groupIdIn("camunda-admin", "manager")
				.groupMember("camunda@accso.de")
				.singleResult();
		assertNotNull(group);
		assertEquals("camunda-admin", group.getName());
	}
	
	public void testFilterByGroupName() {
		Group group = identityService.createGroupQuery().groupName("manager").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals("manager", group.getId());
		assertEquals("manager", group.getName());

		group = identityService.createGroupQuery().groupName("whatever").singleResult();
		assertNull(group);
	}

	public void testFilterByGroupNameLike() {
		Group group = identityService.createGroupQuery().groupNameLike("manage*").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals("manager", group.getId());
		assertEquals("manager", group.getName());

		group = identityService.createGroupQuery().groupNameLike("what*").singleResult();
		assertNull(group);
	}
	
	public void testFilterByGroupNameAndGroupNameLike() {
		Group group = identityService.createGroupQuery().groupNameLike("ma*").groupName("manager").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals("manager", group.getId());
		assertEquals("manager", group.getName());
	}

	public void testFilterByGroupMember() {
		List<Group> list = identityService.createGroupQuery().groupMember("camunda@accso.de").list();
		assertEquals(1, list.size());
		list = identityService.createGroupQuery().groupMember("Gunnar.von-der-Beck@accso.de").list();
		assertEquals(2, list.size());
		list = identityService.createGroupQuery().groupMember("hans.mustermann@tradermail.info").list();
		assertEquals(1, list.size());
		list = identityService.createGroupQuery().groupMember("non-existing").list();
		assertEquals(0, list.size());
	}

	public void testOrderByGroupId() {
		List<Group> groupList = identityService.createGroupQuery().orderByGroupId().desc().list();
		assertEquals(9, groupList.size());
		assertTrue(groupList.get(0).getId().compareTo(groupList.get(1).getId()) > 0);
		assertTrue(groupList.get(1).getId().compareTo(groupList.get(2).getId()) > 0);
		assertTrue(groupList.get(2).getId().compareTo(groupList.get(3).getId()) > 0);
		assertTrue(groupList.get(5).getId().compareTo(groupList.get(6).getId()) > 0);
		assertTrue(groupList.get(6).getId().compareTo(groupList.get(7).getId()) > 0);
	}

	public void testOrderByGroupName() {
		List<Group> groupList = identityService.createGroupQuery().orderByGroupName().list();
		assertEquals(9, groupList.size());
		assertTrue(groupList.get(0).getName().compareTo(groupList.get(1).getName()) < 0);
		assertTrue(groupList.get(1).getName().compareTo(groupList.get(2).getName()) < 0);
		assertTrue(groupList.get(2).getName().compareTo(groupList.get(3).getName()) < 0);
		assertTrue(groupList.get(5).getName().compareTo(groupList.get(6).getName()) < 0);
		assertTrue(groupList.get(6).getName().compareTo(groupList.get(7).getName()) < 0);
	}

	// ------------------------------------------------------------------------
	// User Query tests
	// ------------------------------------------------------------------------

	public void testFilterUserByGroupId() {
		List<User> result = identityService.createUserQuery().memberOfGroup("teamlead").list();
		assertEquals(2, result.size());

		result = identityService.createUserQuery().memberOfGroup("non-exist").list();
		assertEquals(0, result.size());
	}

	public void testFilterUserByChildGroupId() {
		List<User> result = identityService.createUserQuery().memberOfGroup("root/child1/subchild1").list();
		assertEquals(1, result.size());
	}

	public void testFilterUserByGroupIdAndFirstname() {
		List<User> result = identityService.createUserQuery()
				.memberOfGroup("teamlead")
				.userFirstName("Gunnar")
				.list();
		assertEquals(1, result.size());
	}

	public void testFilterUserByGroupIdAndId() {
		List<User> result = identityService.createUserQuery()
				.memberOfGroup("teamlead")
				.userId("gunnar.von-der-beck@accso.de")
				.list();
		assertEquals(1, result.size());
	}

	public void testFilterUserByChildGroupIdAndId() {
		List<User> result = identityService.createUserQuery()
				.memberOfGroup("root/child1/subchild1")
				.userId("johnfoo@gmail.com")
				.list();
		assertEquals(1, result.size());
	}

	public void testFilterUserByGroupIdAndLastname() {
		List<User> result = identityService.createUserQuery()
				.memberOfGroup("teamlead")
				.userLastName("von der Beck")
				.list();
		assertEquals(1, result.size());
	}

	public void testFilterUserByGroupIdAndEmail() {
		List<User> result = identityService.createUserQuery()
				.memberOfGroup("teamlead")
				.userEmail("gunnar.von-der-beck@accso.de")
				.list();
		assertEquals(1, result.size());
	}

	public void testFilterUserByGroupIdAndEmailLike() {
		List<User> result = identityService.createUserQuery()
				.memberOfGroup("teamlead")
				.userEmailLike("*@accso.de")
				.list();
		assertEquals(1, result.size());
	}

	public void testFilterUserByGroupIdSimilarToClientName() {
		User user = identityService.createUserQuery().memberOfGroup("camunda-identity-service").singleResult();
		assertNotNull(user);
		assertEquals("identity.service@test.de", user.getId());
	}
	
}

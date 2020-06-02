package org.camunda.bpm.extension.keycloak.test;

import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;

import java.util.List;

import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.identity.Group;

/**
 * Tests group queries.
 */
public class KeycloakGroupQueryTest extends AbstractKeycloakIdentityProviderTest {

	public void testQueryNoFilter() {
		List<Group> groupList = identityService.createGroupQuery().list();
		assertEquals(9, groupList.size());
	}

	public void testQueryUnlimitedList() {
		List<Group> groupList = identityService.createGroupQuery().unlimitedList();
		assertEquals(9, groupList.size());
	}
	
	public void testQueryPaging() {
		// First page
		List<Group> result = identityService.createGroupQuery().listPage(0, 3);
		assertEquals(3, result.size());

		// Next page
		List<Group> resultNext = identityService.createGroupQuery().listPage(3, 3);
		assertEquals(3, resultNext.size());

		// Next page
		List<Group> resultLast = identityService.createGroupQuery().listPage(6, 10);
		assertEquals(3, resultLast.size());

		// unique results
		assertEquals(0, result.stream().filter(group -> resultNext.contains(group)).count());
		assertEquals(0, result.stream().filter(group -> resultLast.contains(group)).count());
	}

	public void testFilterByGroupId() {
		Group group = identityService.createGroupQuery().groupId(GROUP_ID_ADMIN).singleResult();
		assertNotNull(group);

		// validate result
		assertEquals(GROUP_ID_ADMIN, group.getId());
		assertEquals("camunda-admin", group.getName());
		assertEquals("SYSTEM", group.getType());

		group = identityService.createGroupQuery().groupId("whatever").singleResult();
		assertNull(group);
	}

	public void testFilterByUserId() {
		List<Group> result = identityService.createGroupQuery().groupMember("camunda@accso.de").list();
		assertEquals(1, result.size());
	}

	public void testAuthenticatedUserCanQueryOwnGroups() {
		try {
			processEngineConfiguration.setAuthorizationEnabled(true);
			identityService.setAuthenticatedUserId("johnfoo@gmail.com");

			assertEquals(0, identityService.createGroupQuery().groupMember("camunda@accso.de").count());
			assertEquals(2, identityService.createGroupQuery().groupMember("johnfoo@gmail.com").count());

		} finally {
			processEngineConfiguration.setAuthorizationEnabled(false);
			identityService.clearAuthentication();
		}
	}
	
	/* The REST API of Keycloak (get list(!) of groups) does not deliver group attributes :-(
	public void testFilterByGroupType() {
		List<Group> result = identityService.createGroupQuery().groupType("SYSTEM").list();
		assertEquals(2, result.size());
	}
	*/
	
	public void testFilterByGroupTypeAndGroupId() {
		Group group = identityService.createGroupQuery().groupType("SYSTEM").groupId(GROUP_ID_SYSTEM_READONLY).singleResult();
		assertNotNull(group);

		// validate result
		assertEquals(GROUP_ID_SYSTEM_READONLY, group.getId());
		assertEquals("cam-read-only", group.getName());
		assertEquals("SYSTEM", group.getType());
	}
	
	
	public void testFilterByGroupIdIn() {
		List<Group> groups = identityService.createGroupQuery()
				.groupIdIn(GROUP_ID_ADMIN, GROUP_ID_MANAGER)
				.list();

		assertEquals(2, groups.size());
		for (Group group : groups) {
			if (!group.getName().equals("camunda-admin") && !group.getName().equals("manager")) {
				fail();
			}
		}
	}

	public void testFilterByGroupIdInAndType() {
		Group group = identityService.createGroupQuery()
				.groupIdIn(GROUP_ID_ADMIN, GROUP_ID_MANAGER)
				.groupType("WORKFLOW")
				.singleResult();
		assertNotNull(group);
		assertEquals("manager", group.getName());
		
		group = identityService.createGroupQuery()
				.groupIdIn(GROUP_ID_ADMIN, GROUP_ID_MANAGER)
				.groupType("SYSTEM")
				.singleResult();
		assertNotNull(group);
		assertEquals("camunda-admin", group.getName());
	}

	public void testFilterByGroupIdInAndUserId() {
		Group group = identityService.createGroupQuery()
				.groupIdIn(GROUP_ID_ADMIN, GROUP_ID_MANAGER)
				.groupMember("camunda@accso.de")
				.singleResult();
		assertNotNull(group);
		assertEquals("camunda-admin", group.getName());
	}
	
	public void testFilterByGroupName() {
		Group group = identityService.createGroupQuery().groupName("manager").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals(GROUP_ID_MANAGER, group.getId());
		assertEquals("manager", group.getName());

		group = identityService.createGroupQuery().groupName("whatever").singleResult();
		assertNull(group);
	}

	public void testFilterByGroupNameLike() {
		Group group = identityService.createGroupQuery().groupNameLike("manage*").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals(GROUP_ID_MANAGER, group.getId());
		assertEquals("manager", group.getName());

		group = identityService.createGroupQuery().groupNameLike("what*").singleResult();
		assertNull(group);
	}
	
	public void testFilterByGroupNameAndGroupNameLike() {
		Group group = identityService.createGroupQuery().groupNameLike("ma*").groupName("manager").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals(GROUP_ID_MANAGER, group.getId());
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

	public void testOrderByGroupType() {
		List<Group> groupList = identityService.createGroupQuery().orderByGroupType().desc().list();
		assertEquals(9, groupList.size());
		assertTrue(groupList.get(0).getType().compareTo(groupList.get(1).getType()) >= 0);
		assertTrue(groupList.get(1).getType().compareTo(groupList.get(2).getType()) >= 0);
		assertTrue(groupList.get(2).getType().compareTo(groupList.get(3).getType()) >= 0);
		assertTrue(groupList.get(5).getType().compareTo(groupList.get(6).getType()) >= 0);
		assertTrue(groupList.get(6).getType().compareTo(groupList.get(7).getType()) >= 0);
	}

	protected void createGrantAuthorization(Resource resource, String resourceId, String userId, Permission... permissions) {
		Authorization authorization = createAuthorization(AUTH_TYPE_GRANT, resource, resourceId);
		authorization.setUserId(userId);
		for (Permission permission : permissions) {
			authorization.addPermission(permission);
		}
		authorizationService.saveAuthorization(authorization);
	}

	protected Authorization createAuthorization(int type, Resource resource, String resourceId) {
		Authorization authorization = authorizationService.createNewAuthorization(type);

		authorization.setResource(resource);
		if (resourceId != null) {
			authorization.setResourceId(resourceId);
		}

		return authorization;
	}

}

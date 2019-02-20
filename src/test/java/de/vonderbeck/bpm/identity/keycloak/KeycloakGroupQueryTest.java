package de.vonderbeck.bpm.identity.keycloak;

import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;

import java.util.List;

import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.identity.Group;

/**
 * Tests group queries.
 */
public class KeycloakGroupQueryTest extends KeycloakIdentityProviderTest {

	public void testQueryNoFilter() {
		List<Group> groupList = identityService.createGroupQuery().list();

		assertEquals(3, groupList.size());
	}

	public void testFilterByGroupId() {
		Group group = identityService.createGroupQuery().groupId("16527365-c79a-4f0e-bfcf-86770f1f27a6").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals("16527365-c79a-4f0e-bfcf-86770f1f27a6", group.getId());
		assertEquals("camunda-admin", group.getName());
		assertEquals("SYSTEM", group.getType());

		group = identityService.createGroupQuery().groupId("whatever").singleResult();
		assertNull(group);
	}

	public void testFilterByUserId() {
		List<Group> result = identityService.createGroupQuery().groupMember("camunda@accso.de").list();
		assertEquals(1, result.size());
	}

	public void testFilterByGroupIdIn() {
		List<Group> groups = identityService.createGroupQuery()
				.groupIdIn("16527365-c79a-4f0e-bfcf-86770f1f27a6", "09d9ba73-0eb7-4c02-99a3-b37438517826")
				.list();

		assertEquals(2, groups.size());
		for (Group group : groups) {
			if (!group.getName().equals("camunda-admin") && !group.getName().equals("management")) {
				fail();
			}
		}
	}

	public void testFilterByGroupIdInAndType() {
		Group group = identityService.createGroupQuery()
				.groupIdIn("16527365-c79a-4f0e-bfcf-86770f1f27a6", "09d9ba73-0eb7-4c02-99a3-b37438517826")
				.groupType("WORKFLOW")
				.singleResult();
		assertNotNull(group);
		assertEquals("management", group.getName());
		
		group = identityService.createGroupQuery()
				.groupIdIn("16527365-c79a-4f0e-bfcf-86770f1f27a6", "09d9ba73-0eb7-4c02-99a3-b37438517826")
				.groupType("SYSTEM")
				.singleResult();
		assertNotNull(group);
		assertEquals("camunda-admin", group.getName());
	}

	public void testFilterByGroupIdInAndUserId() {
		Group group = identityService.createGroupQuery()
				.groupIdIn("16527365-c79a-4f0e-bfcf-86770f1f27a6", "09d9ba73-0eb7-4c02-99a3-b37438517826")
				.groupMember("camunda@accso.de")
				.singleResult();
		assertNotNull(group);
		assertEquals("camunda-admin", group.getName());
	}
	
	public void testFilterByGroupName() {
		Group group = identityService.createGroupQuery().groupName("management").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals("09d9ba73-0eb7-4c02-99a3-b37438517826", group.getId());
		assertEquals("management", group.getName());

		group = identityService.createGroupQuery().groupName("whatever").singleResult();
		assertNull(group);
	}

	public void testFilterByGroupNameLike() {
		Group group = identityService.createGroupQuery().groupNameLike("manage*").singleResult();
		assertNotNull(group);

		// validate result
		assertEquals("09d9ba73-0eb7-4c02-99a3-b37438517826", group.getId());
		assertEquals("management", group.getName());

		group = identityService.createGroupQuery().groupNameLike("what*").singleResult();
		assertNull(group);
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

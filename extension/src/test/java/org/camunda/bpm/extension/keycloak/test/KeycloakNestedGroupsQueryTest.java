package org.camunda.bpm.extension.keycloak.test;

import java.util.List;

import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;

/**
 * Tests queries with focus on nested group hierarchy.
 */
public class KeycloakNestedGroupsQueryTest extends AbstractKeycloakIdentityProviderTest {

	public void testGroupQueryFilterByGroupIdRoot() {
		Group group = identityService.createGroupQuery().groupId(GROUP_ID_HIERARCHY_ROOT).singleResult();
		assertNotNull(group);
		assertEquals(GROUP_ID_HIERARCHY_ROOT, group.getId());
		assertEquals("root", group.getName());
	}

	public void testGroupQueryFilterByGroupIdChild() {
		Group group = identityService.createGroupQuery().groupId(GROUP_ID_HIERARCHY_CHILD1).singleResult();
		assertNotNull(group);
		assertEquals(GROUP_ID_HIERARCHY_CHILD1, group.getId());
		assertEquals("child1", group.getName());
	}

	public void testGroupQueryFilterByGroupIdSubChild() {
		Group group = identityService.createGroupQuery().groupId(GROUP_ID_HIERARCHY_SUBCHILD1).singleResult();
		assertNotNull(group);
		assertEquals(GROUP_ID_HIERARCHY_SUBCHILD1, group.getId());
		assertEquals("subchild1", group.getName());
	}

	
	public void testGroupQueryFilterByUserId() {
		List<Group> result = identityService.createGroupQuery().groupMember("johnfoo@gmail.com").list();
		assertEquals(2, result.size());
		assertEquals("expected johnfoo@gmail.com to member of group child2", 1, result.stream().filter(g -> g.getName().equals("child2")).count());
		assertEquals("expected johnfoo@gmail.com to member of group subchild1", 1, result.stream().filter(g -> g.getName().equals("subchild1")).count());
	}
	
	
	public void testGroupQueryFilterByGroupIdIn() {
		List<Group> groups = identityService.createGroupQuery()
				.groupIdIn(GROUP_ID_ADMIN, GROUP_ID_HIERARCHY_CHILD1)
				.list();
		assertEquals(2, groups.size());
		assertEquals("camunda-admin not found", 1, groups.stream().filter(g -> g.getName().equals("camunda-admin")).count());
		assertEquals("child1 not found", 1, groups.stream().filter(g -> g.getName().equals("child1")).count());
	}

	public void testGroupQueryFilterByGroupIdInAndUserId() {
		Group group = identityService.createGroupQuery()
				.groupIdIn(GROUP_ID_ADMIN, GROUP_ID_MANAGER, GROUP_ID_HIERARCHY_CHILD2, GROUP_ID_HIERARCHY_CHILD1)
				.groupMember("johnfoo@gmail.com")
				.singleResult();
		assertNotNull(group);
		assertEquals("child2", group.getName());
	}
	
	public void testGroupQueryFilterByGroupName() {
		Group group = identityService.createGroupQuery().groupName("child1").singleResult();
		assertNotNull(group);
		assertEquals(GROUP_ID_HIERARCHY_CHILD1, group.getId());
		assertEquals("child1", group.getName());
	}

	public void testGroupQueryFilterByGroupNameLike() {
		List<Group> result = identityService.createGroupQuery().groupNameLike("child*").list();
		assertEquals(2, result.size());
		assertEquals("expected group child1 to be included", 1, result.stream().filter(g -> g.getName().equals("child1")).count());
		assertEquals("expected group child2 to be included", 1, result.stream().filter(g -> g.getName().equals("child2")).count());

		result = identityService.createGroupQuery().groupNameLike("*child*").list();
		assertEquals(3, result.size());
		assertEquals("expected group child1 to be included", 1, result.stream().filter(g -> g.getName().equals("child1")).count());
		assertEquals("expected group child2 to be included", 1, result.stream().filter(g -> g.getName().equals("child2")).count());
		assertEquals("expected group subchild1 to be included", 1, result.stream().filter(g -> g.getName().equals("subchild1")).count());
	}
	
	public void testGroupQueryFilterByGroupNameAndGroupNameLike() {
		Group group = identityService.createGroupQuery().groupNameLike("child*").groupName("child2").singleResult();
		assertNotNull(group);
		assertEquals(GROUP_ID_HIERARCHY_CHILD2, group.getId());
		assertEquals("child2", group.getName());
	}

	public void testGroupQueryFilterByGroupMember() {
		List<Group> result = identityService.createGroupQuery().groupMember("johnfoo@gmail.com").list();
		assertEquals(2, result.size());
		assertEquals("expected johnfoo@gmail.com to member of group child2", 1, result.stream().filter(g -> g.getName().equals("child2")).count());
		assertEquals("expected johnfoo@gmail.com to member of group subchild1", 1, result.stream().filter(g -> g.getName().equals("subchild1")).count());
	}
	
	public void testUserQueryFilterByMemberOfGroup() {
		User user = identityService.createUserQuery().memberOfGroup(GROUP_ID_HIERARCHY_SUBCHILD1).singleResult();
		assertNotNull(user);
		assertEquals("johnfoo@gmail.com", user.getId());

		user = identityService.createUserQuery().memberOfGroup(GROUP_ID_HIERARCHY_CHILD2).singleResult();
		assertNotNull(user);
		assertEquals("johnfoo@gmail.com", user.getId());

		user = identityService.createUserQuery().memberOfGroup(GROUP_ID_HIERARCHY_CHILD1).singleResult();
		assertNull(user);
	}

}

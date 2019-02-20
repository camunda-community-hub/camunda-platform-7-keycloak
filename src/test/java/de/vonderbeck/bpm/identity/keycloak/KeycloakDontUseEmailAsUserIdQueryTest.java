package de.vonderbeck.bpm.identity.keycloak;

import java.util.List;

import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.test.ResourceProcessEngineTestCase;

/**
 * User query test for the Auth0 identity provider.
 */
public class KeycloakDontUseEmailAsUserIdQueryTest extends ResourceProcessEngineTestCase {

	public KeycloakDontUseEmailAsUserIdQueryTest() {
		super("camunda.dontUseEmailAsCamundaUserId.cfg.xml");
	}

	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
		Thread.sleep(250); // avoid HTTP 429 "too many requests" when calling free Auth0 API
	}

	// ------------------------------------------------------------------------
	// Authorization tests
	// ------------------------------------------------------------------------
	
	public void testAuth0LoginSuccess() {
		assertTrue(identityService.checkPassword("01cf2464-2233-4569-a354-cd5101171d7d", "camunda1!"));
	}

	// ------------------------------------------------------------------------
	// User Query tests
	// ------------------------------------------------------------------------
	
	public void testUserQueryFilterByUserId() {
		User user = identityService.createUserQuery().userId("9e7906b2-f67d-4937-97c6-d98791b7d790").singleResult();
		assertNotNull(user);

		user = identityService.createUserQuery().userId("01cf2464-2233-4569-a354-cd5101171d7d").singleResult();
		assertNotNull(user);

		// validate user
		assertEquals("01cf2464-2233-4569-a354-cd5101171d7d", user.getId());
		assertEquals("Admin", user.getFirstName());
		assertEquals("Camunda", user.getLastName());
		assertEquals("camunda@accso.de", user.getEmail());


		user = identityService.createUserQuery().userId("non-existing").singleResult();
		assertNull(user);
	}

	public void testUserQueryFilterByUserIdIn() {
		List<User> users = identityService.createUserQuery().userIdIn("01cf2464-2233-4569-a354-cd5101171d7d", "9e7906b2-f67d-4937-97c6-d98791b7d790").list();
		assertNotNull(users);
		assertEquals(2, users.size());

		users = identityService.createUserQuery().userIdIn("01cf2464-2233-4569-a354-cd5101171d7d", "non-existing").list();
		assertNotNull(users);
		assertEquals(1, users.size());
	}

	public void testUserQueryFilterByEmail() {
		User user = identityService.createUserQuery().userEmail("camunda@accso.de").singleResult();
		assertNotNull(user);

		// validate user
		assertEquals("01cf2464-2233-4569-a354-cd5101171d7d", user.getId());
		assertEquals("Admin", user.getFirstName());
		assertEquals("Camunda", user.getLastName());
		assertEquals("camunda@accso.de", user.getEmail());

		user = identityService.createUserQuery().userEmail("non-exist*").singleResult();
		assertNull(user);
	}

	public void testUserQueryFilterByGroupIdAndId() {
		List<User> result = identityService.createUserQuery()
				.memberOfGroup("16527365-c79a-4f0e-bfcf-86770f1f27a6")
				.userId("01cf2464-2233-4569-a354-cd5101171d7d")
				.list();
		assertEquals(1, result.size());

		result = identityService.createUserQuery()
				.memberOfGroup("16527365-c79a-4f0e-bfcf-86770f1f27a6")
				.userId("non-exist")
				.list();
		assertEquals(0, result.size());

		result = identityService.createUserQuery()
				.memberOfGroup("non-exist")
				.userId("01cf2464-2233-4569-a354-cd5101171d7d")
				.list();
		assertEquals(0, result.size());
		
	}

	public void testAuthenticatedUserSeesHimself() {
		try {
			processEngineConfiguration.setAuthorizationEnabled(true);

			identityService.setAuthenticatedUserId("non-existing");
			assertEquals(0, identityService.createUserQuery().count());

			identityService.setAuthenticatedUserId("01cf2464-2233-4569-a354-cd5101171d7d");
			assertEquals(1, identityService.createUserQuery().count());

		} finally {
			processEngineConfiguration.setAuthorizationEnabled(false);
			identityService.clearAuthentication();
		}
	}

	// ------------------------------------------------------------------------
	// Group query tests
	// ------------------------------------------------------------------------

	public void testGroupQueryFilterByUserId() {
		List<Group> result = identityService.createGroupQuery().groupMember("01cf2464-2233-4569-a354-cd5101171d7d").list();
		assertEquals(1, result.size());

		result = identityService.createGroupQuery().groupMember("non-exist").list();
		assertEquals(0, result.size());
	}

	public void testFilterByGroupIdAndUserId() {
		Group group = identityService.createGroupQuery()
				.groupId("16527365-c79a-4f0e-bfcf-86770f1f27a6")
				.groupMember("01cf2464-2233-4569-a354-cd5101171d7d")
				.singleResult();
		assertNotNull(group);
		assertEquals("camunda-admin", group.getName());

		group = identityService.createGroupQuery()
				.groupId("non-exist")
				.groupMember("01cf2464-2233-4569-a354-cd5101171d7d")
				.singleResult();
		assertNull(group);

		group = identityService.createGroupQuery()
				.groupId("16527365-c79a-4f0e-bfcf-86770f1f27a6")
				.groupMember("non-exist")
				.singleResult();
		assertNull(group);
	}
	
	public void testFilterByGroupIdInAndUserId() {
		Group group = identityService.createGroupQuery()
				.groupIdIn("16527365-c79a-4f0e-bfcf-86770f1f27a6", "09d9ba73-0eb7-4c02-99a3-b37438517826")
				.groupMember("01cf2464-2233-4569-a354-cd5101171d7d")
				.singleResult();
		assertNotNull(group);
		assertEquals("camunda-admin", group.getName());

		group = identityService.createGroupQuery()
				.groupIdIn("16527365-c79a-4f0e-bfcf-86770f1f27a6", "09d9ba73-0eb7-4c02-99a3-b37438517826")
				.groupMember("non-exist")
				.singleResult();
		assertNull(group);
	}
	
}

package org.camunda.bpm.extension.keycloak.test;

import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;

import java.util.List;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.identity.User;

/**
 * User query test for the Keycloak identity provider.
 */
public class KeycloakUserQueryTest extends AbstractKeycloakIdentityProviderTest {

  public void testQueryNoFilter() {
    List<User> result = identityService.createUserQuery().list();
    assertEquals(4, result.size());
  }

  public void testQueryPaging() {
	  // First page
	  List<User> result = identityService.createUserQuery().listPage(0, 2);
	  assertEquals(2, result.size());
	  
	  // Next page
	  List<User> resultNext = identityService.createUserQuery().listPage(2, 10);
	  assertEquals(2, resultNext.size());
	  
	  // unique results
	  assertEquals(0, result.stream().filter(user -> resultNext.contains(user)).count());
  }

  public void testFilterByUserId() {
    User user = identityService.createUserQuery().userId("camunda@accso.de").singleResult();
    assertNotNull(user);

    // validate user
    assertEquals("camunda@accso.de", user.getId());
    assertEquals("Admin", user.getFirstName());
    assertEquals("Camunda", user.getLastName());
    assertEquals("camunda@accso.de", user.getEmail());

    user = identityService.createUserQuery().userId("non-existing").singleResult();
    assertNull(user);
  }

  public void testFilterByUserIdIn() {
    List<User> users = identityService.createUserQuery().userIdIn("camunda@accso.de", "gunnar.von-der-beck@accso.de").list();
    assertNotNull(users);
    assertEquals(2, users.size());

    users = identityService.createUserQuery().userIdIn("camunda@accso.de", "non-existing").list();
    assertNotNull(users);
    assertEquals(1, users.size());
  }
  
  public void testFilterByFirstname() {
    User user = identityService.createUserQuery().userFirstName("Gunnar").singleResult();
    assertNotNull(user);

    user = identityService.createUserQuery().userFirstName("non-existing").singleResult();
    assertNull(user);
  }
  
  public void testFilterByFirstnameLike() {
    User user = identityService.createUserQuery().userFirstNameLike("Gun*").singleResult();
    assertNotNull(user);

    user = identityService.createUserQuery().userFirstNameLike("non-exist*").singleResult();
    assertNull(user);
  }

  public void testFilterByLastname() {
    User user = identityService.createUserQuery().userLastName("von der Beck").singleResult();
    assertNotNull(user);

    user = identityService.createUserQuery().userLastName("non-existing").singleResult();
    assertNull(user);
  }

  
  public void testFilterByLastnameLike() {
    User user = identityService.createUserQuery().userLastNameLike("von*").singleResult();
    assertNotNull(user);

    user = identityService.createUserQuery().userLastNameLike("non-exist*").singleResult();
    assertNull(user);
  }

  public void testFilterByFirstnameLikeAndLastnameLike() {
	  User user = identityService.createUserQuery()
			  .userFirstNameLike("*n%").userLastNameLike("von*")
			  .singleResult();
	  assertNotNull(user);

	  user = identityService.createUserQuery()
			  .userFirstNameLike("nox-exist*").userLastNameLike("von*")
			  .singleResult();
	  assertNull(user);

	  user = identityService.createUserQuery()
			  .userFirstNameLike("*n%").userLastNameLike("non-exist*")
			  .singleResult();
	  assertNull(user);
  }

  public void testFilterByFirstLastNameEmailUsingPartsOfNames() {
	  User user = identityService.createUserQuery()
			  .userFirstNameLike("Gun*").userLastNameLike("von*").userEmailLike("*accso.de")
			  .singleResult();
	  assertNotNull(user);

	  user = identityService.createUserQuery()
			  .userFirstName("Gun")
			  .singleResult();
	  assertNull(user);

	  user = identityService.createUserQuery()
			  .userLastName("von")
			  .singleResult();
	  assertNull(user);

	  user = identityService.createUserQuery()
			  .userEmail("accso.de")
			  .singleResult();
	  assertNull(user);
  }

  public void testFilterByFirstNameExactEmailLike() {
	  assertEquals(2, identityService.createUserQuery().userEmailLike("%accso.de").count());
	  
	  // Must deliver a single result
	  User user = identityService.createUserQuery()
			  .userFirstName("Gunnar").userEmailLike("%accso.de")
			  .singleResult();
	  assertNotNull(user);
  }
  
  public void testFilterByEmail() throws Exception {
    User user = identityService.createUserQuery().userEmail("camunda@accso.de").singleResult();
    assertNotNull(user);

    user = identityService.createUserQuery().userEmail("non-exist*").singleResult();
    assertNull(user);
  }

  public void testFilterByEmailLike() throws Exception {
    User user = identityService.createUserQuery().userEmailLike("camunda@*").singleResult();
    assertNotNull(user);
    user = identityService.createUserQuery().userEmailLike("camunda@%").singleResult();
    assertNotNull(user);
    
    List<User> users = identityService.createUserQuery().userEmailLike("%@accso.de").list();
    assertNotNull(users);
    assertEquals(2, users.size());

    user = identityService.createUserQuery().userEmailLike("non-exist*").singleResult();
    assertNull(user);
  }


  public void testFilterByGroupId() {
    List<User> result = identityService.createUserQuery().memberOfGroup(GROUP_ID_TEAMLEAD).list();
    assertEquals(2, result.size());
    
    result = identityService.createUserQuery().memberOfGroup("non-exist").list();
    assertEquals(0, result.size());
  }

 
  public void testFilterByGroupIdAndFirstname() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup(GROUP_ID_TEAMLEAD)
        .userFirstName("Gunnar")
        .list();
    assertEquals(1, result.size());
  }

  public void testFilterByGroupIdAndId() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup(GROUP_ID_TEAMLEAD)
        .userId("gunnar.von-der-beck@accso.de")
        .list();
    assertEquals(1, result.size());
  }

  public void testFilterByGroupIdAndLastname() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup(GROUP_ID_TEAMLEAD)
        .userLastName("von der Beck")
        .list();
    assertEquals(1, result.size());
  }

  public void testFilterByGroupIdAndEmail() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup(GROUP_ID_TEAMLEAD)
        .userEmail("gunnar.von-der-beck@accso.de")
        .list();
    assertEquals(1, result.size());
  }

  public void testFilterByGroupIdAndEmailLike() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup(GROUP_ID_TEAMLEAD)
        .userEmailLike("*@accso.de")
        .list();
    assertEquals(1, result.size());
  }
  
  public void testOrderByUserId() {
	  List<User> result = identityService.createUserQuery().orderByUserId().desc().list();
	  assertEquals(4, result.size());
	  assertTrue(result.get(0).getId().compareTo(result.get(1).getId()) > 0);
	  assertTrue(result.get(1).getId().compareTo(result.get(2).getId()) > 0);
  }

  public void testOrderByUserEmail() {
	  List<User> result = identityService.createUserQuery().orderByUserEmail().list();
	  assertEquals(4, result.size());
	  assertTrue(result.get(0).getEmail().compareTo(result.get(1).getEmail()) < 0);
	  assertTrue(result.get(1).getEmail().compareTo(result.get(2).getEmail()) < 0);
  }

  public void testOrderByUserFirstName() {
	  List<User> result = identityService.createUserQuery().orderByUserFirstName().desc().list();
	  assertEquals(4, result.size());
	  assertTrue(result.get(0).getFirstName().compareTo(result.get(1).getFirstName()) > 0);
	  assertTrue(result.get(1).getFirstName().compareTo(result.get(2).getFirstName()) > 0);
  }

  public void testOrderByUserLastName() {
	  List<User> result = identityService.createUserQuery().orderByUserLastName().list();
	  assertEquals(4, result.size());
	  assertTrue(result.get(0).getLastName().compareTo(result.get(1).getLastName()) < 0);
	  assertTrue(result.get(1).getLastName().compareTo(result.get(2).getLastName()) < 0);
  }
  
  public void testAuthenticatedUserSeesHimself() {
    try {
      processEngineConfiguration.setAuthorizationEnabled(true);

      identityService.setAuthenticatedUserId("non-existing");
      assertEquals(0, identityService.createUserQuery().count());

      identityService.setAuthenticatedUserId("camunda@accso.de");
      assertEquals(1, identityService.createUserQuery().count());

    } finally {
      processEngineConfiguration.setAuthorizationEnabled(false);
      identityService.clearAuthentication();
    }
  }

  public void testNativeQueryFail() {
    try {
      identityService.createNativeUserQuery();
      fail("Native queries are not supported in Keycloak case.");
    } catch (BadUserRequestException ex) {
      assertTrue("Wrong exception", ex.getMessage().contains("Native user queries are not supported for Keycloak"));
    }

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

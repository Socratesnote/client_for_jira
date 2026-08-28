package com.almworks.jira.provider3.sync.download2.meta;

import com.almworks.items.entities.api.Entity;
import com.almworks.items.entities.api.collector.transaction.EntityHolder;
import com.almworks.items.entities.api.collector.transaction.EntityTransaction;
import com.almworks.jira.provider3.sync.schema.ServerProject;
import com.almworks.jira.provider3.sync.schema.ServerProjectRole;
import com.almworks.util.tests.BaseTestCase;
import org.json.simple.JSONObject;

/**
 * Covers how a project role is identified, across the two halves that have to agree: the resolution declared on
 * {@link ServerProjectRole#TYPE}, and the {@code addEntity} call in {@link LoadProjects#storeRoles} that supplies the
 * identifying values. Neither file references the other, so nothing but a test holds them together - and landing one
 * half without the other breaks role loading entirely, with a single log line to show for it.
 * <p>
 * A role is identified by (id, project), and searchable by (name, project). Both are here, because both are reachable
 * from real data: the id form is what a project sync stores, and the name form is what a downloaded comment
 * visibility resolves through once SimpleDependent has stamped the issue's project onto it.
 * <p>
 * Everything here runs against a bare EntityTransaction with no database and no session. Search resolution is done by
 * the collector rather than by the write pass, so it needs no fixture; see EntityCollector2Tests in ItemEntities for
 * the generic coverage of that mechanism.
 */
public class LoadProjectsRolesTests extends BaseTestCase {
  private EntityTransaction myTransaction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTransaction = new EntityTransaction();
  }

  /**
   * The case the (id, project) identity exists for: Jira's built-in roles carry the same id in every project that
   * offers them. If the identity ever collapses back to the id alone, the second project's role resolves onto the
   * first project's entity and the project loaded last silently wins.
   */
  public void testTwoProjectsSharingARoleIdStoreTwoRoles() {
    EntityHolder projectOne = project(10000, "TESTPROJECT1");
    EntityHolder projectTwo = project(10001, "TESTPROJECT2");

    storeRoles(projectOne, 10000, roles("Administrators", 10002));
    storeRoles(projectTwo, 10001, roles("Administrators", 10002));

    EntityHolder roleOne = findRoleById(projectOne, 10002);
    EntityHolder roleTwo = findRoleById(projectTwo, 10002);
    assertNotSamePlace(roleOne, roleTwo);
    assertEquals("Administrators", roleOne.getScalarValue(ServerProjectRole.NAME));
    assertEquals("Administrators", roleTwo.getScalarValue(ServerProjectRole.NAME));
    assertSamePlace(projectOne, roleOne.getReference(ServerProjectRole.PROJECT));
    assertSamePlace(projectTwo, roleTwo.getReference(ServerProjectRole.PROJECT));
  }

  /**
   * The mirror image, and the reason the id stays part of the identity: a team-managed project mints its own ids, so
   * the same role name arrives under different ids and must not be merged by name within one project.
   */
  public void testTwoRoleIdsInOneProjectStoreTwoRoles() {
    EntityHolder project = project(10000, "TESTPROJECT1");

    storeRoles(project, 10000, roles("Administrators", 10002, "Developers", 10003));

    assertNotSamePlace(findRoleById(project, 10002), findRoleById(project, 10003));
    assertEquals("Administrators", findRoleById(project, 10002).getScalarValue(ServerProjectRole.NAME));
    assertEquals("Developers", findRoleById(project, 10003).getScalarValue(ServerProjectRole.NAME));
  }

  /**
   * Storing the same project twice - which a re-sync does - updates one role rather than accumulating duplicates.
   */
  public void testRestoringTheSameRoleReusesTheEntity() {
    EntityHolder project = project(10000, "TESTPROJECT1");

    storeRoles(project, 10000, roles("Administrators", 10002));
    EntityHolder first = findRoleById(project, 10002);
    storeRoles(project, 10000, roles("Renamed administrators", 10002));

    assertSamePlace(first, findRoleById(project, 10002));
    assertEquals("Renamed administrators", first.getScalarValue(ServerProjectRole.NAME));
  }

  /**
   * The search half. A downloaded comment visibility names its role but carries no id, so it can only resolve by
   * (name, project) - which is what SimpleDependent.stampRoleProject prepares it for. It has to land on the same
   * entity the project sync already stored by id, or the visibility points at a role nobody else knows about.
   */
  public void testARoleFoundByNameAndProjectIsTheRoleStoredById() {
    EntityHolder project = project(10000, "TESTPROJECT1");
    storeRoles(project, 10000, roles("Administrators", 10002));

    EntityHolder found = myTransaction.addEntity(
      new Entity(ServerProjectRole.TYPE)
        .put(ServerProjectRole.NAME, "Administrators")
        .put(ServerProjectRole.PROJECT, project.restore()));

    assertSamePlace(findRoleById(project, 10002), found);
  }

  /**
   * The same search restricted to the wrong project must not find the role, or a visibility on one project's comment
   * would resolve onto another project's role.
   */
  public void testARoleIsNotFoundByNameInAnotherProject() {
    EntityHolder projectOne = project(10000, "TESTPROJECT1");
    EntityHolder projectTwo = project(10001, "TESTPROJECT2");
    storeRoles(projectOne, 10000, roles("Administrators", 10002));

    EntityHolder found = myTransaction.addEntity(
      new Entity(ServerProjectRole.TYPE)
        .put(ServerProjectRole.NAME, "Administrators")
        .put(ServerProjectRole.PROJECT, projectTwo.restore()));

    assertNotSamePlace(findRoleById(projectOne, 10002), found);
  }

  /**
   * A role that names itself but no project - the shape a comment visibility has before SimpleDependent stamps the
   * issue's project onto it. Both resolutions name PROJECT, and the search ANDs one clause per key, so this entity
   * satisfies neither.
   * <p>
   * TODO(human): assert what the collector actually does with it.
   */
  public void testARoleWithoutAProjectDoesNotIdentify() {
    project(10000, "TESTPROJECT1");

    EntityHolder nameOnly = myTransaction.addEntity(
      new Entity(ServerProjectRole.TYPE).put(ServerProjectRole.NAME, "Administrators"));

    // TODO(human)
  }

  // Drives the real call site. The session is only reached through the membership loader, which is null on a
  // LoadProjects that was constructed rather than run, so no session is needed.
  private void storeRoles(EntityHolder project, int projectId, JSONObject roles) {
    new LoadProjects(myTransaction, false).storeRoles(null, project, projectId, roles);
  }

  // Builds the roles map in the shape a project GET returns it: role name to the role's self URL, whose last segment
  // is the id.
  private static JSONObject roles(Object... nameAndIdPairs) {
    JSONObject roles = new JSONObject();
    for (int i = 0; i < nameAndIdPairs.length; i += 2)
      roles.put(nameAndIdPairs[i], "https://example.atlassian.net/rest/api/3/project/10000/role/" + nameAndIdPairs[i + 1]);
    return roles;
  }

  private EntityHolder project(int id, String key) {
    EntityHolder project = ServerProject.project(myTransaction, id);
    assertNotNull(project);
    project.setValue(ServerProject.KEY, key);
    return project;
  }

  // Resolves a role the way storeRoles created it. Asserts non-null so a failed identification shows up as this
  // lookup rather than as a null dereference in the caller.
  private EntityHolder findRoleById(EntityHolder project, int id) {
    EntityHolder role = myTransaction.addEntity(ServerProjectRole.TYPE, ServerProjectRole.PROJECT, project, ServerProjectRole.ID, id);
    assertNotNull(role);
    return role;
  }

  // EntityHolder is a fresh wrapper on every call and declares no equals(), so two holders that both resolve to the
  // same stored entity are still distinct objects: assertSame/assertNotSame on the holders themselves would compare
  // wrapper identity, not entity identity. Compare the underlying EntityPlace instead, following
  // EntityCollector2Tests.assertSamePlace in ItemEntities.
  private void assertSamePlace(EntityHolder expected, EntityHolder actual) {
    assertEquals(expected.getPlace().getIndex(), actual.getPlace().getIndex());
  }

  private void assertNotSamePlace(EntityHolder one, EntityHolder other) {
    assertTrue(one.getPlace().getIndex() != other.getPlace().getIndex());
  }
}

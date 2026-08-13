package com.almworks.jira.provider3.sync.download2.meta;

import com.almworks.items.entities.api.collector.transaction.EntityHolder;
import com.almworks.items.entities.api.collector.transaction.EntityTransaction;
import com.almworks.jira.connector2.JiraInternalException;
import com.almworks.jira.provider3.sync.schema.ServerComponent;
import com.almworks.jira.provider3.sync.schema.ServerProject;
import com.almworks.jira.provider3.sync.schema.ServerProjectRole;
import com.almworks.jira.provider3.sync.schema.ServerVersion;
import com.almworks.util.Pair;
import com.almworks.util.tests.BaseTestCase;

import java.util.Arrays;

/**
 * Covers what {@link ResolutionProblems#prepare()} decides to re-download when the writer hands it an entity it could
 * not create. Two independent decisions come out of it: whether the brief project list has to be re-read, and which
 * projects have to be re-read in full.
 * <p>
 * Project roles are the reason this class changed. They are identified by (id, project) like a version or a
 * component, since neither their id nor their name is unique on its own, so an unresolvable role now costs one
 * project's reload rather than a full reload of every configured project.
 * <p>
 * There is deliberately no test for a role that arrives without a project. Such an entity cannot exist as a problem:
 * a place is only created for a row satisfying at least one of the type's resolutions, and both role resolutions -
 * (id, project) and the (name, project) search - name the project. A role built from comment visibility carries only
 * a name and so fails to identify at collection time, long before it could reach prepare().
 * <p>
 * Everything here runs against a bare EntityTransaction with no database and no session.
 */
public class ResolutionProblemsTests extends BaseTestCase {
  private EntityTransaction myTransaction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTransaction = new EntityTransaction();
  }

  /**
   * A version problem names its project, so only that project is reloaded. This is the shape the role case is being
   * moved onto, so it is worth reading first.
   */
  public void testVersionProblemMarksOnlyItsProject() throws JiraInternalException {
    EntityHolder project = project(10000, "TESTPROJECT1");
    EntityHolder version = myTransaction.addEntity(ServerVersion.TYPE, ServerVersion.PROJECT, project, ServerVersion.ID, 10100);
    assertNotNull(version);

    ResolutionProblems problems = prepare(version);

    assertFalse(problems.isReloadProjectList());
    assertEquals(1, problems.getFullProjects().size());
    assertProject(10000, "TESTPROJECT1", problems.getFullProjects().get(0));
  }

  public void testComponentProblemMarksOnlyItsProject() throws JiraInternalException {
    EntityHolder project = project(10001, "TESTPROJECT2");
    EntityHolder component = myTransaction.addEntity(ServerComponent.TYPE, ServerComponent.PROJECT, project, ServerComponent.ID, 10200);
    assertNotNull(component);

    ResolutionProblems problems = prepare(component);

    assertFalse(problems.isReloadProjectList());
    assertEquals(1, problems.getFullProjects().size());
    assertProject(10001, "TESTPROJECT2", problems.getFullProjects().get(0));
  }

  /**
   * A role stored by LoadProjects.storeRoles always carries its project, so an unresolvable one costs a single project's reload rather than a full reload of every configured project.
   */
  public void testRoleWithAProjectMarksOnlyThatProject() throws JiraInternalException {
    EntityHolder project = project(10000, "TESTPROJECT1");
    EntityHolder role = role(project, 10002, "Administrators");
    assertNotNull(role);

    ResolutionProblems problems = prepare(role);

    assertFalse(problems.isReloadProjectList());
    assertEquals(1, problems.getFullProjects().size());
    assertProject(10000, "TESTPROJECT1", problems.getFullProjects().get(0));
  }

  /**
   * Two projects reporting the same role id are two distinct problems, and each names its own project. If role identity ever collapses back to the id alone, this is where it shows: the second addEntity would return the first project's holder and only one project would be marked.
   */
  public void testTwoProjectsSharingARoleIdMarkBothProjects() throws JiraInternalException {
    EntityHolder testProject1 = project(10000, "TESTPROJECT1");
    EntityHolder testProject2 = project(10001, "TESTPROJECT2");
    EntityHolder testProject1Role = role(testProject1, 10002, "Administrators");
    EntityHolder testProject2Role = role(testProject2, 10002, "Administrators");
    assertNotSame(testProject1Role, testProject2Role);

    ResolutionProblems problems = prepare(testProject1Role, testProject2Role);

    assertFalse(problems.isReloadProjectList());
    assertEquals(2, problems.getFullProjects().size());
    assertProject(10000, "TESTPROJECT1", problems.getFullProjects().get(0));
    assertProject(10001, "TESTPROJECT2", problems.getFullProjects().get(1));
  }

  /**
   * A project that could not be created needs only the brief project list, not a full load of everything.
   */
  public void testProjectProblemAsksForABriefLoad() throws JiraInternalException {
    EntityHolder project = project(10000, "TESTPROJECT1");

    ResolutionProblems problems = prepare(project);
    assertTrue(problems.isReloadProjectList());
    assertTrue(problems.getFullProjects().isEmpty());
  }

  /**
   * The two decisions are independent and both are recorded: a project problem asks for the brief list of every
   * project, a version problem asks for its own project in full. Neither overwrites the other.
   */
  public void testAProjectAndAVersionProblemRecordBothDecisions() throws JiraInternalException {
    EntityHolder project = project(10000, "TESTPROJECT1");
    EntityHolder version = myTransaction.addEntity(ServerVersion.TYPE, ServerVersion.PROJECT, project, ServerVersion.ID, 10100);
    assertNotNull(version);

    ResolutionProblems problems = prepare(project, version);

    assertTrue(problems.isReloadProjectList());
    assertEquals(1, problems.getFullProjects().size());
    assertProject(10000, "TESTPROJECT1", problems.getFullProjects().get(0));
  }

  /** Two problems in the same project ask for it once. */
  public void testTheSameProjectIsMarkedOnce() throws JiraInternalException {
    EntityHolder project = project(10000, "TESTPROJECT1");
    EntityHolder version = myTransaction.addEntity(ServerVersion.TYPE, ServerVersion.PROJECT, project, ServerVersion.ID, 10100);
    EntityHolder role = role(project, 10002, "Administrators");

    ResolutionProblems problems = prepare(version, role);

    assertEquals(1, problems.getFullProjects().size());
  }

  // The session, server info and meta config are only read by resolve(), which goes to the network; prepare() reads nothing but the problem entities, so nulls are enough to drive it.
  private static ResolutionProblems prepare(EntityHolder... problems) throws JiraInternalException {
    ResolutionProblems resolution = new ResolutionProblems(null, null, null);
    resolution.addAll(Arrays.asList(problems));
    resolution.prepare();
    return resolution;
  }

  private EntityHolder project(int id, String key) {
    EntityHolder project = ServerProject.project(myTransaction, id);
    assertNotNull(project);
    project.setValue(ServerProject.KEY, key);
    return project;
  }

  // A role as LoadProjects.storeRoles stores it: identified by (id, project).
  private EntityHolder role(EntityHolder project, int id, String name) {
    EntityHolder role = myTransaction.addEntity(ServerProjectRole.TYPE, ServerProjectRole.PROJECT, project, ServerProjectRole.ID, id);
    assertNotNull(role);
    role.setValue(ServerProjectRole.NAME, name);
    return role;
  }

  private static void assertProject(Integer expectedId, String expectedKey, Pair<Integer, String> actual) {
    assertEquals(expectedId, actual.getFirst());
    assertEquals(expectedKey, actual.getSecond());
  }
}

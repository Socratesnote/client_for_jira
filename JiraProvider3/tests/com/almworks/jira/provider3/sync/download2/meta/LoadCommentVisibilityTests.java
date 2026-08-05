package com.almworks.jira.provider3.sync.download2.meta;

import com.almworks.restconnector.operations.LoadUserInfo;
import com.almworks.util.tests.BaseTestCase;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Arrays;
import java.util.List;

/**
 * Covers the group list offered for comment and worklog visibility, now sourced from the current user's own groups
 * rather than scraped from the Server-era Add Comment page.
 * <p>
 * The property worth pinning is the three-way contract the caller depends on. `LoadRestMeta.loadCommentsVisibility`
 * returns early on null and leaves the stored configuration alone, but writes `PROJECT_ROLES_ONLY` from an empty list.
 * Collapsing "could not ask" into "the answer is none" would therefore record a definite configuration on the strength
 * of a failed request - which is how the scrape used to behave, silently, on every sync.
 */
public class LoadCommentVisibilityTests extends BaseTestCase {
  @SuppressWarnings("unchecked")
  private static JSONObject group(String name, String groupId) {
    JSONObject group = new JSONObject();
    if (name != null) group.put("name", name);
    if (groupId != null) group.put("groupId", groupId);
    return group;
  }

  /** The api/3/myself?expand=groups shape, captured live on 2026-08-05. */
  @SuppressWarnings("unchecked")
  private static LoadUserInfo me(JSONObject... groups) {
    JSONObject user = new JSONObject();
    user.put("accountId", "557058:3b0ee120-c538-4e30-bed4-69137bbde875");
    JSONArray items = new JSONArray();
    items.addAll(Arrays.asList(groups));
    JSONObject groupsObject = new JSONObject();
    groupsObject.put("size", groups.length);
    groupsObject.put("items", items);
    user.put("groups", groupsObject);
    return new LoadUserInfo(user);
  }

  public void testGroupNamesAreCollected() {
    List<String> names = LoadCommentVisibility.collectGroupNames(
      me(group("axe_users", "83cd28ca-b6fe-48cb-92d3-182eee20cb8f"),
         group("site-admins", "b670f89b-de12-4a58-a5f4-ac3c72d7d218")));
    assertEquals(Arrays.asList("axe_users", "site-admins"), names);
  }

  /**
   * The name is stored as Jira reports it. The visibility payload carries the group in its "value" field in that same
   * form, so a downloaded visibility resolves against this list only while the two agree.
   */
  public void testNameCaseIsPreserved() {
    List<String> names = LoadCommentVisibility.collectGroupNames(me(group("AXE_Users", "83cd28ca")));
    assertEquals(Arrays.asList("AXE_Users"), names);
  }

  /**
   * A failed request must not look like a definite "no groups". The caller writes PROJECT_ROLES_ONLY from an empty
   * list but does nothing at all on null, so this is the difference between recording a configuration and leaving it.
   */
  public void testFailureToLoadTheUserIsNull() {
    assertNull(LoadCommentVisibility.collectGroupNames(null));
  }

  // A user who belongs to no group is a definite answer: only project roles can be offered.
  public void testUserWithNoGroupsIsEmptyNotNull() {
    List<String> names = LoadCommentVisibility.collectGroupNames(me());
    assertNotNull(names);
    assertTrue(names.isEmpty());
  }

  /**
   * A user loaded without the groups expansion has no "groups" object at all. That is indistinguishable from having
   * no groups here, which is why only `loadMeWithGroups` may be used as the source - noted so a future caller does
   * not substitute a plainly-loaded user and quietly turn every account into "project roles only".
   */
  @SuppressWarnings("unchecked")
  public void testUserLoadedWithoutTheExpansionYieldsEmpty() {
    JSONObject user = new JSONObject();
    user.put("accountId", "557058:3b0ee120-c538-4e30-bed4-69137bbde875");
    List<String> names = LoadCommentVisibility.collectGroupNames(new LoadUserInfo(user));
    assertNotNull(names);
    assertTrue(names.isEmpty());
  }

  public void testGroupWithoutANameIsSkipped() {
    List<String> names = LoadCommentVisibility.collectGroupNames(
      me(group(null, "83cd28ca"), group("axe_users", "b670f89b")));
    assertEquals(Arrays.asList("axe_users"), names);
  }

  public void testDuplicateNamesCollapse() {
    List<String> names = LoadCommentVisibility.collectGroupNames(
      me(group("axe_users", "83cd28ca"), group("axe_users", "b670f89b")));
    assertEquals(Arrays.asList("axe_users"), names);
  }
}

package com.almworks.jira.provider3.sync.download2.meta;

import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Collections15;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.Arrays;
import java.util.List;

/**
 * Covers who the visibility picker may offer a project role to. Jira rejects a comment or worklog restricted to a
 * role its author does not belong to, so an unusable role must not be offered - but the property that actually
 * matters here is the other direction: an answer that could not be established has to stay null and keep the role
 * on offer. The actor endpoint needs project-administration permission, so an ordinary user can be refused it for
 * roles they use every day, and a null read as "not a member" would hide the picker's contents from exactly those
 * users. Membership is also inherited through group actors, which is the second way a naive check hides a usable
 * role.
 */
public class RoleMembershipTests extends BaseTestCase {
  private static final String ME = "557058:3b0ee120-c538-4e30-bed4-69137bbde875";

  /** Captured live on 2026-08-03: GET api/3/project/AXE/role/10072, the Administrator role of project 10012. */
  private static final String CAPTURED_ROLE =
    "{\"self\":\"https://example.atlassian.net/rest/api/3/project/10012/role/10072\",\"name\":\"Administrator\"," +
    "\"id\":10072,\"description\":\"Admins can do most things.\"," +
    "\"actors\":[{\"id\":10389,\"displayName\":\"Thomas P\",\"type\":\"atlassian-user-role-actor\"," +
    "\"actorUser\":{\"accountId\":\"" + ME + "\"}}]," +
    "\"scope\":{\"type\":\"PROJECT\",\"project\":{\"id\":\"10012\"}}}";

  private static JSONObject parse(String json) throws ParseException {
    return (JSONObject) new JSONParser().parse(json);
  }

  @SuppressWarnings("unchecked")
  private static JSONObject group(String groupId, String name) {
    JSONObject group = new JSONObject();
    if (groupId != null) group.put("groupId", groupId);
    if (name != null) group.put("name", name);
    return group;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject userActor(String accountId) {
    JSONObject actor = new JSONObject();
    actor.put("type", "atlassian-user-role-actor");
    JSONObject user = new JSONObject();
    user.put("accountId", accountId);
    actor.put("actorUser", user);
    return actor;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject groupActor(String groupId, String name) {
    JSONObject actor = new JSONObject();
    actor.put("type", "atlassian-group-role-actor");
    actor.put("actorGroup", group(groupId, name));
    return actor;
  }

  /** A role payload carrying exactly these actors. */
  @SuppressWarnings("unchecked")
  private static JSONObject role(JSONObject... actors) {
    JSONObject role = new JSONObject();
    role.put("name", "Administrator");
    role.put("id", 10072);
    JSONArray array = new JSONArray();
    array.addAll(Arrays.asList(actors));
    role.put("actors", array);
    return role;
  }

  /** A role payload with no actor list at all, which is a shape the client cannot draw a conclusion from. */
  @SuppressWarnings("unchecked")
  private static JSONObject roleWithoutActors() {
    JSONObject role = new JSONObject();
    role.put("name", "Administrator");
    role.put("id", 10072);
    return role;
  }

  private static RoleMembership me(JSONObject... myGroups) {
    return new RoleMembership(ME, Arrays.<JSONObject>asList(myGroups));
  }

  public void testCapturedPayloadNamesTheCurrentUser() throws ParseException {
    assertEquals(Boolean.TRUE, me().isCurrentUserMember(parse(CAPTURED_ROLE)));
  }

  public void testCapturedPayloadDoesNotNameAnotherUser() throws ParseException {
    List<JSONObject> noGroups = Collections15.emptyList();
    RoleMembership someoneElse = new RoleMembership("557058:0000-different", noGroups);
    assertEquals(Boolean.FALSE, someoneElse.isCurrentUserMember(parse(CAPTURED_ROLE)));
  }

  public void testUserActorOfSomeoneElseIsNotMembership() {
    assertEquals(Boolean.FALSE, me().isCurrentUserMember(role(userActor("557058:0000-different"))));
  }

  /**
   * The failure this part exists to avoid in the first place: a user in an actor group is a member of the role
   * without appearing as a user actor of it, so matching on accountId alone would hide a role they can use.
   */
  public void testGroupActorMatchedByGroupId() {
    RoleMembership membership = me(group("g-1", "axe-users"));
    assertEquals(Boolean.TRUE, membership.isCurrentUserMember(role(groupActor("g-1", "axe-users"))));
  }

  // Older payloads carry a group name and no id, so the name has to remain a usable match.
  public void testGroupActorMatchedByNameWhenNoIdIsGiven() {
    RoleMembership membership = me(group(null, "axe-users"));
    assertEquals(Boolean.TRUE, membership.isCurrentUserMember(role(groupActor(null, "axe-users"))));
  }

  /**
   * A group deleted and recreated under the same name gets a new id, so id and name can disagree. Accepting the
   * name match leaves the role on offer, which is the harmless direction: the alternative hides a role the user
   * may well still be able to use.
   */
  public void testGroupActorMatchedByNameDespiteADifferentId() {
    RoleMembership membership = me(group("g-old", "axe-users"));
    assertEquals(Boolean.TRUE, membership.isCurrentUserMember(role(groupActor("g-new", "axe-users"))));
  }

  public void testGroupActorTheUserIsNotInIsNotMembership() {
    RoleMembership membership = me(group("g-1", "axe-users"));
    assertEquals(Boolean.FALSE, membership.isCurrentUserMember(role(groupActor("g-2", "other-group"))));
  }

  public void testUserActorFoundAmongSeveralActors() {
    RoleMembership membership = me(group("g-1", "axe-users"));
    JSONObject payload = role(groupActor("g-2", "other-group"), userActor(ME));
    assertEquals(Boolean.TRUE, membership.isCurrentUserMember(payload));
  }

  // A role with an empty actor list genuinely has no members, so this is an answer and not an unknown.
  public void testEmptyActorListIsADefiniteNo() {
    assertEquals(Boolean.FALSE, me().isCurrentUserMember(role()));
  }

  /**
   * The degradation rule, and the reason it is a rule rather than a detail. A 403 from the actor endpoint arrives
   * as a null payload, and reading that as "not a member" would empty the picker for every non-administrator.
   */
  public void testUnreadableRoleIsUnknown() {
    assertNull(me().isCurrentUserMember((JSONObject) null));
  }

  public void testRoleWithoutAnActorListIsUnknown() {
    assertNull(me().isCurrentUserMember(roleWithoutActors()));
  }

  /**
   * A group actor the client cannot identify is the same kind of gap as a 403: the user may reach the role
   * through precisely that group, so it cannot be answered with a no. This is the case that would otherwise
   * hide roles rather than merely offer too many, which is the direction that matters.
   */
  public void testUnreadableGroupActorIsUnknown() {
    assertNull(me(group("g-1", "axe-users")).isCurrentUserMember(role(groupActor(null, null))));
  }

  @SuppressWarnings("unchecked")
  public void testGroupActorWithoutAGroupObjectIsUnknown() {
    JSONObject actor = new JSONObject();
    actor.put("type", "atlassian-group-role-actor");
    assertNull(me().isCurrentUserMember(role(actor)));
  }

  // A definite yes is still a yes: an unreadable actor elsewhere in the list cannot demote it to unknown.
  public void testUserActorWinsOverAnUnreadableGroupActor() {
    JSONObject payload = role(groupActor(null, null), userActor(ME));
    assertEquals(Boolean.TRUE, me().isCurrentUserMember(payload));
  }

  // A user actor that is not the current user is not "unreadable" - it is a plain no.
  public void testUnknownUserActorDoesNotMakeTheAnswerUnknown() {
    assertEquals(Boolean.FALSE, me().isCurrentUserMember(role(userActor("557058:0000-different"))));
  }
}

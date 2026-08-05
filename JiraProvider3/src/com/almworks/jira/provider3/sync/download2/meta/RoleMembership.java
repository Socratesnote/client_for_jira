package com.almworks.jira.provider3.sync.download2.meta;

import com.almworks.jira.provider3.sync.download2.rest.JRProjectRole;
import com.almworks.jira.provider3.sync.download2.rest.RestOperations;
import com.almworks.restconnector.RestSession;
import com.almworks.restconnector.operations.LoadUserInfo;
import com.almworks.util.LogHelper;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.Set;

/**
 * Answers whether the current user is a member of a project role. Jira rejects a comment or worklog restricted
 * to a role its author does not belong to, so this is what decides which roles the visibility picker may offer.
 * <p>
 * Every answer is a nullable Boolean and null means "could not be established", never "not a member". Two things
 * make that distinction load-bearing: the actor endpoint needs project-administration permission, so an ordinary
 * user may be refused it for roles they can perfectly well use, and the membership of a role is inherited through
 * group actors as well as user actors. An unknown role stays on offer - hiding a usable role would turn a
 * "too many options" bug into a "missing options" one, which is worse.
 */
class RoleMembership {
  private static final String GROUP_ROLE_ACTOR = "atlassian-group-role-actor";

  private final String myAccountId;
  private final Set<String> myGroupIds = Collections15.hashSet();
  private final Set<String> myGroupNames = Collections15.hashSet();

  /**
   * @param groups the current user's own groups, each carrying a {@code groupId} and a {@code name}
   */
  RoleMembership(String accountId, List<JSONObject> groups) {
    myAccountId = accountId;
    for (JSONObject group : groups) {
      String id = JRProjectRole.ACTOR_GROUP_ID.getValue(group);
      String name = JRProjectRole.ACTOR_GROUP_NAME.getValue(group);
      if (id != null) myGroupIds.add(id);
      if (name != null) myGroupNames.add(name);
    }
  }

  /**
   * @return null when the current user cannot be identified, which makes every membership question unanswerable
   */
  @Nullable
  static RoleMembership load(RestSession session) {
    LoadUserInfo me = LoadUserInfo.loadMeWithGroups(session);
    if (me == null) return null;
    String accountId = me.getAccountId();
    if (accountId == null || accountId.isEmpty()) {
      LogHelper.warning("Missing accountId of the current user, role membership stays unknown");
      return null;
    }
    return new RoleMembership(accountId, me.getGroups());
  }

  /**
   * @return null when the role's actors cannot be read
   */
  @Nullable
  Boolean isCurrentUserMember(RestSession session, int projectId, int roleId) {
    return isCurrentUserMember(RestOperations.projectRole(session, projectId, roleId));
  }

  /**
   * @param role the payload of one project role, or null when it could not be loaded
   * @return null when the answer cannot be established
   */
  @Nullable
  Boolean isCurrentUserMember(@Nullable JSONObject role) {
    if (role == null) return null;
    // An absent actor list is a payload the client does not understand, so it is unknown. An empty one is an
    // answer: the role has no members.
    if (!role.containsKey(JRProjectRole.ACTORS_KEY)) {
      LogHelper.warning("No actor list on project role", role.get("id"), role.get("name"));
      return null;
    }
    boolean unreadableGroupActor = false;
    for (JSONObject actor : JRProjectRole.ACTORS.list(role)) {
      JSONObject user = JRProjectRole.ACTOR_USER.getValue(actor);
      if (user != null && myAccountId.equals(JRProjectRole.ACTOR_ACCOUNT_ID.getValue(user))) return true;
      JSONObject group = JRProjectRole.ACTOR_GROUP.getValue(actor);
      if (group != null && isCurrentUserGroup(group)) return true;
      if (isUnreadableGroupActor(actor, group)) unreadableGroupActor = true;
    }
    // A group actor whose group cannot be read is not evidence of anything, and "not a member" has to be a
    // positive finding: the user may well reach this role through exactly that group. Saying no here is what
    // would turn an unusable-role bug into a hidden-role one.
    if (unreadableGroupActor) return null;
    return false;
  }

  // True for an actor that names a group the client cannot identify, either because the group object is absent
  // or because it carries neither an id nor a name under the keys we know.
  private static boolean isUnreadableGroupActor(JSONObject actor, @Nullable JSONObject group) {
    if (!GROUP_ROLE_ACTOR.equals(JRProjectRole.ACTOR_TYPE.getValue(actor))) return false;
    if (group == null) return true;
    return JRProjectRole.ACTOR_GROUP_ID.getValue(group) == null && JRProjectRole.ACTOR_GROUP_NAME.getValue(group) == null;
  }

  // Matches on either the group id or the group name. Names are not stable identity - deleting a group and
  // recreating it with the same name yields a different id - but a name match that the id contradicts only
  // errs towards offering the role, which is the harmless direction here.
  private boolean isCurrentUserGroup(JSONObject group) {
    String id = JRProjectRole.ACTOR_GROUP_ID.getValue(group);
    if (id != null && myGroupIds.contains(id)) return true;
    String name = JRProjectRole.ACTOR_GROUP_NAME.getValue(group);
    return name != null && myGroupNames.contains(name);
  }
}

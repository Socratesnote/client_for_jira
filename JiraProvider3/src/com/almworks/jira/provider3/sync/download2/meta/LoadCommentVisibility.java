package com.almworks.jira.provider3.sync.download2.meta;

import com.almworks.api.connector.ConnectorException;
import com.almworks.restconnector.RestSession;
import com.almworks.restconnector.json.JsonKey;
import com.almworks.restconnector.operations.LoadUserInfo;
import com.almworks.util.LogHelper;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The groups a comment or worklog may be restricted to.
 * <p>
 * This used to scrape {@code select[name=commentLevel]} out of the Server-era {@code secure/AddComment!default.jspa}
 * page. On Cloud that URL answers {@code 200 OK} with HTML containing none of those markers, so the scrape yielded no
 * groups and never visibly failed - the group picker was permanently empty and no Jira configuration could change it.
 * <p>
 * The replacement is the **current user's own groups**, which is what the Server page's selector listed. Two site-wide
 * alternatives were rejected: {@code api/3/group/bulk} and {@code api/3/groups/picker} both return every group on the
 * site. This can include even Confluence, Bitbucket and Compass groups. Offering a group the author is not in reproduces, for groups, the failed-upload bug that the role membership filter exists to prevent. Prefer widening this only with evidence that Jira accepts such a value.
 */
class LoadCommentVisibility {
  private static final JsonKey<String> GROUP_NAME = JsonKey.text("name");

  /**
   * @return Null if the group list could not be established at all<br>
   * Empty list if only project roles can be offered<br>
   * Not empty list of group names if groups are allowed for comment visibility
   */
  @Nullable
  public static List<String> loadCommentVisibilityGroups(RestSession session) throws ConnectorException {
    return collectGroupNames(LoadUserInfo.loadMeWithGroups(session));
  }

  /**
   * Kept separate from the request so the null-versus-empty contract can be tested without a server. The distinction
   * is load-bearing for the caller: null leaves the stored configuration untouched, while empty positively means
   * "project roles only" and is written to the connection.
   *
   * @param me the current user loaded with their groups expanded, or null when that failed
   */
  @Nullable
  static List<String> collectGroupNames(@Nullable LoadUserInfo me) {
    if (me == null) {
      LogHelper.warning("Failed to load the current user, comment visibility groups stay unknown");
      return null;
    }
    ArrayList<String> groups = Collections15.arrayList();
    for (JSONObject group : me.getGroups()) {
      String name = GROUP_NAME.getValue(group);
      if (name == null || name.isEmpty()) continue;
      // The name is stored as Jira reports it, because that is the form the visibility payload carries in its
      // "value" field and therefore the form a downloaded visibility has to resolve against.
      if (!groups.contains(name)) groups.add(name);
    }
    return groups;
  }
}

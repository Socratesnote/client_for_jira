package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.jira.provider3.sync.schema.ServerProject;
import com.almworks.restconnector.json.ArrayKey;
import com.almworks.restconnector.json.JsonKey;
import com.almworks.restconnector.json.SelfIdExtractor;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;

public class JRProject {
  // Available in brief
  public static final JsonKey<String> NAME = JsonKey.text("name");
  public static final JsonKey<String> KEY = JsonKey.text("key");
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<String> ICON = JRAvatar.EXT_URL_16;

  // Available in full only
  public static final JsonKey<String> DESCRIPTION = JsonKey.text("description");
  public static final JsonKey<String> ASSIGNEE_TYPE = JsonKey.text("assigneeType");
  /**
   * @see JRUser
   */
  public static final JsonKey<JSONObject> LEAD = JsonKey.object("lead");
  /**
   * @see JRComponent
   */
  public static final ArrayKey<JSONObject> COMPONENTS = ArrayKey.objectArray("components");
  /**
   * @see JRIssueType
   */
  public static final ArrayKey<JSONObject> ISSUE_TYPES = ArrayKey.objectArray("issueTypes");
  /**
   * @see JRVersion
   */
  public static final ArrayKey<JSONObject> VERSIONS = ArrayKey.objectArray("versions");
  /**
   * Map "Role name" -&gt; "Role url" (format [server]/rest/PATH_PROJECT/[prjKey]/role/[roleId])
   */
  public static final JsonKey<JSONObject> ROLES = JsonKey.object("roles");
  /**
   * Convertor to convert project role url to role ID
   * @see #ROLES
   */
  public static final SelfIdExtractor EXTRACT_ROLE_ID = new SelfIdExtractor("/role/");
  public static final EntityParser PARSER =
    new EntityParser.Builder()
      .map(ID, ServerProject.ID)
      .map(NAME, ServerProject.NAME)
      .map(KEY, ServerProject.KEY)
      .create(null); // todo description, projectURL, lead, URL

  @Nullable
  public static String getDisplayName(JSONObject project) {
    String key = KEY.getValue(project);
    String name = NAME.getValue(project);
    if (key == null && name == null) return null;
    else if (key == null || name == null) return key != null ? key : name;
    else return String.format("%s (%s)", key, name);
  }
}

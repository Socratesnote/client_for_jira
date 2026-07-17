package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.jira.provider3.sync.schema.ServerIssueType;
import com.almworks.restconnector.json.JsonKey;

public class JRIssueType {
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<String> NAME = JsonKey.text("name");
  public static final JsonKey<String> DESCRIPTION = JsonKey.text("description");
  public static final JsonKey<String> ICON = JsonKey.text("iconUrl");
  public static final JsonKey<Boolean> SUBTASK = JsonKey.bool("subtask");

  public static final EntityParser PARSER =
    new EntityParser.Builder()
      .map(ID, ServerIssueType.ID)
      .map(NAME, ServerIssueType.NAME)
      .map(ICON, ServerIssueType.ICON_URL)
      .map(DESCRIPTION, ServerIssueType.DESCRIPTION)
      .create(null);// todo Subtask ??

}

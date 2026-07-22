package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.jira.provider3.sync.schema.ServerPriority;
import com.almworks.restconnector.json.JsonKey;

public class JRPriority {
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<String> NAME = JsonKey.text("name");
  public static final JsonKey<String> COLOR = JsonKey.text("statusColor");
  public static final JsonKey<String> DESCRIPTION = JsonKey.text("description");
  public static final JsonKey<String> ICON = JsonKey.text("iconUrl");
  public static final EntityParser PARSER =
    new EntityParser.Builder()
      .map(ID, ServerPriority.ID)
      .map(NAME, ServerPriority.NAME)
      .map(ICON, ServerPriority.ICON_URL)
      .create(null); // todo other
}

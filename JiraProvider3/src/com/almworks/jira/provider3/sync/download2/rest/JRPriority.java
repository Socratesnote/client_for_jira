package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.jira.provider3.sync.schema.ServerPriority;
import com.almworks.restconnector.json.JsonKey;
import com.almworks.restconnector.json.SelfIdExtractor;
import com.almworks.util.collections.Convertor;

public class JRPriority {
  // Jira Cloud returns the priority "self" URL against api/2 even under v3, so match the id version-agnostically.
  private static final Convertor<Object, Integer> ID_EXTRACTOR = new SelfIdExtractor("/priority/");
  public static final JsonKey<Integer> ID = new JsonKey<Integer>("self", ID_EXTRACTOR);
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

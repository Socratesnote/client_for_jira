package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.items.entities.api.Entity;
import com.almworks.jira.provider3.sync.schema.ServerStatus;
import com.almworks.restconnector.json.JsonKey;
import com.almworks.util.collections.Convertor;
import org.json.simple.JSONObject;

public class JRStatus {
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<String> NAME = JsonKey.text("name");
  public static final JsonKey<String> ICON = JsonKey.text("iconUrl");
  public static final JsonKey<String> DESCRIPTION = JsonKey.text("description");
  public static final JsonKey<JSONObject> CATEGORY = JsonKey.object("statusCategory");

  public static final EntityParser PARSER =
    new EntityParser.Builder()
      .map(ID, ServerStatus.ID)
      .map(NAME, ServerStatus.NAME)
      .map(DESCRIPTION, ServerStatus.DESCRIPTION)
      .map(ICON, ServerStatus.ICON_URL)
      .mapEntity(CATEGORY, ServerStatus.CATEGORY, JRStatusCategory.JSON_CONVERTOR, false)
      .create(null);
  public static final Convertor<Object, Entity> JSON_CONVERTOR = new EntityParser.AsConvertor(ServerStatus.TYPE, PARSER);
}

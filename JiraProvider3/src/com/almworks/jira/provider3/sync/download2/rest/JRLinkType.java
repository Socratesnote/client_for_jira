package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.items.entities.api.Entity;
import com.almworks.jira.provider3.sync.schema.ServerLinkType;
import com.almworks.restconnector.json.JsonKey;
import com.almworks.util.collections.Convertor;

public class JRLinkType {
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<String> OUTWARD = JsonKey.text("outward");
  public static final JsonKey<String> INWARD = JsonKey.text("inward");
  public static final JsonKey<String> NAME = JsonKey.text("name");

  public static final EntityParser PARSER = new EntityParser.Builder()
    .map(ID, ServerLinkType.ID)
    .map(OUTWARD, ServerLinkType.OUTWARD_DESCRIPTION)
    .map(INWARD, ServerLinkType.INWARD_DESCRIPTION)
    .map(NAME, ServerLinkType.NAME)
    .create(null);
  public static final Convertor<Object, Entity> JSON_CONVERTOR = new EntityParser.AsConvertor(ServerLinkType.TYPE, PARSER);

}

package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.jira.provider3.sync.schema.ServerComponent;
import com.almworks.restconnector.json.JsonKey;

public class JRComponent {
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<String> NAME = JsonKey.text("name");
  public static final EntityParser PARSER =
    new EntityParser.Builder()
      .map(ID, ServerComponent.ID)
      .map(NAME, ServerComponent.NAME)
      .create(SupplyReference.supplyProject(ServerComponent.PROJECT));
}

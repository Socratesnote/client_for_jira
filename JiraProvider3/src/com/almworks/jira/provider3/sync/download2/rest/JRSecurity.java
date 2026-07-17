package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.jira.provider3.sync.schema.ServerSecurity;
import com.almworks.restconnector.json.JsonKey;

public class JRSecurity {
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<String> NAME = JsonKey.text("name");
  public static final JsonKey<String> DESCRIPTION = JsonKey.text("description");

  public static final EntityParser PARSER =
    new EntityParser.Builder()
      .map(ID, ServerSecurity.ID)
      .map(NAME, ServerSecurity.NAME)
//      .map(DESCRIPTION, ServerSecurity.DESCRIPTION)
      .create(null);
}

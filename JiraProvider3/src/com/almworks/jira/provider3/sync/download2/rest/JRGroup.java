package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.jira.provider3.sync.schema.ServerGroup;
import com.almworks.restconnector.json.JsonKey;

public class JRGroup {
  public static final JsonKey<String> ID = JsonKey.textLower("name");
  public static final EntityParser PARSER =
    new EntityParser.Builder()
      .map(ID, ServerGroup.ID)
      .create(null);
}

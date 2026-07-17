package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.jira.provider3.sync.schema.ServerVersion;
import com.almworks.restconnector.json.JsonKey;

import java.util.Date;

public class JRVersion {
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<String> NAME = JsonKey.text("name");
  public static final JsonKey<Boolean> ARCHIVED = JsonKey.bool("archived");
  public static final JsonKey<Boolean> RELEASED = JsonKey.bool("released");
  public static final JsonKey<Date> RELEASED_DATE = JsonKey.date("releaseDate");

  public static final EntityParser PARSER =
    new EntityParser.Builder()
      .map(ID, ServerVersion.ID)
      .map(NAME, ServerVersion.NAME)
      .map(ARCHIVED, ServerVersion.ARCHIVED)
      .map(RELEASED, ServerVersion.RELEASED)
      .map(RELEASED_DATE, ServerVersion.RELEASE_DATE)
      .create(SupplyReference.supplyProject(ServerVersion.PROJECT));
}

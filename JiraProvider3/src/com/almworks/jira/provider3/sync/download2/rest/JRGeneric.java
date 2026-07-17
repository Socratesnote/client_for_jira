package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.restconnector.json.JsonKey;

public class JRGeneric {
  public static final JsonKey<String> ID_STR = JsonKey.text("id");
  public static final JsonKey<Integer> ID_INT = JsonKey.integer("id");
  public static final JsonKey<String> NAME = JsonKey.text("name");
  public static final JsonKey<String> DESCRIPTION = JsonKey.text("description");
  public static final JsonKey<String> VALUE = JsonKey.text("value");

}

package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.restconnector.json.JsonKey;
import org.json.simple.JSONObject;

public class JRTransition {
  public static final JsonKey<JSONObject> TO_STATUS = JsonKey.object("to");
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<String> NAME = JsonKey.textNNTrim("name");
  public static final JsonKey<JSONObject> FIELDS = JsonKey.object("fields");
}

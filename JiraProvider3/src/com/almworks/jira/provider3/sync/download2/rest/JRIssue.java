package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.items.entities.api.Entity;
import com.almworks.items.entities.dbwrite.downloadstage.DownloadStageMark;
import com.almworks.jira.provider3.sync.schema.ServerIssue;
import com.almworks.restconnector.json.JsonKey;
import com.almworks.util.collections.Convertor;
import org.json.simple.JSONObject;

import java.util.Date;

/**
 * Applicable to issue obtained from /search
 */
public class JRIssue {
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<String> KEY = JsonKey.text("key");

  public static final JsonKey<JSONObject> FIELDS = JsonKey.object("fields");
  private static final JsonKey<Date> F_UPDATED = JsonKey.dateTime("updated");
  private static final JsonKey<Date> F_CREATED = JsonKey.dateTime("created");
  private static final JsonKey<String> F_SUMMARY = JsonKey.textNNTrim("summary");
  private static final JsonKey<JSONObject> F_ISSUE_TYPE = JsonKey.object("issuetype");
  private static final JsonKey<JSONObject> F_PROJECT = JsonKey.object("project");
  private static final JsonKey<JSONObject> F_PARENT = JsonKey.object("parent");
  private static final JsonKey<JSONObject> F_STATUS = JsonKey.object("status");

  public static final JsonKey<Date> UPDATED = JsonKey.composition(FIELDS, F_UPDATED);
  public static final JsonKey<Date> CREATED = JsonKey.composition(FIELDS, F_CREATED);
  public static final JsonKey<String> SUMMARY = JsonKey.composition(FIELDS, F_SUMMARY);
  public static final JsonKey<JSONObject> ISSUE_TYPE = JsonKey.composition(FIELDS, F_ISSUE_TYPE);
  public static final JsonKey<JSONObject> PROJECT = JsonKey.composition(FIELDS, F_PROJECT);
  public static final JsonKey<JSONObject> PARENT = JsonKey.composition(FIELDS, F_PARENT);
  public static final JsonKey<JSONObject> STATUS = JsonKey.composition(FIELDS, F_STATUS);
  public static final EntityParser PARSER = new EntityParser.Builder()
    .map(ID, ServerIssue.ID)
    .map(KEY, ServerIssue.KEY)
    .downloadStage(DownloadStageMark.DUMMY)
    .create(null);
  public static final Convertor<Object, Entity> DUMMY_JSON_CONERTOR = new EntityParser.AsConvertor(ServerIssue.TYPE, PARSER); // todo add fields JCO-1373
}

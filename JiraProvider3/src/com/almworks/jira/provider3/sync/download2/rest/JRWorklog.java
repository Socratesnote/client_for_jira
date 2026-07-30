package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.items.entities.api.Entity;
import com.almworks.jira.provider3.remotedata.issue.fields.JsonUserParser;
import com.almworks.jira.provider3.sync.schema.ServerWorklog;
import com.almworks.restconnector.json.JsonKey;
import com.almworks.util.collections.Convertor;

import java.util.Date;

public class JRWorklog {
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<Entity> AUTHOR = JsonUserParser.jsonKey("author");
  public static final JsonKey<Date> CREATED = JsonKey.dateTime("created");
  public static final JsonKey<Entity> UPDATE_AUTHOR = JsonUserParser.jsonKey("updateAuthor");
  public static final JsonKey<Date> UPDATED = JsonKey.dateTime("updated");
  public static final JsonKey<Integer> TIME_SECONDS = JsonKey.integer("timeSpentSeconds");
  public static final JsonKey<Date> STARTED = JsonKey.dateTime("started");
  public static final JsonKey<String> COMMENT = AdfText.textTrimLines("comment");
  /** Same field as {@link #COMMENT}, kept as its raw document. Mapping is by key instance, so the shared name is fine. */
  public static final JsonKey<String> COMMENT_ADF = AdfText.rawAdf("comment");
  public static final JsonKey<Entity> VISIBILITY = JRVisibility.jsonKey("visibility");

  public static final Convertor<Object, Entity> PARTIAL_JSON_CONVERTOR =
    new EntityParser.Builder()
      .map(ID, ServerWorklog.ID)
      .map(AUTHOR, ServerWorklog.AUTHOR)
      .map(CREATED, ServerWorklog.CREATED)
      .map(UPDATE_AUTHOR, ServerWorklog.EDITOR)
      .map(UPDATED, ServerWorklog.UPDATED)
      .map(TIME_SECONDS, ServerWorklog.TIME_SECONDS)
      .map(STARTED, ServerWorklog.START_DATE)
      .map(COMMENT, ServerWorklog.COMMENT)
      .map(COMMENT_ADF, ServerWorklog.COMMENT_ADF)
      .set(ServerWorklog.SECURITY, null)
      .map(VISIBILITY, ServerWorklog.SECURITY)
      .createPartialConvertor(ServerWorklog.TYPE);
}

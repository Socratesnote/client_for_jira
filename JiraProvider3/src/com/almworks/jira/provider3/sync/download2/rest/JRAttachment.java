package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.items.entities.api.Entity;
import com.almworks.jira.provider3.remotedata.issue.fields.JsonUserParser;
import com.almworks.jira.provider3.sync.schema.ServerAttachment;
import com.almworks.restconnector.json.JsonKey;
import com.almworks.util.collections.Convertor;

import java.util.Date;

public class JRAttachment {
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<Entity> AUTHOR = JsonUserParser.jsonKey("author");
  public static final JsonKey<Date> CREATED = JsonKey.dateTime("created");
  public static final JsonKey<String> FILENAME = JsonKey.text("filename");
  public static final JsonKey<String> MIME_TYPE = JsonKey.text("mimeType");
  public static final JsonKey<String> CONTENT = JsonKey.text("content");
  public static final JsonKey<String> SIZE_STRING = JsonKey.textOrInteger("size");

  public static final Convertor<Object, Entity> PARTIAL_JSON_CONVERTOR =
    new EntityParser.Builder()
      .map(ID, ServerAttachment.ID)
      .map(AUTHOR, ServerAttachment.AUTHOR)
      .map(CREATED, ServerAttachment.DATE)
      .map(FILENAME, ServerAttachment.FILE_NAME)
      .map(MIME_TYPE, ServerAttachment.MIME_TYPE)
      .map(CONTENT, ServerAttachment.FILE_URL)
      .map(SIZE_STRING, ServerAttachment.SIZE_STRING)
      .createPartialConvertor(ServerAttachment.TYPE);
}

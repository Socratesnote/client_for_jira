package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.items.entities.api.Entity;
import com.almworks.restconnector.json.JsonKey;

public class JRLink {
  public static final JsonKey<Integer> ID = JsonKey.integer("id");
  public static final JsonKey<Entity> LINK_TYPE = new JsonKey<Entity>("type", JRLinkType.JSON_CONVERTOR);
  public static final JsonKey<Entity> INWARD_ISSUE = new JsonKey<Entity>("inwardIssue", JRIssue.DUMMY_JSON_CONERTOR);
  public static final JsonKey<Entity> OUTWARD_ISSUE = new JsonKey<Entity>("outwardIssue", JRIssue.DUMMY_JSON_CONERTOR);
}

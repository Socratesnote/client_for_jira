package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.restconnector.json.ArrayKey;
import com.almworks.restconnector.json.JsonKey;
import org.json.simple.JSONObject;

/**
 * Accessors for the single-role payload of api/3/project/{projectIdOrKey}/role/{id}, which is the only place
 * the actors of a role are returned. The brief role map on the project payload carries names and urls only.
 */
public class JRProjectRole {
  public static final String ACTORS_KEY = "actors";
  public static final ArrayKey<JSONObject> ACTORS = ArrayKey.objectArray(ACTORS_KEY);
  /**
   * An actor is either a user ({@link #ACTOR_USER} present) or a group ({@link #ACTOR_GROUP} present). A user
   * who belongs to an actor group is a member of the role without appearing as a user actor of it.
   */
  public static final JsonKey<String> ACTOR_TYPE = JsonKey.text("type");
  public static final JsonKey<JSONObject> ACTOR_USER = JsonKey.object("actorUser");
  public static final JsonKey<JSONObject> ACTOR_GROUP = JsonKey.object("actorGroup");
  public static final JsonKey<String> ACTOR_ACCOUNT_ID = JsonKey.text("accountId");
  public static final JsonKey<String> ACTOR_GROUP_ID = JsonKey.text("groupId");
  public static final JsonKey<String> ACTOR_GROUP_NAME = JsonKey.text("name");
}

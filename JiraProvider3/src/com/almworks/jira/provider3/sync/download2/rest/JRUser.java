package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.restconnector.json.JsonKey;
import com.almworks.restconnector.operations.RestAuth1Session;

public class JRUser {
  public static final JsonKey<String> ACCOUNT_ID = RestAuth1Session.USER_ACCOUNT_ID;
  public static final JsonKey<String> NAME = JsonKey.text("displayName");
  public static final JsonKey<String> ICON_16 = JRAvatar.EXT_URL_16;
}

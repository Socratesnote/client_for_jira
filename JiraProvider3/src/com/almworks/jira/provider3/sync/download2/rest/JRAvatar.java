package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.restconnector.json.JsonKey;
import org.json.simple.JSONObject;

public class JRAvatar {
  /**
   * Url of 16x16 icon
   */
  public static final JsonKey<String> URL_16 = JsonKey.text("16x16");
  /**
   * Url of 48x48 icon
   */
  public static final JsonKey<String> URL_48 = JsonKey.text("48x48");

  /**
   * Common reference name to avatar object
   */
  public static final JsonKey<JSONObject> EXT_REF = JsonKey.object("avatarUrls");

  /**
   * Accessor key to get 16x16 right from external object.<br><br>
   * <code>JSONObject avatar = EXT_REF.getValue(myObject);<br>
   * String url = URL_16.getValue(avatar);
   * </code><br><br>
   * is equal to<br><br>
   * <code>
   *   String url = EXT_URL16.getValue(myObject);
   * </code>
   */
  public static final JsonKey<String> EXT_URL_16 = JsonKey.composition(EXT_REF, URL_16);
}

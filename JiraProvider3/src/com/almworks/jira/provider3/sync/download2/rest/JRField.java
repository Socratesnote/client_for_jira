package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.restconnector.json.ArrayKey;
import com.almworks.restconnector.json.JsonKey;
import org.json.simple.JSONObject;

public class JRField {
  /**
   * Map of fieldId -> field details<br>
   * /issue/createmeta - in project/type<br>
   * /issue/KEY/editmeta - in root object
   *
   */
  public static final JsonKey<JSONObject> FIELDS  = JsonKey.object("fields");
  /**
   * /field
   */
  public static final JsonKey<String> ID = JsonKey.text("id");
  /**
   * /field, /issue/createmeta, /issue/editmeta|fields|_fieldName_
   */
  public static final JsonKey<String> NAME = JsonKey.text("name");
  /**
   * /field
   * @deprecated avoid usage of this flag since labels if JIRA system field, but it is "custom" from JC point of view
   */
  @SuppressWarnings("UnusedDeclaration") @Deprecated
  public static final JsonKey<Boolean> CUSTOM = JsonKey.bool("custom");
  public static final JsonKey<JSONObject> SCHEMA = JsonKey.object("schema");
  public static final JsonKey<String> SCHEMA_CUSTOM = JsonKey.text("custom");
  /**
   * @deprecated use String field id instead. Labels has no integer id, however JC treats labels as custom field.
   */
  @SuppressWarnings("UnusedDeclaration") @Deprecated
  private static final JsonKey<Integer> SCHEMA_CUSTOM_ID = JsonKey.integer("customId");
  public static final JsonKey<String> SCHEMA_SYSTEM = JsonKey.text("system");
  /**
   * /issue/createmeta
   */
  public static final ArrayKey<JSONObject> ALLOWED_VALUES = ArrayKey.objectArray("allowedValues");

  /**
   * /issue/editmeta|fields|_fieldName_
   */
  public static final JsonKey<Boolean> REQUIRED = JsonKey.bool("required");
  /**
   * /issue/editmeta|fields|_fieldName_
   */
  public static final ArrayKey<String> OPERATIONS = ArrayKey.textArray("operations");

}

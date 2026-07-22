package com.almworks.jira.provider3.remotedata.issue.move;

import com.almworks.util.tests.BaseTestCase;
import org.json.simple.JSONObject;

// Guards the JSON shape SetEpicParent PUTs to api/3/issue/{id} to set a standard issue's parent.
// On Jira Cloud a parent is set via the normal "parent" field, not the legacy subtask move wizards.
public class SetEpicParentTests extends BaseTestCase {
  public void testCreateRequestShape() {
    JSONObject request = SetEpicParent.createRequest(10042);
    // { "fields": { "parent": { "id": "10042" } } }
    assertEquals(1, request.size());
    Object fields = request.get("fields");
    assertTrue(String.valueOf(fields), fields instanceof JSONObject);
    assertEquals(1, ((JSONObject) fields).size());
    Object parent = ((JSONObject) fields).get("parent");
    assertTrue(String.valueOf(parent), parent instanceof JSONObject);
    // Id is sent as a string, matching how NewIssue posts the parent field.
    assertEquals("10042", ((JSONObject) parent).get("id"));
    assertEquals(1, ((JSONObject) parent).size());
  }
}

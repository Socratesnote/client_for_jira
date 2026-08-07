package com.almworks.jira.provider3.remotedata.issue.move;

import com.almworks.util.tests.BaseTestCase;
import org.json.simple.JSONObject;

// Guards the JSON shapes sent to PUT api/3/issue/{id} on Jira Cloud. These assert request bodies only, offline.
//
// Scope note, from the live capture of 2026-08-07: this endpoint cannot change an issue's project and cannot cross
// the subtask boundary, so the projectAndType and typeAndParent shapes below apply only to a type change that stays
// within one project and on one side of that boundary. Cross-project moves and subtask conversions go through the
// asynchronous POST api/3/bulk/issues/move instead, and are not covered here - see MoveRequests for why.
public class MoveRequestsTests extends BaseTestCase {
  public void testProjectAndType() {
    // { "fields": { "project": { "id": "10000" }, "issuetype": { "id": "10100" } } }
    JSONObject fields = fieldsOf(MoveRequests.projectAndType(10000, 10100));
    assertEquals(2, fields.size());
    checkEntity(fields, "project", "10000");
    checkEntity(fields, "issuetype", "10100");
  }

  // A null id means "leave unchanged", so the key must be absent rather than present and null: Jira reads an
  // explicit null as a clear, which would blank the field instead of leaving it alone.
  public void testProjectOnlyOmitsType() {
    JSONObject fields = fieldsOf(MoveRequests.projectAndType(10000, null));
    assertEquals(1, fields.size());
    checkEntity(fields, "project", "10000");
    assertFalse(fields.containsKey("issuetype"));
  }

  public void testTypeOnlyOmitsProject() {
    JSONObject fields = fieldsOf(MoveRequests.projectAndType(null, 10100));
    assertEquals(1, fields.size());
    checkEntity(fields, "issuetype", "10100");
    assertFalse(fields.containsKey("project"));
  }

  // Converting to a subtask takes the new type and the parent in one update.
  public void testTypeAndParent() {
    JSONObject fields = fieldsOf(MoveRequests.typeAndParent(10100, 10042));
    assertEquals(2, fields.size());
    checkEntity(fields, "issuetype", "10100");
    checkEntity(fields, "parent", "10042");
  }

  public void testType() {
    JSONObject fields = fieldsOf(MoveRequests.type(10100));
    assertEquals(1, fields.size());
    checkEntity(fields, "issuetype", "10100");
  }

  public void testParent() {
    JSONObject fields = fieldsOf(MoveRequests.parent(10042));
    assertEquals(1, fields.size());
    checkEntity(fields, "parent", "10042");
  }

  // SetEpicParent is the unit this shared builder was extracted from, so the two must not drift apart.
  public void testSetEpicParentUsesTheSameShape() {
    assertEquals(MoveRequests.parent(10042).toJSONString(), SetEpicParent.createRequest(10042).toJSONString());
  }

  private static JSONObject fieldsOf(JSONObject request) {
    assertEquals(1, request.size());
    Object fields = request.get("fields");
    assertTrue(String.valueOf(fields), fields instanceof JSONObject);
    return (JSONObject) fields;
  }

  // Ids travel as strings, matching how NewIssue posts entity references.
  private static void checkEntity(JSONObject fields, String name, String expectedId) {
    Object entity = fields.get(name);
    assertTrue(name + ": " + entity, entity instanceof JSONObject);
    assertEquals(1, ((JSONObject) entity).size());
    assertEquals(expectedId, ((JSONObject) entity).get("id"));
  }
}

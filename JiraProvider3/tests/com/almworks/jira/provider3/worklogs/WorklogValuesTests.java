package com.almworks.jira.provider3.worklogs;

import com.almworks.jira.provider3.remotedata.issue.VisibilityLevel;
import com.almworks.jira.provider3.sync.download2.rest.AdfDocument;
import com.almworks.util.tests.BaseTestCase;
import org.json.simple.JSONObject;

import java.lang.reflect.Constructor;
import java.util.Date;

// Guards the JSON shape AddEditWorklog posts to Jira. WorklogValues has a private
// constructor and an ItemVersion-based load(), so reflection keeps this a pure unit test.
public class WorklogValuesTests extends BaseTestCase {
  private static WorklogValues create(Integer id, Date started, Integer seconds, String comment) throws Exception {
    Constructor<WorklogValues> c = WorklogValues.class.getDeclaredConstructor(
      Integer.class, Date.class, Integer.class, String.class, VisibilityLevel.class, String.class, Date.class);
    c.setAccessible(true);
    return c.newInstance(id, started, seconds, comment, null, "author", new Date(0));
  }

  public void testCreateJsonShape() throws Exception {
    JSONObject json = create(10001, new Date(0), 3600, "worked").createJson();
    assertEquals(5, json.size());
    assertEquals(Integer.valueOf(10001), json.get("id"));
    assertEquals(Integer.valueOf(3600), json.get("timeSpentSeconds"));
    assertNull(json.get("visibility"));
    assertNotNull(json.get("started"));
    // api/3 requires the comment as an ADF document. AdfDocumentTests covers the conversion itself.
    assertEquals(AdfDocument.fromText("worked"), json.get("comment"));
  }

  // JIRA rejects a rich-text value carrying no content, so a comment-less worklog must omit the key entirely.
  public void testCreateJsonOmitsEmptyComment() throws Exception {
    JSONObject json = create(10001, new Date(0), 3600, null).createJson();
    assertFalse(json.containsKey("comment"));
    assertEquals(4, json.size());
    assertFalse(create(10001, new Date(0), 3600, "").createJson().containsKey("comment"));
  }

  // A new worklog has no id yet, so the "id" key must be omitted from the request.
  public void testCreateJsonOmitsNullId() throws Exception {
    JSONObject json = create(null, new Date(0), 60, "x").createJson();
    assertFalse(json.containsKey("id"));
    assertEquals(4, json.size());
  }
}

package com.almworks.jira.provider3.comments;

import com.almworks.jira.provider3.remotedata.issue.VisibilityLevel;
import com.almworks.util.tests.BaseTestCase;
import org.json.simple.JSONObject;

import java.lang.reflect.Constructor;
import java.util.Date;

// Guards the JSON shape AddEditComment posts to Jira. CommentValues has a private
// constructor and an ItemVersion-based load(), so reflection keeps this a pure unit test.
public class CommentValuesTests extends BaseTestCase {
  private static CommentValues create(Integer id, String text, VisibilityLevel visibility) throws Exception {
    Constructor<CommentValues> c = CommentValues.class.getDeclaredConstructor(
      Integer.class, String.class, VisibilityLevel.class, String.class, Date.class);
    c.setAccessible(true);
    return c.newInstance(id, text, visibility, "author", new Date(0));
  }

  public void testCreateJsonShape() throws Exception {
    JSONObject json = create(10105, "hello world", null).createJson();
    assertEquals(2, json.size());
    assertTrue(json.containsKey("body"));
    assertTrue(json.containsKey("visibility"));
    assertNull(json.get("visibility"));
    // NOTE: api/3 expects "body" as an ADF document, not a plain string (see CommentValues TODO).
    // When text->ADF conversion lands, update this assertion to reflect the ADF object.
    assertEquals("hello world", json.get("body"));
  }
}

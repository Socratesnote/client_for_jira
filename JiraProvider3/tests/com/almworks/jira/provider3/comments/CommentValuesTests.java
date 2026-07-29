package com.almworks.jira.provider3.comments;

import com.almworks.jira.provider3.remotedata.issue.VisibilityLevel;
import com.almworks.jira.provider3.sync.download2.rest.AdfCanonical;
import com.almworks.jira.provider3.sync.download2.rest.AdfDocument;
import com.almworks.util.tests.BaseTestCase;
import org.json.simple.JSONObject;

import java.lang.reflect.Constructor;
import java.util.Date;

// Guards the JSON shape AddEditComment posts to Jira. CommentValues has a private
// constructor and an ItemVersion-based load(), so reflection keeps this a pure unit test.
public class CommentValuesTests extends BaseTestCase {
  private static CommentValues create(Integer id, String text, VisibilityLevel visibility) throws Exception {
    return create(id, text, null, visibility);
  }

  private static CommentValues create(Integer id, String text, String textAdf, VisibilityLevel visibility) throws Exception {
    Constructor<CommentValues> c = CommentValues.class.getDeclaredConstructor(
      Integer.class, String.class, String.class, VisibilityLevel.class, String.class, Date.class);
    c.setAccessible(true);
    return c.newInstance(id, text, textAdf, visibility, "author", new Date(0));
  }

  public void testCreateJsonShape() throws Exception {
    JSONObject json = create(10105, "hello world", null).createJson();
    assertEquals(2, json.size());
    assertTrue(json.containsKey("body"));
    assertTrue(json.containsKey("visibility"));
    assertNull(json.get("visibility"));
    // api/3 requires the body as an ADF document. AdfDocumentTests covers the conversion itself.
    assertEquals(AdfDocument.fromText("hello world"), json.get("body"));
  }

  public void testMultiLineBodyIsAdf() throws Exception {
    JSONObject json = create(10105, "first\nsecond", null).createJson();
    assertEquals(AdfDocument.fromText("first\nsecond"), json.get("body"));
  }

  /**
   * The point of storing the server's document: an edit sends it back rather than a flat rebuild, so
   * formatting this client cannot express is not destroyed on the way out.
   */
  public void testStoredAdfIsSentVerbatim() throws Exception {
    String stored = "{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"paragraph\",\"content\":"
      + "[{\"type\":\"text\",\"text\":\"bold\",\"marks\":[{\"type\":\"strong\"}]}]}]}";
    JSONObject body = (JSONObject) create(10105, "bold", stored, null).createJson().get("body");
    assertTrue(AdfCanonical.areEqualRaw(stored, body.toJSONString()));
    // The flat rebuild would have dropped the mark, which is exactly the loss this replaces.
    assertFalse(AdfCanonical.areEqual(AdfDocument.fromText("bold"), body));
  }

  // A comment that predates the companion attribute has no document, and must still upload as before.
  public void testFallsBackToBuildingFromText() throws Exception {
    JSONObject json = create(10105, "plain", null, null).createJson();
    assertEquals(AdfDocument.fromText("plain"), json.get("body"));
  }

  // Malformed stored data must not block an upload; it falls back rather than sending something invalid.
  public void testUnparseableStoredAdfFallsBack() throws Exception {
    JSONObject json = create(10105, "plain", "not json", null).createJson();
    assertEquals(AdfDocument.fromText("plain"), json.get("body"));
  }
}

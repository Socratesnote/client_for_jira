package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.util.tests.BaseTestCase;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * The central property is that text survives a trip through AdfDocument and back through AdfText, since that
 * is what makes an edit uploaded by this client read back unchanged.
 */
public class AdfDocumentTests extends BaseTestCase {
  private static void assertRoundTrip(String text) {
    JSONObject doc = AdfDocument.fromText(text);
    assertNotNull(text, doc);
    // AdfText appends a line break after every block node, so the extracted text carries one extra trailing newline.
    assertEquals(text, dropTrailingNewline(AdfText.TO_TEXT.convert(doc)));
  }

  private static String dropTrailingNewline(String text) {
    return text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
  }

  public void testRoundTrip() {
    assertRoundTrip("hello");
    assertRoundTrip("hello\nworld");
    assertRoundTrip("first\n\nsecond");
    assertRoundTrip("\nleading blank line");
    assertRoundTrip("trailing blank line\n");
    assertRoundTrip("  keeps leading and trailing spaces  ");
    assertRoundTrip("quotes \" backslash \\ brace } unicode é");
  }

  public void testWindowsAndMacLineBreaksNormalized() {
    assertEquals(AdfDocument.fromText("a\nb"), AdfDocument.fromText("a\r\nb"));
    assertEquals(AdfDocument.fromText("a\nb"), AdfDocument.fromText("a\rb"));
  }

  // Callers must be able to tell "nothing to send" apart from text, so they can omit the field or send null.
  public void testNoTextGivesNull() {
    assertNull(AdfDocument.fromText(null));
    assertNull(AdfDocument.fromText(""));
  }

  public void testDocumentShape() {
    JSONObject doc = AdfDocument.fromText("hello");
    assertEquals("doc", doc.get("type"));
    assertEquals(1, doc.get("version"));
    JSONArray content = (JSONArray) doc.get("content");
    assertEquals(1, content.size());
    JSONObject paragraph = (JSONObject) content.get(0);
    assertEquals("paragraph", paragraph.get("type"));
    JSONObject text = (JSONObject) ((JSONArray) paragraph.get("content")).get(0);
    assertEquals("text", text.get("type"));
    assertEquals("hello", text.get("text"));
  }

  /**
   * The ADF spec forbids an empty text node and JIRA rejects the whole request when it finds one, so a blank
   * line must produce a paragraph with no content at all.
   */
  public void testBlankLineIsParagraphWithoutContent() {
    JSONArray content = (JSONArray) AdfDocument.fromText("a\n\nb").get("content");
    assertEquals(3, content.size());
    JSONObject blank = (JSONObject) content.get(1);
    assertEquals("paragraph", blank.get("type"));
    assertFalse(blank.containsKey("content"));
  }

  public void testNoEmptyTextNodeAnywhere() {
    assertNoEmptyText(AdfDocument.fromText("\n\na\n\n"));
  }

  private static void assertNoEmptyText(JSONObject node) {
    if ("text".equals(node.get("type"))) assertFalse("".equals(node.get("text")));
    JSONArray content = (JSONArray) node.get("content");
    if (content == null) return;
    for (Object child : content) assertNoEmptyText((JSONObject) child);
  }
}

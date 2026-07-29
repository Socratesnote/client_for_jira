package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.util.tests.BaseTestCase;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * The property under test is that documents differ here exactly when they differ in meaning: incidental
 * differences (key order, number width, an empty content array) must not register, and a real formatting
 * difference must.
 */
public class AdfCanonicalTests extends BaseTestCase {
  public void testKeyOrderIsNotSignificant() {
    JSONObject a = object("type", "text", "text", "hello");
    JSONObject b = object("text", "hello", "type", "text");
    assertTrue(AdfCanonical.areEqual(a, b));
  }

  /**
   * org.json.simple parses whole numbers as Long, while AdfDocument builds them as Integer. Without this the
   * client's own document would never compare equal to the one the server echoes back.
   */
  public void testNumberWidthIsNotSignificant() {
    assertTrue(AdfCanonical.areEqual(object("version", 1), object("version", 1L)));
    assertTrue(AdfCanonical.areEqual(object("version", (short) 1), object("version", 1L)));
  }

  public void testAbsentAndEmptyCollapse() {
    JSONObject withEmpty = object("type", "paragraph");
    withEmpty.put("content", new JSONArray());
    assertTrue(AdfCanonical.areEqual(withEmpty, object("type", "paragraph")));

    JSONObject withEmptyMarks = object("type", "text", "text", "a");
    withEmptyMarks.put("marks", new JSONArray());
    assertTrue(AdfCanonical.areEqual(withEmptyMarks, object("type", "text", "text", "a")));
  }

  // The collapse is deliberately limited to content/marks/attrs; elsewhere an empty value is a real difference.
  public void testEmptyIsSignificantForOtherKeys() {
    JSONObject withEmpty = object("type", "codeBlock");
    withEmpty.put("something", new JSONArray());
    assertFalse(AdfCanonical.areEqual(withEmpty, object("type", "codeBlock")));
  }

  public void testNullValuedKeyIsDropped() {
    JSONObject withNull = object("type", "text", "text", "a");
    withNull.put("attrs", null);
    assertTrue(AdfCanonical.areEqual(withNull, object("type", "text", "text", "a")));
  }

  public void testArrayOrderIsSignificant() {
    assertFalse(AdfCanonical.areEqual(array("a", "b"), array("b", "a")));
    assertTrue(AdfCanonical.areEqual(array("a", "b"), array("a", "b")));
  }

  /**
   * The case the whole design rests on: a formatting-only change must register as a difference, since that is
   * what makes a server-side rich-text edit visible to conflict detection.
   */
  public void testDifferingMarkIsADifference() {
    JSONObject plain = object("type", "text", "text", "hello");
    JSONObject bold = object("type", "text", "text", "hello");
    JSONArray marks = new JSONArray();
    marks.add(object("type", "strong"));
    bold.put("marks", marks);
    assertFalse(AdfCanonical.areEqual(plain, bold));
  }

  /**
   * Marks are a set in ADF, so bold-then-italic is the same text as italic-then-bold. Anything that rebuilds
   * a document emits marks in its own order, and without this every rebuilt field would look changed.
   */
  public void testMarkOrderIsNotSignificant() {
    assertTrue(AdfCanonical.areEqual(textWithMarks("a", "strong", "em"), textWithMarks("a", "em", "strong")));
    assertFalse(AdfCanonical.areEqual(textWithMarks("a", "strong"), textWithMarks("a", "em")));
    assertFalse(AdfCanonical.areEqual(textWithMarks("a", "strong", "em"), textWithMarks("a", "strong")));
  }

  /**
   * A server document often splits one formatted run across several text nodes; a rebuilt one emits a single
   * node. Same document, so they must compare equal.
   */
  public void testAdjacentTextNodesMerge() {
    JSONObject split = paragraph(text("hel"), text("lo"));
    JSONObject single = paragraph(text("hello"));
    assertTrue(AdfCanonical.areEqual(split, single));
  }

  // Merging must not reach across a difference in marks, or bold text would absorb the plain text beside it.
  public void testAdjacentTextMergeRespectsMarks() {
    JSONObject boldThenPlain = paragraph(textWithMarks("hel", "strong"), text("lo"));
    assertFalse(AdfCanonical.areEqual(boldThenPlain, paragraph(text("hello"))));
    assertFalse(AdfCanonical.areEqual(boldThenPlain, paragraph(textWithMarks("hello", "strong"))));
    // Same marks on both sides still merges.
    assertTrue(AdfCanonical.areEqual(paragraph(textWithMarks("hel", "strong"), textWithMarks("lo", "strong")),
      paragraph(textWithMarks("hello", "strong"))));
  }

  // Merging must not silently drop or reorder anything that is not a text node.
  public void testMergeKeepsOtherNodes() {
    JSONObject withBreak = paragraph(text("a"), object("type", "hardBreak"), text("b"));
    assertFalse(AdfCanonical.areEqual(withBreak, paragraph(text("ab"))));
    assertTrue(AdfCanonical.areEqual(withBreak, paragraph(text("a"), object("type", "hardBreak"), text("b"))));
  }

  public void testTextIsNotWhitespaceNormalized() {
    assertFalse(AdfCanonical.areEqual(object("text", "a b"), object("text", "a  b")));
    assertFalse(AdfCanonical.areEqual(object("text", "a"), object("text", "a ")));
  }

  // Length-prefixed strings, so text content cannot imitate the surrounding structure.
  public void testTextCannotForgeStructure() {
    assertFalse(AdfCanonical.areEqual(object("a", "x", "b", "y"), object("a", "x;b=sy")));
  }

  public void testDocumentBuiltFromTextMatchesItsOwnReparse() {
    JSONObject built = AdfDocument.fromText("first\n\nsecond");
    JSONObject reparsed = AdfCanonical.parse(built.toJSONString());
    assertNotNull(reparsed);
    assertTrue(AdfCanonical.areEqual(built, reparsed));
  }

  public void testRawComparison() {
    assertTrue(AdfCanonical.areEqualRaw(null, null));
    assertFalse(AdfCanonical.areEqualRaw(null, "{}"));
    assertTrue(AdfCanonical.areEqualRaw("{\"a\":1,\"b\":2}", "{\"b\":2,\"a\":1}"));
    assertFalse(AdfCanonical.areEqualRaw("{\"a\":1}", "{\"a\":2}"));
  }

  /**
   * Stored data can be malformed or predate a format change, so parsing must degrade quietly. A SEVERE record
   * would fail this test outright, which is the check that matters here.
   */
  public void testMalformedInputIsQuiet() {
    assertNull(AdfCanonical.parse("not json"));
    assertNull(AdfCanonical.parse("[1,2]"));
    assertNull(AdfCanonical.parse(""));
    assertNull(AdfCanonical.parse(null));
    assertFalse(AdfCanonical.areEqualRaw("not json", "{}"));
    // Identical unparseable values still compare equal, so a stale stored value does not look like an edit.
    assertTrue(AdfCanonical.areEqualRaw("not json", "not json"));
  }

  private static JSONObject text(String value) {
    return object("type", "text", "text", value);
  }

  @SuppressWarnings("unchecked")
  private static JSONObject textWithMarks(String value, String... markTypes) {
    JSONObject node = text(value);
    JSONArray marks = new JSONArray();
    for (String markType : markTypes) marks.add(object("type", markType));
    node.put("marks", marks);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject paragraph(JSONObject... children) {
    JSONObject node = object("type", "paragraph");
    JSONArray content = new JSONArray();
    for (JSONObject child : children) content.add(child);
    node.put("content", content);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject object(Object... keyValues) {
    JSONObject result = new JSONObject();
    for (int i = 0; i < keyValues.length; i += 2) result.put(keyValues[i], keyValues[i + 1]);
    return result;
  }

  @SuppressWarnings("unchecked")
  private static JSONArray array(Object... values) {
    JSONArray result = new JSONArray();
    for (Object value : values) result.add(value);
    return result;
  }
}

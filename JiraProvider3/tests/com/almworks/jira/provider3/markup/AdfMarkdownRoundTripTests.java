package com.almworks.jira.provider3.markup;

import com.almworks.jira.provider3.sync.download2.rest.AdfCanonical;
import com.almworks.util.tests.BaseTestCase;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * The correctness criterion for the converter pair: rendering a document to Markdown and parsing it straight
 * back must give the same document. Everything the editor does sits between those two steps, so a construct
 * that round-trips here is a construct an untouched edit cannot destroy.
 */
public class AdfMarkdownRoundTripTests extends BaseTestCase {
  private static void assertRoundTrip(JSONObject doc) {
    AdfToMarkdown.Rendered rendered = AdfToMarkdown.render(doc);
    JSONObject back = MarkdownToAdf.parse(rendered.getMarkdown(), rendered.getTable());
    if (!AdfCanonical.areEqual(doc, back)) {
      fail("Round trip changed the document.\nMarkdown:\n" + rendered.getMarkdown()
        + "\n\nExpected: " + AdfCanonical.canonicalize(doc) + "\nActual:   " + AdfCanonical.canonicalize(back));
    }
  }

  public void testPlainParagraphs() {
    assertRoundTrip(doc(paragraph(text("hello"))));
    assertRoundTrip(doc(paragraph(text("hello")), paragraph(text("world"))));
    assertRoundTrip(doc(paragraph(text("first")), paragraph(), paragraph(text("third"))));
    assertRoundTrip(doc(paragraph()));
  }

  // Text that looks like markup must come back as the text it is, not as the markup it resembles.
  public void testTextContainingMarkupCharacters() {
    assertRoundTrip(doc(paragraph(text("2 * 3 * 4"))));
    assertRoundTrip(doc(paragraph(text("a_b_c"))));
    assertRoundTrip(doc(paragraph(text("use `code` here"))));
    assertRoundTrip(doc(paragraph(text("[not a link](x)"))));
    assertRoundTrip(doc(paragraph(text("back\\slash"))));
    assertRoundTrip(doc(paragraph(text("~~struck~~"))));
    assertRoundTrip(doc(paragraph(text("trailing backslash \\"))));
  }

  // A paragraph whose text begins like a block must not be read back as that block.
  public void testTextLookingLikeABlock() {
    assertRoundTrip(doc(paragraph(text("# not a heading"))));
    assertRoundTrip(doc(paragraph(text("> not a quote"))));
    assertRoundTrip(doc(paragraph(text("- not a list"))));
    assertRoundTrip(doc(paragraph(text("1. not a list"))));
    assertRoundTrip(doc(paragraph(text("---"))));
    assertRoundTrip(doc(paragraph(text("```"))));
  }

  public void testMarks() {
    assertRoundTrip(doc(paragraph(marked("bold", "strong"))));
    assertRoundTrip(doc(paragraph(marked("italic", "em"))));
    assertRoundTrip(doc(paragraph(marked("struck", "strike"))));
    assertRoundTrip(doc(paragraph(marked("code", "code"))));
    assertRoundTrip(doc(paragraph(marked("both", "strong", "em"))));
    assertRoundTrip(doc(paragraph(text("plain "), marked("bold", "strong"), text(" plain"))));
  }

  /**
   * The link URL is the one thing plain-text extraction destroyed outright, so it gets its own case.
   */
  public void testLinks() {
    assertRoundTrip(doc(paragraph(link("Atlassian", "https://example.com/a?b=c&d=e"))));
    assertRoundTrip(doc(paragraph(text("see "), link("here", "https://example.com"), text(" for more"))));
  }

  public void testHeadings() {
    for (int level = 1; level <= 6; level++) assertRoundTrip(doc(heading(level, "Title " + level)));
    assertRoundTrip(doc(heading(2, "Title"), paragraph(text("body"))));
  }

  public void testBlockquote() {
    assertRoundTrip(doc(block("blockquote", paragraph(text("quoted")))));
    assertRoundTrip(doc(block("blockquote", paragraph(text("one")), paragraph(text("two")))));
  }

  public void testCodeBlock() {
    assertRoundTrip(doc(codeBlock(null, "x = 1;")));
    assertRoundTrip(doc(codeBlock("java", "int x = 1;\nint y = 2;")));
    // Code is literal, so markup characters inside it must survive untouched.
    assertRoundTrip(doc(codeBlock("java", "a * b // **not bold**")));
  }

  public void testRule() {
    assertRoundTrip(doc(paragraph(text("above")), node("rule"), paragraph(text("below"))));
  }

  public void testLists() {
    assertRoundTrip(doc(list(false, "one", "two")));
    assertRoundTrip(doc(list(true, "first", "second")));
    assertRoundTrip(doc(paragraph(text("before")), list(false, "a"), paragraph(text("after"))));
  }

  public void testNestedLists() {
    JSONObject inner = list(false, "inner one", "inner two");
    JSONObject outer = node("bulletList");
    putContent(outer, array(listItem(paragraph(text("outer")), inner)));
    assertRoundTrip(doc(outer));
  }

  public void testHardBreak() {
    assertRoundTrip(doc(paragraph(text("first"), node("hardBreak"), text("second"))));
  }

  /**
   * The whole point of placeholders: a node the converter does not understand keeps its exact JSON, including
   * ids that could never be retyped. This is what stops mentions and images being destroyed.
   */
  public void testUnsupportedNodesArePreserved() {
    assertRoundTrip(doc(paragraph(mention("Jane Doe", "557058:abc-123"))));
    assertRoundTrip(doc(mediaSingle()));
    assertRoundTrip(doc(table()));
    assertRoundTrip(doc(paragraph(text("before")), table(), paragraph(text("after"))));
  }

  // A node type this client has never seen must round-trip as faithfully as one it knows.
  public void testUnknownFutureNodeIsPreserved() {
    JSONObject future = node("futureThing");
    future.put("attrs", attrs("localId", "xyz"));
    assertRoundTrip(doc(paragraph(text("a")), future));
  }

  public void testMixedDocument() {
    assertRoundTrip(doc(
      heading(1, "Title"),
      paragraph(text("Intro with "), marked("bold", "strong"), text(" and "), link("a link", "https://example.com")),
      list(false, "one", "two"),
      codeBlock("java", "int x = 1;"),
      block("blockquote", paragraph(text("quoted"))),
      paragraph(mention("Jane Doe", "557058:abc-123"))));
  }

  @SuppressWarnings("unchecked")
  private static JSONObject doc(JSONObject... blocks) {
    JSONObject doc = node("doc");
    doc.put("version", 1L);
    JSONArray content = new JSONArray();
    for (JSONObject block : blocks) content.add(block);
    doc.put("content", content);
    return doc;
  }

  private static JSONObject paragraph(JSONObject... children) {
    return block("paragraph", children);
  }

  @SuppressWarnings("unchecked")
  private static JSONObject block(String type, JSONObject... children) {
    JSONObject node = node(type);
    if (children.length > 0) putContent(node, array(children));
    return node;
  }

  private static JSONObject listItem(JSONObject... blocks) {
    return block("listItem", blocks);
  }

  @SuppressWarnings("unchecked")
  private static JSONObject list(boolean ordered, String... items) {
    JSONObject node = node(ordered ? "orderedList" : "bulletList");
    JSONArray content = new JSONArray();
    for (String item : items) content.add(listItem(paragraph(text(item))));
    node.put("content", content);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject heading(int level, String title) {
    JSONObject node = block("heading", text(title));
    node.put("attrs", attrs("level", (long) level));
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject codeBlock(String language, String code) {
    JSONObject node = block("codeBlock", text(code));
    if (language != null) node.put("attrs", attrs("language", language));
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject text(String value) {
    JSONObject node = node("text");
    node.put("text", value);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject marked(String value, String... markTypes) {
    JSONObject node = text(value);
    JSONArray marks = new JSONArray();
    for (String markType : markTypes) marks.add(node(markType));
    node.put("marks", marks);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject link(String label, String href) {
    JSONObject node = text(label);
    JSONArray marks = new JSONArray();
    JSONObject mark = node("link");
    mark.put("attrs", attrs("href", href));
    marks.add(mark);
    node.put("marks", marks);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject mention(String name, String id) {
    JSONObject node = node("mention");
    JSONObject attrs = attrs("id", id);
    attrs.put("text", "@" + name);
    node.put("attrs", attrs);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject mediaSingle() {
    JSONObject media = node("media");
    JSONObject attrs = attrs("id", "abc-123");
    attrs.put("type", "file");
    attrs.put("collection", "coll");
    media.put("attrs", attrs);
    JSONObject single = node("mediaSingle");
    putContent(single, array(media));
    return single;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject table() {
    JSONObject cell = block("tableCell", paragraph(text("cell")));
    JSONObject row = block("tableRow", cell);
    return block("table", row);
  }

  @SuppressWarnings("unchecked")
  private static JSONArray array(JSONObject... nodes) {
    JSONArray result = new JSONArray();
    for (JSONObject node : nodes) result.add(node);
    return result;
  }

  @SuppressWarnings("unchecked")
  private static void putContent(JSONObject node, JSONArray content) {
    node.put("content", content);
  }

  @SuppressWarnings("unchecked")
  private static JSONObject node(String type) {
    JSONObject node = new JSONObject();
    node.put("type", type);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject attrs(String name, Object value) {
    JSONObject attrs = new JSONObject();
    attrs.put(name, value);
    return attrs;
  }
}

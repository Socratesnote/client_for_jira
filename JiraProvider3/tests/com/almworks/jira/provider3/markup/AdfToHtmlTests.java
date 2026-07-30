package com.almworks.jira.provider3.markup;

import com.almworks.util.tests.BaseTestCase;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * The viewer is read-only, so the risks here are different from the editable path: text from the server must
 * never be able to inject markup, and the output has to stay inside what Java 8 HTMLEditorKit understands.
 */
public class AdfToHtmlTests extends BaseTestCase {
  public void testParagraphs() {
    assertEquals("<p>hello</p>", AdfToHtml.render(doc(paragraph(text("hello")))));
    assertEquals("<p>one</p><p>two</p>", AdfToHtml.render(doc(paragraph(text("one")), paragraph(text("two")))));
    assertEquals("<p></p>", AdfToHtml.render(doc(paragraph())));
  }

  /**
   * Descriptions come from the server and can contain anything, so markup characters must be escaped rather
   * than passed into the renderer.
   */
  public void testTextIsEscaped() {
    assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>",
      AdfToHtml.render(doc(paragraph(text("<script>alert(1)</script>")))));
    assertEquals("<p>a &amp; b</p>", AdfToHtml.render(doc(paragraph(text("a & b")))));
    assertEquals("<p>say &quot;hi&quot;</p>", AdfToHtml.render(doc(paragraph(text("say \"hi\"")))));
  }

  // Presentational tags, not CSS: HTMLEditorKit is roughly HTML 3.2 and largely ignores the stylesheet.
  public void testMarks() {
    assertEquals("<p><b>bold</b></p>", AdfToHtml.render(doc(paragraph(marked("bold", "strong")))));
    assertEquals("<p><i>it</i></p>", AdfToHtml.render(doc(paragraph(marked("it", "em")))));
    assertEquals("<p><tt>c</tt></p>", AdfToHtml.render(doc(paragraph(marked("c", "code")))));
    assertEquals("<p><s>x</s></p>", AdfToHtml.render(doc(paragraph(marked("x", "strike")))));
    assertEquals("<p><u>u</u></p>", AdfToHtml.render(doc(paragraph(marked("u", "underline")))));
  }

  public void testNestedMarksAreProperlyClosed() {
    String html = AdfToHtml.render(doc(paragraph(marked("both", "strong", "em"))));
    assertEquals("<p><b><i>both</i></b></p>", html);
  }

  public void testLink() {
    JSONObject node = text("label");
    JSONArray marks = new JSONArray();
    JSONObject mark = node("link");
    mark.put("attrs", attrs("href", "https://example.com/a?b=1&c=2"));
    marks.add(mark);
    node.put("marks", marks);
    assertEquals("<p><a href=\"https://example.com/a?b=1&amp;c=2\">label</a></p>", AdfToHtml.render(doc(paragraph(node))));
  }

  public void testHeadings() {
    assertEquals("<h2>Title</h2>", AdfToHtml.render(doc(heading(2, "Title"))));
    // Out-of-range levels are clamped rather than emitting a tag no renderer knows.
    assertEquals("<h6>Deep</h6>", AdfToHtml.render(doc(heading(9, "Deep"))));
  }

  public void testLists() {
    String html = AdfToHtml.render(doc(list("bulletList", "one", "two")));
    assertEquals("<ul><li><p>one</p></li><li><p>two</p></li></ul>", html);
    assertTrue(AdfToHtml.render(doc(list("orderedList", "a"))).startsWith("<ol>"));
  }

  public void testCodeBlockIsLiteral() {
    assertEquals("<pre><tt>a &lt; b</tt></pre>", AdfToHtml.render(doc(codeBlock("a < b"))));
  }

  // An empty code block collapsed to nothing and vanished from the view entirely.
  public void testEmptyCodeBlockStaysVisible() {
    assertEquals("<pre><tt>&nbsp;</tt></pre>", AdfToHtml.render(doc(block("codeBlock"))));
  }

  public void testBlockquoteAndRule() {
    assertEquals("<blockquote><p>q</p></blockquote>", AdfToHtml.render(doc(block("blockquote", paragraph(text("q"))))));
    assertEquals("<hr>", AdfToHtml.render(doc(node("rule"))));
  }

  public void testTable() {
    JSONObject cell = block("tableCell", paragraph(text("c")));
    JSONObject row = block("tableRow", cell);
    String html = AdfToHtml.render(doc(block("table", row)));
    // The border attribute is honoured where a stylesheet border would not be.
    assertTrue(html, html.startsWith("<table border=\"1\""));
    assertTrue(html, html.contains("<tr><td><p>c</p></td></tr>"));
  }

  public void testPanelBecomesATintedCell() {
    JSONObject panel = block("panel", paragraph(text("careful")));
    panel.put("attrs", attrs("panelType", "warning"));
    String html = AdfToHtml.render(doc(panel));
    assertTrue(html, html.contains("bgcolor=\"#fffae6\""));
    assertTrue(html, html.contains("<p>careful</p>"));
  }

  public void testMention() {
    JSONObject mention = node("mention");
    JSONObject a = attrs("id", "557058:abc");
    a.put("text", "@Jane");
    mention.put("attrs", a);
    assertEquals("<p><b>@Jane</b></p>", AdfToHtml.render(doc(paragraph(mention))));
  }

  /**
   * Images cannot render: TextUtil.preprocessHtml strips every img tag before display. A visible label beats
   * a silent gap, since the attachment is listed elsewhere in the viewer.
   */
  public void testMediaBecomesALabel() {
    JSONObject media = node("media");
    media.put("attrs", attrs("alt", "screenshot.png"));
    String html = AdfToHtml.render(doc(block("mediaSingle", media)));
    assertEquals("<p><i>[attachment: screenshot.png]</i></p>", html);
    assertFalse("img tags are stripped downstream, so emitting one would show nothing", html.contains("<img"));
    // Media also appears inline inside a paragraph, where it must not add a nested paragraph.
    assertEquals("<p>see <i>[attachment: screenshot.png]</i></p>",
      AdfToHtml.render(doc(paragraph(text("see "), media))));
  }

  // An unknown node must still surface its text rather than disappearing.
  public void testUnknownNodeKeepsItsText() {
    JSONObject future = block("futureThing", paragraph(text("kept")));
    assertEquals("<p>kept</p>", AdfToHtml.render(doc(future)));
  }

  public void testNullAndEmpty() {
    assertEquals("", AdfToHtml.render(null));
    assertEquals("", AdfToHtml.render(node("doc")));
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
    if (children.length > 0) {
      JSONArray content = new JSONArray();
      for (JSONObject child : children) content.add(child);
      node.put("content", content);
    }
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject list(String type, String... items) {
    JSONObject node = node(type);
    JSONArray content = new JSONArray();
    for (String item : items) content.add(block("listItem", paragraph(text(item))));
    node.put("content", content);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject heading(int level, String title) {
    JSONObject node = block("heading", text(title));
    node.put("attrs", attrs("level", (long) level));
    return node;
  }

  private static JSONObject codeBlock(String code) {
    return block("codeBlock", text(code));
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

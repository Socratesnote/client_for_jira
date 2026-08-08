package com.almworks.jira.provider3.gui.viewer;

import com.almworks.api.application.ModelKey;
import com.almworks.api.application.TestModelMap;
import com.almworks.api.application.util.PredefinedKey;
import com.almworks.api.application.viewer.LinksEditorKit;
import com.almworks.api.application.viewer.textdecorator.TextDecoration;
import com.almworks.api.application.viewer.textdecorator.TextDecorationParser;
import com.almworks.api.application.viewer.textdecorator.TextDecoratorRegistry;
import com.almworks.util.tests.GUITestCase;
import com.almworks.util.ui.UIUtil;
import org.almworks.util.detach.DetachComposite;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;

/**
 * Covers the wiring between the model and the editor pane, which is the part that unit tests of the ADF
 * converters cannot reach. The renderer itself is covered by AdfToHtmlTests.
 */
public class AdfHtmlControllerTests extends GUITestCase {
  private final ModelKey<String> myTextKey = PredefinedKey.create("description");
  private final ModelKey<String> myAdfKey = PredefinedKey.create("descriptionAdf");
  private final DetachComposite myLife = new DetachComposite();

  private TestModelMap myModel;
  private JEditorPane myComponent;

  protected void setUp() throws Exception {
    super.setUp();
    myModel = new TestModelMap();
    myComponent = new JEditorPane();
    myComponent.setEditorKit(LinksEditorKit.create(new EmptyRegistry(), true));
    new AdfHtmlController(myTextKey, myAdfKey).connectUI(myLife, myModel, myComponent);
  }

  protected void tearDown() throws Exception {
    myLife.detach();
    myComponent = null;
    myModel = null;
    super.tearDown();
  }

  public void testDocumentIsRendered() {
    setAdf(doc(paragraph(text("hello world"))));
    assertEquals("hello world", documentText());
  }

  /**
   * Marks have to survive as rendered structure rather than as literal text: the failure this guards against
   * showed the markup itself in the field.
   */
  public void testMarksBecomeStructureNotText() {
    setAdf(doc(paragraph(marked("bold bit", "strong"))));
    assertEquals("bold bit", documentText());
    assertTrue(myComponent.getText(), myComponent.getText().contains("<b>"));
  }

  /**
   * The fragment is wrapped by TextUtil.preprocessHtml before display, and that wrapper is what applies the UI
   * font. An unwrapped fragment falls back to the editor kit's serif and looks nothing like the rest of the
   * client - which is exactly the defect this pins down. Assert the rule reached the document's stylesheet.
   */
  public void testUiFontIsApplied() {
    setAdf(doc(paragraph(text("any text"))));
    Font expected = UIManager.getFont("Label.font");
    assertNotNull("no Label.font in this look and feel", expected);
    StyleSheet styleSheet = ((HTMLDocument) myComponent.getDocument()).getStyleSheet();
    Object family = styleSheet.getRule("body").getAttribute(javax.swing.text.StyleConstants.FontFamily);
    assertEquals(expected.getFamily(), String.valueOf(family));
  }

  /**
   * Values downloaded before the ADF companion existed, and fields that were never rich, have plain text and
   * no document. They must still display.
   */
  public void testPlainTextFallbackWhenNoDocument() {
    myModel.setValue(myTextKey, "plain only");
    assertEquals("plain only", documentText());
  }

  public void testEmptyModelShowsNothing() {
    assertEquals("", documentText());
  }

  /**
   * A malformed document must not take the field down with it; the plain text is the fallback.
   */
  public void testUnparseableAdfFallsBackToText() {
    myModel.setValue(myTextKey, "the plain form");
    myModel.setValue(myAdfKey, "{not json");
    assertEquals("the plain form", documentText());
  }

  public void testValueChangeRefreshesComponent() {
    setAdf(doc(paragraph(text("first"))));
    assertEquals("first", documentText());
    setAdf(doc(paragraph(text("second"))));
    assertEquals("second", documentText());
  }

  private void setAdf(JSONObject document) {
    myModel.setValue(myAdfKey, document.toJSONString());
  }

  /**
   * The rendered text, with markup resolved - what a reader actually sees.
   */
  private String documentText() {
    return UIUtil.getDocumentText(myComponent).trim();
  }

  /**
   * A registry that finds nothing. TextDecoratorRegistry.Delegating is unusable here: without a delegate it
   * logs at SEVERE, and a SEVERE record fails the test.
   */
  private static class EmptyRegistry implements TextDecoratorRegistry {
    public void addParser(TextDecorationParser parser) {
    }

    public Collection<? extends TextDecoration> processText(String text) {
      return Collections.emptyList();
    }
  }

  @SuppressWarnings("unchecked")
  private static JSONObject doc(JSONObject... blocks) {
    JSONObject node = node("doc");
    node.put("version", 1L);
    JSONArray content = new JSONArray();
    Collections.addAll(content, blocks);
    node.put("content", content);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject paragraph(JSONObject... inline) {
    JSONObject node = node("paragraph");
    JSONArray content = new JSONArray();
    Collections.addAll(content, inline);
    node.put("content", content);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject text(String value) {
    JSONObject node = node("text");
    node.put("text", value);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject marked(String value, String markType) {
    JSONObject node = text(value);
    JSONArray marks = new JSONArray();
    marks.add(node(markType));
    node.put("marks", marks);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject node(String type) {
    JSONObject node = new JSONObject();
    node.put("type", type);
    return node;
  }
}

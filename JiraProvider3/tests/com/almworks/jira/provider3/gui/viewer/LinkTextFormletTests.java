package com.almworks.jira.provider3.gui.viewer;

import com.almworks.api.application.ModelKey;
import com.almworks.api.application.TestModelMap;
import com.almworks.api.application.util.PredefinedKey;
import com.almworks.api.application.viewer.LinksEditorKit;
import com.almworks.api.application.viewer.textdecorator.TextDecoration;
import com.almworks.api.application.viewer.textdecorator.TextDecorationParser;
import com.almworks.api.application.viewer.textdecorator.TextDecoratorRegistry;
import com.almworks.engine.gui.LinkTextFormlet;
import com.almworks.util.config.Configuration;
import com.almworks.util.config.MapMedium;
import com.almworks.util.tests.GUITestCase;
import org.almworks.util.detach.DetachComposite;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

/**
 * What the formlet around a rich-text field reports to the form that lays it out: whether it takes up space at
 * all, and what it says when collapsed. Both are driven by the plain text the controller passes to its
 * updaters, so handing the formlet markup instead makes an empty field look occupied and puts tags in the
 * caption. That is the invariant these tests hold in place.
 */
public class LinkTextFormletTests extends GUITestCase {
  private final ModelKey<String> myTextKey = PredefinedKey.create("description");
  private final ModelKey<String> myAdfKey = PredefinedKey.create("descriptionAdf");
  private final DetachComposite myLife = new DetachComposite();

  private TestModelMap myModel;
  private JEditorPane myComponent;
  private Configuration myConfig;
  private LinkTextFormlet myFormlet;

  protected void setUp() throws Exception {
    super.setUp();
    myModel = new TestModelMap();
    myComponent = new JEditorPane();
    myComponent.setEditorKit(LinksEditorKit.create(new EmptyRegistry(), true));
    myConfig = MapMedium.createConfig();
    AdfHtmlController controller = new AdfHtmlController(myTextKey, myAdfKey);
    // The formlet registers its updater in its constructor, so it must exist before the model is connected.
    myFormlet = new LinkTextFormlet(myComponent, controller, myConfig);
    controller.connectUI(myLife, myModel, myComponent);
  }

  protected void tearDown() throws Exception {
    myLife.detach();
    myFormlet = null;
    myComponent = null;
    myModel = null;
    super.tearDown();
  }

  public void testEmptyFieldIsNotVisible() {
    assertFalse(myFormlet.isVisible());
  }

  public void testFieldWithContentIsVisible() {
    setRichText("something", doc(paragraph(text("something"))));
    assertTrue(myFormlet.isVisible());
  }

  /**
   * An empty paragraph renders as markup that is not empty, so visibility must be decided on the text and not
   * on what was handed to the component - otherwise a cleared field keeps its panel.
   */
  public void testDocumentWithNoTextIsNotVisible() {
    setRichText("", doc(paragraph()));
    assertFalse(myFormlet.isVisible());
  }

  public void testWhitespaceOnlyIsNotVisible() {
    myModel.setValue(myTextKey, "   \n  ");
    assertFalse(myFormlet.isVisible());
  }

  public void testClearingHidesTheField() {
    setRichText("here", doc(paragraph(text("here"))));
    assertTrue(myFormlet.isVisible());
    setRichText(null, null);
    assertFalse(myFormlet.isVisible());
  }

  /**
   * Visibility follows the flattened text, not the document. The two always arrive together - the download
   * path stores the flattened form alongside the document - so this is a statement of which one governs
   * rather than a case the client meets in practice.
   */
  public void testVisibilityFollowsPlainTextNotDocument() {
    setAdf(doc(paragraph(text("only in the document"))));
    assertFalse(myFormlet.isVisible());
    myModel.setValue(myTextKey, "only in the document");
    assertTrue(myFormlet.isVisible());
  }

  public void testExpandedFormletHasNoCaption() {
    setRichText("visible in full", doc(paragraph(text("visible in full"))));
    assertFalse(myFormlet.isCollapsed());
    assertNull(myFormlet.getCaption());
  }

  /**
   * The caption is the collapsed summary of the field. It must be the rendered text: reading the component's
   * own getText on an HTML editor kit returns the markup source, which then shows up as tags in the form.
   */
  public void testCollapsedCaptionIsTextNotMarkup() {
    setRichText("emphasised", doc(paragraph(marked("emphasised", "strong"))));
    myFormlet.toggleExpand();
    assertTrue(myFormlet.isCollapsed());
    String caption = myFormlet.getCaption();
    assertEquals("emphasised", caption.trim());
    assertFalse(caption, caption.contains("<"));
  }

  private void setAdf(JSONObject document) {
    myModel.setValue(myAdfKey, document.toJSONString());
  }

  /**
   * Loads the field the way the download path does: the document and its flattened text together.
   */
  private void setRichText(String plain, JSONObject document) {
    myModel.setValue(myAdfKey, document != null ? document.toJSONString() : null);
    myModel.setValue(myTextKey, plain);
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

package com.almworks.jira.provider3.gui.viewer;

import com.almworks.api.application.ModelKey;
import com.almworks.api.application.ModelMap;
import com.almworks.engine.gui.BaseTextController;
import com.almworks.jira.provider3.markup.AdfToHtml;
import com.almworks.jira.provider3.sync.download2.rest.AdfCanonical;
import com.almworks.util.text.TextUtil;
import org.almworks.util.Util;
import org.json.simple.JSONObject;

import javax.swing.text.JTextComponent;

/**
 * Shows a rich-text field as rendered HTML rather than flattened plain text.<br>
 * Reads two model keys: the field's stored plain text, and the companion holding the server's own ADF
 * document. When the document is there it is rendered; when it is not - a value downloaded before the
 * companion existed, or a field that was never rich - the plain text is shown instead, so the viewer behaves
 * the same either way.
 * <p>
 * Read-only. Editing rich text goes through the Markdown editors, not this component.
 */
public class AdfHtmlController extends BaseTextController<String> {
  private final ModelKey<String> myAdfKey;

  public AdfHtmlController(ModelKey<String> textKey, ModelKey<String> adfKey) {
    super(textKey, true);
    myAdfKey = adfKey;
  }

  /**
   * Renders the document when there is one, and falls back to the plain text otherwise.
   */
  @Override
  protected void updateComponent(ModelMap model, JTextComponent component) {
    setComponentText(component, toHtml(model));
    // Formlets use this to decide whether they are visible and what their collapsed caption says, so they
    // get the plain text. Handing them markup makes an empty field look non-empty.
    notifyUpdated(plainText(model), component);
  }

  private String plainText(ModelMap model) {
    return myKey.hasValue(model) ? Util.NN(myKey.getValue(model)) : "";
  }

  /**
   * The fragment is wrapped before display: that is what applies the UI font, since an unwrapped fragment
   * falls back to the editor kit's built-in serif and looks nothing like the rest of the client.
   */
  private String toHtml(ModelMap model) {
    String rawAdf = myAdfKey.hasValue(model) ? myAdfKey.getValue(model) : null;
    JSONObject doc = AdfCanonical.parse(rawAdf);
    String fragment = doc != null
      ? AdfToHtml.render(doc)
      : AdfToHtml.renderPlainText(myKey.hasValue(model) ? myKey.getValue(model) : null);
    return TextUtil.preprocessHtml(fragment);
  }

  @Override
  protected String toValue(String text) {
    return text;
  }

  @Override
  protected boolean isEditable() {
    return false;
  }

  @Override
  protected String toText(String value) {
    return value;
  }

  @Override
  protected String getEmptyStringValue() {
    return null;
  }
}

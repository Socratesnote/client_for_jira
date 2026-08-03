package com.almworks.items.gui.edit.editors.text;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.gui.edit.*;
import com.almworks.items.sync.VersionSource;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.ui.UIUtil;
import org.almworks.util.StringUtil;
import org.almworks.util.TypedKey;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.JTextComponent;
import java.math.BigDecimal;

public abstract class ScalarValueKey<T> {

  public abstract void setValue(EditModelState model, T value);

  public abstract void setText(EditModelState model, String newText);

  @NotNull
  public abstract String getText(EditModelState model);

  public abstract boolean isChanged(EditItemModel model);

  public abstract T getValue(EditModelState model);
  
  public abstract T getInitialValue(EditModelState model);

  public abstract void verifyData(DataVerification verifyContext, ScalarFieldEditor<T> editor);

  public abstract void commitValue(CommitContext context, DBAttribute<T> attribute);

  public abstract boolean hasValue(EditModelState model);

  /**
   * Called once the editor has loaded its stored value, for keys that need more than that attribute alone.
   * Does nothing by default.
   */
  public void prepareValue(VersionSource source, EditItemModel model, ScalarFieldEditor<T> editor) {}

  /**
   * Companion attribute holding the rich value beside the plain text, for keys that have one.
   * @return null for every key whose whole value is the plain text, which is all of them by default
   */
  @Nullable
  public DBAttribute<String> getSourceAttribute() {
    return null;
  }

  @Nullable
  public RichTextTransform getRichTextTransform() {
    return null;
  }

  /**
   * Explanation shown on the editor component, for keys whose editable form needs one.
   * @return null when the field is ordinary text and needs no explaining, which is the default
   */
  @Nullable
  public String getEditorTooltip() {
    return null;
  }

  /**
   * Sets the value together with the rich form it came from, so that replacing the value - as resolving a
   * merge does - carries the formatting rather than just the extracted text.
   */
  public void setValue(EditModelState model, T value, @Nullable String rawSource) {
    setValue(model, value);
  }

  public boolean[] listenTextComponent(Lifespan life, final EditModelState model, final JTextComponent textComponent) {
    final boolean[] duringUpdate = {false};
    UIUtil.addTextListener(life, textComponent, new ChangeListener() {
      @Override
      public void onChange() {
        if (duringUpdate[0]) return;
        duringUpdate[0] = true;
        try {
          setText(model, textComponent.getText());
        } finally {
          duringUpdate[0] = false;
        }
      }
    });
    return duringUpdate;
  }

  protected abstract T fromText(String text);

  protected abstract String toText(T value);

  public static class Text extends ScalarValueKey<String> {
    private final TypedKey<String> myKey;
    private final boolean myAllowEmpty;

    public Text(String debugName, boolean allowEmpty) {
      myAllowEmpty = allowEmpty;
      myKey = TypedKey.create(debugName);
    }

    @Override
    protected String fromText(String text) {
      return Util.NN(text).trim();
    }

    @Override
    protected String toText(String value) {
      return Util.NN(value).trim();
    }

    @Override
    public void setValue(EditModelState model, String value) {
      value = normalizeText(value);
      model.putValue(myKey, value);
    }

    @Override
    public void setText(EditModelState model, String newText) {
      String value = normalizeText(newText);
      String currentText = model.getValue(myKey);
      if (Util.equals(currentText, value)) return;
      setValue(model, value);
    }

    public static String normalizeText(String newText) {
      String value = Util.NN(newText).trim();
      // http://snow:10430/browse/JC-131
      value = value.replaceAll(StringUtil.LOCAL_LINE_SEPARATOR, "\n");
      if (value.isEmpty()) value = null;
      return value;
    }

    @NotNull
    @Override
    public String getText(EditModelState model) {
      return Util.NN(model.getValue(myKey));
    }

    @Override
    public boolean isChanged(EditItemModel model) {
      return !model.isEqualValue(myKey);
    }

    @Override
    public String getInitialValue(EditModelState model) {
      return model.getInitialValue(myKey);
    }

    @Override
    public String getValue(EditModelState model) {
      return normalizeText(getText(model));
    }

    @Override
    public void verifyData(DataVerification verifyContext, ScalarFieldEditor<String> editor) {
      if (myAllowEmpty || verifyContext.getPurpose() == DataVerification.Purpose.EDIT_WARNING) return;
      if (getValue(verifyContext.getModel()) == null) verifyContext.addError(editor, "Should be not empty");
    }

    @Override
    public void commitValue(CommitContext context, DBAttribute<String> attribute) {
      context.getCreator().setValue(attribute, getValue(context.getModel()));
    }

    @Override
    public boolean hasValue(EditModelState model) {
      return getValue(model) != null;
    }
  }

  /**
   * Holds a rich-text field as editable text while keeping the server's own rich value beside it.<br>
   * The stored plain text is left to the ordinary attribute; this key adds the rich source the edit started
   * from, and a snapshot of the text as first shown. This helps detect whether a field was edited, such that if the text still equals the snapshot, committing writes nothing at all.
   */
  public static class RichText extends ScalarValueKey<String> {
    private final TypedKey<String> myKey;
    private final TypedKey<String> mySourceKey;
    private final TypedKey<String> myOpenedKey;
    private final DBAttribute<String> mySourceAttribute;
    private final RichTextTransform myTransform;

    public RichText(String debugName, DBAttribute<String> sourceAttribute, RichTextTransform transform) {
      myKey = TypedKey.create(debugName);
      mySourceKey = TypedKey.create(debugName + "/source");
      myOpenedKey = TypedKey.create(debugName + "/opened");
      mySourceAttribute = sourceAttribute;
      myTransform = transform;
    }

    /**
     * Replaces the plain text loaded by the editor with its editable form, and records what was shown.
     */
    @Override
    public void prepareValue(VersionSource source, EditItemModel model, ScalarFieldEditor<String> editor) {
      String rawSource = null;
      LongList items = model.getEditingItems();
      // One document belongs to one item. Editing several at once falls back to plain text, which is
      // lossless here only because an untouched field is never written back.
      if (items != null && items.size() == 1) rawSource = source.forItem(items.get(0)).getValue(mySourceAttribute);
      String editable = myTransform.toEditable(model.getValue(myKey), rawSource);
      model.putValue(myKey, editable);
      model.putValue(mySourceKey, rawSource);
      model.putValue(myOpenedKey, editable);
    }

    @Override
    protected String fromText(String text) {
      return Util.NN(text);
    }

    /**
     * No trimming: leading spaces are meaningful in this text, where an indented line can be a nested list
     * item or part of a code block.
     */
    @Override
    protected String toText(String value) {
      return Util.NN(value);
    }

    @Override
    public void setValue(EditModelState model, String value) {
      model.putValue(myKey, Util.NN(value));
    }

    /**
     * Replaces both halves, so the editor shows the rich value's own editable form and a later commit
     * rebuilds from that document rather than from whichever one the editor happened to open with.
     */
    @Override
    public void setValue(EditModelState model, String value, @Nullable String rawSource) {
      model.putValue(mySourceKey, rawSource);
      model.putValue(myKey, myTransform.toEditable(value, rawSource));
    }

    @Nullable
    @Override
    public DBAttribute<String> getSourceAttribute() {
      return mySourceAttribute;
    }

    @Nullable
    @Override
    public RichTextTransform getRichTextTransform() {
      return myTransform;
    }

    /**
     * Says what the editable form is and what the placeholder tokens are, since a user meeting one otherwise
     * has no way to know it stands for something real and must not be edited by hand.
     */
    @Nullable
    @Override
    public String getEditorTooltip() {
      return "<html>This field is edited as Markdown with text marks like <b>**bold**</b>, <i>_italic_</i>, <tt>`code`</tt>,"
        + " <tt>[label](url)</tt>, <tt>-</tt> for lists, etc.<br>"
        + "Items shown as <tt>⟦...⟧</tt> (tables, panels, images, and mentions) cannot be edited in this Client. To change these, use Jira in a web browser.<br>"
        + "These items are preserved during upload/download so you can safely edit the content around them; if you delete one you will be warned.";
    }

    @Override
    public void setText(EditModelState model, String newText) {
      String value = normalize(newText);
      if (Util.equals(model.getValue(myKey), value)) return;
      model.putValue(myKey, value);
    }

    private static String normalize(String text) {
      return Util.NN(text).replaceAll(StringUtil.LOCAL_LINE_SEPARATOR, "\n");
    }

    @NotNull
    @Override
    public String getText(EditModelState model) {
      return Util.NN(model.getValue(myKey));
    }

    @Override
    public String getValue(EditModelState model) {
      return getText(model);
    }

    @Override
    public String getInitialValue(EditModelState model) {
      return model.getValue(myOpenedKey);
    }

    /**
     * Changed means the text differs from what was first shown. Comparing against the snapshot rather than
     * the stored attribute lets an untouched rich field be left completely alone.
     */
    @Override
    public boolean isChanged(EditItemModel model) {
      return !Util.equals(getText(model), model.getValue(myOpenedKey));
    }

    @Override
    public boolean hasValue(EditModelState model) {
      return !getText(model).isEmpty();
    }

    /**
     * Warns when the edit would drop something the editable form only stands in for, such as an image or a
     * mention. Raised only just before a commit, so it does not interrupt typing.
     */
    @Override
    public void verifyData(DataVerification verifyContext, ScalarFieldEditor<String> editor) {
      if (verifyContext.getPurpose() == DataVerification.Purpose.EDIT_WARNING) return;
      EditModelState model = verifyContext.getModel();
      if (!Util.equals(getText(model), model.getValue(myOpenedKey))) {
        String loss = myTransform.checkLoss(getText(model), model.getValue(mySourceKey));
        if (loss != null) verifyContext.addError(editor, editor.getLabelText().getText() + " loses: " + loss);
      }
    }

    /**
     * Writes both halves, and only when the text actually changed: so opening an editor and closing it, or
     * editing some other field of the same item never rewrites a rich document.
     */
    @Override
    public void commitValue(CommitContext context, DBAttribute<String> attribute) {
      EditModelState model = context.getModel();
      String text = getText(model);
      if (Util.equals(text, model.getValue(myOpenedKey))) return;
      RichTextTransform.Result result = myTransform.fromEditable(text, model.getValue(mySourceKey));
      context.getCreator().setValue(attribute, result.getPlainText());
      context.getCreator().setValue(mySourceAttribute, result.getRawSource());
    }
  }

  public abstract static class Converting<T> extends ScalarValueKey<T> {
    private final TypedKey<T> myKey;
    private final TypedKey<String> myTextKey;
    @Nullable
    private final String myNullMessage;

    public Converting(String debugName, @Nullable String nullMessage) {
      myNullMessage = nullMessage;
      myKey = TypedKey.create(debugName + "/val");
      myTextKey = TypedKey.create(debugName + "/text");
    }

    @Override
    protected String toText(T value) {
      return value != null ? value.toString() : null;
    }

    protected abstract void verifyText(DataVerification verifyContext, FieldEditor editor, @NotNull String text);

    @Override
    public void setValue(EditModelState model, T value) {
      if (value != null) model.putValues(myKey, value, myTextKey, toText(value));
      else model.putValues(myKey, null, myTextKey, null);
    }

    @Override
    public void setText(EditModelState model, String newText) {
      newText = Text.normalizeText(newText);
      if (Util.equals(model.getValue(myTextKey), newText)) return;
      T value = fromText(newText);
      if (!Util.equals(model.getValue(myKey), value)) model.putValues(myTextKey, newText, myKey, value);
      else model.putValue(myTextKey, newText);
    }

    @NotNull
    @Override
    public String getText(EditModelState model) {
      String text = model.getValue(myTextKey);
      if (text == null) {
        T value = getValue(model);
        text = value != null ? toText(value) : null;
      }
      return Util.NN(text);
    }

    @Override
    public boolean isChanged(EditItemModel model) {
      return !model.isEqualValue(myKey);
    }

    @Override
    public T getInitialValue(EditModelState model) {
      return model.getInitialValue(myKey);
    }

    @Override
    public T getValue(EditModelState model) {
      return model.getValue(myKey);
    }

    @Override
    public void verifyData(DataVerification verifyContext, ScalarFieldEditor<T> editor) {
      EditModelState model = verifyContext.getModel();
      String text = model.getValue(myTextKey);
      if (text != null) verifyText(verifyContext, editor, text);
      else if (myNullMessage != null) verifyContext.addError(editor, myNullMessage);
    }
  }

  public static class Decimal extends Converting<BigDecimal> {
    public Decimal(String debugName) {
      super(debugName, null);
    }

    @Override
    protected BigDecimal fromText(String text) {
      try {
        return text != null ? new BigDecimal(text) : null;
      } catch (NumberFormatException e) {
        return null;
      }
    }

    protected void verifyText(DataVerification verifyContext, FieldEditor editor, @NotNull String text) {
      try {
        new BigDecimal(text);
      } catch (NumberFormatException e) {
        verifyContext.addError(editor, "Illegal value '" + text + "'");
      }
    }

    @Override
    public void commitValue(CommitContext context, DBAttribute<BigDecimal> attribute) {
      context.getCreator().setValue(attribute, getValue(context.getModel()));
    }

    @Override
    public boolean hasValue(EditModelState model) {
      return getValue(model) != null;
    }
  }
}

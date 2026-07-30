package com.almworks.items.gui.edit.merge;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.gui.edit.EditItemModel;
import com.almworks.items.gui.edit.FieldEditor;
import com.almworks.items.gui.edit.editors.text.RichTextTransform;
import com.almworks.items.gui.edit.util.BaseScalarFieldEditor;
import com.almworks.util.LogHelper;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.renderer.CellState;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;

public class ScalarMergeValue<T>  extends BaseScalarMergeValue<T>{
  private final BaseScalarFieldEditor<T> myEditor;

  private ScalarMergeValue(String displayName, EditItemModel model, BaseScalarFieldEditor<T> editor, T[] values, String[] sources,
    @Nullable RichTextTransform transform, long item) {
    super(displayName, item, values, sources, transform, model);
    myEditor = editor;
  }

  /**
   * Shows the editable form of a rich value rather than its extracted text, so two versions differing only
   * in formatting do not appear identical in the merge table.
   */
  @Override
  public void render(CellState state, Canvas canvas, int version) {
    String plainText = myEditor.convertToText(getValue(version));
    String source = getSource(version);
    RichTextTransform transform = myEditor.getRichTextTransform();
    String text = transform != null && source != null ? transform.toEditable(plainText, source) : plainText;
    // The chosen side is emphasized, the way slave values already do it. Rows stay listed after a choice is
    // made, so without this there would be nothing showing which side is currently selected.
    if (getChosenVersion() == version) canvas.setFontStyle(Font.BOLD);
    if (text != null) canvas.appendText(text);
  }

  /**
   * Carries the chosen version's rich form across, not only its text. Without the source, taking the remote
   * value would put plain text into the editor and the merge would flatten the very formatting it exists to
   * preserve.
   */
  @Override
  protected void doSetResolution(int version) {
    myEditor.setValue(getModel(), getValue(version), getSource(version));
  }

  @Override
  protected FieldEditor getEditor() {
    return myEditor;
  }

  public static <T> MergeValue load(DBReader reader, EditItemModel model, BaseScalarFieldEditor<T> editor) {
    LogHelper.assertError(model.getAllEditors().contains(editor), editor);
    long item = getSingleItem(model);
    if (item <= 0) return null;
    String displayName = editor.getLabelText(model).getText();
    T[] values = (T[]) new Object[3];
    for (int i = 0; i < 3; i++) values[i] = loadValue(reader, item, editor.getAttribute(), i);
    // A rich-text field also has a document per version. A plain field leaves these null and compares as before.
    String[] sources = new String[3];
    DBAttribute<String> sourceAttribute = editor.getSourceAttribute();
    if (sourceAttribute != null) for (int i = 0; i < 3; i++) sources[i] = loadValue(reader, item, sourceAttribute, i);
    return new ScalarMergeValue<T>(displayName, model, editor, values, sources, editor.getRichTextTransform(), item);
  }
}

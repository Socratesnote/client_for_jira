package com.almworks.items.gui.edit.util;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.gui.edit.EditItemModel;
import com.almworks.items.gui.edit.EditModelState;
import com.almworks.items.gui.edit.editors.text.RichTextTransform;
import com.almworks.items.sync.util.ItemValues;
import com.almworks.util.LogHelper;
import com.almworks.util.text.NameMnemonic;
import gnu.trove.TLongObjectHashMap;
import org.jetbrains.annotations.Nullable;

public abstract class BaseScalarFieldEditor<V> extends BaseFieldEditor {
  private final DBAttribute<V> myAttribute;

  public BaseScalarFieldEditor(NameMnemonic labelText, DBAttribute<V> attribute) {
    super(labelText);
    myAttribute = attribute;
  }

  @Override
  public final void onItemsChanged(EditItemModel model, TLongObjectHashMap<ItemValues> newValues) {
    LongList items = model.getEditingItems();
    TLongObjectHashMap<ItemValues> filtered = new TLongObjectHashMap<>();
    DBAttribute<?> attribute = getAttribute();
    for (int i = 0; i < items.size(); i++) {
      long item = items.get(i);
      ItemValues values = newValues.get(item);
      if (values != null && values.indexOf(attribute) >= 0) filtered.put(item, values);
    }
    if (!filtered.isEmpty()) onItemsAttributeChanged(model, filtered);
  }

  private void onItemsAttributeChanged(EditItemModel model, TLongObjectHashMap<ItemValues> filtered) {
    LongList items = model.getEditingItems();
    if (items.size() == 1) {
      ItemValues values = filtered.get(items.get(0));
      if (values != null)
        setValue(model, values.getValue(myAttribute)); // todo set initial JC-183
    } else
      LogHelper.assertError(model.getEditingItems().isEmpty(), "Multiple concurrent update not supported",
        model.getEditingItems(), getAttribute());
  }

  public final DBAttribute<V> getAttribute() {
    return myAttribute;
  }

  public abstract String convertToText(V value);

  public abstract void setValue(EditModelState model, V value);

  /**
   * Companion attribute holding the rich value beside the plain text, when this editor has one.
   * @return null for a plain-text editor, which is the default
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
   * Sets the value together with the rich form it came from. Falls back to the plain value for editors that
   * have no rich form.
   */
  public void setValue(EditModelState model, V value, @Nullable String rawSource) {
    setValue(model, value);
  }
}

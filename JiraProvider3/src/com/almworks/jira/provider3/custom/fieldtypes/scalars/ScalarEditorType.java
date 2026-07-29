package com.almworks.jira.provider3.custom.fieldtypes.scalars;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.gui.edit.FieldEditor;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.util.BadUtil;
import com.almworks.jira.provider3.schema.CustomField;
import com.almworks.util.text.NameMnemonic;
import org.jetbrains.annotations.Nullable;

public abstract class ScalarEditorType<T> {
  private final Class<T> myScalarClass;

  public ScalarEditorType(Class<T> scalarClass) {
    myScalarClass = scalarClass;
  }

  public FieldEditor createEditor(ItemVersion field) {
    final DBAttribute<T> attribute = readScalarAttr(field, myScalarClass);
    if(attribute == null) {
      return null;
    }

    final String name = readName(field);
    if(name == null) {
      return null;
    }

    return createEditor(NameMnemonic.rawText(name), attribute, readAdfAttr(field));
  }

  protected abstract FieldEditor createEditor(NameMnemonic name, DBAttribute<T> attribute);

  /**
   * @param adfAttribute companion attribute holding the field's rich value, null for kinds that have none and
   * for a rich-text field not yet re-synced since the companion was introduced.
   */
  protected FieldEditor createEditor(NameMnemonic name, DBAttribute<T> attribute, @Nullable DBAttribute<String> adfAttribute) {
    return createEditor(name, attribute);
  }

  @Nullable
  private static DBAttribute<String> readAdfAttr(ItemVersion field) {
    Long attr = field.getValue(CustomField.ADF_ATTRIBUTE);
    if (attr == null || attr <= 0) return null;
    return BadUtil.castScalar(String.class, BadUtil.getAttribute(field.getReader(), attr));
  }

  private static <T> DBAttribute<T> readScalarAttr(ItemVersion field, Class<T> scalarClass) {
    final DBAttribute<?> rawAttr = BadUtil.getAttribute(field.getReader(), field.getValue(CustomField.ATTRIBUTE));
    if(rawAttr == null) {
      return null;
    }
    return BadUtil.castScalar(scalarClass, rawAttr);
  }

  private static String readName(ItemVersion field) {
    final String id = field.getValue(CustomField.ID);
    final String name = field.getValue(CustomField.NAME);
    return name == null ? id : name;
  }
}

package com.almworks.jira.provider3.custom.fieldtypes.scalars;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.entities.api.Entity;
import com.almworks.items.entities.api.EntityKey;
import com.almworks.items.entities.api.collector.transaction.EntityHolder;
import com.almworks.items.entities.api.util.EntityKeyProperties;
import com.almworks.items.gui.edit.FieldEditor;
import com.almworks.items.gui.meta.util.FieldInfo;
import com.almworks.items.gui.meta.util.ScalarFieldInfo;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.items.sync.util.identity.DBIdentity;
import com.almworks.items.sync.util.identity.ScalarSequence;
import com.almworks.items.util.BadUtil;
import com.almworks.jira.provider3.custom.FieldKind;
import com.almworks.jira.provider3.custom.FieldType;
import com.almworks.jira.provider3.custom.fieldtypes.CommonFieldInfo;
import com.almworks.jira.provider3.custom.fieldtypes.ConvertorFactory;
import com.almworks.jira.provider3.remotedata.issue.fields.IssueFieldDescriptor;
import com.almworks.jira.provider3.remotedata.issue.fields.scalar.ScalarFieldDescriptor;
import com.almworks.jira.provider3.remotedata.issue.fields.scalar.ScalarProperties;
import com.almworks.jira.provider3.schema.CustomField;
import com.almworks.jira.provider3.sync.download2.meta.FieldSetup;
import com.almworks.jira.provider3.sync.jql.JQLConvertor;
import com.almworks.jira.provider3.sync.schema.ServerCustomField;
import com.almworks.jira.provider3.sync.schema.ServerJira;
import com.almworks.util.LogHelper;
import com.almworks.util.Pair;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ScalarKind<T> implements FieldKind, FieldSetup {
  private final String myPrefix;
  private final CommonFieldInfo myFieldInfo;
  private final ScalarConvertor<T> myConvertor;
  private final ConvertorFactory myRemoteSearch;
  private final ScalarEditorType<?> myEditorType;
  private final ScalarProperties<T> myScalarProperties;

  private ScalarKind(ScalarConvertor<T> convertor, String prefix, CommonFieldInfo fieldInfo, ConvertorFactory remoteSearch, ScalarEditorType<?> editorType,
    ScalarProperties<T> scalarProperties) {
    myConvertor = convertor;
    myPrefix = prefix;
    myFieldInfo = fieldInfo;
    myRemoteSearch = remoteSearch;
    myEditorType = editorType;
    myScalarProperties = scalarProperties;
  }

  @NotNull
  public static <T> ScalarKind<T> create(ScalarConvertor<T> convertor, String prefix, CommonFieldInfo fieldInfo, @Nullable ConvertorFactory remoteSearch,
    ScalarEditorType<?> editorType, ScalarProperties<T> scalarProperties) {
    return new ScalarKind<T>(convertor, prefix, fieldInfo, remoteSearch, editorType, scalarProperties);
  }

  /**
   * Suffix of the companion key holding the raw ADF document of a rich-text field. A field id never contains
   * a dot after its prefix segment, so this cannot collide with another field's key.
   */
  private static final String ADF_SUFFIX = ".adf";

  /**
   * Companion key for rich-text kinds, built from the same full id as the value key.
   * @return null when this kind is not rich text, which is what clears the link on a kind change
   */
  @Nullable
  private EntityKey<String> createAdfKey(String fullId) {
    if (!myScalarProperties.isRichText()) return null;
    return EntityKey.scalar(fullId + ADF_SUFFIX, String.class, EntityKeyProperties.shadowable());
  }

  @Nullable
  public EntityKey<T> createEntityKey(EntityHolder field) {
    String fullId = ServerCustomField.createFullId(field, myPrefix);
    if (fullId == null) return null;
    Entity description = myScalarProperties.isEditSupported() ? EntityKeyProperties.shadowable() : null;
    EntityKey<T> key = EntityKey.scalar(fullId, myScalarProperties.getScalarClass(), description);
    field.setValue(ServerCustomField.ATTRIBUTE, key.toEntity());
    EntityKey<String> adfKey = createAdfKey(fullId);
    // Written even when null, so a field that changes to a kind without rich text drops its stale companion.
    field.setValue(ServerCustomField.ADF_ATTRIBUTE, adfKey != null ? adfKey.toEntity() : null);
    return key;
  }

  @Override
  public Field createFieldsDescriptor(String fieldId, String connectionId, String fieldName) {
    String fullId = ServerCustomField.createFullId(connectionId, myPrefix, fieldId);
    Entity description = myScalarProperties.isEditSupported() ? EntityKeyProperties.shadowable() : null;
    final EntityKey<T> key = EntityKey.scalar(fullId, myScalarProperties.getScalarClass(), description);
    IssueFieldDescriptor descriptor =
      ScalarFieldDescriptor.create(fieldId, myScalarProperties, key, createAdfKey(fullId), fieldName, myScalarProperties.isEditSupported());
    return new Field(descriptor, null);
  }

  @Override
  public void setupField(EntityHolder field) {
    createEntityKey(field);
  }

  @Override
  public void migrateField(ItemVersionCreator field) throws FieldType.MigrationProblem {
    Pair<String, String> pair = FieldType.getConnectionFieldIds(field);
    String connectionId = pair.getFirst();
    String fieldId = pair.getSecond();
    String fullId = ServerCustomField.createFullId(connectionId, myPrefix, fieldId);
    EntityKey<T> key = EntityKey.scalar(fullId, myScalarProperties.getScalarClass(), myScalarProperties.isEditSupported() ? EntityKeyProperties.shadowable() : null);
    DBAttribute<?> attribute = FieldType.MigrationProblem.ensureCanMigrateAttribute(field, ServerJira.createAttributeInfo(key));
    field.setValue(CustomField.ATTRIBUTE, attribute);
    EntityKey<String> adfKey = createAdfKey(fullId);
    DBAttribute<?> adfAttribute = adfKey != null ? FieldType.MigrationProblem.ensureCanMigrateAttribute(field, ServerJira.createAttributeInfo(adfKey)) : null;
    // Set unconditionally: a field that moved to a kind without rich text must lose the companion, not keep a stale one.
    field.setValue(CustomField.ADF_ATTRIBUTE, adfAttribute);
  }

  @Override
  public FieldInfo createFieldInfo(ItemVersion field, String id, String name, DBAttribute<?> a, DBIdentity connection, String connectionIdPrefix,
    ScalarSequence applicability)
  {
    Class<T> scalarClass = myScalarProperties.getScalarClass();
    final DBAttribute<T> attribute = BadUtil.castScalar(scalarClass, a);
    if (attribute == null) {
      LogHelper.error("Wrong attribute", a, id, name, scalarClass, a.getScalarClass(), a.getComposition(), field.getValue(CustomField.KEY));
      return null;
    }
    ScalarFieldInfo<T> info = myConvertor.createFieldInfo(connectionIdPrefix, id);
    info
      .setAttribute(attribute)
      .setOwner(connection)
      .setId(id)
      .setConstraintId(name != null ? name : id)
      .setDisplayName(name)
      .setApplicability(applicability);
    myFieldInfo.update(info);
    return myFieldInfo.wrapFieldInfo(info);
  }

  @Override
  public JQLConvertor getJqlSearch(ItemVersion field) {
    return myRemoteSearch != null ? myRemoteSearch.createJql(field) : null;
  }

  @Override
  public boolean isEditable() {
    return myScalarProperties.isEditSupported();
  }

  @Override
  public FieldEditor createEditor(ItemVersion field) {
    return myScalarProperties.isEditSupported() && myEditorType != null ? myEditorType.createEditor(field) : null;
  }

  @Override
  public <T> T getExtension(TypedKey<T> extensionKey) {
    if (SETUP_FIELD.equals(extensionKey)) return extensionKey.cast(this);
    return null;
  }

  public static abstract class ScalarConvertor<T> {

    public abstract ScalarFieldInfo<T> createFieldInfo(String connectionIdPrefix, String id);
  }
}

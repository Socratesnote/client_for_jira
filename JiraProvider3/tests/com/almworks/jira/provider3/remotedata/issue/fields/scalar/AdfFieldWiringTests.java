package com.almworks.jira.provider3.remotedata.issue.fields.scalar;

import com.almworks.jira.provider3.custom.fieldtypes.scalars.TextFieldType;
import com.almworks.jira.provider3.remotedata.issue.fields.IssueFields;
import com.almworks.util.tests.BaseTestCase;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Which text fields upload as ADF and which stay plain is decided by a single properties choice per field, so a
 * swapped entry compiles cleanly and only shows up as an HTTP 400 against a live server. These tests pin the
 * choice instead. Rich text on api/3 means description, environment and textarea custom fields; summary, short
 * text and url are plain.
 */
public class AdfFieldWiringTests extends BaseTestCase {
  private static ScalarUploadType<?> uploadTypeOf(ScalarFieldDescriptor<?> descriptor) throws Exception {
    Method getProperties = ScalarFieldDescriptor.class.getDeclaredMethod("getProperties");
    getProperties.setAccessible(true);
    return ((ScalarProperties<?>) getProperties.invoke(descriptor)).getUploadType();
  }

  public void testIssueFieldsUploadTypes() throws Exception {
    assertSame(ScalarUploadType.ADF_TEXT, uploadTypeOf(IssueFields.DESCRIPTION));
    assertSame(ScalarUploadType.ADF_TEXT, uploadTypeOf(IssueFields.ENVIRONMENT));
    // Summary is plain text on api/3. Wrapping it in ADF breaks every issue create, since summary is mandatory.
    assertSame(ScalarUploadType.TEXT, uploadTypeOf(IssueFields.SUMMARY));
  }

  public void testCustomFieldKindProperties() throws Exception {
    // Only the textarea custom field type maps to longText, and it is the one custom-field kind that is rich text.
    assertSame(ScalarFieldDescriptor.EDITABLE_ADF_TEXT, editablePropertiesOf("longText"));
    assertSame(ScalarFieldDescriptor.EDITABLE_TEXT, editablePropertiesOf("shortText"));
    assertSame(ScalarFieldDescriptor.EDITABLE_TEXT, editablePropertiesOf("url"));
  }

  /**
   * Only rich text carries a companion attribute for the server's own document. A companion on a plain field
   * would store a document nothing maintains; a missing one on a rich field silently reinstates flattening.
   */
  public void testRichTextFlagFollowsTheUploadType() {
    assertTrue(ScalarFieldDescriptor.EDITABLE_ADF_TEXT.isRichText());
    assertFalse(ScalarFieldDescriptor.EDITABLE_TEXT.isRichText());
    assertFalse(ScalarFieldDescriptor.READONLY_TEXT.isRichText());
    assertFalse(ScalarFieldDescriptor.EDITABLE_DATE.isRichText());
  }

  public void testOnlyRichTextIssueFieldsHaveAdfCompanion() {
    assertNotNull(IssueFields.DESCRIPTION.getAdfEntityKey());
    assertNotNull(IssueFields.ENVIRONMENT.getAdfEntityKey());
    assertNull(IssueFields.SUMMARY.getAdfEntityKey());
    // The two companions must be distinct, or one field would overwrite the other's document.
    assertFalse(IssueFields.DESCRIPTION.getAdfEntityKey().equals(IssueFields.ENVIRONMENT.getAdfEntityKey()));
  }

  private static Object editablePropertiesOf(String kind) throws Exception {
    Field kinds = TextFieldType.class.getDeclaredField("KINDS");
    kinds.setAccessible(true);
    Object info = ((Map<?, ?>) kinds.get(null)).get(kind);
    assertNotNull("No such text kind: " + kind, info);
    Field properties = info.getClass().getDeclaredField("myEditableProperties");
    properties.setAccessible(true);
    return properties.get(info);
  }
}

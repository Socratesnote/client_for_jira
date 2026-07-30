package com.almworks.jira.provider3.remotedata.issue.fields.scalar;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.entities.api.EntityKey;
import com.almworks.items.entities.api.collector.transaction.EntityHolder;
import com.almworks.items.sync.ItemVersion;
import com.almworks.jira.provider3.remotedata.issue.EditIssueRequest;
import com.almworks.jira.provider3.remotedata.issue.fields.BaseValue;
import com.almworks.jira.provider3.remotedata.issue.fields.IssueFieldDescriptor;
import com.almworks.jira.provider3.remotedata.issue.fields.IssueFieldValue;
import com.almworks.jira.provider3.services.upload.PostUploadContext;
import com.almworks.jira.provider3.services.upload.UploadJsonUtil;
import com.almworks.jira.provider3.sync.download2.details.JsonIssueField;
import com.almworks.jira.provider3.sync.download2.details.fields.AdfScalarField;
import com.almworks.jira.provider3.sync.download2.details.fields.ScalarField;
import com.almworks.jira.provider3.sync.download2.rest.AdfCanonical;
import com.almworks.jira.provider3.sync.download2.rest.AdfText;
import com.almworks.jira.provider3.sync.schema.ServerJira;
import com.almworks.restconnector.json.JsonKey;
import com.almworks.restconnector.operations.RestServerInfo;
import com.almworks.util.LogHelper;
import com.almworks.util.collections.Convertor;
import com.almworks.util.collections.Equality;
import org.almworks.util.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;

public class ScalarFieldDescriptor<T> extends IssueFieldDescriptor {
  private static final Convertor<BigDecimal, String> DISPLAY_DECIMAL = new Convertor<BigDecimal, String>() {
    private final DecimalFormat myFormat;
    {
      DecimalFormat format = new DecimalFormat();
      format.setMinimumFractionDigits(0);
      format.setDecimalSeparatorAlwaysShown(false);
      format.setGroupingUsed(false);
      format.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance());
      myFormat = format;
    }

    @Override
    public String convert(BigDecimal value) {
      return value == null ? M_NO_VALUE.create() : myFormat.format(value);
    }
  };

  private static final Equality<BigDecimal> DECIMAL_EQUALITY = new Equality<BigDecimal>() {
    private final DecimalFormat myFormat;
    {
      DecimalFormat format = new DecimalFormat();
      format.setMinimumFractionDigits(0);
      format.setDecimalSeparatorAlwaysShown(false);
      format.setGroupingUsed(false);
      format.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
      myFormat = format;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean areEqual(BigDecimal a, BigDecimal b) {
      if (Util.equals(a, b)) return true;
      if (a == null || b == null) return false;
      return myFormat.format(a).equals(myFormat.format(b));
    }
  };

  /**
   * Plain-text equality, still the right comparison for fields that hold no formatting, such as summary.
   * Rich-text fields compare their ADF documents instead - see MyValue.areEqual - because two documents that
   * differ only in formatting extract to identical text.
   */
  public static final Equality<String> TEXT_EQUALITY = new Equality<String>() {
    @Override
    public boolean areEqual(String a, String b) {
      if (Util.equals(a, b)) return true;
      a = ScalarUploadType.Text.normalize(a);
      b = ScalarUploadType.Text.normalize(b);
      return Util.equals(a, b);
    }
  };
  public static final Equality<Date> DATE_EQUALITY = new Equality<Date>() {
    @Override
    public boolean areEqual(Date o1, Date o2) {
      if (Util.equals(o1, o2)) return true;
      if (o1 == null || o2 == null) return false;
      long time1 = o1.getTime();
      long time2 = o2.getTime();
      return (time1 / 1000) == (time2 / 1000); // Reduce precision to seconds when comparing. In some cases JIRA adds millis (observed: right after workflow action has been started)
    }
  };
  /**
   * Shows long time because of dates are different if seconds are different.
   */
  public static final Convertor<Date, String> DATE_TO_DISPLAYABLE = new Convertor<Date, String>() {
    @Override
    public String convert(Date value) {
      if (value == null) return IssueFieldDescriptor.M_NO_VALUE.create();
      return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG).format(new Date(value.getTime()));
    }
  };

  public static final ScalarProperties<String> EDITABLE_TEXT =
    new ScalarProperties<String>(JsonKey.emptyTextToNull(AdfText.adfAware(JsonKey.TEXT_TRIM)), ScalarUploadType.TEXT, TEXT_EQUALITY, Convertor.<String>identity(), String.class);
  /**
   * For rich-text fields, which api/3 requires as ADF documents. Differs from {@link #EDITABLE_TEXT} in the
   * upload type - reading already accepts ADF for every text field - and in being rich text, which gives the
   * field a companion attribute holding the server's own document.
   */
  public static final ScalarProperties<String> EDITABLE_ADF_TEXT =
    new ScalarProperties<String>(JsonKey.emptyTextToNull(AdfText.adfAware(JsonKey.TEXT_TRIM)), ScalarUploadType.ADF_TEXT, TEXT_EQUALITY, Convertor.<String>identity(), String.class, true);
  public static final ScalarProperties<String> READONLY_TEXT =
    new ScalarProperties<String>(AdfText.adfAware(JsonKey.TEXT_TRIM_TO_NULL), null, TEXT_EQUALITY, Convertor.<String>identity(), String.class);
  public static final ScalarProperties<Date> EDITABLE_DATE =
    new ScalarProperties<Date>(JsonKey.DATE_TIME, ScalarUploadType.DATE, DATE_EQUALITY, DATE_TO_DISPLAYABLE, Date.class);
  public static final ScalarProperties<Date> READ_ONLY_DATE =
    new ScalarProperties<Date>(JsonKey.DATE_TIME, null, DATE_EQUALITY, DATE_TO_DISPLAYABLE, Date.class);
  public static final ScalarProperties<Integer>
    EDITABLE_DAYS = new ScalarProperties<Integer>(JsonKey.DAYS_DATE, ScalarUploadType.DAYS, Equality.GENERAL, DaysUpload.INSTANCE, Integer.class);
  public static final ScalarProperties<Integer> READONLY_DAYS = new ScalarProperties<Integer>(JsonKey.DAYS_DATE, null, Equality.GENERAL, DaysUpload.INSTANCE, Integer.class);
  public static final ScalarProperties<BigDecimal>
    EDITABLE_DECIMAL = new ScalarProperties<BigDecimal>(JsonKey.DECIMAL, ScalarUploadType.DECIMAL, DECIMAL_EQUALITY, DISPLAY_DECIMAL, BigDecimal.class);
  public static final ScalarProperties<BigDecimal> READONLY_DECIMAL = new ScalarProperties<BigDecimal>(JsonKey.DECIMAL, null, DECIMAL_EQUALITY, DISPLAY_DECIMAL, BigDecimal.class);

  private final ScalarProperties<T> myScalarProperties;
  private final EntityKey<T> myKey;
  private final DBAttribute<T> myAttribute;
  @Nullable
  private final EntityKey<String> myAdfKey;
  @Nullable
  private final DBAttribute<String> myAdfAttribute;
  private final boolean myCheckConflict;

  /**
   * @param checkConflict if true - create upload value to check conflict even when upload is not supported.<br>
   *                      If false - loads value (and checks conflict) iff upload is supported
   * @param adfKey companion key holding the server's raw ADF document. Null for everything but rich text
   */
  private ScalarFieldDescriptor(String fieldId, ScalarProperties<T> scalarProperties, EntityKey<T> key, @Nullable EntityKey<String> adfKey, String displayName,
    boolean checkConflict) {
    super(fieldId, displayName);
    myScalarProperties = scalarProperties;
    myKey = key;
    myCheckConflict = checkConflict;
    myAttribute = ServerJira.toScalarAttribute(myKey);
    myAdfKey = scalarProperties.isRichText() ? adfKey : null;
    myAdfAttribute = myAdfKey != null ? ServerJira.toScalarAttribute(myAdfKey) : null;
    LogHelper.assertError(adfKey == null || scalarProperties.isRichText(), "ADF companion on a field that is not rich text", fieldId);
  }

  public static ScalarFieldDescriptor<String> editableText(String fieldId, String displayName, EntityKey<String> entityKey) {
    return new ScalarFieldDescriptor<String>(fieldId, EDITABLE_TEXT, entityKey, null, displayName, true);
  }

  /**
   * For rich-text fields. Plain-text fields such as summary must use {@link #editableText}.
   * @param adfEntityKey companion key storing the server's own ADF document, so an edit rebuilds it rather
   * than replacing it with flat paragraphs
   */
  public static ScalarFieldDescriptor<String> editableAdfText(String fieldId, String displayName, EntityKey<String> entityKey, EntityKey<String> adfEntityKey) {
    return new ScalarFieldDescriptor<String>(fieldId, EDITABLE_ADF_TEXT, entityKey, adfEntityKey, displayName, true);
  }

  public static ScalarFieldDescriptor<Date> readonlyDateTime(String fieldId, String displayName, EntityKey<Date> entityKey, boolean checkConflict) {
    return new ScalarFieldDescriptor<Date>(fieldId, READ_ONLY_DATE, entityKey, null, displayName, checkConflict);
  }

  public static ScalarFieldDescriptor<Integer> editableDays(String fieldId, String displayName, EntityKey<Integer> entityKey) {
    return new ScalarFieldDescriptor<Integer>(fieldId, EDITABLE_DAYS, entityKey, null, displayName, true);
  }

  public static <T> ScalarFieldDescriptor<T> create(String fieldId, ScalarProperties<T> scalarProperties, EntityKey<T> key, String displayName, boolean checkConflict) {
    return create(fieldId, scalarProperties, key, null, displayName, checkConflict);
  }

  /**
   * @param adfKey companion key for rich-text kinds, built by the caller alongside the value key. Ignored
   * when the properties are not rich text
   */
  public static <T> ScalarFieldDescriptor<T> create(String fieldId, ScalarProperties<T> scalarProperties, EntityKey<T> key, @Nullable EntityKey<String> adfKey, String displayName,
    boolean checkConflict) {
    return new ScalarFieldDescriptor<T>(fieldId, scalarProperties, key, adfKey, displayName, checkConflict);
  }

  @Override
  public IssueFieldValue load(ItemVersion trunk, ItemVersion base) {
    ScalarUploadType<T> uploadType = myScalarProperties.getUploadType();
    if (uploadType == null && !myCheckConflict) return null;
    T change = trunk.getValue(myAttribute);
    T expected = base.getValue(myAttribute);
    String changeAdf = myAdfAttribute != null ? trunk.getValue(myAdfAttribute) : null;
    String expectedAdf = myAdfAttribute != null ? base.getValue(myAdfAttribute) : null;
    return new MyValue<T>(this, expected, change, expectedAdf, changeAdf);
  }

  @NotNull
  @Override
  public EntityKey<T> getIssueEntityKey() {
    return myKey;
  }

  @Nullable
  @Override
  public EntityKey<String> getAdfEntityKey() {
    return myAdfKey;
  }

  @Override
  public JsonIssueField createDownloadField() {
    if (myAdfKey == null) return ScalarField.independent(myKey, myScalarProperties.getFromJson());
    return AdfScalarField.create(myKey, myScalarProperties.getFromJson(), myAdfKey);
  }

  @Nullable
  public T findChangeValue(Collection<? extends IssueFieldValue> values) {
    for (IssueFieldValue value : values) {
      @SuppressWarnings("unchecked")
      MyValue<T> myValue = Util.castNullable(MyValue.class, value);
      if (myValue != null && myValue.myDescriptor == this) return myValue.myChange;
    }
    return null;
  }

  public DBAttribute<T> getAttribute() {
    return myAttribute;
  }

  public String getConflictMessage(T expected, T actual) {
    return createConflictMessage(getDisplayName(), myScalarProperties.toDisplayable(expected), myScalarProperties.toDisplayable(actual));
  }

  private ScalarProperties<T> getProperties() {
    return myScalarProperties;
  }

  @Override
  public String toString() {
    return "Scalar(" + myKey + ")";
  }

  private static class MyValue<T> extends BaseValue {
    private static final String SET = "set";
    private final ScalarFieldDescriptor<T> myDescriptor;

    private final T myExpected;
    private final T myChange;
    /** The server's own document at edit start, and the one the edit produced. Null when the field has no ADF. */
    @Nullable
    private final String myExpectedAdf;
    @Nullable
    private final String myChangeAdf;

    public MyValue(ScalarFieldDescriptor<T> descriptor, T expected, T change, @Nullable String expectedAdf, @Nullable String changeAdf) {
      super(descriptor.getProperties().isEditSupported());
      myDescriptor = descriptor;
      myExpected = expected;
      myChange = change;
      myExpectedAdf = expectedAdf;
      myChangeAdf = changeAdf;
    }

    /**
     * Compares two states of the field, preferring the ADF documents when both are present.<br>
     * Extracted plain text is equal for two documents that differ only in formatting, so comparing text alone
     * hides a formatting-only change in either direction. Values stored before the companion attribute
     * existed have no document, and those fall back to the text comparison until the next sync fills them in.
     */
    private boolean areEqual(T text, T otherText, @Nullable String adf, @Nullable String otherAdf) {
      if (adf != null && otherAdf != null) return AdfCanonical.areEqualRaw(adf, otherAdf);
      return myDescriptor.getProperties().areEqual(text, otherText);
    }

    @Override
    public IssueFieldDescriptor getDescriptor() {
      return myDescriptor;
    }

    @NotNull
    @Override
    public String[] getFormValue(RestServerInfo serverInfo) {
      T value = myChange;
      if (value == null) value = myExpected;
      if (value == null) return new String[]{""};
      return new String[]{myDescriptor.getProperties().convertToForm(value, serverInfo)};
    }

    @Override
    public String checkInitialState(EntityHolder issue) {
      T server = issue.getScalarValue(myDescriptor.getIssueEntityKey());
      if (areEqual(server, myExpected, serverAdf(issue), myExpectedAdf)) return null;
      return myDescriptor.getConflictMessage(myExpected, server);
    }

    @Nullable
    private String serverAdf(EntityHolder issue) {
      EntityKey<String> adfKey = myDescriptor.getAdfEntityKey();
      return adfKey != null ? issue.getScalarValue(adfKey) : null;
    }

    /**
     * Sends the document the edit produced rather than rebuilding one from plain text, so formatting the
     * client cannot express still reaches the server intact.
     */
    @Override
    public void addChange(EditIssueRequest edit) {
      ScalarUploadType<T> uploadType = myDescriptor.getProperties().getUploadType();
      if (uploadType == null) return;
      String fieldId = myDescriptor.getFieldId();
      if (edit.needsUpload(fieldId, SET, needsUpload(edit.getServerInfo()))) {
        Object jsonValue = myChangeAdf != null ? AdfCanonical.parse(myChangeAdf) : null;
        // Falls back for values that never had a document, and for a stored document that will not parse.
        if (jsonValue == null) jsonValue = uploadType.toJsonValue(myChange, edit.getServerInfo());
        //noinspection unchecked
        edit.addEdit(this, fieldId, UploadJsonUtil.singleObjectElementArray(SET, jsonValue));
      }
    }

    /**
     * Deliberately compares the extracted text rather than the documents.<br>
     * The question here is only whether the edit landed, and JIRA may normalize a document the client built -
     * assigning localId and the like - so a document comparison can report a successful upload as failed and
     * leave the item locally changed for ever. Formatting differences matter in {@link #isChanged} and
     * {@link #checkInitialState}, not here.
     */
    @Override
    protected void doFinishUpload(long issueItem, EntityHolder issue, PostUploadContext context) {
      if (myDescriptor.getProperties().isEditSupported()) {
        if (!isDone()) return;
        T server = issue.getScalarValue(myDescriptor.getIssueEntityKey());
        if (!myDescriptor.getProperties().areEqual(myChange, server)) {
          LogHelper.debug("Not uploaded", issueItem, this, server);
          return;
        }
      }
      context.reportUploaded(issueItem, myDescriptor.getAttribute());
      // The commit wrote both halves, so both must be reported. Reporting only the text leaves the companion
      // looking like an unuploaded local change, and the item never stops showing as modified.
      DBAttribute<String> adfAttribute = myDescriptor.myAdfAttribute;
      if (adfAttribute != null) context.reportUploaded(issueItem, adfAttribute);
    }

    public boolean isChanged() {
      return !areEqual(myExpected, myChange, myExpectedAdf, myChangeAdf);
    }

    @Override
    public String toString() {
      return "Upload " + myDescriptor + "[" + myExpected + "->" + myChange + "]";
    }
  }


  private static class DaysUpload extends Convertor<Integer, String> {
    public static final DaysUpload INSTANCE = new DaysUpload();

    @Override
    public String convert(Integer value) {
      if (value == null) return M_NO_VALUE.create();
      return DateFormat.getDateInstance(DateFormat.SHORT).format(value);
    }
  }
}

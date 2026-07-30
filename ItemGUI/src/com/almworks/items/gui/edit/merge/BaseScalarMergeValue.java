package com.almworks.items.gui.edit.merge;

import com.almworks.items.gui.edit.EditItemModel;
import com.almworks.items.gui.edit.editors.text.RichTextTransform;
import com.almworks.util.LogHelper;
import org.almworks.util.Util;
import org.jetbrains.annotations.Nullable;

public abstract class BaseScalarMergeValue<T> extends MergeValue.Simple {
  private final EditItemModel myModel;
  private final T[] myValues;
  /** The rich form of each version, when the field has one. Null entries mean there is nothing richer than the text. */
  private final String[] mySources;
  @Nullable
  private final RichTextTransform myTransform;

  public BaseScalarMergeValue(String displayName, long item, T[] values, EditItemModel model) {
    this(displayName, item, values, new String[3], null, model);
  }

  public BaseScalarMergeValue(String displayName, long item, T[] values, String[] sources, @Nullable RichTextTransform transform, EditItemModel model) {
    super(displayName, item);
    myValues = values;
    mySources = sources;
    myTransform = transform;
    myModel = model;
  }

  @Override
  public boolean isConflict() {
    return differs(LOCAL, BASE) && differs(REMOTE, BASE) && differs(LOCAL, REMOTE);
  }

  @Override
  public boolean isChanged(boolean remote) {
    return differs(remote ? REMOTE : LOCAL, BASE);
  }

  /**
   * Compares two versions, preferring their rich forms.<br>
   * Comparing the extracted text alone would report two versions as identical when they differ only in
   * formatting, so a formatting-only change would never appear in the merge table and could not be resolved.
   */
  private boolean differs(int version, int other) {
    String source = getSource(version);
    String otherSource = getSource(other);
    if (myTransform != null && source != null && otherSource != null) return !myTransform.isSameSource(source, otherSource);
    return !Util.equals(getValue(version), getValue(other));
  }

  @Nullable
  public String getSource(int version) {
    if (version < 0 || version >= 3) return null;
    return mySources[version];
  }

  public T getValue(int version) {
    if (version < 0 || version >= 3) {
      LogHelper.error("Illegal version", version);
      return null;
    }
    return myValues[version];
  }

  @Override
  protected EditItemModel getModel() {
    return myModel;
  }
}

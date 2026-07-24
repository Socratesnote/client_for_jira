package com.almworks.jira.provider3.gui.edit.editors.move;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A candidate parent issue offered by the Parent field's autocomplete. */
class ParentSuggestion {
  private final long myItem;
  private final String myKey;
  private final String mySummary;

  ParentSuggestion(long item, @NotNull String key, @Nullable String summary) {
    myItem = item;
    myKey = key;
    mySummary = summary;
  }

  public long getItem() {
    return myItem;
  }

  @NotNull
  public String getKey() {
    return myKey;
  }

  /** Rendered by the completion popup's default (toString-based) renderer. */
  @Override
  public String toString() {
    return mySummary == null || mySummary.isEmpty() ? myKey : myKey + "  " + mySummary;
  }
}

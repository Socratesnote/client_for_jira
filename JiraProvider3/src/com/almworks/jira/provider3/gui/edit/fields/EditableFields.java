package com.almworks.jira.provider3.gui.edit.fields;

import com.almworks.integers.LongList;
import com.almworks.items.gui.edit.EditItemModel;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.VersionSource;
import com.almworks.jira.provider3.gui.edit.ResolvedField;
import com.almworks.jira.provider3.schema.Issue;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The fields Jira reports as editable for the issues being edited, read from each issue's own editmeta. The data is
 * downloaded per issue into {@link Issue#FIELDS_FOR_EDIT}, which is the same source the upload path consults. Missing fields will throw a "do not know how to upload"-error.
 * <br><br>
 * For a bulk edit, the intersection of editable fields is taken so that only shared elements can be edited.
 * <br><br>
 * Nothing is claimed about issues that were never fully downloaded, since they have no stored editmeta. Those are
 * skipped rather than emptying the intersection, and if no issue has data the result is "not known" - callers must
 * treat that as "do not restrict anything" rather than "nothing is editable".
 * <br><br>
 * The relevant-fields screen derives its list from the same attribute under the same two rules, in
 * {@code RelevantFields.ensureLoaded}. The two must agree: this class decides what gets disabled, that one decides
 * what multi-issue edit is offered at all.
 */
public class EditableFields {
  private static final TypedKey<EditableFields> KEY = TypedKey.create("editableFields");

  @Nullable("When no edited issue has stored editmeta")
  private final Set<String> myEditableIds;

  EditableFields(@Nullable Set<String> editableIds) {
    myEditableIds = editableIds;
  }

  public static EditableFields ensureLoaded(VersionSource source, EditItemModel model) {
    EditItemModel root = model.getRootModel();
    EditableFields loaded = root.getValue(KEY);
    if (loaded == null) {
      loaded = new EditableFields(collectCommonIds(source, model.getEditingItems()));
      root.putHint(KEY, loaded);
    }
    return loaded;
  }

  @Nullable
  public static EditableFields getInstance(@Nullable EditItemModel model) {
    return model != null ? model.getRootModel().getValue(KEY) : null;
  }

  /**
   * @return true only when every edited issue is known to reject the field. False whenever nothing is known, so a
   * caller that disables on this answer never disables a field on a guess.
   */
  public boolean isKnownNotEditable(String fieldId) {
    return myEditableIds != null && !myEditableIds.contains(fieldId);
  }

  /**
   * @return false when no edited issue has stored editmeta, so nothing can be said about any field. Callers that
   * restrict what the user may edit must fall back to offering everything in that case.
   */
  public boolean isKnown() {
    return myEditableIds != null;
  }

  /**
   * @return the field IDs editable on every edited issue, or null when no edited issue has stored editmeta
   */
  @Nullable
  public Set<String> getEditableIds() {
    return myEditableIds != null ? Collections.unmodifiableSet(myEditableIds) : null;
  }

  @Nullable
  private static Set<String> collectCommonIds(VersionSource source, LongList issues) {
    if (issues.isEmpty()) return null;
    ArrayList<Set<String>> perIssue = Collections15.arrayList();
    for (ItemVersion issue : source.readItems(issues)) {
      LongList fields = Issue.FIELDS_FOR_EDIT.getValue(issue);
      if (fields == null || fields.isEmpty()) {
        perIssue.add(null); // No stored editmeta: unknown, which is not the same as nothing being editable.
        continue;
      }
      HashSet<String> ids = Collections15.hashSet();
      for (ResolvedField field : ResolvedField.load(source, fields)) ids.add(field.getJiraId());
      perIssue.add(ids);
    }
    return intersect(perIssue);
  }

  /**
   * Intersects what is known per issue, skipping the issues that know nothing (null) rather than letting them empty
   * the result.
   * @param perIssue one entry per edited issue, null where the issue has no stored editmeta
   * @return the fields editable on every issue that has data, or null when no issue has any
   */
  @Nullable
  static Set<String> intersect(List<Set<String>> perIssue) {
    Set<String> common = null;
    for (Set<String> ids : perIssue) {
      if (ids == null) continue;
      if (common == null) common = Collections15.hashSet(ids);
      else common.retainAll(ids);
    }
    return common;
  }
}

package com.almworks.jira.provider3.gui.edit.editors.move;

import com.almworks.api.application.ItemKey;
import com.almworks.integers.LongList;
import com.almworks.items.gui.edit.*;
import com.almworks.items.gui.edit.editors.enums.single.SingleEnumFieldEditor;
import com.almworks.items.gui.meta.LoadedItemKey;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.VersionSource;
import com.almworks.jira.provider3.remotedata.issue.MoveIssueStep;
import com.almworks.jira.provider3.schema.Issue;
import com.almworks.jira.provider3.schema.IssueType;
import com.almworks.util.LogHelper;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.LongSet;
import com.almworks.util.commons.Condition;
import com.almworks.util.commons.Procedure;
import com.almworks.util.text.NameMnemonic;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MoveController {
  public static final Condition<LoadedItemKey> IS_SUBTASK = new IsSubtask(true);
  public static final Condition<LoadedItemKey> IS_GENERIC = new IsSubtask(false);

  public static final FieldEditor PROJECT = ProjectEditor.INSTANCE;
  public static final FieldEditor COMMON_ISSUE_TYPE = new IssueTypeEditor(false);
  public static final FieldEditor MOVE_ISSUE_TYPE = new IssueTypeEditor(true);
  public static final FieldEditor PARENT = new ReadonlyParentEditor();
  /** Editable Parent field for the Move/Convert dialog (always visible, non-mandatory). */
  public static final FieldEditor MOVE_PARENT = new MoveParentEditor(NameMnemonic.rawText("Parent"));

  public static final int MODE_ALL = 0;
  public static final int MODE_SUBTASK = 1;
  public static final int MODE_GENERIC = 2;
  public static final int MODE_DISABLED = 3;

  private static final TypedKey<MoveController> KEY = TypedKey.create("moveController");
  private static final TypedKey<Integer> CURRENT_MODE = TypedKey.create("issueType/controller/mode");

  private final ParentSupport myParentSupport;
  private ProjectEditor myProjectEditor;
  private IssueTypeEditor myTypeEditor;
  private ParentEditor myParentEditor;
  private int myDefaultMode;
  /** True if every edited issue is generic (by its issue type's subtask flag). Drives the project editor. */
  private final boolean myAllGeneric;

  private MoveController(ParentSupport parentSupport, int defaultMode, boolean allGeneric) {
    myParentSupport = parentSupport;
    myDefaultMode = defaultMode;
    myAllGeneric = allGeneric;
  }

  @NotNull
  public static MoveController ensureLoaded(VersionSource source, EditModelState model) {
    MoveController controller = model.getValue(KEY);
    if (controller == null) {
      ParentSupport parentSupport = ParentSupport.ensureLoaded(source, model);
      int defaultMode;
      boolean allGeneric;
      if (!model.isNewItem()) {
        // Classify by the issue type's own subtask flag, not by parent presence: on Jira Cloud a standard
        // issue can have a parent (an Epic), so parent-presence no longer implies subtask.
        Boolean allSubtasks = classifyBySubtaskFlag(source, model);
        if (allSubtasks == null) defaultMode = MODE_DISABLED; // mixed selection, or issue-type meta not loaded yet
        else defaultMode = allSubtasks ? MODE_SUBTASK : MODE_GENERIC;
        allGeneric = Boolean.FALSE.equals(allSubtasks);
      } else {
        allGeneric = parentSupport.isGenericOnly();
        defaultMode = allGeneric ? MODE_GENERIC : MODE_SUBTASK;
      }
      controller = new MoveController(parentSupport, defaultMode, allGeneric);
      model.putHint(KEY, controller);
    }
    return controller;
  }

  @Nullable("If not installed")
  public static MoveController getInstance(EditModelState model) {
    return model.getValue(KEY);
  }

  /**
   * Classifies the edited issues by their issue type's {@link IssueType#SUBTASK subtask} flag.
   * @return {@code true} if every edited issue's type is a subtask, {@code false} if every one is generic,
   *   {@code null} if the selection mixes both or the flag is not loaded (issue-type meta not synced yet).
   */
  @Nullable
  public static Boolean classifyBySubtaskFlag(VersionSource source, EditModelState model) {
    List<ItemVersion> issues = source.readItems(model.getEditingItems());
    Boolean hasSubtasks = null;
    Boolean hasGeneric = null;
    for (ItemVersion issue : issues) {
      Boolean subtask = IssueType.getSubtask(issue.getReader(), issue.getValue(Issue.ISSUE_TYPE));
      if (subtask == null) continue;
      boolean isGeneric = !subtask;
      hasSubtasks = changeFlag(hasSubtasks, subtask, isGeneric);
      hasGeneric = changeFlag(hasGeneric, isGeneric, subtask);
    }
    if (hasSubtasks == null) return hasGeneric != null ? !hasGeneric : null;
    if (hasGeneric == null) return hasSubtasks;
    if (hasSubtasks) return hasGeneric ? null : true;
    return hasGeneric ? false : null;
  }

  public static void setNewSubtaskParent(EditModelState model, long parent) {
    if (!model.isNewItem()) {
      LogHelper.error("Not a new issue model", parent);
      return;
    }
    ParentSupport.prepareSubtask(model, parent);
  }

  public static Boolean changeFlag(Boolean current, boolean set, boolean clear) {
    if (current == null) {
      if (set) return true;
      if (clear) return false;
      return null;
    }
    return current || set;
  }

  public LongList getAllParents() {
    return myParentSupport.getAllParents();
  }

  public boolean isGenericOnly() {
    return myAllGeneric;
  }

  void setProjectEditor(ProjectEditor projectEditor) {
    myProjectEditor = projectEditor;
  }

  void setTypeEditor(IssueTypeEditor typeEditor) {
    myTypeEditor = typeEditor;
  }

  void setParentEditor(ParentEditor parentEditor) {
    myParentEditor = parentEditor;
  }

  /** The issue type currently selected in this model's Issue Type field, or {@code null} if not available. */
  @Nullable
  ItemKey getTypeValue(EditModelState model) {
    return myTypeEditor == null ? null : myTypeEditor.getValue(model);
  }

  /**
   * Re-selects the Issue Type after the offered variants have been rebuilt for a different project. Does nothing
   * when no type is selected; otherwise applies {@link #pickTypeForVariants}, which may clear the field.
   */
  void repickTypeForProject(EditModelState model, AListModel<? extends ItemKey> variants) {
    if (myTypeEditor == null) return;
    ItemKey current = getTypeValue(model);
    if (current == null || current.getItem() <= 0) return;
    myTypeEditor.setValue(model, pickTypeForVariants(current, variants));
  }

  /**
   * The Issue Type to select from a freshly narrowed variants list, given the one currently selected. Returns
   * {@code current} when it is still offered, the variant with the same name when it is not, and {@code null}
   * when no name matches - which the caller applies as clearing the field.
   *
   * Two projects on different issue type schemes share no issue type ids, so the id cannot be carried across a
   * project change and the name is the only thing left to match on. Names do not always correspond either - Jira
   * spells the sub-task type differently from one scheme to the next - which is what the null return is for.
   * Matching uses the display name rather than IssueType.NAME, which is not subloaded onto LoadedItemKey and so
   * reads as null.
   */
  @Nullable
  static ItemKey pickTypeForVariants(@Nullable ItemKey current, AListModel<? extends ItemKey> variants) {
    if (current == null || current.getItem() <= 0) return null;
    String name = current.getDisplayName();
    ItemKey sameName = null;
    for (int i = 0; i < variants.getSize(); i++) {
      ItemKey variant = variants.getAt(i);
      if (variant == null) continue;
      if (variant.getItem() == current.getItem()) return current; // Still offered - keep the user's selection.
      if (sameName == null && name != null && name.equalsIgnoreCase(variant.getDisplayName())) sameName = variant;
    }
    return sameName;
  }

  int getCurrentMode(EditModelState model) {
    Integer mode = model.getValue(CURRENT_MODE);
    if (mode != null && checkMode(mode)) return mode;
    return myDefaultMode;
  }

  public void setCurrentMode(EditModelState model, int mode) {
    if (checkMode(mode)) model.putHint(CURRENT_MODE, mode);
  }

  private boolean checkMode(int mode) {
    return mode >= MODE_ALL && mode <= MODE_DISABLED;
  }

  public static void performCommit(CommitContext context) throws CancelCommitException {
    MoveController controller = MoveController.getInstance(context.getModel());
    if (controller != null) controller.commit(context);
  }

  private static final TypedKey<LongSet> COMMITTED = TypedKey.create("committedIssues");
  private void commit(CommitContext context) throws CancelCommitException {
    EditItemModel model = context.getModel();
    LongSet committed = model.getValue(COMMITTED);
    if (committed == null) {
      committed = new LongSet();
      model.putHint(COMMITTED, committed);
    }
    if (!committed.addValue(context.getItem())) return; // already committed
    if (model.isNewItem()) commitNewIssue(context);
    else commitEdit(context);
    if (myProjectEditor != null) myProjectEditor.updateDefaults(context);
    if (myTypeEditor != null) myTypeEditor.updateDefaults(context);
  }

  private void commitEdit(CommitContext context) throws CancelCommitException {
    EditItemModel model = context.getModel();
    ItemVersion project = getChangedValue(context, myProjectEditor);
    ItemVersion type = getChangedValue(context, myTypeEditor);
    List<FieldEditor> commitEditors = model.getCommitEditors();
    ItemVersion issue = context.readTrunk();
    if (type == null) type = issue.readValue(Issue.ISSUE_TYPE);
    if (type == null) throw new CancelCommitException();
    if (commitEditors.contains(myParentEditor)) { // Parent has been changed
      long parent = myParentEditor.getSingleParent(model, context.getReader(), issue);
      // Allowed combinations: generic+no-parent, subtask+parent, generic+parent (Epic). Only a subtask with no
      // parent is invalid.
      if (Boolean.TRUE.equals(type.getValue(IssueType.SUBTASK)) && parent <= 0) throw new CancelCommitException();
      if (parent > 0) context.getCreator().setValue(Issue.PARENT, parent);
      else context.getCreator().setValue(Issue.PARENT, (Long) null);
      context.getCreator().setValue(Issue.ISSUE_TYPE, type.getItem());
    } else { // Parent editor not in commit set => user did NOT change the parent
      // Never touch Issue.PARENT here. It may hold a legitimate Epic parent (Jira Cloud sets fields.parent for
      // any hierarchy parent, not just subtasks), and this branch runs on every ordinary edit - nulling it would
      // silently drop the Epic link. Only the (possibly changed) type is written.
      context.getCreator().setValue(Issue.ISSUE_TYPE, type.getItem());
    }
    if (project != null) context.getCreator().setValue(Issue.PROJECT, project.getItem());
    if (commitEditors.contains(myProjectEditor) || commitEditors.contains(myTypeEditor) || commitEditors.contains(myParentEditor))
      commitMove(context);
  }

  private static long commitMove(CommitContext context) throws CancelCommitException {
    final long parent = Issue.getParent(context.readTrunk());
    ParentSupport parents = ParentSupport.getInstance(context.getModel());
    if (parents == null) {
      LogHelper.error("Should not happen", context);
      throw new CancelCommitException();
    }
    final long oldParent = parents.getInitialParent(context.getItem());
    context.afterCommit(new Procedure<CommitContext>() {
      @Override
      public void invoke(CommitContext context) {
        MoveIssueStep.addHistory(context.getCreator(), oldParent, parent);
      }
    });
    return parent;
  }

  private void commitNewIssue(CommitContext context) throws CancelCommitException {
    EditItemModel model = context.getModel();
    ItemVersion project = getChangedValue(context, myProjectEditor);
    ItemVersion type = getChangedValue(context, myTypeEditor);
    if (type == null) throw new CancelCommitException();
    long parent = myParentEditor.getSingleParent(model, context.getReader(), null);
    if (parent > 0) {
      // A parent is allowed for a subtask or for a generic issue under an Epic; both inherit the parent's project.
      project = context.readTrunk(parent).readValue(Issue.PROJECT);
      if (project == null) throw new CancelCommitException();
      context.getCreator().setValue(Issue.PARENT, parent);
    } else {
      if (project == null) throw new CancelCommitException();
      if (Boolean.TRUE.equals(type.getValue(IssueType.SUBTASK))) throw new CancelCommitException(); // a subtask needs a parent
    }
    context.getCreator().setValue(Issue.ISSUE_TYPE, type.getItem());
    context.getCreator().setValue(Issue.PROJECT, project.getItem());
  }

  @Nullable
  private ItemVersion getChangedValue(CommitContext context, @Nullable SingleEnumFieldEditor editor) {
    if (editor == null) return null;
    EditItemModel model = context.getModel();
    if (!model.getCommitEditors().contains(editor)) return null;
    ItemKey value = editor.getValue(model);
    if (value == null || value.getItem() <= 0) return null;
    return context.readTrunk(value.getItem());
  }

  private static class IsSubtask extends Condition<LoadedItemKey> {
    private final boolean mySubtask;

    public IsSubtask(boolean subtask) {
      mySubtask = subtask;
    }

    @Override
    public boolean isAccepted(LoadedItemKey value) {
      return IssueType.isSubtask(value, mySubtask);
    }
  }}

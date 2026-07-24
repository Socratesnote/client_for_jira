package com.almworks.jira.provider3.gui.edit.editors.move;

import com.almworks.actions.console.CompletionFieldController;
import com.almworks.actions.console.CompletionTextField;
import com.almworks.actions.console.VariantModelController;
import com.almworks.api.application.ItemKey;
import com.almworks.explorer.PrimaryItemKeyTransferHandler;
import com.almworks.integers.LongArray;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBReader;
import com.almworks.items.api.DP;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.gui.edit.*;
import com.almworks.items.gui.edit.editors.text.ScalarValueKey;
import com.almworks.items.gui.edit.util.BaseFieldEditor;
import com.almworks.items.gui.edit.util.FieldEditorUtil;
import com.almworks.items.gui.edit.util.SimpleComponentControl;
import com.almworks.items.sync.EditPrepare;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.VersionSource;
import com.almworks.items.sync.util.ItemValues;
import com.almworks.items.sync.util.TransactionCacheKey;
import com.almworks.items.util.SyncAttributes;
import com.almworks.jira.provider3.schema.Issue;
import com.almworks.jira.provider3.schema.IssueKeyComparator;
import com.almworks.jira.provider3.schema.IssueType;
import com.almworks.jira.provider3.schema.Project;
import com.almworks.jira.provider3.services.JiraPatterns;
import com.almworks.util.LogHelper;
import com.almworks.util.Pair;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.commons.Function;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.widgets.util.CanvasWidget;
import com.almworks.util.ui.widgets.util.list.ColumnListWidget;
import com.almworks.util.ui.widgets.util.list.ListSelectionProcessor;
import gnu.trove.TLongObjectHashMap;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class MoveParentEditor extends BaseFieldEditor implements ParentEditor {
  private final TypedKey<ComponentControl.Enabled> EDITABLE = TypedKey.create("parent/editable");
  private final ScalarValueKey.Text KEYS = new ScalarValueKey.Text("parent/keys", true);
  private final TypedKey<Pair<Long, String>> COMMON_PRJ = TypedKey.create("parent/commonPrj");
  private final TypedKey<List<ParentSuggestion>> SUGGESTIONS = TypedKey.create("parent/suggestions");
  private final TransactionCacheKey<Long> PARENT = TransactionCacheKey.create("parent/resolved");

  public MoveParentEditor(NameMnemonic labelText) {
    super(labelText);
  }

  @Override
  public void prepareModel(VersionSource source, EditItemModel model, EditPrepare editPrepare) {
    // Register with the controller so this standalone field participates in commit (the Issue Type editor no
    // longer embeds the parent).
    MoveController.ensureLoaded(source, model).setParentEditor(this);
    ParentSupport parents = ParentSupport.ensureLoaded(source, model);
    boolean hasNew = false;
    List<String> keys = Collections15.arrayList();
    List<ItemVersion> allParents = source.readItems(parents.getAllParents());
    for (ItemVersion issue : allParents) {
      if (issue.getItem() == 0) continue;
      String key = issue.getValue(Issue.KEY);
      if (key == null) hasNew = true;
      else keys.add(key);
    }
    Collections.sort(keys, IssueKeyComparator.INSTANCE);
    KEYS.setText(model, collectText(keys, hasNew));
    ArrayList<ItemVersion> allIssues = Collections15.arrayList();
    allIssues.addAll(allParents);
    allIssues.addAll(source.readItems(model.getEditingItems()));
    Pair<Long, String> commonProject = chooseCommonProject(allIssues);
    if (commonProject == null && model.isNewItem()) {
      // A fresh new issue with no pre-set parent has no existing item to read a project from - fall back to
      // whatever project the Project field currently holds (e.g. the create dialog's default/chosen project).
      commonProject = chooseNewItemProject(source, model);
    }
    ComponentControl.Enabled enabled;
    if (commonProject == null || commonProject.getSecond() == null) enabled = ComponentControl.Enabled.DISABLED;
    else if (hasNew || keys.size() > 1) enabled = ComponentControl.Enabled.DISABLED;
    else if (model.getEditingItems().size() > 1) enabled = ComponentControl.Enabled.ENABLED;
    else enabled = ComponentControl.Enabled.NOT_APPLICABLE;
    model.putHint(EDITABLE, enabled);
    model.putHint(COMMON_PRJ, commonProject);
    model.putHint(SUGGESTIONS, loadSuggestions(source, model, commonProject));
    model.registerEditor(this);
  }

  @Nullable
  private static Pair<Long, String> chooseNewItemProject(VersionSource source, EditItemModel model) {
    Long project = model.getSingleEnumValue(Issue.PROJECT);
    if (project == null || project <= 0) return null;
    String key = source.forItem(project).getValue(Project.KEY);
    return key == null ? null : Pair.create(project, key);
  }

  private static final int MAX_SUGGESTIONS = 500;

  /** All issues in the field's common project (same connection), for client-side prefix filtering as the user types. */
  @NotNull
  private static List<ParentSuggestion> loadSuggestions(VersionSource source, EditItemModel model, @Nullable Pair<Long, String> commonProject) {
    if (commonProject == null) return Collections.emptyList();
    Long connection = model.getSingleEnumValue(SyncAttributes.CONNECTION);
    if (connection == null || connection <= 0) return Collections.emptyList();
    BoolExpr<DP> query = DPEquals.create(Issue.PROJECT, commonProject.getFirst()).and(DPEquals.create(SyncAttributes.CONNECTION, connection));
    LongArray items = source.getReader().query(query).copyItemsSorted();
    List<ParentSuggestion> result = Collections15.arrayList();
    for (ItemVersion issue : source.readItems(items)) {
      String key = issue.getValue(Issue.KEY);
      if (key == null) continue; // not yet uploaded: no key to type/match against
      result.add(new ParentSuggestion(issue.getItem(), key, issue.getValue(Issue.SUMMARY)));
      if (result.size() >= MAX_SUGGESTIONS) break;
    }
    Collections.sort(result, new Comparator<ParentSuggestion>() {
      @Override
      public int compare(ParentSuggestion a, ParentSuggestion b) {
        return IssueKeyComparator.INSTANCE.compare(a.getKey(), b.getKey());
      }
    });
    return result;
  }

  private Pair<Long, String> chooseCommonProject(List<ItemVersion> issues) {
    ItemVersion common = null;
    for (ItemVersion issue : issues) {
      if (issue.getItem() <= 0) continue;
      Long project = issue.getValue(Issue.PROJECT);
      if (project == null || project <= 0) continue;
      if (common == null) common = issue.forItem(project);
      else if (common.getItem() != project) return null;
    }
    if (common == null) return null;
    String key = common.getValue(Project.KEY);
    return Pair.create(common.getItem(), key);
  }

  @NotNull
  @Override
  public List<? extends ComponentControl> createComponents(Lifespan life, EditItemModel model) {
    ComponentControl component = createComponent(life, model);
    if (component == null) return Collections.emptyList();
    return Collections.singletonList(component);
  }

  @Nullable
  public ComponentControl createComponent(Lifespan life, EditItemModel model) {
    final CompletionTextField<ParentSuggestion> field = new CompletionTextField<ParentSuggestion>();
    field.setColumns(15);
    final List<ParentSuggestion> suggestions = Util.NN(model.getValue(SUGGESTIONS), Collections.<ParentSuggestion>emptyList());
    final CompletionFieldController<ParentSuggestion> controller = field.getController();
    controller.setModelFactory(new Function<Lifespan, VariantModelController<ParentSuggestion>>() {
      @Override
      public VariantModelController<ParentSuggestion> invoke(Lifespan life) {
        return new ParentSuggestionModel(suggestions);
      }
    });
    // The controller only holds the data model; without an actual widget attached to its list, rows render blank.
    CanvasWidget<ParentSuggestion> widget = new CanvasWidget<ParentSuggestion>();
    ColumnListWidget<ParentSuggestion> listWidget = controller.getListWidget();
    widget.setStateFactory(new ListSelectionProcessor.StateFactory(listWidget));
    listWidget.addWidget(widget);
    listWidget.setColumnPolicy(widget, 1, 0, 10);
    // Show the drop-down only once something has been typed (no blank single-row popup on focus), keep each row
    // at least as tall as the field, and let a double-click pick a row (same as Enter).
    controller.setHideWhenNoVariants(true);
    controller.setMinRowHeight(field.getPreferredSize().height);
    controller.setDoubleClickActivates(true);
    field.setAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ParentSuggestion selected = controller.getSelected();
        if (selected == null) return;
        field.setText(selected.getKey());
        controller.getPopup().hide();
      }
    });
    return attachComponent(life, model, field);
  }

  public ComponentControl attachComponent(Lifespan life, EditItemModel model, JTextField field) {
    ComponentControl.Enabled enabled = model.getValue(EDITABLE);
    if (enabled == null) return null;
    field.setText(KEYS.getText(model));
    field.setTransferHandler(PrimaryItemKeyTransferHandler.getInstance(true));
    KEYS.listenTextComponent(life, model, field);
    FieldEditorUtil.registerComponent(model, this, field);
    return SimpleComponentControl.singleLine(field, this, model, enabled);
  }

  private String collectText(List<String> keys, boolean hasNew) {
    List<String> allKeys;
    if (hasNew) {
      ArrayList<String> copy = Collections15.arrayList(keys);
      copy.add(0, "<new>");
      allKeys = copy;
    } else allKeys = keys;
    return TextUtil.separateToString(allKeys, ", ");
  }

  @Override
  public boolean isChanged(EditItemModel model) {
    if (!KEYS.isChanged(model)) return false;
    Long connection = model.getSingleEnumValue(SyncAttributes.CONNECTION);
    if (connection == null || connection <= 0) {
      LogHelper.warning("Model missing connection");
      return false;
    }
    List<String> keys = Issue.extractIssueKeys(KEYS.getValue(model));
    return keys.size() <= 1;
  }

  @Override
  public boolean hasValue(EditModelState model) {
    return true;
  }

  @Override
  public void verifyData(DataVerification verifyContext) {
    EditItemModel model = verifyContext.getModel();
    boolean changed = KEYS.isChanged(model);
    String text = Util.NN(KEYS.getText(model));
    List<String> keys = Issue.extractIssueKeys(text);
    if (changed) {
      if (keys.isEmpty()) {
        String trimmed = text.trim();
        if (!trimmed.isEmpty()) verifyContext.addError(this, "'" + trimmed + "' is not a valid parent key");
      } else if (keys.size() != 1) {
        verifyContext.addError(this, "Expected single parent key");
      } else {
        String prj = JiraPatterns.extractProjectKeyNoLog(keys.get(0));
        Pair<Long, String> project = model.getValue(COMMON_PRJ);
        if (project == null || project.getSecond() == null) LogHelper.error("No common project", project, text);
        else if (prj == null) LogHelper.error("Cannot extract project key", keys, text);
        else if (!prj.equalsIgnoreCase(project.getSecond())) verifyContext.addError(this, "Cannot move subtask to another project");
      }
    }
    // Surface "a sub-task must have a parent" here rather than a silent CancelCommitException at commit,
    // regardless of whether the (possibly still blank) parent field itself was touched - e.g. picking a
    // sub-task Issue Type in the New Issue dialog and leaving Parent empty.
    if (keys.isEmpty()) {
      MoveController controller = MoveController.getInstance(model);
      ItemKey type = controller == null ? null : controller.getTypeValue(model);
      if (IssueType.isSubtask(type, true)) verifyContext.addError(this, "A sub-task must have a parent");
    }
  }

  @Override
  public void onItemsChanged(EditItemModel model, TLongObjectHashMap<ItemValues> newValues) {
    FieldEditorUtil.assertNotChanged(model, newValues, Issue.PARENT, this);
  }

  @Override
  public void commit(CommitContext context) throws CancelCommitException {
    MoveController.performCommit(context);
  }

  @Override
  public long getSingleParent(@NotNull EditItemModel model, @NotNull DBReader reader, ItemVersion issue) throws CancelCommitException {
    if (!model.getAllEditors().contains(this)) return issue != null ? Issue.getParent(issue) : 0;
    Long parent = PARENT.get(reader);
    if (parent == null) {
      parent = resolveParent(reader, model);
      PARENT.put(reader, parent);
    }
    return parent > 0 ? parent : 0;
  }

  private long resolveParent(DBReader reader, EditItemModel model) {
    String keys = KEYS.getValue(model);
    if (keys == null || !KEYS.isChanged(model)) return 0;
    List<String> keysList = Issue.extractIssueKeys(keys);
    if (keysList.size() != 1) {
      LogHelper.error("Cannot change parent to several values", keys);
      throw new DBOperationCancelledException();
    }
    Long thisConnection = model.getSingleEnumValue(SyncAttributes.CONNECTION);
    if (thisConnection == null) {
      LogHelper.error("Missing this connection");
      throw new DBOperationCancelledException();
    }
    BoolExpr<DP> query =
      DPEquals.create(Issue.KEY, keysList.get(0)).and(DPEquals.create(SyncAttributes.CONNECTION, thisConnection));
    LongArray issues = reader.query(query).copyItemsSorted();
    if (issues.size() != 1) {
      LogHelper.warning("Parent not found", issues); // todo JC-121
      return 0;
    }
    return issues.get(0);
  }
}

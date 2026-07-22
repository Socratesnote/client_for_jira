package com.almworks.jira.provider3.gui.edit.editors.move;

import com.almworks.explorer.PrimaryItemKeyTransferHandler;
import com.almworks.items.api.DBReader;
import com.almworks.items.gui.edit.*;
import com.almworks.items.gui.edit.util.FieldEditorUtil;
import com.almworks.items.gui.edit.util.SimpleComponentControl;
import com.almworks.items.sync.EditPrepare;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.VersionSource;
import com.almworks.items.sync.util.ItemValues;
import com.almworks.jira.provider3.schema.Issue;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.InlineLayout;
import gnu.trove.TLongObjectHashMap;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class ReadonlyParentEditor implements ParentEditor {
  private static final TypedKey<List<ParentInfo>> PARENTS = TypedKey.create("parents");

  @Override
  public void prepareModel(VersionSource source, EditItemModel model, EditPrepare editPrepare) {
    MoveController controller = MoveController.ensureLoaded(source, model);
    controller.setParentEditor(this);
    List<ParentInfo> parents = Collections15.arrayList();
    for (ItemVersion parent : source.readItems(controller.getAllParents())) parents.add(ParentInfo.load(parent));
    model.putHint(PARENTS, parents);
  }

  @Override
  @NotNull
  public NameMnemonic getLabelText(EditModelState model) {
    return NameMnemonic.rawText("Parent");
  }

  @NotNull
  @Override
  public List<? extends ComponentControl> createComponents(Lifespan life, EditItemModel model) {
    // Always show the Parent row (blank when the issue has no parent), with the key and the summary as two
    // separate read-only fields on one line: "Parent: <KEY> <Summary>".
    List<ParentInfo> parents = model.getValue(PARENTS);
    JTextField keyField = new JTextField(10);
    JTextField summaryField = new JTextField();
    if (parents != null && !parents.isEmpty()) {
      if (parents.size() == 1) {
        keyField.setText(parents.get(0).getKey());
        summaryField.setText(Util.NN(parents.get(0).getSummary()));
      } else {
        Set<String> keySet = Collections15.hashSet();
        for (ParentInfo parent : parents) keySet.add(parent.getKey());
        ArrayList<String> keys = Collections15.arrayList(keySet);
        Collections.sort(keys);
        keyField.setText(TextUtil.separate(keys, " "));
      }
    }
    keyField.setEditable(false);
    summaryField.setEditable(false);
    keyField.setTransferHandler(PrimaryItemKeyTransferHandler.getInstance(true));
    InlineLayout layout = InlineLayout.horizontal(5);
    layout.setLastTakesAllSpace(true);
    JPanel panel = new JPanel(layout);
    panel.add(keyField);
    panel.add(summaryField);
    FieldEditorUtil.registerComponent(model, this, keyField);
    return SimpleComponentControl.singleLine(panel, this, model, ComponentControl.Enabled.NOT_APPLICABLE).singleton();
  }

  @Override
  public void afterModelFixed(EditItemModel model) {}

  @Override
  public boolean isChanged(EditItemModel model) {
    return false;
  }

  @Override
  public boolean hasDataToCommit(EditItemModel model) {
    return false;
  }

  @Override
  public boolean hasValue(EditModelState model) {
    List<ParentInfo> parents = model.getValue(PARENTS);
    return parents != null && !parents.isEmpty();
  }

  @Override
  public void verifyData(DataVerification verifyContext) {
  }

  @Override
  public void afterModelCopied(EditItemModel copy) {
  }

  @Override
  public void commit(CommitContext context) throws CancelCommitException {
    MoveController.performCommit(context);
  }

  @Override
  public void onItemsChanged(EditItemModel model, TLongObjectHashMap<ItemValues> newValues) {
    FieldEditorUtil.assertNotChanged(model, newValues, Issue.PARENT, this);
  }

  @Override
  public long getSingleParent(@NotNull EditItemModel model, @NotNull DBReader reader, ItemVersion issue) throws CancelCommitException {
    List<ParentInfo> parents = model.getValue(PARENTS);
    if (parents == null || parents.isEmpty()) return 0;
    if (parents.size() == 1) return parents.get(0).myItem;
    throw new CancelCommitException();
  }

  private static class ParentInfo {
    private final long myItem;
    private final String myKey;
    private final String mySummary;

    public ParentInfo(long item, String key, String summary) {
      myItem = item;
      myKey = key;
      mySummary = summary;
    }

    public static ParentInfo load(ItemVersion parent) {
      String key = parent.getValue(Issue.KEY);
      String summary = parent.getValue(Issue.SUMMARY);
      return new ParentInfo(parent.getItem(), key, summary);
    }

    public String getKey() {
      return myKey == null ? "<New issue>" : myKey;
    }

    public String getSummary() {
      return mySummary;
    }
  }
}

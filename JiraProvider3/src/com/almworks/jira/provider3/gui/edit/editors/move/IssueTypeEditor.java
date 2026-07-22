package com.almworks.jira.provider3.gui.edit.editors.move;

import com.almworks.api.application.ItemKey;
import com.almworks.items.gui.edit.*;
import com.almworks.items.gui.edit.editors.composition.SingleEnumDelegatingEditor;
import com.almworks.items.gui.edit.editors.enums.DefaultItemSelector;
import com.almworks.items.gui.edit.editors.enums.single.BaseSingleEnumEditor;
import com.almworks.items.gui.edit.editors.enums.single.DropdownEditorBuilder;
import com.almworks.items.gui.edit.editors.enums.single.DropdownEnumEditor;
import com.almworks.items.sync.EditPrepare;
import com.almworks.items.sync.VersionSource;
import com.almworks.jira.provider3.schema.Issue;
import com.almworks.jira.provider3.schema.IssueType;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.text.NameMnemonic;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

class IssueTypeEditor extends SingleEnumDelegatingEditor<DropdownEnumEditor> {
  private static final CanvasRenderer<ItemKey> RENDERER = new CanvasRenderer<ItemKey>() {
    @Override
    public void renderStateOn(CellState state, Canvas canvas, ItemKey item) {
      if (item == null) return;
      if (IssueType.isSubtask(item, true)) canvas.emptySection().setFontStyle(Font.ITALIC);
      item.renderOn(canvas, state);
      canvas.setIcon(item.getIcon());
    }
  };
  private static final DropdownEnumEditor DROPDOWN =
    new DropdownEditorBuilder().setVariants(new IssueTypeVariants())
      .setAttribute(Issue.ISSUE_TYPE)
      .setDefaultItem(DefaultItemSelector.ANY)
      .setLabelText(NameMnemonic.parseString("Issue &Type"))
      .setVerify(true)
      .overrideRenderer(RENDERER)
      .createFixed();

  /** True for the Move/Convert dialog: show all issue types; the parent is edited by a separate {@link MoveParentEditor} field. */
  private final boolean myMoveDialog;

  IssueTypeEditor(boolean moveDialog) {
    super(DROPDOWN.getAttribute(), DROPDOWN.getVariants());
    myMoveDialog = moveDialog;
  }

  @Nullable
  @Override
  protected DropdownEnumEditor getDelegate(VersionSource source, EditModelState model) {
    return DROPDOWN;
  }

  @Override
  protected void prepareWrapper(VersionSource source, ModelWrapper<DropdownEnumEditor> wrapper, EditPrepare editPrepare) {
    EditItemModel unwrappedModel = wrapper.getOriginalModel();
    MoveController controller = MoveController.ensureLoaded(source, unwrappedModel);
    controller.setTypeEditor(this);
    super.prepareWrapper(source, wrapper, editPrepare);
    // In the Move/Convert dialog every issue type is selectable; the parent is a separate, always-visible field.
    if (myMoveDialog) controller.setCurrentMode(unwrappedModel, MoveController.MODE_ALL);
  }

  @NotNull
  @Override
  public List<? extends ComponentControl> createComponents(Lifespan life, EditItemModel model) {
    List<? extends ComponentControl> components = super.createComponents(life, model);
    MoveController controller = MoveController.getInstance(model);
    if (controller != null && controller.getCurrentMode(model) == MoveController.MODE_DISABLED)
      components = ComponentControl.EnableWrapper.disableAll(components);
    return components;
  }

  @Override
  public void commit(CommitContext context) throws CancelCommitException {
    MoveController.performCommit(context);
  }

  public void updateDefaults(CommitContext context) throws CancelCommitException {
    BaseSingleEnumEditor.wrapperUpdateDefaults(context, this);
  }
}

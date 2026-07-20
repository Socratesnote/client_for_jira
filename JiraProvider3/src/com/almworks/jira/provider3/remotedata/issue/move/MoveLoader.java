package com.almworks.jira.provider3.remotedata.issue.move;

import com.almworks.items.api.DBReader;
import com.almworks.items.sync.HistoryRecord;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.util.AttributeMap;
import com.almworks.jira.provider3.remotedata.issue.MoveIssueStep;
import com.almworks.jira.provider3.remotedata.issue.StepLoader;
import com.almworks.jira.provider3.remotedata.issue.edit.CreateIssueUnit;
import com.almworks.jira.provider3.remotedata.issue.edit.EditIssue;
import com.almworks.jira.provider3.remotedata.issue.fields.IssueFieldValue;
import com.almworks.jira.provider3.schema.Issue;
import com.almworks.jira.provider3.schema.IssueType;
import com.almworks.jira.provider3.services.upload.LoadUploadContext;
import com.almworks.jira.provider3.services.upload.UploadUnit;
import com.almworks.util.LogHelper;
import com.almworks.util.i18n.text.CurrentLocale;
import com.almworks.util.i18n.text.LocalizedAccessor;
import org.almworks.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class MoveLoader implements StepLoader {
  public static final StepLoader INSTANCE = new MoveLoader();
  static final LocalizedAccessor I18N = CurrentLocale.createAccessor(MoveLoader.class.getClassLoader(), "com/almworks/jira/provider3/remotedata/issue/move/message");

  private MoveLoader() {
  }

  @Override
  public UploadUnit loadStep(ItemVersion trunk, HistoryRecord record, CreateIssueUnit create, LoadUploadContext context, @Nullable UploadUnit prevStep, int stepIndex)
    throws UploadUnit.CantUploadException {
    DBReader reader = trunk.getReader();
    MoveIssueStep step = MoveIssueStep.load(reader, record.getDataStream());
    if (step == null) throw UploadUnit.CantUploadException.internalError();
    AttributeMap postState = step.getState();
    ArrayList<IssueFieldValue> values = EditIssue.loadValues(context, postState, trunk.switchToServer());
    int expectedType = step.getExpectedTypeId(reader);
    long newParentItem = step.getNewParent();
    long prevParentItem = step.getPreviousParent();
    CreateIssueUnit newParent;
    if (newParentItem > 0) {
      newParent = CreateIssueUnit.getExisting(trunk.forItem(newParentItem), context);
      if (newParent == null) {
        LogHelper.warning("New parent is not submitted and not prepared yet", trunk, newParentItem);
        throw UploadUnit.CantUploadException.create("Move to not submitted parent is not supported yet");
      }
    } else newParent = null;
    Integer prevParentId = prevParentItem > 0 ? trunk.forItem(prevParentItem).getValue(Issue.ID) : null;

    // Route by the target issue type's subtask flag, not by parent presence: on Jira Cloud a standard (generic)
    // issue can have a parent (an Epic), so parent-presence no longer implies a subtask move.
    ItemVersion server = trunk.switchToServer();
    Long newTypeItem = postState.get(Issue.ISSUE_TYPE);
    Long oldTypeItem = server.getValue(Issue.ISSUE_TYPE);
    Boolean newSub = IssueType.getSubtask(reader, newTypeItem);
    Boolean oldSub = IssueType.getSubtask(reader, oldTypeItem);
    boolean parentChanged = newParentItem != prevParentItem;

    if (newSub == null) { // issue-type meta not loaded yet - fall back to the legacy parent-presence routing
      LogHelper.warning("Subtask flag not loaded; routing move by parent presence", trunk, newTypeItem);
      return legacyRoute(create, prevStep, stepIndex, newParent, prevParentId, expectedType, values);
    }
    if (newSub) { // target is a subtask - it must have a parent
      if (newParent == null) throw UploadUnit.CantUploadException.create("A subtask must have a parent");
      return prevParentId != null
        ? new MoveParentType(create, prevStep, stepIndex, newParent, expectedType, values)
        : new MoveToSubtask(create, prevStep, stepIndex, newParent, values);
    }
    // Target is a standard (generic) issue.
    if (Boolean.TRUE.equals(oldSub)) { // subtask -> generic conversion
      if (newParent != null) { // converting AND placing under an Epic in one step: unsupported (see plan B-5)
        LogHelper.warning("Converting a subtask to a standard issue under an Epic parent is not supported", trunk, newParentItem);
        throw UploadUnit.CantUploadException.create("Cannot convert a subtask to a standard issue and set an Epic parent at once");
      }
      return new MoveFromSubtask(create, prevStep, stepIndex, values);
    }
    if (!parentChanged) return new GenericMove(create, prevStep, stepIndex, values); // e.g. retype of an Epic-childed issue
    // Generic issue whose Epic parent changed.
    boolean typeChanged = !Util.equals(newTypeItem, oldTypeItem);
    boolean projectChanged = !Util.equals(postState.get(Issue.PROJECT), server.getValue(Issue.PROJECT));
    if (typeChanged || projectChanged) // combined type/project + Epic-parent change: do them as separate edits
      throw UploadUnit.CantUploadException.create("Cannot change the type/project and the Epic parent in one step");
    if (newParent == null) {
      //TODO: Support clearing an Epic parent. Needs a Cloud-verified remove payload ("parent":null is not reliably
      // honored; an {"update":{"parent":[{"remove":...}]}} form may be required). Revisit.
      throw UploadUnit.CantUploadException.create("Removing an Epic parent is not supported yet");
    }
    return new SetEpicParent(create, prevStep, stepIndex, newParent);
  }

  /** Legacy parent-presence routing, used only when the target issue type's subtask flag is not loaded. */
  private static UploadUnit legacyRoute(CreateIssueUnit create, @Nullable UploadUnit prevStep, int stepIndex,
    @Nullable CreateIssueUnit newParent, @Nullable Integer prevParentId, int expectedType, ArrayList<IssueFieldValue> values) {
    if (prevParentId != null) {
      return newParent != null
        ? new MoveParentType(create, prevStep, stepIndex, newParent, expectedType, values)
        : new MoveFromSubtask(create, prevStep, stepIndex, values);
    }
    return newParent != null
      ? new MoveToSubtask(create, prevStep, stepIndex, newParent, values)
      : new GenericMove(create, prevStep, stepIndex, values);
  }
}

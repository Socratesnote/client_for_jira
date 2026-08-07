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

  /**
   * Which upload unit kind a move step should become, given the target/source subtask flags and what changed.
   * Pure decision table, extracted from {@link #loadStep} so it can be unit-tested without building real
   * ItemVersion/CreateIssueUnit machinery.
   */
  enum RouteKind {
    MOVE_TO_SUBTASK, MOVE_PARENT_TYPE, MOVE_FROM_SUBTASK, GENERIC_MOVE, SET_EPIC_PARENT,
    LEGACY_MOVE_TO_SUBTASK, LEGACY_MOVE_PARENT_TYPE, LEGACY_MOVE_FROM_SUBTASK, LEGACY_GENERIC_MOVE,
    ERROR_SUBTASK_NEEDS_PARENT, ERROR_CONVERT_WITH_EPIC_PARENT, ERROR_COMBINED_TYPE_OR_PROJECT_CHANGE, ERROR_CLEAR_PARENT_UNSUPPORTED
  }

  static RouteKind route(@Nullable Boolean newSub, @Nullable Boolean oldSub, boolean newParentPresent,
    boolean prevParentPresent, boolean parentChanged, boolean typeChanged, boolean projectChanged)
  {
    if (newSub == null) { // issue-type meta not loaded yet - fall back to the legacy parent-presence routing
      if (prevParentPresent) return newParentPresent ? RouteKind.LEGACY_MOVE_PARENT_TYPE : RouteKind.LEGACY_MOVE_FROM_SUBTASK;
      return newParentPresent ? RouteKind.LEGACY_MOVE_TO_SUBTASK : RouteKind.LEGACY_GENERIC_MOVE;
    }
    if (newSub) { // target is a subtask - it must have a parent
      if (!newParentPresent) return RouteKind.ERROR_SUBTASK_NEEDS_PARENT;
      return prevParentPresent ? RouteKind.MOVE_PARENT_TYPE : RouteKind.MOVE_TO_SUBTASK;
    }
    // Target is a standard (generic) issue.
    if (Boolean.TRUE.equals(oldSub)) { // subtask -> generic conversion
      if (newParentPresent) return RouteKind.ERROR_CONVERT_WITH_EPIC_PARENT; // converting AND placing under an Epic in one step: unsupported (see plan B-5)
      return RouteKind.MOVE_FROM_SUBTASK;
    }
    if (!parentChanged) return RouteKind.GENERIC_MOVE; // e.g. retype of an Epic-childed issue
    // Generic issue whose Epic parent changed.
    if (typeChanged || projectChanged) return RouteKind.ERROR_COMBINED_TYPE_OR_PROJECT_CHANGE; // do them as separate edits
    if (!newParentPresent) return RouteKind.ERROR_CLEAR_PARENT_UNSUPPORTED;
    return RouteKind.SET_EPIC_PARENT;
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
    boolean typeChanged = !Util.equals(newTypeItem, oldTypeItem);
    boolean projectChanged = !Util.equals(postState.get(Issue.PROJECT), server.getValue(Issue.PROJECT));

    if (newSub == null) LogHelper.warning("Subtask flag not loaded; routing move by parent presence", trunk, newTypeItem);

    RouteKind kind = route(newSub, oldSub, newParent != null, prevParentId != null, parentChanged, typeChanged, projectChanged);
    switch (kind) {
      case MOVE_TO_SUBTASK: case LEGACY_MOVE_TO_SUBTASK:
        return new MoveToSubtask(create, prevStep, stepIndex, newParent, values);
      case MOVE_PARENT_TYPE: case LEGACY_MOVE_PARENT_TYPE:
        return new MoveParentType(create, prevStep, stepIndex, newParent, expectedType, values);
      case MOVE_FROM_SUBTASK: case LEGACY_MOVE_FROM_SUBTASK:
        return new MoveFromSubtask(create, prevStep, stepIndex, values);
      case GENERIC_MOVE: case LEGACY_GENERIC_MOVE:
        return new GenericMove(create, prevStep, stepIndex, values);
      case SET_EPIC_PARENT:
        return new SetEpicParent(create, prevStep, stepIndex, newParent);
      case ERROR_SUBTASK_NEEDS_PARENT:
        throw UploadUnit.CantUploadException.create("A subtask must have a parent");
      case ERROR_CONVERT_WITH_EPIC_PARENT:
        LogHelper.warning("Converting a subtask to a standard issue under an Epic parent is not supported", trunk, newParentItem);
        throw UploadUnit.CantUploadException.create("Cannot convert a subtask to a standard issue and set an Epic parent at once");
      case ERROR_COMBINED_TYPE_OR_PROJECT_CHANGE:
        throw UploadUnit.CantUploadException.create("Cannot change the type/project and the Epic parent in one step");
      case ERROR_CLEAR_PARENT_UNSUPPORTED:
        //TODO: Support clearing a parent. The payload question is settled - verified against Jira Cloud 2026-08-07:
        // {"fields":{"parent":null}} works and really clears the parent, but ONLY on a non-subtask (a standard issue
        // under an Epic). On a subtask Jira refuses it with 400 "A parent of a subtask cannot be removed", which is
        // correct: a subtask is defined by having a parent, so the equivalent user intent is conversion away from a
        // subtask type instead. Do NOT use {"update":{"parent":[{"set":null}]}} - it returns 204 and silently does
        // nothing. Implementing the split is Stage D5 in the plan; this also covers emptying the Parent field in the
        // Move/Convert dialog to detach an issue.
        LogHelper.warning("Clearing a parent is not supported yet", trunk, prevParentItem);
        throw UploadUnit.CantUploadException.create("Removing a parent is not supported yet");
      default:
        throw UploadUnit.CantUploadException.internalError();
    }
  }
}

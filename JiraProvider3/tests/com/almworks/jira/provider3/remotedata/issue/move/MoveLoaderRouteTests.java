package com.almworks.jira.provider3.remotedata.issue.move;

import com.almworks.util.tests.BaseTestCase;

import static com.almworks.jira.provider3.remotedata.issue.move.MoveLoader.RouteKind.*;
import static com.almworks.jira.provider3.remotedata.issue.move.MoveLoader.route;

// Truth table for MoveLoader.route: which upload unit a move step becomes, given the target/source
// subtask flags and what changed. Pure function - no ItemVersion/CreateIssueUnit machinery needed.
public class MoveLoaderRouteTests extends BaseTestCase {
  // newSub == null: legacy parent-presence routing (issue-type meta not synced yet).
  public void testLegacyNoParents() {
    assertEquals(LEGACY_GENERIC_MOVE, route(null, null, false, false, false, false, false));
  }

  public void testLegacyNewParentOnly() {
    assertEquals(LEGACY_MOVE_TO_SUBTASK, route(null, null, true, false, true, false, false));
  }

  public void testLegacyPrevParentOnly() {
    assertEquals(LEGACY_MOVE_FROM_SUBTASK, route(null, null, false, true, true, false, false));
  }

  public void testLegacyBothParents() {
    assertEquals(LEGACY_MOVE_PARENT_TYPE, route(null, null, true, true, false, false, false));
  }

  // newSub == true: target is a subtask, must have a parent.
  public void testSubtaskWithoutParentErrors() {
    assertEquals(ERROR_SUBTASK_NEEDS_PARENT, route(true, false, false, false, false, false, false));
  }

  public void testSubtaskNewParent() {
    assertEquals(MOVE_TO_SUBTASK, route(true, false, true, false, true, false, false));
  }

  public void testSubtaskChangedParent() {
    assertEquals(MOVE_PARENT_TYPE, route(true, false, true, true, false, false, false));
  }

  // newSub == false, oldSub == true: subtask -> generic conversion.
  public void testConvertToGenericWithEpicParentErrors() {
    assertEquals(ERROR_CONVERT_WITH_EPIC_PARENT, route(false, true, true, true, false, false, false));
  }

  public void testConvertToGenericNoParent() {
    assertEquals(MOVE_FROM_SUBTASK, route(false, true, false, true, true, false, false));
  }

  // newSub == false, oldSub != true: already-generic issue.
  public void testGenericRetypeParentUnchanged() {
    assertEquals(GENERIC_MOVE, route(false, false, true, true, false, false, false));
  }

  public void testGenericParentUnchangedEvenWithoutOldSubInfo() {
    assertEquals(GENERIC_MOVE, route(false, null, false, false, false, false, false));
  }

  public void testGenericCombinedTypeChangeWithParentChangeErrors() {
    assertEquals(ERROR_COMBINED_TYPE_OR_PROJECT_CHANGE, route(false, false, true, false, true, true, false));
  }

  public void testGenericCombinedProjectChangeWithParentChangeErrors() {
    assertEquals(ERROR_COMBINED_TYPE_OR_PROJECT_CHANGE, route(false, false, true, false, true, false, true));
  }

  public void testGenericClearParentUnsupported() {
    assertEquals(ERROR_CLEAR_PARENT_UNSUPPORTED, route(false, false, false, true, true, false, false));
  }

  public void testGenericSetEpicParent() {
    assertEquals(SET_EPIC_PARENT, route(false, false, true, false, true, false, false));
  }
}

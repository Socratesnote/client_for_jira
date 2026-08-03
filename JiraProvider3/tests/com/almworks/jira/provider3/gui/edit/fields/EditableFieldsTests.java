package com.almworks.jira.provider3.gui.edit.fields;

import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Collections15;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Covers which fields a multi-issue edit may offer. The rule that matters: an issue with no stored editmeta knows
 * nothing, and must not be read as "nothing is editable" - doing so would empty the intersection and leave the user
 * with an editor offering almost no fields.
 */
public class EditableFieldsTests extends BaseTestCase {
  public void testOneIssueKeepsItsOwnFields() {
    assertEquals(set("summary", "priority"), EditableFields.intersect(perIssue(set("summary", "priority"))));
  }

  public void testSeveralIssuesIntersect() {
    Set<String> common = EditableFields.intersect(perIssue(set("summary", "priority"), set("summary", "duedate")));
    // Priority is editable on one issue only, so offering it would fail on upload for the other.
    assertEquals(set("summary"), common);
  }

  public void testNoOverlapLeavesNothing() {
    assertEquals(set(), EditableFields.intersect(perIssue(set("priority"), set("duedate"))));
  }

  public void testIssueWithoutStoredEditmetaIsSkipped() {
    Set<String> common = EditableFields.intersect(perIssue(set("summary", "priority"), null));
    // The unknown issue must not empty the result; what the known issue reports still stands.
    assertEquals(set("summary", "priority"), common);
  }

  public void testNothingKnownAtAll() {
    assertNull(EditableFields.intersect(perIssue(null, null)));
    assertNull(EditableFields.intersect(Collections15.<Set<String>>arrayList()));
  }

  /**
   * A field is only reported as not editable when there is data saying so. Without data every field stays editable,
   * so nothing is ever disabled on a guess.
   */
  public void testUnknownNeverDisables() {
    EditableFields unknown = new EditableFields(null);
    assertFalse(unknown.isKnown());
    assertFalse(unknown.isKnownNotEditable("priority"));

    EditableFields known = new EditableFields(set("summary"));
    assertTrue(known.isKnown());
    assertTrue(known.isKnownNotEditable("priority"));
    assertFalse(known.isKnownNotEditable("summary"));
  }

  private static List<Set<String>> perIssue(Set<String>... issues) {
    return Arrays.asList(issues);
  }

  private static Set<String> set(String... ids) {
    return Collections15.hashSet(Arrays.asList(ids));
  }
}

package com.almworks.jira.provider3.gui.edit.editors.move;

import com.almworks.integers.LongArray;
import com.almworks.items.api.DBReader;
import com.almworks.items.api.DBWriter;
import com.almworks.items.api.MemoryDatabaseFixture;
import com.almworks.items.api.ReadTransaction;
import com.almworks.items.gui.edit.DefaultEditModel;
import com.almworks.items.sync.VersionSource;
import com.almworks.items.sync.util.BranchSource;
import com.almworks.jira.provider3.schema.Issue;
import com.almworks.jira.provider3.schema.IssueType;
import com.almworks.util.commons.Procedure;

// Truth table for MoveController.classifyBySubtaskFlag: whether a selection of issues is
// unanimously subtask, unanimously generic, mixed, or undeterminable (type meta not synced).
public class MoveControllerClassifyTests extends MemoryDatabaseFixture {
  private long myGenericType;
  private long mySubtaskType;
  private long myUnknownType; // IssueType.SUBTASK left unset (null)

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    writeNoFail(new Procedure<DBWriter>() {
      @Override
      public void invoke(DBWriter writer) {
        myGenericType = writer.nextItem();
        writer.setValue(myGenericType, IssueType.SUBTASK, false);
        mySubtaskType = writer.nextItem();
        writer.setValue(mySubtaskType, IssueType.SUBTASK, true);
        myUnknownType = writer.nextItem();
      }
    });
  }

  private long createIssue(final long type) {
    final long[] id = {0};
    writeNoFail(new Procedure<DBWriter>() {
      @Override
      public void invoke(DBWriter writer) {
        id[0] = writer.nextItem();
        writer.setValue(id[0], Issue.ISSUE_TYPE, type);
      }
    });
    return id[0];
  }

  private Boolean classify(long... issues) {
    final LongArray items = LongArray.create(issues);
    final DefaultEditModel.Root model = DefaultEditModel.Root.editItems(items);
    return db.readForeground(new ReadTransaction<Boolean>() {
      @Override
      public Boolean transaction(DBReader reader) {
        VersionSource source = BranchSource.trunk(reader);
        return MoveController.classifyBySubtaskFlag(source, model);
      }
    }).waitForCompletion();
  }

  public void testNoIssues() {
    assertNull(classify());
  }

  public void testSingleGeneric() {
    assertEquals(Boolean.FALSE, classify(createIssue(myGenericType)));
  }

  public void testSingleSubtask() {
    assertEquals(Boolean.TRUE, classify(createIssue(mySubtaskType)));
  }

  public void testSingleUnknown() {
    assertNull(classify(createIssue(myUnknownType)));
  }

  public void testAllGeneric() {
    assertEquals(Boolean.FALSE, classify(createIssue(myGenericType), createIssue(myGenericType)));
  }

  public void testAllSubtask() {
    assertEquals(Boolean.TRUE, classify(createIssue(mySubtaskType), createIssue(mySubtaskType)));
  }

  public void testMixedGenericAndSubtask() {
    assertNull(classify(createIssue(myGenericType), createIssue(mySubtaskType)));
  }

  // An unknown-type issue is skipped rather than treated as a mismatch, so it doesn't force "mixed".
  public void testGenericAndUnknown() {
    assertEquals(Boolean.FALSE, classify(createIssue(myGenericType), createIssue(myUnknownType)));
  }

  public void testSubtaskAndUnknown() {
    assertEquals(Boolean.TRUE, classify(createIssue(mySubtaskType), createIssue(myUnknownType)));
  }

  public void testAllUnknown() {
    assertNull(classify(createIssue(myUnknownType), createIssue(myUnknownType)));
  }
}

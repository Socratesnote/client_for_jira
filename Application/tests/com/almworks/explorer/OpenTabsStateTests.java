package com.almworks.explorer;

import com.almworks.api.application.ItemCollectionContext;
import com.almworks.api.application.ItemSource;
import com.almworks.api.application.tree.QueryResult;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.items.api.DBFilter;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.tabs.ContentTab;
import com.almworks.util.components.tabs.TabsManager;
import com.almworks.util.config.Configuration;
import com.almworks.util.config.MapMedium;
import com.almworks.util.tests.GUITestCase;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;

/**
 * Covers tab persistence: the workspace-config round trip, which open tabs are collected for saving, what a restore
 * re-opens, and the startup sequence that ties the two together. What is still uncovered is the delivery of the
 * change notification itself - {@code Explorer.addTabChangeListener} gates it through {@code ThreadGate.AWT_QUEUED},
 * a thread-timing property - and the readiness gate deciding when startup runs, both of which need the container.
 */
public class OpenTabsStateTests extends GUITestCase {
  private Configuration myConfig;
  private TabsManager myTabsManager;

  protected void setUp() throws Exception {
    super.setUp();
    myConfig = MapMedium.createConfig();
    myTabsManager = new TabsManager();
    myTabsManager.getComponent();
  }

  // ---- config round trip ----

  public void testConfigRoundTrip() {
    OpenTabsState.write(myConfig, listOf("n1", "n2", "n3"), "n2");
    assertEquals(listOf("n1", "n2", "n3"), OpenTabsState.readNodeIds(myConfig));
    assertEquals("n2", OpenTabsState.readSelectedNodeId(myConfig));
  }

  public void testEmptyConfigReadsAsNothingOpen() {
    assertTrue(OpenTabsState.readNodeIds(myConfig).isEmpty());
    assertNull(OpenTabsState.readSelectedNodeId(myConfig));
  }

  public void testNoSelectionRoundTripsAsNull() {
    OpenTabsState.write(myConfig, listOf("n1"), null);
    assertNull(OpenTabsState.readSelectedNodeId(myConfig));
  }

  /** The save path fires on every tab change, so a write has to replace the stored list rather than add to it. */
  public void testWriteReplacesThePreviousList() {
    OpenTabsState.write(myConfig, listOf("n1", "n2", "n3"), "n1");
    OpenTabsState.write(myConfig, listOf("n4"), "n4");
    assertEquals(listOf("n4"), OpenTabsState.readNodeIds(myConfig));
    assertEquals("n4", OpenTabsState.readSelectedNodeId(myConfig));
  }

  public void testWritingAnEmptyListClearsTheStoredTabs() {
    OpenTabsState.write(myConfig, listOf("n1", "n2"), "n1");
    OpenTabsState.write(myConfig, Collections15.<String>arrayList(), null);
    assertTrue(OpenTabsState.readNodeIds(myConfig).isEmpty());
    assertNull(OpenTabsState.readSelectedNodeId(myConfig));
  }

  // ---- collecting from open tabs ----

  public void testCollectsNodeIdsInTabOrder() {
    addNodeTab("a", "n1");
    addNodeTab("b", "n2");
    addNodeTab("c", "n3");
    assertEquals(listOf("n1", "n2", "n3"), OpenTabsState.collectOpenNodeIds(myTabsManager));
  }

  /** An ad-hoc tab - text search, URL, summary - carries no node id and is not restorable, so it is not saved. */
  public void testAdHocTabsAreSkipped() {
    addNodeTab("a", "n1");
    addTab("adhoc");
    addNodeTab("c", "n3");
    assertEquals(listOf("n1", "n3"), OpenTabsState.collectOpenNodeIds(myTabsManager));
  }

  public void testSelectedNodeId() {
    ContentTab a = addNodeTab("a", "n1");
    addNodeTab("b", "n2");
    assertEquals("n2", OpenTabsState.getSelectedNodeId(myTabsManager));
    a.select();
    assertEquals("n1", OpenTabsState.getSelectedNodeId(myTabsManager));
  }

  public void testSelectedAdHocTabHasNoNodeId() {
    addNodeTab("a", "n1");
    addTab("adhoc");
    assertNull(OpenTabsState.getSelectedNodeId(myTabsManager));
  }

  public void testNoTabsMeansNoSelection() {
    assertNull(OpenTabsState.getSelectedNodeId(myTabsManager));
  }

  /**
   * Reordering a tab changes the saved order, with no help from the reorder code - the collected list simply follows
   * tab order. This is the whole mechanism behind "the new order is persisted for free".
   */
  public void testReorderingChangesTheCollectedOrder() {
    addNodeTab("a", "n1");
    addNodeTab("b", "n2");
    ContentTab c = addNodeTab("c", "n3");
    myTabsManager.moveTab(c, 0);
    assertEquals(listOf("n3", "n1", "n2"), OpenTabsState.collectOpenNodeIds(myTabsManager));
  }

  // ---- restore ----

  public void testRestoreOpensEveryResolvedNodeInOrder() {
    RecordingOpener opener = new RecordingOpener();
    OpenTabsState.restoreNodeTabs(myTabsManager, runnable("n1", "n2", "n3"), opener, listOf("n1", "n2", "n3"), null);
    assertEquals(listOf("n1", "n2", "n3"), opener.myOpened);
  }

  public void testUnresolvedNodeIdIsSkipped() {
    RecordingOpener opener = new RecordingOpener();
    OpenTabsState.restoreNodeTabs(myTabsManager, runnable("n1", "n3"), opener, listOf("n1", "n2", "n3"), null);
    assertEquals(listOf("n1", "n3"), opener.myOpened);
  }

  /** One node failing to open must not cost the others: each is opened inside its own try/catch. */
  public void testAFailedOpenDoesNotAbortTheRest() {
    RecordingOpener opener = new RecordingOpener();
    opener.myFailOn = "n2";
    OpenTabsState.restoreNodeTabs(myTabsManager, runnable("n1", "n2", "n3"), opener, listOf("n1", "n2", "n3"), null);
    assertEquals(listOf("n1", "n3"), opener.myOpened);
  }

  /**
   * Each restored tab selects itself as it is created, so without the trailing re-selection the last restored tab
   * would always win rather than the one the user left selected.
   */
  public void testSelectedTabIsReselectedAfterRestore() {
    OpenTabsState.restoreNodeTabs(myTabsManager, runnable("n1", "n2", "n3"), new TabCreatingOpener(),
      listOf("n1", "n2", "n3"), "n1");
    assertEquals("n1", OpenTabsState.getSelectedNodeId(myTabsManager));
  }

  public void testSelectedIdThatDidNotRestoreLeavesTheSelectionAlone() {
    OpenTabsState.restoreNodeTabs(myTabsManager, runnable("n1", "n2"), new TabCreatingOpener(),
      listOf("n1", "n2"), "gone");
    assertEquals("n2", OpenTabsState.getSelectedNodeId(myTabsManager));
  }

  // ---- startup sequence ----

  public void testStartupRestoresTheSavedTabs() {
    OpenTabsState.write(myConfig, listOf("n1", "n2", "n3"), "n2");
    RecordingRestorer restorer = new RecordingRestorer();
    RecordingTrigger trigger = new RecordingTrigger();
    OpenTabsState.startPersistence(myConfig, restorer, snapshot(), trigger);
    assertEquals(1, restorer.myCalls);
    assertEquals(listOf("n1", "n2", "n3"), restorer.myNodeIds);
    assertEquals("n2", restorer.mySelectedNodeId);
  }

  /** Nothing saved means nothing to re-open, and the stored state must survive untouched rather than be rewritten. */
  public void testStartupWithNothingSavedRestoresNothing() {
    RecordingRestorer restorer = new RecordingRestorer();
    OpenTabsState.startPersistence(myConfig, restorer, snapshot(), new RecordingTrigger());
    assertEquals(0, restorer.myCalls);
    assertTrue(OpenTabsState.readNodeIds(myConfig).isEmpty());
    assertNull(OpenTabsState.readSelectedNodeId(myConfig));
  }

  /**
   * The listener is registered only once the restore has returned. Re-opening tabs changes the tab set repeatedly, so
   * a listener live during the restore would write a partial list over the full one and lose tabs on the next launch.
   */
  public void testSavingStartsOnlyAfterTheRestoreHasFinished() {
    OpenTabsState.write(myConfig, listOf("n1", "n2"), "n1");
    final RecordingTrigger trigger = new RecordingTrigger();
    OpenTabsState.startPersistence(myConfig, new OpenTabsState.TabRestorer() {
      public void restoreNodeTabs(List<String> nodeIds, @Nullable String selectedNodeId) {
        addNodeTab("a", "partial");
        trigger.fire(); // No listener yet, so this cannot reach the config.
      }
    }, snapshot(), trigger);
    assertEquals(listOf("n1", "n2"), OpenTabsState.readNodeIds(myConfig));
    assertEquals("n1", OpenTabsState.readSelectedNodeId(myConfig));
  }

  public void testATabChangeAfterStartupIsPersisted() {
    RecordingTrigger trigger = new RecordingTrigger();
    OpenTabsState.startPersistence(myConfig, new RecordingRestorer(), snapshot(), trigger);
    addNodeTab("a", "n1");
    addNodeTab("b", "n2");
    trigger.fire();
    assertEquals(listOf("n1", "n2"), OpenTabsState.readNodeIds(myConfig));
    assertEquals("n2", OpenTabsState.readSelectedNodeId(myConfig));
  }

  public void testEachChangeReplacesTheStoredState() {
    RecordingTrigger trigger = new RecordingTrigger();
    OpenTabsState.startPersistence(myConfig, new RecordingRestorer(), snapshot(), trigger);
    ContentTab a = addNodeTab("a", "n1");
    addNodeTab("b", "n2");
    trigger.fire();
    a.select();
    trigger.fire();
    assertEquals(listOf("n1", "n2"), OpenTabsState.readNodeIds(myConfig));
    assertEquals("n1", OpenTabsState.readSelectedNodeId(myConfig));
  }

  /**
   * Nothing to restore into - no navigation tree yet - skips the restore only. Saving still starts, because the
   * session goes on and the tabs the user opens by hand have to be persisted like any others.
   */
  public void testStartupWithoutARestorerStillPersistsChanges() {
    OpenTabsState.write(myConfig, listOf("n1"), "n1");
    RecordingTrigger trigger = new RecordingTrigger();
    OpenTabsState.startPersistence(myConfig, null, snapshot(), trigger);
    assertTrue(trigger.isRegistered());
    assertEquals(listOf("n1"), OpenTabsState.readNodeIds(myConfig));
    addNodeTab("a", "n9");
    trigger.fire();
    assertEquals(listOf("n9"), OpenTabsState.readNodeIds(myConfig));
  }

  /**
   * The explorer can be dropped while a tab change is still queued, and a save then has no tab state to read. Writing
   * what it can see - nothing - would clear the saved tabs on the way out, so a null snapshot skips the write.
   */
  public void testASaveWithNoTabStateLeavesTheStoredTabsAlone() {
    OpenTabsState.write(myConfig, listOf("n1", "n2"), "n2");
    RecordingTrigger trigger = new RecordingTrigger();
    OpenTabsState.startPersistence(myConfig, new RecordingRestorer(), new OpenTabsState.TabsSnapshot() {
      @Nullable
      public List<String> collectOpenNodeIds() {
        return null;
      }

      @Nullable
      public String getSelectedNodeId() {
        return null;
      }
    }, trigger);
    trigger.fire();
    assertEquals(listOf("n1", "n2"), OpenTabsState.readNodeIds(myConfig));
    assertEquals("n2", OpenTabsState.readSelectedNodeId(myConfig));
  }

  // ---- current behaviour, pinned deliberately ----

  /**
   * Pins current behaviour, not desired behaviour. A node whose query is not runnable yet is dropped with no retry
   * and no log, which is a suspect in the open "restored tabs come back empty" report. A fix there is expected to
   * change this test.
   */
  public void testNonRunnableQueryIsSkippedOnRestore_currentBehaviour() {
    RecordingOpener opener = new RecordingOpener();
    NodeQueriesStub queries = runnable("n1", "n3");
    queries.myResults.put("n2", QueryResult.NO_RESULT);
    OpenTabsState.restoreNodeTabs(myTabsManager, queries, opener, listOf("n1", "n2", "n3"), null);
    assertEquals(listOf("n1", "n3"), opener.myOpened);
  }

  /**
   * Pins current behaviour, not desired behaviour. A tab is collected only while it is showing, which means having a
   * component - so a tab that exists but has not been populated yet is left out of the saved list, and a save
   * triggered at that moment writes a shorter list than the user has open. The other suspect in the same report.
   */
  public void testTabWithoutAComponentIsNotSaved_currentBehaviour() {
    addNodeTab("a", "n1");
    ContentTab pending = myTabsManager.createTab("pending");
    pending.setUserProperty(OpenTabsState.NODE_ID_KEY, "n2");
    assertFalse(pending.isShowing());
    assertEquals(listOf("n1"), OpenTabsState.collectOpenNodeIds(myTabsManager));
  }

  // ---- helpers ----

  private ContentTab addTab(String name) {
    ContentTab tab = myTabsManager.createTab(name);
    tab.setComponent(new JLabel(name));
    return tab;
  }

  private ContentTab addNodeTab(String name, String nodeId) {
    ContentTab tab = addTab(name);
    tab.setUserProperty(OpenTabsState.NODE_ID_KEY, nodeId);
    return tab;
  }

  private static List<String> listOf(String... values) {
    List<String> result = Collections15.arrayList();
    for (String value : values) result.add(value);
    return result;
  }

  /** The snapshot the explorer supplies in production: whatever the tabs manager currently holds. */
  private OpenTabsState.TabsSnapshot snapshot() {
    return new OpenTabsState.TabsSnapshot() {
      public List<String> collectOpenNodeIds() {
        return OpenTabsState.collectOpenNodeIds(myTabsManager);
      }

      @Nullable
      public String getSelectedNodeId() {
        return OpenTabsState.getSelectedNodeId(myTabsManager);
      }
    };
  }

  private static class RecordingRestorer implements OpenTabsState.TabRestorer {
    private int myCalls = 0;
    private List<String> myNodeIds = null;
    private String mySelectedNodeId = null;

    public void restoreNodeTabs(List<String> nodeIds, @Nullable String selectedNodeId) {
      myCalls++;
      myNodeIds = nodeIds;
      mySelectedNodeId = selectedNodeId;
    }
  }

  /** Keeps the registered listener so a test can fire it, standing in for a real tab change. */
  private static class RecordingTrigger implements OpenTabsState.SaveTrigger {
    private ChangeListener myListener = null;

    public void addTabChangeListener(ChangeListener listener) {
      myListener = listener;
    }

    boolean isRegistered() {
      return myListener != null;
    }

    void fire() {
      if (myListener != null) myListener.onChange();
    }
  }

  private static NodeQueriesStub runnable(String... nodeIds) {
    NodeQueriesStub stub = new NodeQueriesStub();
    for (String nodeId : nodeIds) stub.myResults.put(nodeId, new RunnableQueryResult(nodeId));
    return stub;
  }

  private static class NodeQueriesStub implements OpenTabsState.NodeQueries {
    private final Map<String, QueryResult> myResults = Collections15.hashMap();

    @Nullable
    public QueryResult getQueryResult(String nodeId) {
      return myResults.get(nodeId);
    }
  }

  /** Records which nodes were asked to open, identified by their collection context's short name. */
  private static class RecordingOpener implements OpenTabsState.TabOpener {
    private final List<String> myOpened = Collections15.arrayList();
    private String myFailOn = null;

    public void showItemsInTab(ItemSource source, ItemCollectionContext context, boolean focusToTable) {
      String name = context.getShortName();
      if (name.equals(myFailOn)) throw new IllegalStateException("cannot open " + name);
      myOpened.add(name);
    }
  }

  /** Opens a real tab, so the trailing re-selection in restoreNodeTabs has something to select. */
  private class TabCreatingOpener implements OpenTabsState.TabOpener {
    public void showItemsInTab(ItemSource source, ItemCollectionContext context, boolean focusToTable) {
      addNodeTab(context.getShortName(), context.getShortName());
    }
  }

  private static class RunnableQueryResult extends QueryResult.AlwaysReady {
    private final ItemCollectionContext myContext;

    RunnableQueryResult(String nodeId) {
      myContext = ItemCollectionContext.createGeneral(nodeId, null);
    }

    @Nullable
    public ItemSource getItemSource() {
      return ItemSource.EMPTY;
    }

    @Nullable
    public ItemCollectionContext getCollectionContext() {
      return myContext;
    }

    @Nullable
    public DBFilter getDbFilter() {
      return null;
    }

    public long getVersion() {
      return 0;
    }

    @Nullable
    public ItemHypercube getHypercube(boolean precise) {
      return null;
    }
  }
}

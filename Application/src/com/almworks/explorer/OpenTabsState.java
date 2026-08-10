package com.almworks.explorer;

import com.almworks.api.application.ItemCollectionContext;
import com.almworks.api.application.ItemSource;
import com.almworks.api.application.tree.QueryResult;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.tabs.ContentTab;
import com.almworks.util.components.tabs.TabsManager;
import com.almworks.util.config.Configuration;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The tab-persistence logic, kept apart from the classes that call it so it can be tested: the explorer component is
 * only constructible through the application container, and {@link Explorer} builds a whole form in its constructor,
 * while none of the work below needs either.
 *
 * <p>Two narrow interfaces stand in for the navigation tree and the explorer component, which are wide interfaces with
 * no test double available.
 */
class OpenTabsState {
  /** Node id of the tab's backing navigation node, if any - null for ad-hoc tabs (text search, URL, summary, ...). */
  static final TypedKey<String> NODE_ID_KEY = TypedKey.create("nodeId");

  private static final String OPEN_TABS_CONFIG = "openTabs";
  private static final String OPEN_TAB_NODE = "tab";
  private static final String SELECTED_TAB_NODE = "selected";

  /** Resolves a persisted node id to the query behind it. Null for an id that no longer names a node. */
  interface NodeQueries {
    @Nullable
    QueryResult getQueryResult(String nodeId);
  }

  /** Opens a tab for a resolved query - in production, the explorer component's own tab-opening entry point. */
  interface TabOpener {
    void showItemsInTab(ItemSource source, ItemCollectionContext context, boolean focusToTable);
  }

  /** Re-opens the persisted tabs. Null where there is nothing to restore into - no explorer, or no navigation tree. */
  interface TabRestorer {
    void restoreNodeTabs(List<String> nodeIds, @Nullable String selectedNodeId);
  }

  /**
   * The tab state as it stands now, read whenever a change has to be persisted. A null id list means "there is no
   * tab state to read" - the explorer is gone - and is not the same as an empty one, which would clear the stored
   * tabs. Nothing is written in that case.
   */
  interface TabsSnapshot {
    @Nullable
    List<String> collectOpenNodeIds();

    @Nullable
    String getSelectedNodeId();
  }

  /** Registers a callback for "the set of open tabs or the selection changed". */
  interface SaveTrigger {
    void addTabChangeListener(ChangeListener listener);
  }

  private OpenTabsState() {}

  /**
   * Restores the tabs left open at the end of the previous session, then keeps the stored state current as tabs
   * change. The order matters: the listener is registered only once the restore has returned, so a save triggered
   * while tabs are still being re-opened cannot persist a partial set over the full one.
   */
  static void startPersistence(final Configuration workspaceConfig, @Nullable TabRestorer restorer,
    final TabsSnapshot snapshot, SaveTrigger trigger)
  {
    if (restorer != null) {
      List<String> nodeIds = readNodeIds(workspaceConfig);
      if (!nodeIds.isEmpty()) restorer.restoreNodeTabs(nodeIds, readSelectedNodeId(workspaceConfig));
    }
    trigger.addTabChangeListener(new ChangeListener() {
      public void onChange() {
        List<String> nodeIds = snapshot.collectOpenNodeIds();
        if (nodeIds == null) return;
        write(workspaceConfig, nodeIds, snapshot.getSelectedNodeId());
      }
    });
  }

  static List<String> readNodeIds(Configuration workspaceConfig) {
    return workspaceConfig.getOrCreateSubset(OPEN_TABS_CONFIG).getAllSettings(OPEN_TAB_NODE);
  }

  @Nullable
  static String readSelectedNodeId(Configuration workspaceConfig) {
    String selected = workspaceConfig.getOrCreateSubset(OPEN_TABS_CONFIG).getSetting(SELECTED_TAB_NODE, "");
    return selected.isEmpty() ? null : selected;
  }

  static void write(Configuration workspaceConfig, List<String> nodeIds, @Nullable String selectedNodeId) {
    Configuration config = workspaceConfig.getOrCreateSubset(OPEN_TABS_CONFIG);
    config.setSettings(OPEN_TAB_NODE, nodeIds);
    config.setSetting(SELECTED_TAB_NODE, selectedNodeId == null ? "" : selectedNodeId);
  }

  /** Node ids of currently open, node-backed tabs, in tab order. Ad-hoc tabs (no backing node) are skipped. */
  static List<String> collectOpenNodeIds(TabsManager tabs) {
    List<String> result = Collections15.arrayList();
    for (ContentTab tab : tabs.getTabs()) {
      if (!tab.isShowing()) continue;
      String nodeId = tab.getUserProperty(NODE_ID_KEY);
      if (nodeId != null) result.add(nodeId);
    }
    return result;
  }

  /** Node id of the currently selected tab, or null if there is none or it isn't node-backed. */
  @Nullable
  static String getSelectedNodeId(TabsManager tabs) {
    ContentTab selected = tabs.getSelectedTab();
    return selected == null ? null : selected.getUserProperty(NODE_ID_KEY);
  }

  /**
   * Attempt to re-open previously open node-backed tabs. Node ids that no longer resolve (node deleted,
   * connection removed) or whose query isn't runnable are silently skipped.
   */
  static void restoreNodeTabs(TabsManager tabs, NodeQueries queries, TabOpener opener, List<String> nodeIds,
    @Nullable String selectedNodeId)
  {
    for (String nodeId : nodeIds) {
      QueryResult result = queries.getQueryResult(nodeId);
      if (result == null) continue;
      if (!result.isRunnable()) continue;
      ItemSource source = result.getItemSource();
      ItemCollectionContext context = result.getCollectionContext();
      if (source == null || context == null) continue;
      try {
        opener.showItemsInTab(source, context, false);
      } catch (Exception e) {
        Log.warn("Failed to restore tab for node " + nodeId, e);
      }
    }
    if (selectedNodeId == null) return;
    for (ContentTab tab : tabs.getTabs()) {
      if (selectedNodeId.equals(tab.getUserProperty(NODE_ID_KEY))) {
        tab.select();
        break;
      }
    }
  }
}

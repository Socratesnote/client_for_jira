package com.almworks.util.components.tabs;

import com.almworks.util.collections.ChangeListener;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.tests.GUITestCase;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Covers drag-to-reorder at the model level: {@link TabsManager#moveTab} and {@link ContentTab#moveTo}, which is
 * everything the tab-header drag gesture does once it has resolved a target index. The gesture itself lives in
 * {@link TabComponent} and is not covered here.
 */
public class TabsManagerReorderTests extends GUITestCase {
  private TabsManager myTabsManager;
  private JComponent myTabsHolderComponent;

  protected void setUp() throws Exception {
    super.setUp();
    myTabsManager = new TabsManager();
    myTabsHolderComponent = myTabsManager.getComponent();
  }

  public void testMoveForward() {
    ContentTab a = addTab("a");
    ContentTab b = addTab("b");
    ContentTab c = addTab("c");
    myTabsManager.moveTab(a, 2);
    checkOrder("b", "c", "a");
    checkModelOrder(b, c, a);
  }

  public void testMoveBackward() {
    ContentTab a = addTab("a");
    ContentTab b = addTab("b");
    ContentTab c = addTab("c");
    myTabsManager.moveTab(c, 0);
    checkOrder("c", "a", "b");
    checkModelOrder(c, a, b);
  }

  public void testMoveToMiddle() {
    ContentTab a = addTab("a");
    ContentTab b = addTab("b");
    ContentTab c = addTab("c");
    ContentTab d = addTab("d");
    myTabsManager.moveTab(d, 1);
    checkOrder("a", "d", "b", "c");
    checkModelOrder(a, d, b, c);
  }

  /** A drop past either end of the strip is a real gesture, so the index is clamped rather than rejected. */
  public void testTargetIndexIsClamped() {
    ContentTab a = addTab("a");
    addTab("b");
    ContentTab c = addTab("c");
    myTabsManager.moveTab(a, 99);
    checkOrder("b", "c", "a");
    myTabsManager.moveTab(c, -5);
    checkOrder("c", "b", "a");
  }

  public void testNullTabIsIgnored() {
    addTab("a");
    addTab("b");
    myTabsManager.moveTab(null, 0);
    checkOrder("a", "b");
  }

  public void testHiddenTabIsNotMoved() {
    ContentTab a = addTab("a");
    ContentTab b = addTab("b");
    b.setVisible(false);
    myTabsManager.moveTab(b, 0);
    checkOrder("a");
    checkModelOrder(a, b);
  }

  public void testMoveToOwnPositionChangesNothing() {
    ContentTab a = addTab("a");
    ContentTab b = addTab("b");
    myTabsManager.moveTab(b, 1);
    checkOrder("a", "b");
    checkModelOrder(a, b);
  }

  /** With a single tab there is no tabbed pane - the tab is shown directly - so there is nothing to reorder. */
  public void testSingleTabIsNotReordered() {
    ContentTab a = addTab("a");
    assertNull(getTabbedPaneOrNull());
    myTabsManager.moveTab(a, 0);
    myTabsManager.moveTab(a, 3);
    assertNull(getTabbedPaneOrNull());
    checkModelOrder(a);
  }

  /**
   * The target index counts shown tabs only, so a hidden tab sitting between two shown ones must not consume a
   * position. This is the one non-obvious step in {@link TabsManager#moveTab}.
   */
  public void testHiddenTabsDoNotConsumeAPosition() {
    ContentTab a = addTab("a");
    ContentTab hidden = addTab("hidden");
    ContentTab b = addTab("b");
    ContentTab c = addTab("c");
    hidden.setVisible(false);
    checkOrder("a", "b", "c");

    myTabsManager.moveTab(c, 0);
    checkOrder("c", "a", "b");
    checkModelOrder(c, a, hidden, b);

    // The hidden tab still comes back in its model position, between the tabs it sits between.
    hidden.setVisible(true);
    checkOrder("c", "a", "hidden", "b");
  }

  public void testContentTabMoveToDelegatesToTheManager() {
    ContentTab a = addTab("a");
    ContentTab b = addTab("b");
    ContentTab c = addTab("c");
    c.moveTo(0);
    checkOrder("c", "a", "b");
    checkModelOrder(c, a, b);
  }

  /**
   * The saved tab order rides on this: the tab-persistence listener in the explorer is registered on the manager's
   * Modifiable, so a reorder has to fire it for the new order to reach the workspace config.
   *
   * <p>A move re-inserts the tab, and insertion selects it, so more than one change can fire and the moved tab ends
   * up selected. Assert neither an exact count nor an unchanged selection.
   */
  public void testMoveFiresTheModifiableAndSelectsTheMovedTab() {
    addTab("a");
    addTab("b");
    ContentTab c = addTab("c");
    final int[] fired = {0};
    myTabsManager.getModifiable().addChangeListener(Lifespan.FOREVER, ThreadGate.STRAIGHT, new ChangeListener() {
      public void onChange() {
        fired[0]++;
      }
    });
    myTabsManager.moveTab(c, 0);
    assertTrue("expected at least one change event, got " + fired[0], fired[0] > 0);
    assertSame(c, myTabsManager.getSelectedTab());
    assertEquals(0, myTabsManager.getSelectedTabIndex());
  }

  private ContentTab addTab(String name) {
    ContentTab tab = myTabsManager.createTab(name);
    tab.setComponent(new JLabel(name));
    return tab;
  }

  private void checkOrder(String... expectedNames) {
    JTabbedPane pane = getTabbedPaneOrNull();
    if (pane == null) {
      assertEquals("no tabbed pane, so at most one tab is shown", 1, expectedNames.length);
      return;
    }
    assertEquals(expectedNames.length, pane.getTabCount());
    for (int i = 0; i < expectedNames.length; i++) {
      assertEquals("shown tab " + i, expectedNames[i], pane.getTitleAt(i));
    }
  }

  private void checkModelOrder(ContentTab... expected) {
    List<ContentTab> tabs = myTabsManager.getTabs();
    assertEquals(expected.length, tabs.size());
    for (int i = 0; i < expected.length; i++) {
      assertSame("model tab " + i, expected[i], tabs.get(i));
    }
  }

  private JTabbedPane getTabbedPaneOrNull() {
    if (myTabsHolderComponent.getComponentCount() == 0)
      return null;
    Component component = myTabsHolderComponent.getComponent(0);
    return component instanceof JTabbedPane ? (JTabbedPane) component : null;
  }
}

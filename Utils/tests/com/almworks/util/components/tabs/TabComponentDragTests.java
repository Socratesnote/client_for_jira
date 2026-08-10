package com.almworks.util.components.tabs;

import com.almworks.util.tests.GUITestCase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Covers the drag-to-reorder gesture in {@link TabComponent}: the movement threshold, the drop target resolved from
 * the release point, and the rule that a drag-release does not also act as a click on the tab header.
 *
 * <p>These need a laid-out tab strip, because the drop target comes from {@link JTabbedPane#indexAtLocation}, which
 * goes through the look-and-feel's tab bounds. No window or peer is needed - a size and an explicit layout pass are
 * enough. {@link MacTabComponent} has no drag handling, so on Aqua there is nothing here to test.
 */
public class TabComponentDragTests extends GUITestCase {
  private static final int PANE_WIDTH = 400;
  private static final int PANE_HEIGHT = 120;

  private TabsManager myTabsManager;
  private JTabbedPane myPane;

  protected void setUp() throws Exception {
    super.setUp();
    myTabsManager = new TabsManager();
    myTabsManager.getComponent();
    addTab("a");
    addTab("b");
    addTab("c");
    myPane = (JTabbedPane) myTabsManager.getComponent().getComponent(0);
    myPane.setSize(PANE_WIDTH, PANE_HEIGHT);
    myPane.doLayout();
  }

  public void testMovementBelowTheThresholdIsNotADrag() {
    TabComponent header = header(0);
    if (header == null) return;
    press(header, 20);
    drag(header, 23);
    release(header, atTabCentre(2, header));
    checkOrder("a", "b", "c");
  }

  public void testDragDropsTheTabAtTheReleasePosition() {
    TabComponent header = header(0);
    if (header == null) return;
    press(header, 20);
    drag(header, 40);
    release(header, atTabCentre(2, header));
    checkOrder("b", "c", "a");
  }

  public void testDragBackwards() {
    TabComponent header = header(2);
    if (header == null) return;
    press(header, 20);
    drag(header, 5);
    release(header, atTabCentre(0, header));
    checkOrder("c", "a", "b");
  }

  /** A drag-release is consumed, so it must not also close the tab the way a plain middle-click release would. */
  public void testDragReleaseDoesNotCloseTheTab() {
    TabComponent header = header(0);
    if (header == null) return;
    press(header, 20);
    drag(header, 40);
    Point at = atTabCentre(2, header);
    header.mouseReleased(new MouseEvent(header, MouseEvent.MOUSE_RELEASED, 0, 0, at.x, at.y, 1, false,
      MouseEvent.BUTTON2));
    assertEquals(3, myPane.getTabCount());
    checkOrder("b", "c", "a");
  }

  /** Without a drag, a middle-click release still closes the tab - the behaviour the drag branch has to step around. */
  public void testMiddleClickWithoutADragStillCloses() {
    TabComponent header = header(0);
    if (header == null) return;
    press(header, 20);
    header.mouseReleased(new MouseEvent(header, MouseEvent.MOUSE_RELEASED, 0, 0, 20, 5, 1, false,
      MouseEvent.BUTTON2));
    checkOrder("b", "c");
  }

  private void press(TabComponent header, int x) {
    header.mousePressed(new MouseEvent(header, MouseEvent.MOUSE_PRESSED, 0, 0, x, 5, 1, false, MouseEvent.BUTTON1));
  }

  private void drag(TabComponent header, int x) {
    header.mouseDragged(new MouseEvent(header, MouseEvent.MOUSE_DRAGGED, 0, 0, x, 5, 0, false));
  }

  private void release(TabComponent header, Point at) {
    header.mouseReleased(new MouseEvent(header, MouseEvent.MOUSE_RELEASED, 0, 0, at.x, at.y, 1, false,
      MouseEvent.BUTTON1));
  }

  /** The centre of the given tab's header, in the coordinates of the component holding the mouse capture. */
  private Point atTabCentre(int index, TabComponent capturedBy) {
    Rectangle bounds = myPane.getBoundsAt(index);
    assertNotNull("tab strip is not laid out, so there are no tab bounds", bounds);
    Point centre = new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    assertEquals("indexAtLocation disagrees with getBoundsAt", index, myPane.indexAtLocation(centre.x, centre.y));
    return SwingUtilities.convertPoint(myPane, centre, capturedBy);
  }

  /** The tab header at the given index, or null on Aqua, where the header is a MacTabComponent with no drag support. */
  private TabComponent header(int index) {
    Component component = myPane.getTabComponentAt(index);
    if (component instanceof TabComponent) return (TabComponent) component;
    // Fail rather than pass vacuously if the header is neither of the two known kinds.
    assertTrue("unexpected tab header " + component, component instanceof MacTabComponent);
    return null;
  }

  private void addTab(String name) {
    myTabsManager.createTab(name).setComponent(new JLabel(name));
  }

  private void checkOrder(String... expectedNames) {
    assertEquals(expectedNames.length, myPane.getTabCount());
    for (int i = 0; i < expectedNames.length; i++) {
      assertEquals("shown tab " + i, expectedNames[i], myPane.getTitleAt(i));
    }
  }
}

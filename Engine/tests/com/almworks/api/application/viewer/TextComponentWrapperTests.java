package com.almworks.api.application.viewer;

import com.almworks.util.tests.GUITestCase;

import javax.swing.*;
import javax.swing.text.Element;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Link hit-testing: whether a point in the pane maps to an anchor. The defect this guards against was a link
 * that rendered as a link and did nothing when clicked, because only decorator-produced links were recognised
 * and real HTML anchors were not.
 * <p>
 * A mouse-moved event is used throughout. It exercises the same lookup as a click but stops short of the
 * activation branch, so nothing tries to open a browser.
 */
public class TextComponentWrapperTests extends GUITestCase {
  private static final String HTML_WITH_ANCHOR =
    "<html><body><p>before <a href=\"http://example.com/target\">the link</a> after</p></body></html>";

  private JEditorPane myPane;

  protected void setUp() throws Exception {
    super.setUp();
    myPane = new JEditorPane();
    myPane.setEditorKit(new HTMLEditorKit());
    myPane.setText(HTML_WITH_ANCHOR);
    // Positions only exist once the pane has a width to lay out against; no peer or window is needed.
    myPane.setSize(400, 200);
    myPane.getPreferredSize();
  }

  protected void tearDown() throws Exception {
    myPane = null;
    super.tearDown();
  }

  public void testPointInsideAnchorIsAHit() throws Exception {
    assertTrue(processMouseAt(offsetInsideAnchor()));
  }

  public void testPointOutsideAnchorIsNotAHit() throws Exception {
    assertFalse(processMouseAt(offsetOutsideAnchor()));
  }

  /**
   * Text with no anchor at all must report no hit anywhere, or every field would show a hand cursor.
   */
  public void testPlainTextHasNoLinks() throws Exception {
    myPane.setText("<html><body><p>nothing clickable here</p></body></html>");
    myPane.setSize(400, 200);
    myPane.getPreferredSize();
    HTMLDocument document = (HTMLDocument) myPane.getDocument();
    for (int pos = 1; pos < document.getLength(); pos++) {
      assertFalse("position " + pos, processMouseAt(pos));
    }
  }

  private boolean processMouseAt(int offset) throws Exception {
    Rectangle rect = myPane.modelToView(offset);
    assertNotNull("no view for offset " + offset, rect);
    MouseEvent event =
      new MouseEvent(myPane, MouseEvent.MOUSE_MOVED, 0L, 0, rect.x + 1, rect.y + 1, 0, false);
    return TextComponentWrapper.processMouse(event, myPane);
  }

  private int offsetInsideAnchor() {
    int offset = findOffset(true);
    assertTrue("document has no anchor", offset >= 0);
    return offset;
  }

  private int offsetOutsideAnchor() {
    int offset = findOffset(false);
    assertTrue("document is entirely anchor", offset >= 0);
    return offset;
  }

  /**
   * The first offset that is, or is not, inside an anchor. Offsets are found rather than hardcoded because the
   * editor kit decides how the paragraph maps onto the document.
   */
  private int findOffset(boolean inAnchor) {
    HTMLDocument document = (HTMLDocument) myPane.getDocument();
    for (int pos = 1; pos < document.getLength(); pos++) {
      Element element = document.getCharacterElement(pos);
      boolean anchor = element != null && element.getAttributes().getAttribute(HTML.Tag.A) != null;
      if (anchor == inAnchor) return pos;
    }
    return -1;
  }
}

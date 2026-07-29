package com.almworks.jira.provider3.markup;

import com.almworks.items.gui.edit.editors.text.RichTextTransform;
import com.almworks.jira.provider3.sync.download2.rest.AdfCanonical;
import com.almworks.util.tests.BaseTestCase;

/**
 * The property that matters to a user: opening an editor and saving without typing must not change the
 * document. Everything else in this feature is in service of that.
 */
public class AdfRichTextTransformTests extends BaseTestCase {
  private static final String RICH =
    "{\"type\":\"doc\",\"version\":1,\"content\":["
      + "{\"type\":\"heading\",\"attrs\":{\"level\":2},\"content\":[{\"type\":\"text\",\"text\":\"Title\"}]},"
      + "{\"type\":\"paragraph\",\"content\":["
      + "{\"type\":\"text\",\"text\":\"bold\",\"marks\":[{\"type\":\"strong\"}]},"
      + "{\"type\":\"text\",\"text\":\" and \"},"
      + "{\"type\":\"mention\",\"attrs\":{\"id\":\"557058:abc\",\"text\":\"@Jane\"}}]},"
      + "{\"type\":\"table\",\"content\":[{\"type\":\"tableRow\",\"content\":["
      + "{\"type\":\"tableCell\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"cell\"}]}]}]}]}"
      + "]}";

  public void testNoOpSaveKeepsTheDocument() {
    RichTextTransform transform = AdfRichTextTransform.TRIM;
    String editable = transform.toEditable("Title\nbold and @Jane\ncell", RICH);
    RichTextTransform.Result result = transform.fromEditable(editable, RICH);
    assertTrue("A save with no edit changed the document:\n" + editable + "\n" + result.getRawSource(),
      AdfCanonical.areEqualRaw(RICH, result.getRawSource()));
  }

  /**
   * The mention and the table are the constructs that cannot be retyped, so editing prose around them must
   * leave them exactly as they were.
   */
  public void testEditingAroundPlaceholdersKeepsThem() {
    RichTextTransform transform = AdfRichTextTransform.TRIM;
    String editable = transform.toEditable(null, RICH);
    String edited = editable.replace("Title", "Renamed");
    RichTextTransform.Result result = transform.fromEditable(edited, RICH);
    assertTrue(result.getRawSource().contains("557058:abc"));
    assertTrue(result.getRawSource().contains("tableCell"));
    assertTrue(result.getPlainText().contains("Renamed"));
    assertFalse(AdfCanonical.areEqualRaw(RICH, result.getRawSource()));
  }

  public void testDeletingAPlaceholderIsReported() {
    RichTextTransform transform = AdfRichTextTransform.TRIM;
    String editable = transform.toEditable(null, RICH);
    assertNull("Nothing was removed, so nothing should be reported", transform.checkLoss(editable, RICH));
    String withoutTable = editable.replaceAll("(?m)^" + PlaceholderTable.OPEN + "block.*$", "");
    String loss = transform.checkLoss(withoutTable, RICH);
    assertNotNull("Removing the table should be reported", loss);
    assertTrue(loss, loss.contains("table"));
  }

  // A value that never had a document must still edit as ordinary text rather than coming up empty.
  public void testPlainTextWithoutDocument() {
    RichTextTransform transform = AdfRichTextTransform.TRIM;
    assertEquals("just text", transform.toEditable("just text", null));
    assertEquals("", transform.toEditable(null, null));
    assertNull(transform.checkLoss("anything", null));
  }

  // Clearing a field must send nothing rather than an empty document, which JIRA rejects.
  public void testClearedFieldYieldsNothing() {
    RichTextTransform.Result result = AdfRichTextTransform.TRIM.fromEditable("", RICH);
    assertNull(result.getPlainText());
    assertNull(result.getRawSource());
  }

  /**
   * Comment and worklog bodies trim each line rather than the whole value, matching how the download path
   * reads them. Deriving plain text any other way would leave the field looking permanently changed.
   */
  public void testLineTrimmingVariantMatchesItsDownloadConvertor() {
    RichTextTransform.Result result = AdfRichTextTransform.TRIM_LINES.fromEditable("first\nsecond", null);
    assertEquals("first\nsecond", result.getPlainText());
  }
}

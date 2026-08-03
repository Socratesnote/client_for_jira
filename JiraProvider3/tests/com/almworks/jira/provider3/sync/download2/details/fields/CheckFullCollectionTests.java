package com.almworks.jira.provider3.sync.download2.details.fields;

import com.almworks.util.tests.BaseTestCase;
import org.json.simple.JSONObject;

/**
 * Covers the gate that decides whether an embedded sub-collection page is the whole collection.
 * Getting this wrong is a data-loss bug: answering "full" for a partial page means the records past
 * the first page are never re-read and are silently dropped.
 */
public class CheckFullCollectionTests extends BaseTestCase {
  public void testWholeCollectionInOnePage() {
    assertTrue(CheckFullCollection.isFullCollection(page(0, 20, 3)));
    assertTrue(CheckFullCollection.isFullCollection(page(0, 3, 3))); // Exactly filling the page still holds it all.
  }

  public void testMoreRecordsThanThePageHolds() {
    assertFalse(CheckFullCollection.isFullCollection(page(0, 1, 5)));
  }

  public void testPageDoesNotStartAtTheBeginning() {
    assertFalse(CheckFullCollection.isFullCollection(page(1, 20, 3)));
  }

  /**
   * Missing counters must read as "not proven full" so the caller re-reads.
   */
  public void testMissingCountersAreNotFull() {
    assertFalse(CheckFullCollection.isFullCollection(page(0, 20, null)));
    assertFalse(CheckFullCollection.isFullCollection(page(0, null, 3)));
    assertFalse(CheckFullCollection.isFullCollection(page(null, 20, 3)));
    assertFalse(CheckFullCollection.isFullCollection(new JSONObject()));
  }

  public void testGetTotal() {
    assertEquals(Integer.valueOf(3), CheckFullCollection.getTotal(page(0, 20, 3)));
    assertNull(CheckFullCollection.getTotal(page(0, 20, null)));
  }

  @SuppressWarnings("unchecked")
  private static JSONObject page(Integer startAt, Integer maxResults, Integer total) {
    JSONObject page = new JSONObject();
    if (startAt != null) page.put("startAt", (long) startAt);
    if (maxResults != null) page.put("maxResults", (long) maxResults);
    if (total != null) page.put("total", (long) total);
    return page;
  }
}

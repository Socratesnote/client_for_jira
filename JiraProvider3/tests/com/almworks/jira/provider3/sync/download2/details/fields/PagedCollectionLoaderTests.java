package com.almworks.jira.provider3.sync.download2.details.fields;

import com.almworks.api.connector.ConnectorException;
import com.almworks.items.entities.api.collector.transaction.EntityBag2;
import com.almworks.jira.provider3.sync.download2.details.slaves.SimpleDependent;
import com.almworks.jira.provider3.sync.download2.details.slaves.SlaveLoader;
import com.almworks.jira.provider3.sync.download2.rest.JRComment;
import com.almworks.jira.provider3.sync.schema.ServerComment;
import com.almworks.restconnector.json.ArrayKey;
import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.logging.Level;

/**
 * Covers the offset paging loop behind comment and worklog re-reads. The rule these tests exist to pin is
 * that an incomplete read returns null: callers turn a non-null result into a full bag, and a full bag
 * deletes whatever is missing from it, so a truncated read written as complete would destroy records.
 */
public class PagedCollectionLoaderTests extends BaseTestCase {
  private static final ArrayKey<JSONObject> RECORDS = ArrayKey.objectArray("comments");

  private final SimpleDependent myLoader =
    new SimpleDependent(ServerComment.TYPE, ServerComment.ISSUE, JRComment.PARTIAL_JSON_CONVERTOR, null);

  public void testSinglePage() throws ConnectorException {
    List<SlaveLoader.Parsed<EntityBag2>> result = load(new FakeFetcher(page(0, 3, ids(1, 2, 3))));
    assertNotNull(result);
    assertEquals(3, result.size());
  }

  public void testWalksEveryPage() throws ConnectorException {
    FakeFetcher fetcher = new FakeFetcher(page(0, 5, ids(1, 2)), page(2, 5, ids(3, 4)), page(4, 5, ids(5)));
    List<SlaveLoader.Parsed<EntityBag2>> result = load(fetcher);
    assertNotNull(result);
    assertEquals(5, result.size());
    assertEquals(3, fetcher.myCalls.size());
    assertEquals(Integer.valueOf(0), fetcher.myCalls.get(0));
    assertEquals(Integer.valueOf(2), fetcher.myCalls.get(1));
    assertEquals(Integer.valueOf(4), fetcher.myCalls.get(2));
  }

  /** A server that omits total still has to terminate, on the first empty page. */
  public void testTerminatesWithoutTotal() throws ConnectorException {
    FakeFetcher fetcher = new FakeFetcher(page(0, null, ids(1, 2)), page(2, null, ids(3)), page(3, null, ids()));
    List<SlaveLoader.Parsed<EntityBag2>> result = load(fetcher);
    assertNotNull(result);
    assertEquals(3, result.size());
    assertEquals(3, fetcher.myCalls.size());
  }

  /** The important one: a failure part way through must not look like a complete collection. */
  public void testFailedPageReturnsNull() throws ConnectorException {
    FakeFetcher fetcher = new FakeFetcher(page(0, 10, ids(1, 2)), null);
    assertNull(load(fetcher));
  }

  public void testFirstPageFailureReturnsNull() throws ConnectorException {
    assertNull(load(new FakeFetcher((JSONObject) null)));
  }

  /**
   * A page without the records array is malformed, not an empty collection. The loader logs that at SEVERE,
   * which normally fails the running test, so this case allows SEVERE while still catching anything above it.
   */
  public void testMissingRecordsArrayReturnsNull() throws ConnectorException {
    setTestFailLevel(Level.SEVERE);
    assertNull(load(new FakeFetcher(new JSONObject())));
  }

  @Nullable
  private List<SlaveLoader.Parsed<EntityBag2>> load(PagedCollectionLoader.PageFetcher fetcher) throws ConnectorException {
    return PagedCollectionLoader.loadAllPages(fetcher, RECORDS, myLoader, "comments");
  }

  private static long[] ids(long... ids) {
    return ids;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject page(int startAt, @Nullable Integer total, long[] ids) {
    JSONArray records = new JSONArray();
    for (long id : ids) {
      JSONObject record = new JSONObject();
      record.put("id", String.valueOf(id));
      records.add(record);
    }
    JSONObject page = new JSONObject();
    page.put("startAt", (long) startAt);
    page.put("maxResults", 2L);
    if (total != null) page.put("total", (long) total);
    page.put("comments", records);
    return page;
  }

  /** Returns the supplied pages in order; a null entry stands for a page that could not be read. */
  private static class FakeFetcher implements PagedCollectionLoader.PageFetcher {
    private final JSONObject[] myPages;
    private final List<Integer> myCalls = Collections15.arrayList();
    private int myNext = 0;

    private FakeFetcher(JSONObject... pages) {
      myPages = pages;
    }

    @Nullable
    @Override
    public JSONObject fetchPage(int startAt) {
      myCalls.add(startAt);
      assertTrue("fetched more pages than the fake provides", myNext < myPages.length);
      return myPages[myNext++];
    }
  }
}

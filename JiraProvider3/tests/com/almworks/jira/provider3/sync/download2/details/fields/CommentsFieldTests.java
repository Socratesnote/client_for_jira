package com.almworks.jira.provider3.sync.download2.details.fields;

import com.almworks.api.connector.ConnectorException;
import com.almworks.items.api.DBIdentifiedObject;
import com.almworks.items.entities.api.collector.transaction.EntityHolder;
import com.almworks.items.entities.api.collector.transaction.EntityTransaction;
import com.almworks.items.sync.util.identity.DBIdentity;
import com.almworks.jira.provider3.schema.Jira;
import com.almworks.jira.provider3.sync.ServerInfo;
import com.almworks.jira.provider3.sync.download2.details.JsonIssueField;
import com.almworks.jira.provider3.sync.download2.process.util.ProgressInfo;
import com.almworks.jira.provider3.sync.schema.ServerComment;
import com.almworks.jira.provider3.sync.schema.ServerIssue;
import com.almworks.util.tests.BaseTestCase;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Collection;

/**
 * Covers {@link CommentsField}'s re-fetch path (the {@code loadAllPages} seam), which
 * {@link PagedCollectionLoaderTests} does not reach - that class tests the paging loop in isolation, not the
 * field-level behavior of replacing a partial embedded page with the full re-read, or of leaving it alone when
 * the re-read fails partway.
 */
public class CommentsFieldTests extends BaseTestCase {
  private static final String CONNECTION_ID = "CONN-ECTI-ON_I-D";

  public void testFullReReadReplacesPartialPage() throws ConnectorException {
    EntityHolder issue = newIssue();
    applyEmbeddedPage(issue, page(44, 64, ids(45, 46))); // stand-in for the real last-page embedded shape
    assertEquals(2, countComments(issue));

    // A real re-read starts over from page 0, so it re-covers 45 and 46 as well as the ids the embedded page
    // never carried - the fake mirrors that shape rather than only supplying the "missing" ids.
    FakeFetcher fetcher = new FakeFetcher(page(0, 6, ids(1, 2, 3)), page(3, 6, ids(4, 45, 46)));
    CommentsField.INSTANCE.loadAllPages(issue, fetcher, ProgressInfo.createDeaf());
    assertEquals(6, countComments(issue));
  }

  public void testFailedReReadLeavesPartialPageIntact() throws ConnectorException {
    EntityHolder issue = newIssue();
    applyEmbeddedPage(issue, page(44, 64, ids(45, 46)));
    assertEquals(2, countComments(issue));

    FakeFetcher fetcher = new FakeFetcher(page(0, 10, ids(1, 2)), null); // second page fails to load
    CommentsField.INSTANCE.loadAllPages(issue, fetcher, ProgressInfo.createDeaf());
    assertEquals(2, countComments(issue)); // unchanged - a failed re-read must not overwrite what is known
  }

  private static void applyEmbeddedPage(EntityHolder issue, JSONObject embeddedPage) {
    Collection<? extends JsonIssueField.ParsedValue> parsed = CommentsField.INSTANCE.loadValue(embeddedPage);
    assertNotNull(parsed);
    for (JsonIssueField.ParsedValue value : parsed) value.addTo(issue);
  }

  private static EntityHolder newIssue() {
    DBIdentifiedObject connectionObject = Jira.createConnectionObject(CONNECTION_ID);
    DBIdentity connection = DBIdentity.fromDBObject(connectionObject);
    EntityTransaction transaction = ServerInfo.priCreateTransaction(CONNECTION_ID, connection);
    return transaction.addEntity(ServerIssue.TYPE, ServerIssue.ID, 1);
  }

  private static int countComments(EntityHolder issue) {
    return issue.getTransaction().getAllEntities(ServerComment.TYPE).size();
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
    private int myNext = 0;

    private FakeFetcher(JSONObject... pages) {
      myPages = pages;
    }

    @Nullable
    @Override
    public JSONObject fetchPage(int startAt) {
      assertTrue("fetched more pages than the fake provides", myNext < myPages.length);
      return myPages[myNext++];
    }
  }
}

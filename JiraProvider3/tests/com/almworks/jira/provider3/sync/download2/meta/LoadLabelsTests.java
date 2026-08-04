package com.almworks.jira.provider3.sync.download2.meta;

import com.almworks.api.connector.ConnectorException;
import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Collections15;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Covers reading the paginated {@code api/3/label} endpoint. The critical property is the one the full-bag rule elsewhere
 * depends on: a run that could not read every page returns null rather than a short list, so a partial read is never stored
 * as if it were the whole set.
 */
public class LoadLabelsTests extends BaseTestCase {
  /** Serves pre-built pages by offset and records what was asked for. */
  private static class FakePages implements LoadLabels.PageFetcher {
    private final List<JSONObject> myPages;
    private final List<Integer> myRequested = Collections15.arrayList();

    FakePages(JSONObject... pages) {
      myPages = Arrays.asList(pages);
    }

    @Nullable
    @Override
    public JSONObject fetchPage(int startAt) {
      myRequested.add(startAt);
      for (JSONObject page : myPages) {
        Integer pageStart = (Integer) page.get("startAt");
        if (pageStart != null && pageStart == startAt) return page;
      }
      return null; // Asked for an offset the fake does not serve: treated as a failed page.
    }
  }

  @SuppressWarnings("unchecked")
  private static JSONObject page(int startAt, Integer total, Boolean isLast, String... values) {
    JSONObject object = new JSONObject();
    object.put("startAt", startAt);
    if (total != null) object.put("total", total);
    if (isLast != null) object.put("isLast", isLast);
    JSONArray array = new JSONArray();
    array.addAll(Arrays.asList(values));
    object.put("values", array);
    return object;
  }

  private static Set<String> load(LoadLabels.PageFetcher fetcher) throws ConnectorException {
    return LoadLabels.loadAllLabels(fetcher);
  }

  // The shape captured live on 2026-08-03: a single page that already reports isLast.
  public void testSinglePage() throws ConnectorException {
    Set<String> labels = load(new FakePages(page(0, 1, true, "Meeting")));
    assertNotNull(labels);
    assertEquals(1, labels.size());
    assertTrue(labels.contains("Meeting"));
  }

  public void testAllPagesAreRead() throws ConnectorException {
    FakePages pages = new FakePages(
      page(0, 4, false, "a", "b"),
      page(2, 4, true, "c", "d"));
    Set<String> labels = load(pages);
    assertNotNull(labels);
    assertEquals(Arrays.asList("a", "b", "c", "d"), Collections15.arrayList(labels));
    assertEquals(Arrays.asList(0, 2), pages.myRequested);
  }

  // Order is what the user sees in the variants list, so it has to survive paging.
  public void testOrderPreserved() throws ConnectorException {
    Set<String> labels = load(new FakePages(
      page(0, 4, false, "zebra", "apple"),
      page(2, 4, true, "mango", "banana")));
    assertEquals(Arrays.asList("zebra", "apple", "mango", "banana"), Collections15.arrayList(labels));
  }

  public void testDuplicatesAcrossPagesCollapse() throws ConnectorException {
    Set<String> labels = load(new FakePages(
      page(0, 4, false, "a", "b"),
      page(2, 4, true, "b", "c")));
    assertEquals(Arrays.asList("a", "b", "c"), Collections15.arrayList(labels));
  }

  // A server that reports neither isLast nor total still has to terminate, on the empty page.
  public void testTerminatesOnEmptyPageWithoutCounters() throws ConnectorException {
    FakePages pages = new FakePages(
      page(0, null, null, "a"),
      page(1, null, null));
    Set<String> labels = load(pages);
    assertNotNull(labels);
    assertEquals(Arrays.asList("a"), Collections15.arrayList(labels));
    assertEquals(Arrays.asList(0, 1), pages.myRequested);
  }

  // total alone is enough to stop: no request is made past it.
  public void testTotalStopsPaging() throws ConnectorException {
    FakePages pages = new FakePages(page(0, 2, null, "a", "b"));
    Set<String> labels = load(pages);
    assertNotNull(labels);
    assertEquals(Arrays.asList("a", "b"), Collections15.arrayList(labels));
    assertEquals(Arrays.asList(0), pages.myRequested);
  }

  // isLast wins over a total that would ask for more, since it is the server's explicit answer.
  public void testIsLastStopsPagingDespiteTotal() throws ConnectorException {
    FakePages pages = new FakePages(page(0, 99, true, "a"));
    Set<String> labels = load(pages);
    assertNotNull(labels);
    assertEquals(Arrays.asList("a"), Collections15.arrayList(labels));
    assertEquals(Arrays.asList(0), pages.myRequested);
  }

  /**
   * A failed page must not degrade into a short answer. Storing a partial set as if it were complete is the failure mode
   * that made truncated comment reads dangerous, and the caller can only avoid it if it can tell the two apart.
   */
  public void testFailedFirstPageGivesNull() throws ConnectorException {
    assertNull(load(new FakePages()));
  }

  public void testFailedLaterPageGivesNull() throws ConnectorException {
    assertNull(load(new FakePages(page(0, 4, false, "a", "b"))));
  }

  // No labels on the instance is a legitimate answer and is not a failure.
  public void testEmptyResultIsNotNull() throws ConnectorException {
    Set<String> labels = load(new FakePages(page(0, 0, true)));
    assertNotNull(labels);
    assertTrue(labels.isEmpty());
  }
}

package com.almworks.jira.provider3.sync.download2.details.fields;

import com.almworks.api.connector.ConnectorException;
import com.almworks.items.entities.api.collector.transaction.EntityBag2;
import com.almworks.jira.provider3.sync.download2.details.slaves.DependentBagField;
import com.almworks.jira.provider3.sync.download2.details.slaves.SimpleDependent;
import com.almworks.jira.provider3.sync.download2.details.slaves.SlaveLoader;
import com.almworks.restconnector.RequestPolicy;
import com.almworks.restconnector.RestResponse;
import com.almworks.restconnector.RestSession;
import com.almworks.restconnector.json.ArrayKey;
import com.almworks.util.LogHelper;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.util.List;

/**
 * Reads every page of an issue's sub-collection that Jira paginates with startAt/maxResults/total,
 * such as comments and worklogs. These endpoints use offset pagination and do not return a nextPageToken, unlike issue search.
 */
class PagedCollectionLoader {
  /** Stops rather than looping forever if the server never advances or never reports completion. */
  private static final int MAX_PAGES = 1000;

  private PagedCollectionLoader() {}

  /**
   * One page of a paginated collection. Exists so the paging logic can be tested without an HTTP session.
   */
  interface PageFetcher {
    /** @return the page at the given offset, or null if it could not be read. */
    @Nullable
    JSONObject fetchPage(int startAt) throws ConnectorException;
  }

  /** Reads pages over REST. The path carries no query parameters; startAt is appended here. */
  static PageFetcher restFetcher(final RestSession session, final String path, final String what) {
    return new PageFetcher() {
      @Override
      public JSONObject fetchPage(int startAt) throws ConnectorException {
        RestResponse response = session.restGet(path + "?startAt=" + startAt, RequestPolicy.SAFE_TO_RETRY);
        if (!response.isSuccessful()) {
          LogHelper.error("Failed to load all " + what, response.getStatusCode(), startAt);
          return null;
        }
        try {
          return response.getJSONObject();
        } catch (ParseException e) {
          LogHelper.error("Failed to parse " + what, e);
          return null;
        }
      }
    };
  }

  /**
   * Fetches all pages and returns every entity found.
   * <p>
   * Returns null if any page failed, so callers can tell "here is the whole collection" from "this is
   * as far as I got". <b>A partial result must never be written as a full bag</b>: a full bag deletes
   * the entities missing from it, so storing a truncated read would destroy records.
   *
   * @param arrayKey the field holding the records on each page, e.g. "comments"
   * @param what name used in log messages
   */
  @Nullable
  static List<SlaveLoader.Parsed<EntityBag2>> loadAllPages(PageFetcher fetcher, ArrayKey<JSONObject> arrayKey,
    SimpleDependent recordLoader, String what) throws ConnectorException
  {
    List<SlaveLoader.Parsed<EntityBag2>> collected = Collections15.arrayList();
    int startAt = 0;
    for (int page = 0; page < MAX_PAGES; page++) {
      JSONObject object = fetcher.fetchPage(startAt);
      if (object == null) return null; // The fetcher has already logged why.
      JSONArray records = arrayKey.getValue(object);
      if (records == null) {
        LogHelper.error("Missing " + what + " records", startAt);
        return null;
      }
      collected.addAll(DependentBagField.loadEntities(records, recordLoader));
      if (records.isEmpty()) return collected; // Nothing further to read, whatever the counters say.
      startAt += records.size();
      Integer total = CheckFullCollection.getTotal(object);
      // A server that omits total still terminates, via the empty page above.
      if (total != null && startAt >= total) return collected;
    }
    LogHelper.error("Gave up loading " + what + " after " + MAX_PAGES + " pages");
    return null;
  }
}

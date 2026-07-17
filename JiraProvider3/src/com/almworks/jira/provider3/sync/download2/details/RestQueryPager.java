package com.almworks.jira.provider3.sync.download2.details;

import com.almworks.api.connector.ConnectorException;
import com.almworks.integers.IntArray;
import com.almworks.jira.provider3.sync.ServerFields;
import com.almworks.jira.provider3.sync.download2.process.util.ProgressInfo;
import com.almworks.jira.provider3.sync.download2.rest.JqlSearch;
import com.almworks.restconnector.RestResponse;
import com.almworks.restconnector.RestSession;
import com.almworks.restconnector.jql.JqlQuery;
import com.almworks.restconnector.json.sax.CompositeHandler;
import com.almworks.restconnector.json.sax.JSONCollector;
import com.almworks.restconnector.json.sax.LocationHandler;
import com.almworks.restconnector.json.sax.PeekArrayElement;
import com.almworks.util.LogHelper;
import com.almworks.util.i18n.text.LocalizedAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.simple.parser.ParseException;

import java.io.IOException;

public class RestQueryPager {
  @NotNull
  private final JqlQuery myJql;
  /**
   * HTTP response status codes that are treated as end of query. Otherwise failure exception is thrown.
   */
  private final IntArray myNoResultCodes = new IntArray();
  private String[] myFields = null;
  private int myMaxResult = -1;
  private String myStart = null;

  public RestQueryPager(@NotNull JqlQuery jql) {
    myJql = jql;
  }

  public static RestQueryPager allFields(@NotNull JqlQuery jql) {
    RestQueryPager pager = new RestQueryPager(jql);
    pager.setFields(new String[]{"*all"});
    return pager;
  }

  /**
   * If not null specified used to set fields via {@link JqlSearch#addFields(String...)}
   */
  public void setFields(@Nullable String[] fields) {
    myFields = fields;
  }

  public void setFields(ServerFields.Field ... fields) {
    String[] strIds = new String[fields.length];
    for (int i = 0; i < fields.length; i++) strIds[i] = fields[i].getJiraId();
    setFields(strIds);
  }

  private JqlSearch createSearch() {
    JqlSearch search = new JqlSearch(myJql);
    if (myFields != null) search.addFields(myFields);
    return search;
  }

  /**
   * @see #myNoResultCodes
   */
  public void addNoResultCode(int httpCode) {
    myNoResultCodes.add(httpCode);
  }

  /**
   * Set next page start
   */
  public void setStart(String start) {
    myStart = start;
  }

  /**
   * @param maxResult Sets desired max query result. It should not be too big because it may lead to JIRA failure.
   */
  public void setMaxResult(int maxResult) {
    myMaxResult = maxResult;
  }

  /**
   * @param issueHandler handler that consumes issues (elements of "issues" array)
   * @return number of issues actually loaded on this page. May be 0 if nothing is loaded (see {@link #myNoResultCodes})
   */
  public int loadNext(RestSession session, LocationHandler issueHandler) throws ConnectorException {
    JqlSearch search = createSearch();
    if (myMaxResult < 0) search.setDefaultMaxResult();
    else search.setMaxResult(myMaxResult);
    search.setStart(myStart);
    RestResponse response = search.request(session);
    if (!response.isSuccessful()) {
      int statusCode = response.getStatusCode();
      if (myNoResultCodes.contains(statusCode)) {
        myStart = null; // Set query ended state
        return 0;
      }
      RestResponse.ErrorResponse errorResponse = response.createErrorResponse();
      if (statusCode == 400) {
        ConnectorException problem = search.maybeInaccessibleProject(session, errorResponse);
        if (problem != null) throw problem;
      }
      LogHelper.warning("Query failed", statusCode, response.getLastUrl(), search);
      throw errorResponse.toException();
    }
    JSONCollector getNextPageToken = new JSONCollector(null);
    CountingHandler counter = new CountingHandler(issueHandler);
    response.parseJSON(new CompositeHandler(
      getNextPageToken.peekObjectEntry("nextPageToken"),
      PeekArrayElement.entryArray("issues", counter)
    ));
    myStart = getNextPageToken.getString(); // null means this was the last page
    return counter.getCount();
  }

  /**
   * Loads whole query from current start up to end or up to {@link #myMaxResult} if positive value is specified<br>
   * When optional progress is provided informs it about progress. If an optional activity template is provided - shows current loading state.
   * @param issueHandler issues consumer same as in {@link #loadNext(com.almworks.restconnector.RestSession, com.almworks.restconnector.json.sax.LocationHandler)}
   * @param firstActivity progress activity message shown before the first page is loaded
   * @param nextActivity progress activity pattern shown after each page. arg - number of issues loaded so far
   * @see #loadNext(com.almworks.restconnector.RestSession, com.almworks.restconnector.json.sax.LocationHandler)
   */
  public void loadAll(RestSession session, LocationHandler issueHandler, @Nullable ProgressInfo progress, @Nullable LocalizedAccessor.Value firstActivity, @Nullable LocalizedAccessor.MessageInt nextActivity) throws ConnectorException {
    int loadedCount = 0;
    try {
      while (true) {
        if (progress != null) {
          String message = loadedCount == 0
            ? (firstActivity != null ? firstActivity.create() : null)
            : (nextActivity != null ? nextActivity.formatMessage(loadedCount) : null);
          if (message != null) progress.startActivity(message);
          else progress.checkCancelled();
        }
        int pageCount = loadNext(session, issueHandler);
        loadedCount += pageCount;
        if (myStart == null) break; // no more pages
        if (myMaxResult > 0 && pageCount >= myMaxResult) break; // hard cap reached
        if (pageCount <= 0) {
          LogHelper.error("No issues loaded", myStart, myMaxResult, pageCount);
          break;
        }
      }
    } finally {
      if (progress != null) progress.setDone();
    }
  }

  /**
   * Counts array elements passed through to the wrapped handler, without altering the events it receives.
   */
  private static class CountingHandler implements LocationHandler {
    private final LocationHandler myTarget;
    private int myCount = 0;

    private CountingHandler(LocationHandler target) {
      myTarget = target;
    }

    @Override
    public void visit(Location what, boolean start, @Nullable String key, @Nullable Object value) throws ParseException, IOException {
      if (what == Location.TOP && start) myCount++;
      myTarget.visit(what, start, key, value);
    }

    private int getCount() {
      return myCount;
    }
  }
}

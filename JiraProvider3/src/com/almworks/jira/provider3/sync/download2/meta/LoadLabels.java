package com.almworks.jira.provider3.sync.download2.meta;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.items.entities.api.collector.transaction.EntityTransaction;
import com.almworks.jira.connector2.JiraInternalException;
import com.almworks.jira.provider3.custom.fieldtypes.enums.multi.LabelsEnumFieldType;
import com.almworks.jira.provider3.sync.download2.details.CustomFieldsSchema;
import com.almworks.jira.provider3.sync.download2.meta.core.LoadMetaContext;
import com.almworks.jira.provider3.sync.download2.meta.core.MetaOperation;
import com.almworks.jira.provider3.sync.download2.process.util.ProgressInfo;
import com.almworks.restconnector.RequestPolicy;
import com.almworks.restconnector.RestResponse;
import com.almworks.restconnector.RestSession;
import com.almworks.restconnector.json.ArrayKey;
import com.almworks.restconnector.json.JsonKey;
import com.almworks.util.LogHelper;
import com.almworks.util.i18n.text.LocalizedAccessor;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads the label values offered by the system "labels" field, from the Cloud endpoint {@code GET api/3/label}, which is
 * global rather than per-project and paginated with startAt/maxResults/total/isLast.
 * <p>
 * The values are stored as enum variants of the labels field, unioned across the whole instance. That is what the previous
 * implementation produced too: it walked every project only as a discovery mechanism and discarded the project dimension
 * before storing, so nothing project-scoped is lost by reading a global endpoint. Cloud offers no per-project label endpoint.
 * <p>
 * Custom labels-type fields get no pre-populated variant set. There is no global endpoint for them, and the only candidate,
 * the JQL autocomplete suggestions resource, is prefix-driven and capped, which suits an autocomplete control rather than a
 * stored set. Labels are free text and the editor lets a value be created, so those fields stay usable without one.
 */
class LoadLabels extends MetaOperation {
  private static final String PATH_LABEL = "api/3/label";
  private static final ArrayKey<String> VALUES = ArrayKey.textArray("values");
  private static final JsonKey<Integer> TOTAL = JsonKey.integer("total");
  private static final JsonKey<Boolean> IS_LAST = JsonKey.bool("isLast");
  /** Stops rather than looping forever if the server never advances or never reports completion. */
  private static final int MAX_PAGES = 1000;
  private static final LocalizedAccessor.Value M_LOAD_LABELS = LoadRestMeta.I18N.getFactory("progress.meta.loadLabels");

  public LoadLabels() {
    super(2);
  }

  @Override
  public void perform(RestSession session, EntityTransaction transaction, ProgressInfo progress, LoadMetaContext context) throws JiraInternalException, CancelledException {
    progress.startActivity(M_LOAD_LABELS.create());
    try {
      Set<String> labels = loadAllLabels(restFetcher(session));
      if (labels != null) LabelsEnumFieldType.storeLabels(transaction, CustomFieldsSchema.ID_LABELS, labels);
    } catch (CancelledException e) {
      throw e;
    } catch (ConnectorException e) {
      LogHelper.warning("Load labels failed", e.getMessage());
    }
    progress.setDone();
  }

  /**
   * One page of labels. Exists so the paging logic can be tested without an HTTP session.
   */
  interface PageFetcher {
    /** @return the page at the given offset, or null if it could not be read. */
    @Nullable
    JSONObject fetchPage(int startAt) throws ConnectorException;
  }

  static PageFetcher restFetcher(final RestSession session) {
    return new PageFetcher() {
      @Override
      public JSONObject fetchPage(int startAt) throws ConnectorException {
        RestResponse response = session.restGet(PATH_LABEL + "?startAt=" + startAt, RequestPolicy.SAFE_TO_RETRY);
        if (!response.isSuccessful()) {
          LogHelper.warning("Failed to load labels", response.getStatusCode(), startAt);
          return null;
        }
        try {
          return response.getJSONObject();
        } catch (ParseException e) {
          LogHelper.warning("Failed to parse labels", e);
          return null;
        }
      }
    };
  }

  /**
   * Reads every page.
   *
   * @return every label on the instance, or null if any page failed, so that a partial read is not stored as if it were the
   * whole set. Order is preserved because the values are offered to the user as they come back.
   */
  @Nullable
  static Set<String> loadAllLabels(PageFetcher fetcher) throws ConnectorException {
    Set<String> labels = new LinkedHashSet<String>();
    int startAt = 0;
    for (int page = 0; page < MAX_PAGES; page++) {
      JSONObject object = fetcher.fetchPage(startAt);
      if (object == null) return null; // The fetcher has already logged why.
      List<String> values = VALUES.list(object);
      for (String value : values) if (value != null) labels.add(value);
      if (Boolean.TRUE.equals(IS_LAST.getValue(object))) return labels;
      if (values.isEmpty()) return labels; // Nothing further to read, whatever the counters say.
      startAt += values.size();
      Integer total = TOTAL.getValue(object);
      // A server that omits both isLast and total still terminates, via the empty page above.
      if (total != null && startAt >= total) return labels;
    }
    LogHelper.error("Gave up loading labels after " + MAX_PAGES + " pages");
    return null;
  }
}

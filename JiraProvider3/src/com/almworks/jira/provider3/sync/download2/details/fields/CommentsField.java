package com.almworks.jira.provider3.sync.download2.details.fields;

import com.almworks.api.connector.ConnectorException;
import com.almworks.items.entities.api.collector.transaction.EntityBag2;
import com.almworks.items.entities.api.collector.transaction.EntityHolder;
import com.almworks.jira.provider3.sync.download2.details.JsonIssueField;
import com.almworks.jira.provider3.sync.download2.details.slaves.DependentBagField;
import com.almworks.jira.provider3.sync.download2.details.slaves.SimpleDependent;
import com.almworks.jira.provider3.sync.download2.details.slaves.SlaveLoader;
import com.almworks.jira.provider3.sync.download2.process.util.ProgressInfo;
import com.almworks.jira.provider3.sync.download2.rest.JRComment;
import com.almworks.jira.provider3.sync.schema.ServerComment;
import com.almworks.jira.provider3.sync.schema.ServerIssue;
import com.almworks.restconnector.RestSession;
import com.almworks.restconnector.json.ArrayKey;
import com.almworks.util.LogHelper;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Loads an issue's comments, re-reading them from the comment endpoint when the issue response carried
 * only the first page.
 * <p>
 * The issue response embeds comments as a page ({@code comment.comments} plus startAt/maxResults/total which may be missing for longer pages).
 */
public class CommentsField implements JsonIssueField {
  public static final CommentsField INSTANCE = new CommentsField();

  private static final ArrayKey<JSONObject> COMMENTS = ArrayKey.objectArray("comments");
  private static final HintValue<Boolean> NOT_FULL = HintValue.flag("comment.load.notFull");
  private static final String PATH_ISSUE = "api/3/issue/";

  private final SimpleDependent myCommentLoader =
    new SimpleDependent(ServerComment.TYPE, ServerComment.ISSUE, JRComment.PARTIAL_JSON_CONVERTOR, null, ServerComment.SECURITY);

  @Override
  public Collection<? extends ParsedValue> loadValue(@Nullable Object jsonValue) {
    if (jsonValue == null) return null;
    JSONObject obj = Util.castNullable(JSONObject.class, jsonValue);
    if (obj == null) {
      LogHelper.error("Expected comments object", jsonValue);
      return null;
    }
    JSONArray comments = COMMENTS.getValue(jsonValue);
    if (comments == null) {
      LogHelper.error("Missing comment records");
      return null;
    }
    List<SlaveLoader.Parsed<EntityBag2>> fullBag = DependentBagField.loadEntities(comments, myCommentLoader);
    if (CheckFullCollection.isFullCollection(obj)) return DependentBagField.createBagValueCollection(fullBag, myCommentLoader);
    // Partial page: add what arrived without a full bag, so nothing is deleted, and flag it for re-reading.
    ArrayList<ParsedValue> result = Collections15.arrayList();
    result.add(new SlaveLoader.AsValue(fullBag));
    result.add(NOT_FULL);
    return result;
  }

  /**
   * Deliberately does not treat a missing comment field as an empty collection: that would write an empty
   * full bag and delete every stored comment. Matches the nullAsEmpty=false behavior this field replaced.
   */
  @Override
  public Collection<? extends ParsedValue> loadNull() {
    LogHelper.error("Missing comments in response", myCommentLoader);
    return null;
  }

  public void maybeLoadAdditional(EntityHolder issue, RestSession session, ProgressInfo progress) throws ConnectorException {
    if (!NOT_FULL.isValueSet(issue)) return; // The embedded page held every comment.
    Integer issueId = issue.getScalarValue(ServerIssue.ID);
    if (issueId == null) {
      LogHelper.error("Missing issue ID", issue);
      return;
    }
    loadAllPages(issue, PagedCollectionLoader.restFetcher(session, PATH_ISSUE + issueId + "/comment", "comments"), progress);
  }

  /**
   * Package-visible seam for tests: performs the actual paged re-read given any fetcher, so it can be exercised
   * offline with a fake {@link PagedCollectionLoader.PageFetcher} instead of a live {@link RestSession}.
   */
  void loadAllPages(EntityHolder issue, PagedCollectionLoader.PageFetcher fetcher, ProgressInfo progress) throws ConnectorException {
    List<SlaveLoader.Parsed<EntityBag2>> fullBag = PagedCollectionLoader.loadAllPages(fetcher, COMMENTS, myCommentLoader, "comments");
    // Null means the read did not complete. Leaving the partial value from parsing is right: writing a full
    // bag here would delete the comments that were never fetched.
    if (fullBag != null) DependentBagField.createBagValue(fullBag, myCommentLoader).addTo(issue);
    progress.setDone();
  }
}

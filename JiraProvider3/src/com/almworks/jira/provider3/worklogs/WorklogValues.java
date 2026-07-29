package com.almworks.jira.provider3.worklogs;

import com.almworks.items.entities.api.Entity;
import com.almworks.items.entities.api.collector.transaction.EntityHolder;
import com.almworks.items.sync.ItemVersion;
import com.almworks.jira.provider3.remotedata.issue.AddEditSlaveUnit;
import com.almworks.jira.provider3.remotedata.issue.SlaveValues;
import com.almworks.jira.provider3.remotedata.issue.VisibilityLevel;
import com.almworks.jira.provider3.remotedata.issue.fields.scalar.ScalarUploadType;
import com.almworks.jira.provider3.schema.Worklog;
import com.almworks.jira.provider3.services.upload.UploadUnit;
import com.almworks.jira.provider3.sync.download2.rest.AdfCanonical;
import com.almworks.jira.provider3.sync.download2.rest.AdfDocument;
import com.almworks.jira.provider3.sync.schema.ServerIssue;
import com.almworks.jira.provider3.sync.schema.ServerUser;
import com.almworks.jira.provider3.sync.schema.ServerWorklog;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.i18n.text.LocalizedAccessor;
import org.almworks.util.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;

import java.util.Date;

class WorklogValues extends SlaveValues {
  private final Date myStarted;
  private final Integer mySeconds;
  private final String myComment;
  @Nullable("When the comment never was an ADF document")
  private final String myCommentAdf;
  private final VisibilityLevel myVisibility;
  private final String myAuthorName;
  private final Date myCreated;

  private WorklogValues(Integer id, Date started, Integer seconds, String comment, @Nullable String commentAdf, VisibilityLevel visibility, String authorName, Date created) {
    super(id);
    myStarted = started;
    mySeconds = seconds;
    myComment = comment;
    myCommentAdf = commentAdf;
    myVisibility = visibility;
    myAuthorName = authorName;
    myCreated = created;
  }

  public static WorklogValues load(ItemVersion worklog) throws UploadUnit.CantUploadException {
    Integer id = worklog.getValue(Worklog.ID);
    String comment = worklog.getValue(Worklog.COMMENT);
    String commentAdf = worklog.getValue(Worklog.COMMENT_ADF);
    Date started = worklog.getValue(Worklog.STARTED);
    Integer seconds = worklog.getValue(Worklog.TIME_SECONDS);
    VisibilityLevel visibility = VisibilityLevel.load(worklog.readValue(Worklog.SECURITY));
    if (started == null || seconds == null) throw UploadUnit.CantUploadException.create("Missing worklog data", worklog, started, seconds);
    Date created = worklog.getValue(Worklog.CREATED);
    if (created == null) created = new Date();
    String authorName = AddEditSlaveUnit.loadAuthor(worklog.readValue(Worklog.AUTHOR));
    return new WorklogValues(id, started, seconds, comment, commentAdf, visibility, authorName, created);
  }

  @Override
  public boolean matchesFailure(EntityHolder slave, @NotNull Entity thisUser) {
    return ServerUser.sameUser(thisUser, slave.getReference(ServerWorklog.AUTHOR))
      && Util.equals(slave.getScalarValue(ServerWorklog.START_DATE), myStarted);
  }

  @Nullable("When not new, found or no issue")
  public EntityHolder find(@Nullable EntityHolder issue) {
    if (issue == null) return null;
    Integer id = getId();
    if (id == null) return null;
    Integer issueId = issue.getScalarValue(ServerIssue.ID);
    if (issueId == null) return null;
    return ServerWorklog.find(issue.getTransaction(), issueId, id);
  }

  public boolean checkServer(EntityHolder worklog) {
    Date start = worklog.getScalarValue(ServerWorklog.START_DATE);
    Integer seconds = worklog.getScalarValue(ServerWorklog.TIME_SECONDS);
    String comment = worklog.getScalarValue(ServerWorklog.COMMENT);
    EntityHolder visibility = worklog.getReference(ServerWorklog.SECURITY);
    return Util.equals(myStarted, start) && Util.equals(mySeconds, seconds)
      && sameComment(comment, worklog.getScalarValue(ServerWorklog.COMMENT_ADF)) && VisibilityLevel.areSame(visibility, myVisibility);
  }

  /**
   * Compares the documents when both sides have one, so a formatting-only server edit is not mistaken for an
   * unchanged comment. Worklogs stored before the companion attribute existed fall back to their text.
   */
  private boolean sameComment(@Nullable String serverComment, @Nullable String serverAdf) {
    if (myCommentAdf != null && serverAdf != null) return AdfCanonical.areEqualRaw(myCommentAdf, serverAdf);
    return Util.equals(myComment, serverComment);
  }

  /**
   * Sends the stored document when there is one, so formatting this client cannot express survives an edit.
   */
  @SuppressWarnings("unchecked")
  public JSONObject createJson() {
    JSONObject result = new JSONObject();
    // api/3 requires the comment as an ADF document. A worklog may have no comment at all - omit the key
    // then, since JIRA rejects a rich-text value with no content.
    JSONObject comment = myCommentAdf != null ? AdfCanonical.parse(myCommentAdf) : null;
    if (comment == null) comment = AdfDocument.fromText(myComment);
    if (comment != null) result.put("comment", comment);
    result.put("visibility", myVisibility != null ? myVisibility.createJson() : null);
    result.put("started", ScalarUploadType.DATE.toJsonValue(myStarted));
    result.put("timeSpentSeconds", mySeconds);
    Integer id = getId();
    if (id != null) result.put("id", id);
    return result;
  }

  public String messageAbout(LocalizedAccessor.Message2 message) {
    return message.formatMessage(myAuthorName, DateUtil.toLocalDateOrTime(myCreated));
  }

  public String messageAbout3(LocalizedAccessor.Message3 message3, String arg3) {
    return message3.formatMessage(myAuthorName, DateUtil.toLocalDateOrTime(myCreated), arg3);
  }
}

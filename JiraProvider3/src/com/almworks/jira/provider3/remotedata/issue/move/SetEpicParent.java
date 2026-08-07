package com.almworks.jira.provider3.remotedata.issue.move;

import com.almworks.api.connector.ConnectorException;
import com.almworks.items.entities.api.collector.transaction.EntityTransaction;
import com.almworks.jira.provider3.remotedata.issue.BaseHistoryUnit;
import com.almworks.jira.provider3.remotedata.issue.edit.CreateIssueUnit;
import com.almworks.jira.provider3.schema.Issue;
import com.almworks.jira.provider3.services.upload.PostUploadContext;
import com.almworks.jira.provider3.services.upload.UploadContext;
import com.almworks.jira.provider3.services.upload.UploadProblem;
import com.almworks.jira.provider3.services.upload.UploadUnit;
import com.almworks.restconnector.RequestPolicy;
import com.almworks.restconnector.RestResponse;
import com.almworks.restconnector.RestSession;
import com.almworks.util.LogHelper;
import com.almworks.util.i18n.text.LocalizedAccessor;
import org.json.simple.JSONObject;

import java.util.Collection;

/**
 * Sets a standard (non-subtask) issue's parent - an Epic or other hierarchy parent - via a normal issue-field
 * update ({@code PUT api/3/issue/{id}} with {@code fields.parent}), not the legacy subtask move wizards. On Jira
 * Cloud {@code fields.parent} carries any hierarchy parent, so this is how a generic issue's parent is set.
 */
class SetEpicParent extends BaseHistoryUnit {
  private static final String PATH_ISSUE = "api/3/issue/";
  private static final LocalizedAccessor.Value M_FAILED_SHORT = MoveLoader.I18N.getFactory("upload.problem.setEpicParent.short");
  private static final LocalizedAccessor.Value M_FAILED_FULL = MoveLoader.I18N.getFactory("upload.problem.setEpicParent.full");

  private final CreateIssueUnit myNewParent;

  SetEpicParent(CreateIssueUnit create, UploadUnit prevStep, int stepIndex, CreateIssueUnit newParent) {
    super(prevStep, create, stepIndex);
    myNewParent = newParent;
  }

  @Override
  public UploadProblem onInitialStateLoaded(EntityTransaction transaction, UploadContext context) {
    return null;
  }

  @Override
  protected Collection<? extends UploadProblem> doPerform(RestSession session, UploadContext context, int issueId)
    throws ConnectorException, UploadProblem.Thrown {
    Integer parentId = myNewParent.getIssueId();
    if (parentId == null) return UploadProblem.notNow("Parent not submitted yet " + myNewParent).toCollection();
    RestResponse response = session.restPut(PATH_ISSUE + issueId, createRequest(parentId), RequestPolicy.NEEDS_LOGIN);
    if (response.isSuccessful()) {
      markSuccess();
      return null;
    }
    LogHelper.warning("Set Epic parent failed", issueId, parentId, response.getStatusCode());
    return UploadProblem.fatal(M_FAILED_SHORT.create(), M_FAILED_FULL.create()).toCollection();
  }

  /** Builds the field-update request body: {@code {"fields":{"parent":{"id":"<parentId>"}}}}. Package-visible for tests. */
  static JSONObject createRequest(int parentId) {
    return MoveRequests.parent(parentId);
  }

  @Override
  protected void reportAdditionalUpload(EntityTransaction transaction, PostUploadContext context, long issueItem) {
    context.reportUploaded(issueItem, Issue.PARENT);
  }

  @Override
  public String toString() {
    return "SetEpicParent";
  }
}

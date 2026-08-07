package com.almworks.jira.provider3.remotedata.issue.move;

import com.almworks.jira.provider3.services.upload.UploadJsonUtil;
import com.almworks.jira.provider3.sync.ServerFields;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;

/**
 * Builds {@code PUT api/3/issue/{id}} bodies for the move and convert units on Jira Cloud.
 *
 * <p><b>These cover less than the class name suggests, and the limits were measured, not assumed</b> (live capture
 * against Jira Cloud, 2026-08-07). {@code PUT api/3/issue/{id}} silently discards any field absent from the issue's
 * {@code editmeta}, and {@code project} is always absent. So this endpoint can express only:
 * <ul>
 *   <li>an issue-type change that stays within one project <b>and</b> on the same side of the subtask boundary;</li>
 *   <li>setting a parent (see {@code SetEpicParent}, which delegates to {@link #parent}).</li>
 * </ul>
 *
 * <p><b>A cross-project move and a subtask conversion are not edits at all.</b> Both go through the asynchronous
 * {@code POST api/3/bulk/issues/move}, then a poll, then a re-read - see Stage D1 in the plan for the wire shapes.
 * Sending {@code fields.project} here yields {@code 400 "The issue type selected is invalid."}, an error that names
 * the wrong field: the project change was dropped first, then the type was validated against the unchanged project.
 * Treat any {@code 400} naming {@code issuetype} from this endpoint as "something in this body is inapplicable".
 *
 * <p>Entity references are sent as {@code {"id": "<number>"}} with the id as a string, which is the form
 * {@code SetEpicParent} was verified against and the form {@code NewIssue} posts.
 *
 * <p>Clearing a parent is still absent, but no longer for want of a verified payload: {@code {"fields":{"parent":null}}}
 * works on a standard issue under an Epic and is refused with a {@code 400} on a subtask, where the operation is
 * meaningless. Never use the {@code update} form - it returns {@code 204} and does nothing. Adding the builder is
 * Stage D5; see the TODO at {@code MoveLoader.route}.
 */
class MoveRequests {
  private MoveRequests() {}

  /** A project and/or issue-type change on a generic issue. Either id may be null, meaning "leave unchanged". */
  static JSONObject projectAndType(@Nullable Integer projectId, @Nullable Integer issueTypeId) {
    JSONObject fields = new JSONObject();
    putEntity(fields, ServerFields.PROJECT.getJiraId(), projectId);
    putEntity(fields, ServerFields.ISSUE_TYPE.getJiraId(), issueTypeId);
    return wrapFields(fields);
  }

  /** Converting a generic issue to a subtask: it takes the subtask type and gains a parent in one update. */
  static JSONObject typeAndParent(int issueTypeId, int parentId) {
    JSONObject fields = new JSONObject();
    putEntity(fields, ServerFields.ISSUE_TYPE.getJiraId(), issueTypeId);
    putEntity(fields, ServerFields.PARENT.getJiraId(), parentId);
    return wrapFields(fields);
  }

  /** An issue-type change on its own, used by the subtask-to-generic direction and by a retype within one project. */
  static JSONObject type(int issueTypeId) {
    JSONObject fields = new JSONObject();
    putEntity(fields, ServerFields.ISSUE_TYPE.getJiraId(), issueTypeId);
    return wrapFields(fields);
  }

  /** Setting a parent without changing anything else - a subtask reparent, or a generic issue's hierarchy parent. */
  static JSONObject parent(int parentId) {
    JSONObject fields = new JSONObject();
    putEntity(fields, ServerFields.PARENT.getJiraId(), parentId);
    return wrapFields(fields);
  }

  @SuppressWarnings("unchecked")
  private static void putEntity(JSONObject fields, String jiraId, @Nullable Integer id) {
    if (id != null) fields.put(jiraId, UploadJsonUtil.object("id", Integer.toString(id)));
  }

  @SuppressWarnings("unchecked")
  private static JSONObject wrapFields(JSONObject fields) {
    JSONObject edit = new JSONObject();
    edit.put("fields", fields);
    return edit;
  }
}

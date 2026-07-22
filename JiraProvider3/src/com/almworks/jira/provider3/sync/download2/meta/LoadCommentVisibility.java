package com.almworks.jira.provider3.sync.download2.meta;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.constraint.Constraint;
import com.almworks.api.constraint.FieldSubsetConstraint;
import com.almworks.jira.connector2.JiraInternalException;
import com.almworks.jira.provider3.schema.Issue;
import com.almworks.jira.provider3.services.JiraPatterns;
import com.almworks.jira.provider3.sync.download2.rest.JqlSearch;
import com.almworks.restconnector.RequestPolicy;
import com.almworks.restconnector.RestResponse;
import com.almworks.restconnector.RestSession;
import com.almworks.restconnector.jql.JQLCompareConstraint;
import com.almworks.restconnector.jql.JQLConstraint;
import com.almworks.restconnector.json.JsonKey;
import com.almworks.util.LogHelper;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;

import java.util.*;

class LoadCommentVisibility {
  private static final JsonKey<Integer> ISSUE_ID = JsonKey.integer("id");
  private static final JsonKey<String> ISSUE_KEY = JsonKey.text("key");

  private static Constraint buildProjectConstraint(List<Long> projectIds) {
    if (projectIds == null || projectIds.isEmpty()) {
      return Constraint.FALSE;
    }
    // Build: project IN (id1, id2, ...)
    return FieldSubsetConstraint.Simple.intersection(Issue.PROJECT, projectIds);
  }

  /**
   * Extracts visibility groups (skips project roles) from Add Comment page
   * @return null if no visibility selector found<br>
   * empty list if only project roles are allowed for comment visibility<br>
   * not empty list of groups if groups are allowed for comment visibility
   */
  public static List<String> loadCommentVisibilityGroups(RestSession session) throws ConnectorException {

    // To get visibility groups from all issues, "project is not EMPTY" is Atlassian's recommended harmless restriction and does not exclude any issue.

    JQLConstraint allProjects = JQLCompareConstraint.isEmpty("project", true, "All projects");
    JSONObject issue = new JqlSearch(allProjects).addFields("key").querySingle(session);
    if (issue == null) return null;
    Integer id = ISSUE_ID.getValue(issue);
    String key = ISSUE_KEY.getValue(issue);
    if (id == null || key == null) {
      LogHelper.error("Failed to get issue ID/KEY", id, key);
      throw new JiraInternalException("Failed to load comments visibility");
    }
    Document page = loadAddCommentPage(id.toString(), key, session);
    return processAddComment(page);
  }

  public static Document loadAddCommentPage(String issueId, String issueKey, RestSession session) throws ConnectorException {
    // This call is for the warnings (?).
    JiraPatterns.canBeAnIssueKey(issueKey);

    String addCommentUrl = "secure/AddComment!default.jspa?id=" + issueId + "&decorator=none";
    RestResponse response = session.doGet(addCommentUrl, RequestPolicy.SAFE_TO_RETRY);
    response.ensureSuccessful();
    return response.getHtml();
  }

  @Nullable
  private static List<String> processAddComment(Document page) {
    if (page == null) return null;
    Element select = JDOMUtils.searchElement(page.getRootElement(), "select", "name", "commentLevel");
    if (select == null) {
      return null;
    }
    Iterator<Element> ii = JDOMUtils.searchElementIterator(select, "option");
    ArrayList<String> groups = Collections15.arrayList();
    while (ii.hasNext()) {
      Element option = ii.next();
      String value = JDOMUtils.getAttributeValue(option, "value", "", true);
      if (value.length() == 0) continue;
      String name = JDOMUtils.getText(option);
      if (!value.startsWith("group:")) continue;
      String id = value.substring(6);
      if (!id.equalsIgnoreCase(name)) {
        Log.warn("suspicious group id [" + id + "][" + name + "]");
      }
      groups.add(id);
    }
    return groups;
  }
}

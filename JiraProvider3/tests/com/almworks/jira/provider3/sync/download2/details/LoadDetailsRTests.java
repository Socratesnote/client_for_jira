package com.almworks.jira.provider3.sync.download2.details;

import com.almworks.items.api.DBIdentifiedObject;
import com.almworks.items.entities.api.collector.transaction.EntityTransaction;
import com.almworks.items.entities.api.collector.typetable.TransactionTestUtil;
import com.almworks.items.sync.util.identity.DBIdentity;
import com.almworks.jira.provider3.custom.FieldKind;
import com.almworks.jira.provider3.custom.impl.CustomFieldsComponent;
import com.almworks.jira.provider3.custom.loadxml.FieldKeysLoader;
import com.almworks.jira.provider3.schema.Jira;
import com.almworks.jira.provider3.sync.ServerInfo;
import com.almworks.jira.provider3.sync.download2.TestResources;
import com.almworks.jira.provider3.sync.schema.*;
import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.TypedKey;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LoadDetailsRTests extends BaseTestCase {
  private static final TestResources RESOURCES = TestResources.create(LoadDetailsRTests.class, "com/almworks/jira/provider3/sync/download2/details/");
  private static Map<String,FieldKind> CUSTOM_FIELDS_MAP;

  static {
    try {
      FieldKeysLoader loader = FieldKeysLoader.load("/com/almworks/jira/provider3/customFields.xml", CustomFieldsComponent.SCHEMA);
      List<Map<TypedKey<?>, ?>> kinds = loader.getLoadedKinds();
      CUSTOM_FIELDS_MAP = CustomFieldsComponent.createKindsMap(false, kinds);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void testNewCloudUsers() throws IOException, ParseException {
    runTest("newCloudUsers.json", "newCloudUsers.json.txt");
  }

  /**
   * A comment and a worklog restricted to a project role. The role arrives carrying only its name, and role names
   * repeat across projects, so the issue's project must be stamped onto the role for it to resolve; without that
   * the visibility silently reads back as "visible to all".
   */
  public void testRoleVisibility() throws IOException, ParseException {
    runTest("roleVisibility.json", "roleVisibility.json.txt");
  }

  /**
   * A comment page embedded in the issue's search/get response can be a truncated middle-or-last page rather
   * than the first: this fixture's embedded comment page is total=64, startAt=44, maxResults=20 - the last
   * page, not the first. This only covers the embedded page parsing correctly with a non-zero startAt; the
   * re-fetch that should then bring in the remaining comments is covered separately, offline, by
   * {@link com.almworks.jira.provider3.sync.download2.details.fields.CommentsFieldTests} - LoadDetails never
   * calls CommentsField.maybeLoadAdditional, so this test alone cannot exercise that path.
   */
  public void testPartialCommentPage() throws IOException, ParseException {
    runTest("partialCommentPage.json", "partialCommentPage.json.txt");
  }

  private static final String CONNECTION_ID = "CONN-ECTI-ON_I-D";
  private void runTest(String source, String result) throws IOException, ParseException {
    Object json = RESOURCES.loadJson(source);
    CollectOperations operations = new CollectOperations();
    RESOURCES.parseJsonResource(source, operations.getIssueHandler());
    DBIdentifiedObject connectionObject = Jira.createConnectionObject(CONNECTION_ID);
    DBIdentity connection = DBIdentity.fromDBObject(connectionObject);
    EntityTransaction transaction = ServerInfo.priCreateTransaction(CONNECTION_ID, connection);
    LoadDetails details = new LoadDetails(transaction, new CustomFieldsSchema.RestLoader(CUSTOM_FIELDS_MAP, CONNECTION_ID));
    RESOURCES.parseJsonResource(source, details.createHandler("Test Issue"));
    RESOURCES.assertTextEquals(result, TransactionTestUtil.printTransaction(transaction, ServerIssue.TYPE, ServerComment.TYPE, ServerLink.TYPE, ServerLinkType.TYPE, ServerWorklog.TYPE, ServerAttachment.TYPE));
  }
}

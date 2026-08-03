package com.almworks.jira.provider3.issue.features.edit.screens;

import com.almworks.items.gui.edit.EditItemModel;
import com.almworks.items.gui.edit.FieldEditor;
import com.almworks.items.gui.edit.engineactions.EngineConsts;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.VersionSource;
import com.almworks.jira.provider3.app.connection.JiraConnection3;
import com.almworks.jira.provider3.gui.edit.EditorsScheme;
import com.almworks.jira.provider3.gui.edit.FieldSet;
import com.almworks.jira.provider3.gui.edit.fields.EditableFields;
import com.almworks.jira.provider3.issue.editor.ScreenController;
import com.almworks.jira.provider3.issue.editor.ScreenSet;
import com.almworks.jira.provider3.sync.ServerFields;
import com.almworks.util.LogHelper;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.FixedListModel;
import com.almworks.util.advmodel.SelectionInListModel;
import com.almworks.util.config.Configuration;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ScreenChooser implements ScreenSet {
  private static final TypedKey<Chooser> KEY = TypedKey.create("screenChooser");

  private final EditorsScheme myScheme;
  private final boolean myCreate;

  /**
   * @param create build screens for create issue operation (if false - build for edit operation)
   */
  public ScreenChooser(EditorsScheme scheme, boolean create) {
    myScheme = scheme;
    myCreate = create;
  }

  @Override
  @Nullable
  public List<FieldEditor> install(VersionSource source, EditItemModel model) {
    JiraConnection3 connectionObject = EngineConsts.getConnection(JiraConnection3.class, model);
    if  (connectionObject == null) {
      LogHelper.error("Missing connection");
      return null;
    }
    ItemVersion connection = source.forItem(connectionObject.getConnectionItem());
    Pair<List<EditIssueScreen>, ScreenScheme> allScreens = loadAllScreens(connection, model);
    SelectionInListModel<EditIssueScreen> chooserModel = SelectionInListModel.create(Lifespan.FOREVER, FixedListModel.create(allScreens.getFirst()), null);
    FieldSet allFields = FieldSet.allFields(connectionObject, source.getReader());
    allFields.createEditors(connection, myScheme);
    List<FieldEditor> editors = allFields.extractAllEditors(model);
    Chooser chooser = new Chooser(chooserModel, allScreens.getSecond());
    model.getRootModel().putHint(KEY, chooser);
    return editors;
  }

  @NotNull
  public static Pair<List<EditIssueScreen>, ScreenScheme> loadAllScreens(ItemVersion connection, EditItemModel model) {
    JiraScreens screens = JiraScreens.load(connection);
    List<EditIssueScreen> allScreens = Collections15.arrayList();
    RemoteScreen.collectRemotes(connection, model, screens, allScreens);
    EditableFields editableFields = EditableFields.ensureLoaded(connection, model);
    List<EditIssueScreen> relevantScreen = Collections15.arrayList();
    RelevantScreen.load(connection, model, relevantScreen);
    // "All Fields" lists every field the connection knows of, without asking whether the edited issue accepts it. With
    // one issue that is survivable, because an inapplicable field is shown disabled against that issue's own editmeta.
    // With several there is no single issue to disable against, so the screen is withheld and the edit is limited to
    // the relevant fields, which are applicable to every selected issue by construction.
    //
    // Two fallbacks keep the editor usable: no relevant screen at all, and no stored editmeta on any selected issue.
    // In the second case "relevant" has nothing to stand on either, so withholding "All Fields" would narrow the edit
    // on a guess.
    boolean limitToRelevant = model.getEditingItems().size() > 1 && !relevantScreen.isEmpty() && editableFields.isKnown();
    if (!limitToRelevant) AllFieldsScreen.load(connection, allScreens);
    allScreens.addAll(relevantScreen);
    ScreenScheme resolvedScheme = screens != null ? screens.resolve(connection) : null;
    return Pair.create(allScreens, resolvedScheme);
  }

  @Nullable
  @Override
  public JComponent getScreenSelector(EditItemModel model) {
    Chooser chooser = getInstance(model);
    return chooser != null ? chooser.getComponent() : null;
  }

  @Override
  public boolean attach(Lifespan life, ScreenController controller, Configuration config) {
    final EditItemModel model = controller.getModel();
    final Chooser chooser = getInstance(model);
    if (chooser == null) return false;
    setupController(controller);
    return chooser.attach(life, controller, config, myCreate);
  }

  private static final List<ServerFields.Field> TOP_FIELDS = Collections15.unmodifiableListCopy(ServerFields.PROJECT, ServerFields.ISSUE_TYPE);
  private static final List<ServerFields.Field> BOTTOM_FIELDS = Collections15.unmodifiableListCopy(ServerFields.LINKS, ServerFields.ATTACHMENT);
  private void setupController(ScreenController controller) {
    controller.setTopFields(TOP_FIELDS);
    ArrayList<ServerFields.Field> bottom = Collections15.arrayList(BOTTOM_FIELDS);
    if (!myCreate) bottom.add(0, ServerFields.COMMENTS);
    controller.setBottomFields(bottom);
  }

  @Nullable
  private static Chooser getInstance(EditItemModel model) {
    if (model == null) return null;
    Chooser chooser = model.getRootModel().getValue(KEY);
    if (chooser == null) LogHelper.error("Missing screen chooser");
    return chooser;
  }
}

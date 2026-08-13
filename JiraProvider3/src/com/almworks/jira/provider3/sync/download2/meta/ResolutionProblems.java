package com.almworks.jira.provider3.sync.download2.meta;

import com.almworks.api.connector.ConnectorException;
import com.almworks.items.entities.api.Entity;
import com.almworks.items.entities.api.EntityKey;
import com.almworks.items.entities.api.collector.transaction.EntityHolder;
import com.almworks.items.entities.api.collector.transaction.EntityTransaction;
import com.almworks.jira.connector2.JiraInternalException;
import com.almworks.jira.provider3.custom.impl.RemoteMetaConfig;
import com.almworks.jira.provider3.sync.ServerInfo;
import com.almworks.jira.provider3.sync.download2.process.util.EntityDBUpdate;
import com.almworks.jira.provider3.sync.download2.process.util.ProgressInfo;
import com.almworks.jira.provider3.sync.schema.ServerComponent;
import com.almworks.jira.provider3.sync.schema.ServerProject;
import com.almworks.jira.provider3.sync.schema.ServerProjectRole;
import com.almworks.jira.provider3.sync.schema.ServerVersion;
import com.almworks.restconnector.RestSession;
import com.almworks.spi.provider.CancelFlag;
import com.almworks.util.LogHelper;
import com.almworks.util.Pair;
import com.almworks.util.Trio;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Solves entity-resolution problems for Projects, Components, Versions, and Project Roles<br>
 * Project problems: performs a brief project list reload<br>
 * Component, Version or Project Role problems: reloads full corresponding projects <br>
 * For problems that don't report which project they belong to (see getUncreatable), it throws
 * errors because these violate invariant types and cause synchronization failures.
 * @see com.almworks.items.entities.api.collector.transaction.write.EntityWriter#getUncreatable()
 */
public class ResolutionProblems {
  private final RestSession mySession;
  private final ServerInfo myServerInfo;
  private final CancelFlag myCancelFlag;
  private final RemoteMetaConfig myMetaConfig;
  private final List<EntityHolder> myProblems = Collections15.arrayList();
  /**
   * Indicates that at least some problems can likely be resolved by a brief project list reload.
   */
  private boolean myDoReloadProjectList = false;
  /**
   * Projects to load fully, identified by ID or KEY
   */
  private final List<Pair<Integer, String>> myFullProjects = Collections15.arrayList();

  public ResolutionProblems(RestSession session, ServerInfo serverInfo, RemoteMetaConfig metaConfig) {
    mySession = session;
    myServerInfo = serverInfo;
    myCancelFlag = new CancelFlag();
    myMetaConfig = metaConfig;
  }

  public void addAll(Collection<EntityHolder> problems) {
    myProblems.addAll(problems);
  }

  /**
   * Resolving problems first checks if a brief reload suffices; in that case, only the list of projects is refreshed and written to the database. If more is needed, a full reload of specific projects is carried out.
   */
  public void resolve() throws ConnectorException {
    prepare();
    if (!myDoReloadProjectList && myFullProjects.isEmpty()) {
      LogHelper.error("Resolve called but no problem detected");
      return;
    }
    EntityTransaction transaction = myServerInfo.createTransaction();
    // If only a brief refresh is needed, only load and write that to the database.
    if (myDoReloadProjectList && myFullProjects.isEmpty()) {
      // No need to store the output; we only care about the transaction that occurs.
      LoadProjects.loadBriefProjects(mySession, transaction, ProgressInfo.createDeaf(myCancelFlag));
    }
    // If any full reloads are needed, the brief refresh also occurs and is used to inform the full reload.
    if (!myFullProjects.isEmpty()) {
      ArrayList<Trio<Integer,String,String>> projects = LoadProjects.loadBriefProjects(mySession, transaction, ProgressInfo.createDeaf(myCancelFlag));
      projects.removeIf(project -> !needsFull(project));
      if (projects.isEmpty()) LogHelper.error("No project requires full load", myFullProjects);
      else {
        // Load problematic projects in full.
        new LoadProjects(transaction, false).loadFullProjects(mySession, projects, ProgressInfo.createDeaf(myCancelFlag));
      }
    }
    // Write transactions to database.
    EntityDBUpdate update = new EntityDBUpdate(transaction, myMetaConfig);
    myServerInfo.getSyncManager().writeDownloaded(update).waitForCompletion();
  }

  private boolean needsFull(Trio<Integer, String, String> project) {
    for (Pair<Integer, String> pair : myFullProjects) {
      Integer id = pair.getFirst();
      String key = pair.getSecond();
      if (id != null) {
        if (id.equals(project.getFirst())) return true;
        else continue;
      }
      if (key != null) {
        if (key.equals(project.getSecond())) return true;
        else continue;
      }
      LogHelper.error("Missing project identity", pair);
    }
    return false;
  }

  // Decides what has to be re-downloaded to resolve the problems collected so far. Some problems like projects can be solved by a brief reload of the project list, while more complex problems are written to a list of projects marked for a full re-load, which are handled on @resolve().
  // Package-visible, together with the two accessors below, so ResolutionProblemsTests can drive it without a session.
  void prepare() throws JiraInternalException {
    for (EntityHolder problem : myProblems) {
      if (problem == null) {
        LogHelper.error("Unknown problem type null");
        throw failure();
      }

      Entity type = problem.getItemType();
      // This is a project; a brief load suffices.
      if (ServerProject.TYPE.equals(type)) {
        myDoReloadProjectList = true;
        continue;
      }

      EntityKey<Entity> projectRef;
      // Determine the reference type.
      if (ServerVersion.TYPE.equals(type)) projectRef = ServerVersion.PROJECT;
      else if (ServerComponent.TYPE.equals(type)) projectRef = ServerComponent.PROJECT;
      else if (ServerProjectRole.TYPE.equals(type)) projectRef = ServerProjectRole.PROJECT;
      else {
        LogHelper.error("Unknown problem type", type, type.getTypeId(), problem);
        throw failure();
      }

      EntityHolder project = problem.getReference(projectRef);
      if (project == null) {
        // All three types carry their project as part of their identity, so a problem without one cannot have been
        // collected in the first place: a place is only created for a row that satisfies at least one of the type's
        // resolutions, and every resolution of all three names the project. Roles are potentially an exception because they can be built from comment visibility which carries only a name, but such a role should now fail to identify at collection, well before it could reach this method as a problem.
        LogHelper.error("Missing project", problem, type);
        throw failure();
      }
      markLoadFull(project);
    }
  }

  // Whether a brief reload of the project list was asked for. See the field.
  boolean isReloadProjectList() {
    return myDoReloadProjectList;
  }

  // The projects marked for a full load, as (id, key) pairs. Either half may be null.
  List<Pair<Integer, String>> getFullProjects() {
    return myFullProjects;
  }

  private void markLoadFull(@NotNull EntityHolder project) throws JiraInternalException {
    String key = project.getScalarValue(ServerProject.KEY);
    Integer id = project.getScalarValue(ServerProject.ID);
    if (id == null && key == null) {
      LogHelper.error("No project identity for ", project);
      throw failure();
    }
    for (Pair<Integer, String> idKey : myFullProjects) {
      if (id != null && id.equals(idKey.getFirst())) return;
      if (key != null && key.equals(idKey.getSecond())) return;
    }
    myFullProjects.add(Pair.create(id, key));
  }

  static JiraInternalException failure() {
    return new JiraInternalException("Can not store data in local database");
  }
}

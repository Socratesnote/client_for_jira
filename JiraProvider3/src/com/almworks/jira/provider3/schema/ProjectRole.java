package com.almworks.jira.provider3.schema;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBItemType;
import com.almworks.items.gui.meta.util.EnumTypeBuilder;
import com.almworks.items.sync.util.identity.DBStaticObject;
import com.almworks.jira.provider3.sync.schema.ServerJira;
import com.almworks.jira.provider3.sync.schema.ServerProjectRole;

public class ProjectRole {
  public static final DBItemType DB_TYPE = ServerJira.toItemType(ServerProjectRole.TYPE);
  public static final DBAttribute<Integer> ID = ServerJira.toScalarAttribute(ServerProjectRole.ID);
  public static final DBAttribute<String> NAME = ServerJira.toScalarAttribute(ServerProjectRole.NAME);
  public static final DBAttribute<String> DESCRIPTION = ServerJira.toScalarAttribute(ServerProjectRole.DESCRIPTION);
  public static final DBAttribute<Long> PROJECT = ServerJira.toLinkAttribute(ServerProjectRole.PROJECT);
  public static final DBAttribute<Boolean> CURRENT_USER_MEMBER = ServerJira.toScalarAttribute(ServerProjectRole.CURRENT_USER_MEMBER);

  public static final DBAttribute<Boolean> PROJECT_ROLES_ONLY = ServerJira.toScalarAttribute(ServerProjectRole.PROJECT_ROLES_ONLY);

  // Narrowed by project: roles are stored per project, so an issue must only be offered the roles of its own
  // project.
  // ID, not NAME, is the unique key. Neither is truly unique across the whole type - names repeat in every
  // project, and projects that share a Jira role definition report the same id - and the enum machinery
  // tolerates that: ids are indexed in a multimap and the narrower cuts the candidates down to the issue's
  // project. ID is the better of the two because it survives a role being renamed on the server.
  // The renderer is set explicitly to NAME because it is deliberately not the unique key here: the default
  // renderer falls back to the unique key, which would display the raw role id (e.g. "10072") instead of the name.
  // The membership flag is subloaded rather than narrowed on: a narrower holds only one restriction and the
  // project narrowing uses it, so the membership filter is applied by VisibilityEditor over the loaded values.
  public static final DBStaticObject ENUM_TYPE = new EnumTypeBuilder()
    .setType(DB_TYPE)
    .setUniqueKey(ID)
    .renderFirstNotNull(NAME)
    .narrowByAttribute(Issue.PROJECT, PROJECT)
    .addAttributeSubloaders(CURRENT_USER_MEMBER)
    .create();

}

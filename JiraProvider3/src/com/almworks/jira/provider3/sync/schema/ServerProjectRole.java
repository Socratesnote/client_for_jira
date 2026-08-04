package com.almworks.jira.provider3.sync.schema;

import com.almworks.items.entities.api.Entity;
import com.almworks.items.entities.api.EntityKey;
import com.almworks.items.entities.api.util.EntityResolution;

import java.util.Arrays;

public class ServerProjectRole {
  public static final EntityKey<Integer> ID = Commons.ENTITY_ID;
  public static final EntityKey<String> NAME = Commons.ENTITY_NAME;
  public static final EntityKey<String> DESCRIPTION = Commons.ENTITY_DESCRIPTION;
  /**
   * The project this role belongs to. Jira gives every project its own role instances: the same role names
   * appear in each project with different ids, so a role belongs to exactly one project and this is a single
   * link rather than a set of projects.
   */
  public static final EntityKey<Entity> PROJECT = Commons.ENTITY_PROJECT;

  // Jira Comment Visibility option (true is for "Project Roles Only", false is for "Groups & Project Roles")
  public static final EntityKey<Boolean> PROJECT_ROLES_ONLY = EntityKey.bool("connection.rolesOnly", null);

  public static final Entity TYPE;
  static {
    TYPE = Entity.buildType("types.projectRole");
    // Searching by name alone is ambiguous, because role names repeat across projects, and resolution then picks
    // an arbitrary match. The project scopes it. Note that every producer of a role entity must set PROJECT:
    // the search ANDs one clause per key, so a name-only entity matches nothing at all rather than falling back
    // to a name search. Downloaded visibility gets its project stamped on in SimpleDependent.
    TYPE.put(EntityResolution.KEY, EntityResolution.searchable(true, Arrays.asList(NAME, PROJECT), ID));
    TYPE.fix();
  }
}

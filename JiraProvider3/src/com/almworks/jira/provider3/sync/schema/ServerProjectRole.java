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
   * The project this role applies to. A role id is not unique on its own: projects share Jira's role
   * definitions, so several projects report the same id, while a team-managed project mints its own ids and
   * reports a different id for the same role name. Neither the id nor the name identifies a role by itself,
   * and everything the client needs about a role is per-project anyway - which projects offer it, and whether
   * the current user is a member of it there. So a role is stored once per project, identified by
   * (id, project), and this is a single link rather than a set of projects.
   */
  public static final EntityKey<Entity> PROJECT = Commons.ENTITY_PROJECT;

  /**
   * Whether the current user is a member of this role. Jira only accepts a comment or worklog restricted to a
   * role its author belongs to, so a role the user is not in must not be offered by the visibility picker.
   * <p>
   * Null means membership could not be established, not "not a member": reading the actor list needs
   * project-administration permission, so an ordinary user gets a 403. Unknown roles stay on offer, because
   * hiding a role the user can really use is worse than offering one that fails on upload.
   */
  public static final EntityKey<Boolean> CURRENT_USER_MEMBER = EntityKey.bool("projectRole.currentUserMember", null);

  // Jira Comment Visibility option (true is for "Project Roles Only", false is for "Groups & Project Roles")
  public static final EntityKey<Boolean> PROJECT_ROLES_ONLY = EntityKey.bool("connection.rolesOnly", null);

  public static final Entity TYPE;
  static {
    TYPE = Entity.buildType("types.projectRole");
    // The identity is (id, project), the same shape as versions and components: projects share role ids, so id
    // alone collapses every project that offers a role into one entity, and the project loaded last wins.
    // Searching by name alone is ambiguous for the mirror-image reason - role names repeat across projects - so
    // the project scopes that too. Note that every producer of a role entity must set PROJECT: the search ANDs one
    // clause per key, so a name-only entity matches nothing at all rather than falling back to a name search.
    // Downloaded visibility gets its project stamped on in SimpleDependent.
    TYPE.put(EntityResolution.KEY, EntityResolution.searchable(true, Arrays.asList(NAME, PROJECT), ID, PROJECT));
    TYPE.fix();
  }
}

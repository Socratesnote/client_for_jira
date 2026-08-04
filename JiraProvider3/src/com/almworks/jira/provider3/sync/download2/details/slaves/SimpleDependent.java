package com.almworks.jira.provider3.sync.download2.details.slaves;

import com.almworks.items.entities.api.Entity;
import com.almworks.items.entities.api.EntityKey;
import com.almworks.items.entities.api.collector.transaction.EntityBag2;
import com.almworks.items.entities.api.collector.transaction.EntityHolder;
import com.almworks.items.entities.api.collector.transaction.EntityTransaction;
import com.almworks.jira.provider3.sync.download2.details.JsonIssueField;
import com.almworks.jira.provider3.sync.schema.ServerIssue;
import com.almworks.jira.provider3.sync.schema.ServerProjectRole;
import com.almworks.jira.provider3.sync.download2.details.fields.ObjectField;
import com.almworks.util.LogHelper;
import com.almworks.util.collections.Convertor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class SimpleDependent implements SlaveLoader<EntityBag2> {
  private final Entity myType;
  private final EntityKey<Entity> myMaster;
  private final Convertor<Object, Entity> mySlaveLoader;
  @Nullable
  private final EntityKey<Integer> myOrder;
  @Nullable
  private final EntityKey<Entity> mySecurityKey;

  /**
   * @param order if not null set order
   */
  public SimpleDependent(Entity type, EntityKey<Entity> master, Convertor<Object, Entity> dependentLoader, @Nullable EntityKey<Integer> order) {
    this(type, master, dependentLoader, order, null);
  }

  /**
   * @param order if not null set order
   * @param securityKey the slave's visibility key, if it has one. A visibility restricted to a project role
   *                    arrives carrying only the role name, and role names repeat across projects, so the role
   *                    is stamped with the master issue's project here - the first point where that project is
   *                    known. Without it the role cannot be resolved to the right project's role.
   */
  public SimpleDependent(Entity type, EntityKey<Entity> master, Convertor<Object, Entity> dependentLoader,
    @Nullable EntityKey<Integer> order, @Nullable EntityKey<Entity> securityKey)
  {
    myType = type;
    myMaster = master;
    mySlaveLoader = dependentLoader;
    myOrder = order;
    mySecurityKey = securityKey;
  }

  public JsonIssueField toField(boolean nullAsEmpty) {
    return DependentBagField.create(this, nullAsEmpty);
  }

  public JsonIssueField toField(boolean nullAsEmpty, String... path) {
    JsonIssueField field = toField(nullAsEmpty);
    for (int i = path.length - 1; i >= 0; i--) {
      String key = path[i];
      field = ObjectField.getField(key, field, nullAsEmpty);
    }
    return field;
  }

  @Override
  public Collection<? extends Parsed<EntityBag2>> loadValue(Object jsonObject, int order) {
    final Entity slave = mySlaveLoader.convert(jsonObject);
    if (slave == null) {
      LogHelper.error("Nothing loaded", myType, myMaster);
      return null;
    }
    return MyParsed.singleton(myType, myMaster, slave, myOrder, order, mySecurityKey);
  }

  @Override
  public EntityBag2 createBags(EntityHolder master) {
    return master.getTransaction().addBagRef(myType, myMaster, master).delete();
  }

  @Override
  public String toString() {
    return "Slave(" + myMaster + ")";
  }

  public static class MyParsed implements Parsed<EntityBag2> {
    private final Entity myType;
    private final EntityKey<Entity> myMaster;
    private final Entity myDependent;
    @Nullable
    private final EntityKey<Integer> myOrder;
    private final int myIndex;
    @Nullable
    private final EntityKey<Entity> mySecurityKey;

    public MyParsed(Entity type, EntityKey<Entity> master, Entity dependent, @Nullable EntityKey<Integer> order, int index,
      @Nullable EntityKey<Entity> securityKey)
    {
      myType = type;
      myMaster = master;
      myDependent = dependent;
      myOrder = order;
      myIndex = index;
      mySecurityKey = securityKey;
    }

    public static Collection<? extends Parsed<EntityBag2>> singleton(Entity type, EntityKey<Entity> master, Entity dependent,
      @Nullable EntityKey<Integer> order, int index)
    {
      return singleton(type, master, dependent, order, index, null);
    }

    public static Collection<? extends Parsed<EntityBag2>> singleton(Entity type, EntityKey<Entity> master, Entity dependent,
      @Nullable EntityKey<Integer> order, int index, @Nullable EntityKey<Entity> securityKey)
    {
      return Collections.singleton(new MyParsed(type, master, dependent, order, index, securityKey));
    }

    @Override
    public void addTo(EntityHolder master, @Nullable EntityBag2 bag) {
      EntityTransaction.IdentityBuilder builder = master.getTransaction().buildEntity(myType);
      if (builder == null) {
        LogHelper.error("Failed to store", myType);
        return;
      }
      if (mySecurityKey != null) stampRoleProject(master, myDependent, mySecurityKey);
      builder.copy(myDependent);
      builder.addReference(myMaster, master);
      if (myOrder != null) builder.addValue(myOrder, myIndex);
      EntityHolder slave = builder.create();
      if (bag != null) bag.exclude(slave);
    }

    // Copies the master issue's project onto a project-role visibility, so the role resolves within its own
    // project. Does nothing for group visibility, for an absent visibility, or when the project is already set.
    private static void stampRoleProject(EntityHolder master, Entity dependent, EntityKey<Entity> securityKey) {
      Entity security = dependent.get(securityKey);
      if (security == null || !ServerProjectRole.TYPE.equals(security.getType())) return;
      if (security.get(ServerProjectRole.PROJECT) != null) return;
      EntityHolder project = master.getReference(ServerIssue.PROJECT);
      if (project == null) {
        LogHelper.warning("No project for role visibility", master, security);
        return;
      }
      security.put(ServerProjectRole.PROJECT, project.restore());
    }

    @Override
    public String toString() {
      return myDependent != null ? myDependent.toString() : "<null>";
    }
  }
}

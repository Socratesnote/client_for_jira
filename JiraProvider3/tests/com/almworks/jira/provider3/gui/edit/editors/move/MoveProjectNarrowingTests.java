package com.almworks.jira.provider3.gui.edit.editors.move;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ItemKeyStub;
import com.almworks.api.application.ItemOrder;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.gui.edit.DefaultEditModel;
import com.almworks.items.gui.edit.EditItemModel;
import com.almworks.items.gui.edit.EditModelState;
import com.almworks.items.gui.edit.editors.enums.EnumModelConfigurator;
import com.almworks.items.gui.edit.editors.enums.VariantsAcceptor;
import com.almworks.items.gui.meta.LoadedItemKey;
import com.almworks.jira.provider3.schema.Issue;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.FixedListModel;
import com.almworks.util.collections.Convertor;
import com.almworks.util.collections.UserDataHolder;
import com.almworks.util.config.Configuration;
import com.almworks.util.tests.GUITestCase;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifespan;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.SortedSet;

/**
 * Covers the mechanism the Move/Convert dialog narrows Issue Type by: the {@link Issue#PROJECT} axis of the
 * hypercube the model hands to the enum narrower, and whether a change to the Project field re-drives it.
 * <p>
 * Both halves are read at verification time by {@code EnumTypeProvider.selectInvalid} (the source of the
 * "&lt;type&gt; is not allowed" message) and at variants time by {@link EnumModelConfigurator}, so a stale axis
 * would narrow the offered issue types against the project the dialog opened on rather than the selected one.
 * <p>
 * These tests exercise ItemGUI's model mechanics with the real {@code Issue.PROJECT} attribute; they do not stand
 * up the enum-type machinery, so they say nothing about which issue types a given project actually allows.
 */
public class MoveProjectNarrowingTests extends GUITestCase {
  private static final long ISSUE = 100;
  private static final long SOURCE_PROJECT = 200;
  private static final long TARGET_PROJECT = 201;

  /** Stands in for the Project field's value, which a real editor keeps in the model under its own key. */
  private static final TypedKey<Long> PROJECT_VALUE = TypedKey.create("test/projectEditorValue");

  private DefaultEditModel.Root myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModel = DefaultEditModel.Root.editItems(LongArray.create(ISSUE));
  }

  /**
   * Registers the same kind of single-enum getter {@code EnumValueKey.registerAttribute} installs for a dropdown
   * editor: one that reads the field's current value out of the model on every call.
   */
  private void registerProjectEditor() {
    myModel.registerSingleEnum(Issue.PROJECT, new Convertor<EditModelState, LongList>() {
      @Override
      public LongList convert(EditModelState model) {
        Long value = model.getValue(PROJECT_VALUE);
        return value == null || value <= 0 ? LongList.EMPTY : LongArray.create(value);
      }
    });
  }

  private void selectProject(long project) {
    myModel.putValue(PROJECT_VALUE, project);
  }

  private ItemHypercube projectCube() {
    return myModel.collectHypercube(Collections.<DBAttribute<?>>singleton(Issue.PROJECT));
  }

  private void assertProjectAxis(long expected, ItemHypercube cube) {
    SortedSet<Long> included = cube.getIncludedValues(Issue.PROJECT);
    assertNotNull("No Issue.PROJECT axis in the cube", included);
    assertEquals(Collections.singleton(expected), new HashSet<Long>(included));
  }

  // The cube follows the field's current value, not the value it held when the model was built.
  public void testCubeTracksSelectedProject() {
    registerProjectEditor();
    selectProject(SOURCE_PROJECT);
    assertProjectAxis(SOURCE_PROJECT, projectCube());
    selectProject(TARGET_PROJECT);
    assertProjectAxis(TARGET_PROJECT, projectCube());
  }

  // A registered editor value wins over an axis seeded into the model by setHypercube, so a seeded source
  // project cannot make the narrowing stale while the Project field is in the model.
  public void testEditorValueOverridesSeededHypercube() {
    ItemHypercubeImpl seeded = new ItemHypercubeImpl();
    seeded.addAxisIncluded(Issue.PROJECT, LongArray.create(SOURCE_PROJECT));
    myModel.setHypercube(seeded);
    registerProjectEditor();
    selectProject(TARGET_PROJECT);
    assertProjectAxis(TARGET_PROJECT, projectCube());
  }

  // The other side of the same precedence: with no editor registered for the attribute the seeded axis is all
  // there is, and it never changes. This is the shape a stale narrowing would have.
  public void testSeededHypercubeUsedWhenNoEditorRegistered() {
    ItemHypercubeImpl seeded = new ItemHypercubeImpl();
    seeded.addAxisIncluded(Issue.PROJECT, LongArray.create(SOURCE_PROJECT));
    myModel.setHypercube(seeded);
    selectProject(TARGET_PROJECT);
    assertProjectAxis(SOURCE_PROJECT, projectCube());
  }

  // An empty selection leaves the axis present but unconstrained, which the narrowers read as "accept everything"
  // rather than "accept nothing".
  public void testNoProjectSelectedLeavesAxisUnconstrained() {
    registerProjectEditor();
    SortedSet<Long> included = projectCube().getIncludedValues(Issue.PROJECT);
    assertTrue("Expected an empty axis, got " + included, included == null || included.isEmpty());
  }

  // Changing the Project field re-drives the variants configurator with the new cube, which is what re-narrows
  // the Issue Type dropdown to the selected project.
  public void testConfiguratorRenarrowsOnProjectChange() {
    registerProjectEditor();
    selectProject(SOURCE_PROJECT);
    RecordingConfigurator configurator = new RecordingConfigurator(myModel);
    configurator.start(Lifespan.FOREVER);
    assertEquals(1, configurator.myCubes.size());
    assertProjectAxis(SOURCE_PROJECT, configurator.myCubes.get(0));

    // GUITestCase already runs on the EDT, so ThreadGate.AWT delivers the model change synchronously.
    selectProject(TARGET_PROJECT);
    assertEquals(2, configurator.myCubes.size());
    assertProjectAxis(TARGET_PROJECT, configurator.myCubes.get(1));
  }

  // Re-selecting the same project is not a change, so the variants model is not rebuilt.
  public void testConfiguratorIgnoresUnchangedProject() {
    registerProjectEditor();
    selectProject(SOURCE_PROJECT);
    RecordingConfigurator configurator = new RecordingConfigurator(myModel);
    configurator.start(Lifespan.FOREVER);
    selectProject(SOURCE_PROJECT);
    assertEquals(1, configurator.myCubes.size());
  }


  // ---- MoveController.pickTypeForVariants: what to select once the list has been rebuilt ----

  // A type still offered by the new project is left alone.
  public void testKeepsTypeStillOffered() {
    ItemKey task = type(1, "Task");
    assertSame(task, MoveController.pickTypeForVariants(task, variants(type(1, "Task"), type(2, "Bug"))));
  }

  // The common case: the target project has a same-named type under a different id, so re-pick by name.
  public void testRepicksSameNameUnderDifferentId() {
    ItemKey axeTask = type(10050, "Task");
    ItemKey pphdTask = type(10044, "Task");
    assertSame(pphdTask, MoveController.pickTypeForVariants(axeTask, variants(pphdTask, type(10047, "Bug"))));
  }

  // Case differences are treated as the same name; a project that only recapitalised a type still matches.
  public void testRepickIgnoresCase() {
    ItemKey source = type(10050, "Task");
    ItemKey target = type(10044, "task");
    assertSame(target, MoveController.pickTypeForVariants(source, variants(target)));
  }

  // The fallback that AXE/PPHD actually need: "Subtask" and "Sub-task" are different names, so the field clears
  // rather than picking a wrong type.
  public void testClearsWhenNamesDifferBeyondCase() {
    ItemKey axeSubtask = type(10049, "Subtask");
    assertNull(MoveController.pickTypeForVariants(axeSubtask, variants(type(10045, "Sub-task"), type(10044, "Task"))));
  }

  // AXE's Feature has no counterpart in PPHD at all.
  public void testClearsWhenNoCounterpartExists() {
    ItemKey feature = type(10051, "Feature");
    assertNull(MoveController.pickTypeForVariants(feature, variants(type(10044, "Task"), type(10047, "Bug"))));
  }

  public void testClearsAgainstAnEmptyList() {
    assertNull(MoveController.pickTypeForVariants(type(10050, "Task"), variants()));
  }

  // Nothing selected means nothing to re-pick.
  public void testNoSelectionPicksNothing() {
    assertNull(MoveController.pickTypeForVariants(null, variants(type(10044, "Task"))));
    assertNull(MoveController.pickTypeForVariants(type(0, "Task"), variants(type(10044, "Task"))));
  }

  // The id check wins over the name check, so a list holding both the same item and a same-named duplicate keeps
  // the selection rather than swapping it.
  public void testIdMatchWinsOverNameMatch() {
    ItemKey current = type(10050, "Task");
    ItemKey duplicate = type(10044, "Task");
    assertSame(current, MoveController.pickTypeForVariants(current, variants(duplicate, type(10050, "Task"))));
  }

  private static AListModel<ItemKey> variants(ItemKey... items) {
    return FixedListModel.create(items);
  }

  private static ItemKey type(long item, String name) {
    return new TestItemKey(item, name);
  }

  /** An ItemKey with a real item id; ItemKeyStub itself reports every instance as unresolved. */
  private static class TestItemKey extends ItemKeyStub {
    private final long myItem;

    private TestItemKey(long item, String name) {
      super(String.valueOf(item), name, ItemOrder.byString(name));
      myItem = item;
    }

    @Override
    public long getResolvedItem() {
      return myItem;
    }
  }

  /** Records every cube the configurator narrows by, in order. */
  private static class RecordingConfigurator extends EnumModelConfigurator {
    private final List<ItemHypercube> myCubes = Collections15.arrayList();

    private RecordingConfigurator(EditItemModel model) {
      super(model, new VariantsAcceptor<ItemKey>() {
        @Override
        public void accept(AListModel<? extends ItemKey> variants,
          Configuration recentConfig) {}
      });
    }

    @Override
    protected void collectCubeAttributes(HashSet<DBAttribute<Long>> attributes) {
      attributes.add(Issue.PROJECT);
    }

    @Override
    protected void updateVariants(Lifespan life, VariantsAcceptor<ItemKey> acceptor,
      AListModel<LoadedItemKey> variants, EditItemModel model, UserDataHolder data) {}

    @SuppressWarnings("unchecked")
    @Override
    protected AListModel<LoadedItemKey> getSortedVariantsModel(Lifespan life, EditItemModel model, ItemHypercube cube) {
      myCubes.add(cube);
      return (AListModel<LoadedItemKey>)AListModel.EMPTY;
    }
  }
}

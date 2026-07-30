package com.almworks.items.gui.edit.merge;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.gui.edit.CancelCommitException;
import com.almworks.items.gui.edit.CommitContext;
import com.almworks.items.gui.edit.EditItemModel;
import com.almworks.items.gui.edit.FieldEditor;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.VersionSource;
import com.almworks.items.sync.util.BranchSource;
import com.almworks.items.sync.util.ShadowVersionSource;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.LogHelper;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.Convertor;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.ui.actions.DataRole;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;

public abstract class MergeValue {
  public static final DataRole<MergeValue> ROLE = DataRole.createRole(MergeValue.class);
  public static final Convertor<? super MergeValue,String> GET_DISPLAY_NAME = new Convertor<MergeValue, String>() {
    @Override
    public String convert(MergeValue value) {
      return Util.NN(value != null ? value.getDisplayName() : null);
    }
  };

  public static final int LOCAL = 0;
  public static final int BASE = 1;
  public static final int REMOTE = 2;
  /** No side chosen yet. */
  public static final int NONE = -1;

  private final String myDisplayName;
  private final long myItem;

  protected MergeValue(String displayName, long item) {
    myDisplayName = displayName;
    myItem = item;
  }

  public final String getDisplayName() {
    return myDisplayName;
  }

  public final long getItem() {
    return myItem;
  }

  public abstract void render(CellState state, Canvas canvas, int version);

  public abstract void setResolution(int version);

  public abstract boolean isConflict();

  public abstract boolean isChanged(boolean remote);

  public abstract Object getValue(int version);

  public static <T> T loadValue(DBReader reader, long item, DBAttribute<T> attribute, int version) {
    ItemVersion itemVersion = getItemVersion(reader, item, version);
    return itemVersion != null ? itemVersion.getValue(attribute) : null;
  }

  public static ItemVersion getItemVersion(DBReader reader, long item, int version) {
    VersionSource source;
    switch (version) {
    case LOCAL: source = BranchSource.trunk(reader); break;
    case BASE: source = ShadowVersionSource.base(reader); break;
    case REMOTE: source = ShadowVersionSource.conflict(reader); break;
    default:
      LogHelper.error("Unknown version", version, item);
      return null;
    }
    return source.forItem(item);
  }

  private static ItemVersion trunkIfNull(ItemVersion version, DBReader reader, long item) {
    if (version == null) version = SyncUtils.readTrunk(reader, item);
    return version;
  }

  public static long getSingleItem(EditItemModel model) {
    LongList items = model.getEditingItems();
    if (items.size() != 1) {
      LogHelper.error("Single item supported", items);
      return 0;
    }
    return items.get(0);
  }

  /**
   * Whether this row is part of the merge at all.<br>
   * Deliberately independent of whether a side has been chosen: a chosen row stays listed and stays
   * actionable, so the choice can be changed as often as the user likes until the merge is committed.
   */
  public final boolean isChangeOrConflict() {
    return isConflict() || isChanged(true) || isChanged(false);
  }

  /**
   * @return true iff the value has resolution (initially no value has resolution even it is not affected by local or server changes)
   */
  public abstract boolean isResolved();

  /**
   * Which side the user chose, for showing the choice back to them.
   * @return {@link #NONE} when no side has been chosen, or when the resolution is not one of the three versions
   */
  public int getChosenVersion() {
    return NONE;
  }

  public abstract void addChangeListener(Lifespan life, ChangeListener listener);

  public abstract void commit(CommitContext context) throws CancelCommitException;


  /**
   * A value whose resolution is applied straight into the edit model, so the form below the merge table
   * shows the chosen side immediately.<br>
   * Choosing is repeatable: nothing is written to the database until the merge is committed, so a choice can
   * be replaced any number of times, and cancelling the merge discards all of them. What "resolved" means
   * here is only that the user has picked a side, which is what allows the merge to be uploaded or saved.
   */
  public static abstract class Simple extends MergeValue {
    private final SimpleModifiable myModifiable = new SimpleModifiable();
    private int myChosenVersion = NONE;
    private boolean myIgnored = false;

    protected Simple(String displayName, long item) {
      super(displayName, item);
    }

    @Override
    public final boolean isResolved() {
      return myChosenVersion != NONE || myIgnored;
    }

    @Override
    public final int getChosenVersion() {
      return myChosenVersion;
    }

    @Override
    public final void setResolution(int version) {
      doSetResolution(version);
      myChosenVersion = version;
      myIgnored = false;
      myModifiable.fireChanged();
    }

    @Override
    public final void addChangeListener(Lifespan life, ChangeListener listener) {
      myModifiable.addAWTChangeListener(life, listener);
    }

    /**
     * Records that the user has dealt with this row without taking any particular side, which is what
     * "Ignore Conflict" means: keep whatever the edit model already holds.
     */
    public final void markResolved() {
      myIgnored = true;
      myModifiable.fireChanged();
    }

    @Override
    public final void commit(CommitContext context) throws CancelCommitException {
      FieldEditor editor = getEditor();
      EditItemModel model = getModel();
      if (editor.hasDataToCommit(model)) editor.commit(context.subContext(model));
    }

    protected abstract void doSetResolution(int version);

    protected abstract FieldEditor getEditor();

    protected abstract EditItemModel getModel();
  }
}

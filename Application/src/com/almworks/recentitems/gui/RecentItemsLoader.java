package com.almworks.recentitems.gui;

import com.almworks.api.application.ApplicationLoadStatus;
import com.almworks.api.application.LoadedItem;
import com.almworks.api.engine.Connection;
import com.almworks.api.engine.ConnectionManager;
import com.almworks.api.engine.ConnectionState;
import com.almworks.api.engine.Engine;
import com.almworks.api.explorer.ItemModelRegistry;
import com.almworks.explorer.loader.LoadedItemImpl;
import com.almworks.integers.LongIterator;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBEvent;
import com.almworks.items.api.DBLiveQuery;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBReader;
import com.almworks.items.api.DBWriter;
import com.almworks.items.api.Database;
import com.almworks.items.api.WriteTransaction;
import com.almworks.items.util.SyncAttributes;
import com.almworks.items.wrapper.DatabaseUnwrapper;
import com.almworks.recentitems.RecentItemUtil;
import com.almworks.recentitems.RecentItemsService;
import com.almworks.recentitems.RecordType;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.FilteringListDecorator;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.advmodel.SortedListDecorator;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.ChangeListener1;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.commons.Condition;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.ModelUtils;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import com.almworks.util.properties.Role;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.DetachComposite;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.Startable;

import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecentItemsLoader implements Startable, DBLiveQuery.Listener {
  public static final Role<RecentItemsLoader> ROLE =
    Role.role("RecentItemsLoader", RecentItemsLoader.class);

  private final Database myDatabase;
  private final ApplicationLoadStatus myAppStatus;
  private final ItemModelRegistry myRegistry;
  private final Engine myEngine;

  private final DetachComposite myLife = new DetachComposite();
  private final SimpleModifiable myModifiable = new SimpleModifiable();
  private final OrderListModel<LoadedRecord> myLoadedModel = OrderListModel.create();

  private volatile boolean myProcessingEvent = false;
  private volatile boolean myFirstDbEvent = true;

  private final SortedListDecorator<LoadedRecord> mySortedModel = SortedListDecorator.create(
    myLife, myLoadedModel, new Comparator<LoadedRecord>() {
      @Override
      public int compare(LoadedRecord o1, LoadedRecord o2) {
        return o2.myTimestamp.compareTo(o1.myTimestamp);
      }
    }
  );

  private final FilteringListDecorator<LoadedRecord> myViewModel = FilteringListDecorator.create(
    myLife, mySortedModel,
    new Condition<LoadedRecord>() {
      @Override
      public boolean isAccepted(LoadedRecord value) {
        final LoadedItem item = value.myItem;
        return item != null && item.getConnection() != null && !item.services().isDeleted();
      }
    });

  public RecentItemsLoader(Database database, ApplicationLoadStatus appStatus, ItemModelRegistry registry, Engine engine) {
    myDatabase = database;
    myAppStatus = appStatus;
    myRegistry = registry;
    myEngine = engine;
  }

  @Override
  public void start() {
    myLife.add(new Detach() {
      @Override
      protected void doDetach() throws Exception {
        for(final LoadedRecord rec : myLoadedModel) {
          if(rec != null && rec.myLife != null) {
            rec.myLife.detach();
          }
        }
      }
    });
    
    ModelUtils.whenTrue(
      myAppStatus.getApplicationLoadedModel(), ThreadGate.STRAIGHT,
      new Runnable() {
        @Override
        public void run() {
          // Delay the live query until connections have settled (none still GETTING_READY). Otherwise the query's
          // first pass runs while connections are still STARTING and every recent record whose connection isn't yet
          // READY fails to resolve (LoadedItemServicesImpl.extractConnectionOrNull logs "Unknown connection ..." with
          // a stack trace and drops the item). Waiting for settled state lets those records resolve on the first pass;
          // items on genuinely unavailable connections are skipped as before (and hidden by myViewModel's filter).
          final ConnectionManager connectionManager = myEngine.getConnectionManager();
          connectionManager.whenConnectionsLoaded(myLife, ThreadGate.STRAIGHT, new Runnable() {
            @Override
            public void run() {
              whenConnectionsSettled(connectionManager, new Runnable() {
                @Override
                public void run() {
                  startLiveQuery();
                }
              });
            }
          });
        }
      });
  }

  private void startLiveQuery() {
    myDatabase.liveQuery(myLife, RecentItemsService.EXPR_RECORDS, RecentItemsLoader.this);
    myViewModel.addAWTChangeListener(myLife, new ChangeListener() {
      @Override
      public void onChange() {
        if(!myProcessingEvent) {
          myModifiable.fireChanged();
        }
      }
    });
  }

  /**
   * Runs {@code runnable} once every currently-loaded connection has left the {@link ConnectionState#isGettingReady()
   * getting-ready} state (READY, or a stable/degrading state such as STOPPED). Safe against offline/failed connections
   * since those settle into a non-getting-ready state rather than staying STARTING forever. Assumes the connection set
   * is complete (call from within {@link ConnectionManager#whenConnectionsLoaded}); startup does not add connections.
   */
  private void whenConnectionsSettled(final ConnectionManager connectionManager, final Runnable runnable) {
    if(allConnectionsSettled(connectionManager)) {
      runnable.run();
      return;
    }
    final DetachComposite listeners = new DetachComposite();
    myLife.add(listeners);
    final AtomicBoolean done = new AtomicBoolean(false);
    final Runnable check = new Runnable() {
      @Override
      public void run() {
        if(done.get() || !allConnectionsSettled(connectionManager)) {
          return;
        }
        if(done.compareAndSet(false, true)) {
          listeners.detach();
          runnable.run();
        }
      }
    };
    for(final Connection connection : connectionManager.getConnections().copyCurrent()) {
      connection.getState().getEventSource().addListener(listeners, ThreadGate.STRAIGHT,
        new ScalarModel.Adapter<ConnectionState>() {
          @Override
          public void onScalarChanged(ScalarModelEvent<ConnectionState> event) {
            check.run();
          }
        });
    }
    // Re-check in case a connection settled between the initial snapshot and attaching listeners.
    check.run();
  }

  private static boolean allConnectionsSettled(ConnectionManager connectionManager) {
    for(final Connection connection : connectionManager.getConnections().copyCurrent()) {
      final ConnectionState state = connection.getState().getValue();
      if(state == null || state.isGettingReady()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void stop() {
    myLife.detach();
  }

  @Override
  public void onICNPassed(long icn) {
  }

  @Override
  public void onDatabaseChanged(DBEvent event, DBReader reader) {
    final LongList addedLongs;
    if(myFirstDbEvent) {
      addedLongs = event.getAddedAndChangedSorted();
      myFirstDbEvent = false;
    } else {
      addedLongs = event.getAddedSorted();
    }

    final List<LoadedRecord> added = Collections15.linkedList();
    for(final LongIterator it = addedLongs.iterator(); it.hasNext();) {
      final LoadedRecord record = loadRecord(it.nextValue(), reader);
      if(record != null) {
        added.add(record);
      }
    }

    final LongList removed = event.getRemovedSorted();

    if(added.isEmpty() && removed.isEmpty()) {
      return;
    }

    ThreadGate.AWT.execute(new Runnable() {
      @Override
      public void run() {
        updateModel(added, removed);
      }
    });
  }

  @Nullable
  private LoadedRecord loadRecord(long record, DBReader reader) {
    final long item = reader.getValue(record, RecentItemsService.ATTR_MASTER);
    if(!RecentItemUtil.checkItem(item, reader)) {
      return null;
    }

    // The live query only starts after connections have settled (see start()), so an item whose connection matches no
    // live connection is a genuine orphan (e.g. its connection was removed but the item cleanup (AbstractConnection.
    // removeAllItems) didn't complete) rather than a startup race. Prune the dangling record so it stops re-firing
    // "Unknown connection" warnings from LoadedItemServicesImpl on every relevant DB event, and skip it.
    final Long connectionItem = reader.getValue(item, SyncAttributes.CONNECTION);
    if(connectionItem != null && connectionItem != 0L && !isKnownConnectionItem(connectionItem)) {
      pruneOrphanRecord(record, item, connectionItem);
      return null;
    }

    final Pair<LoadedItemImpl, DetachComposite> pair = loadItem(item, reader);
    if (pair == null) return null;
    final LoadedItem loaded = pair.getFirst();
    final DetachComposite recordLife = pair.getSecond();
    if(loaded == null) return null;

    setupViewModelResync(loaded, recordLife);

    return new LoadedRecord(
      new Date(reader.getValue(record, RecentItemsService.ATTR_TIMESTAMP)),
      loaded, recordLife,
      RecordType.forId(reader.getValue(record, RecentItemsService.ATTR_REC_TYPE)),
      record);
  }

  @Nullable
  private Pair<LoadedItemImpl, DetachComposite> loadItem(long key, DBReader reader) {
    final DetachComposite life = new DetachComposite();
    LoadedItemImpl loadedItem = LoadedItemImpl.createLive(life, myRegistry, key, reader);
    return loadedItem != null ? Pair.create(loadedItem, life) : null;
  }

  /** True if {@code connectionItem} belongs to a currently-known connection (READY, or a stable state such as STOPPED). */
  private boolean isKnownConnectionItem(long connectionItem) {
    final ConnectionManager connectionManager = myEngine.getConnectionManager();
    if(connectionManager.findByItem(connectionItem) != null) {
      return true; // READY fast-path, avoids touching not-yet-initialized connections
    }
    for(final Connection connection : connectionManager.getConnections().copyCurrent()) {
      // Skip connections still getting ready: their DB item may not be materialized yet, and getConnectionItem()
      // would log an error. Settled connections (incl. STOPPED that were once READY) report a stable item id.
      final ConnectionState state = connection.getState().getValue();
      if(state == null || state.isGettingReady()) {
        continue;
      }
      if(connection.getConnectionItem() == connectionItem) {
        return true;
      }
    }
    return false;
  }

  private void pruneOrphanRecord(final long record, final long masterItem, final long connectionItem) {
    Log.warn("RecentItems: pruning orphaned record " + record + " (item " + masterItem
      + ") referencing removed connection " + connectionItem);
    myDatabase.writeBackground(new WriteTransaction<Void>() {
      @Override
      public Void transaction(DBWriter writer) throws DBOperationCancelledException {
        DatabaseUnwrapper.clearItem(writer, record);
        return null;
      }
    });
  }

  private void setupViewModelResync(final LoadedItem item, DetachComposite recordLife) {
    final ChangeListener1<LoadedItem> listener = new ChangeListener1<LoadedItem>() {
      @Override
      public void onChange(LoadedItem object) {
        myViewModel.resynch();
      }
    };
    item.addAWTListener(listener);
    recordLife.add(new Detach() {
      @Override
      protected void doDetach() throws Exception {
        item.removeAWTListener(listener);
      }
    });
  }

  private void updateModel(List<LoadedRecord> added, LongList removed) {
    myProcessingEvent = true;
    try {
      for(final LongIterator itr = removed.iterator(); itr.hasNext();) {
        final long record = itr.nextValue();
        for(final Iterator<LoadedRecord> itl = myLoadedModel.iterator(); itl.hasNext();) {
          if(itl.next().myKey == record) {
            itl.remove();
            break;
          }
        }
      }
      myLoadedModel.addAll(added);
    } finally {
      myProcessingEvent = false;
    }
    myModifiable.fireChanged();
  }

  public Modifiable getModifiable() {
    return myModifiable;
  }

  public AListModel<LoadedRecord> getLoadedModel() {
    return myViewModel;
  }
}

package com.almworks.items.gui.edit.editors;

import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.gui.edit.EditItemModel;
import com.almworks.items.gui.edit.EditModelState;
import com.almworks.items.sync.EditPrepare;
import com.almworks.items.sync.VersionSource;
import com.almworks.util.collections.Convertor;
import gnu.trove.TLongObjectHashMap;
import org.almworks.util.TypedKey;

import java.util.List;

/**
 * Like {@link LoadAttribute}, but for a model whose editing items do not carry the wanted attribute themselves -
 * only a link to an item that does (for example a comment linking to its issue). Follows {@code link} off each
 * editing item, reads {@code attribute} off the linked item, and provides that value the same way
 * {@link LoadAttribute} would, so other editors (and enum narrowers) in the model can see it.
 */
public class LoadLinkedAttribute extends MockEditor {
  private final DBAttribute<Long> myLink;
  private final DBAttribute<Long> myAttribute;
  private final TypedKey<TLongObjectHashMap<Long>> myAllValues;
  private final Convertor<EditModelState, LongList> myConvertor;

  public LoadLinkedAttribute(DBAttribute<Long> link, DBAttribute<Long> attribute) {
    myLink = link;
    myAttribute = attribute;
    myAllValues = TypedKey.create(link.getName() + "/" + attribute.getName() + "/allValues");
    myConvertor = new Convertor<EditModelState, LongList>() {
      @Override
      public LongList convert(EditModelState model) {
        TLongObjectHashMap<Long> allValues = model.getValue(myAllValues);
        if (allValues == null || allValues.isEmpty()) return null;
        LongArray result = new LongArray();
        for (Object obj : allValues.getValues()) {
          Long value = (Long) obj;
          if (value == null || value <= 0) continue;
          result.add(value);
        }
        result.sortUnique();
        return result;
      }
    };
  }

  @Override
  public void prepareModel(VersionSource source, EditItemModel model, EditPrepare editPrepare) {
    LongList items = model.getEditingItems();
    List<Long> linked = source.collectValues(myLink, items);
    LongArray linkedItems = new LongArray();
    for (Long item : linked) if (item != null && item > 0) linkedItems.add(item);
    linkedItems.sortUnique();
    List<Long> linkedValues = source.collectValues(myAttribute, linkedItems);
    TLongObjectHashMap<Long> valueByLinkedItem = new TLongObjectHashMap<>();
    for (int i = 0; i < linkedItems.size(); i++) valueByLinkedItem.put(linkedItems.get(i), linkedValues.get(i));

    TLongObjectHashMap<Long> allValues = new TLongObjectHashMap<>();
    for (int i = 0; i < items.size(); i++) {
      Long linkedItem = linked.get(i);
      Long value = (linkedItem != null && linkedItem > 0) ? valueByLinkedItem.get(linkedItem) : null;
      if (value != null && value <= 0) value = null;
      allValues.put(items.get(i), value);
    }
    model.putHint(myAllValues, allValues);
    model.registerEditor(this);
    model.registerSingleEnum(myAttribute, myConvertor);
  }
}

package com.almworks.jira.provider3.gui.edit.editors.move;

import com.almworks.actions.console.VariantModelController;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.OrderListModel;
import org.almworks.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Filters a precomputed list of same-project candidate issues by the typed key prefix. The candidate list is
 * queried once (in {@link MoveParentEditor#prepareModel}) rather than re-queried per keystroke - projects synced
 * locally are small enough that in-memory prefix filtering is simpler and fast enough.
 */
class ParentSuggestionModel implements VariantModelController<ParentSuggestion> {
  private static final int MAX_SHOWN = 20;

  private final List<ParentSuggestion> myAll;
  private final OrderListModel<ParentSuggestion> myVariants = OrderListModel.create();

  ParentSuggestionModel(List<ParentSuggestion> all) {
    myAll = all;
  }

  @Override
  public void setText(String text) {
    String prefix = Util.NN(text).trim();
    List<ParentSuggestion> matches;
    if (prefix.isEmpty()) matches = Collections.emptyList();
    else {
      matches = new ArrayList<ParentSuggestion>(MAX_SHOWN);
      for (ParentSuggestion candidate : myAll) {
        if (candidate.getKey().regionMatches(true, 0, prefix, 0, prefix.length())) {
          matches.add(candidate);
          if (matches.size() >= MAX_SHOWN) break;
        }
      }
      // Once the field holds a complete, valid key (the sole match equals the text), there is nothing left to
      // suggest - drop it so the popup closes cleanly after a pick and doesn't re-open on the selected value.
      if (matches.size() == 1 && matches.get(0).getKey().equalsIgnoreCase(prefix)) matches = Collections.emptyList();
    }
    myVariants.setElements(matches);
  }

  @Override
  public AListModel<ParentSuggestion> getVariants() {
    return myVariants;
  }
}

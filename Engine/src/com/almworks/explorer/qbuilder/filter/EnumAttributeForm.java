package com.almworks.explorer.qbuilder.filter;

import com.almworks.api.application.ItemKey;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.components.ACheckboxList;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.SelectionAccessor;
import com.almworks.util.components.speedsearch.ListSpeedSearch;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import com.almworks.util.ui.UIUtil;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;

public class EnumAttributeForm {
    private ACheckboxList myEnum;
    private JButton myNone;
    private JButton myInvert;
    private JButton myAll;
    private JPanel myWholePanel;
    private JCheckBox myShowRelevant;
    private AListModel<ItemKey> myAllValues;
    private BasicScalarModel<AListModel<ItemKey>> myRelevantValues;
    private Boolean myShowingRelevant = null;

    public EnumAttributeForm(CanvasRenderer<ItemKey> variantsRenderer, boolean searchSubstring) {
        myEnum.setCanvasRenderer(variantsRenderer);
        ListSpeedSearch.install(myEnum).setSearchSubstring(searchSubstring);
        myAll.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                myEnum.getCheckedAccessor().selectAll();
                myEnum.requestFocusInWindow();
            }
        });
        myNone.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                myEnum.getCheckedAccessor().clearSelection();
                myEnum.requestFocusInWindow();
            }
        });
        myInvert.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                myEnum.getCheckedAccessor().invertSelection();
            }
        });
        clearModels();
    }

    public JComponent getComponent() {
        return myWholePanel;
    }

    public SelectionAccessor<ItemKey> getSelectionAccessor() {
        return myEnum.getCheckedAccessor();
    }

    public void scrollSelectionToView() {
        myEnum.scrollSelectionToView();
    }

    public void clearModels() {
        myShowRelevant.setSelected(true);
        myShowRelevant.setVisible(false);
        myAllValues = null;
        myRelevantValues = null;
        myShowingRelevant = null;
        myEnum.setCollectionModel(null);
    }

    public void setModels(final Lifespan lifespan, @NotNull AListModel<ItemKey> allValues,
                          BasicScalarModel<AListModel<ItemKey>> relevantValues) {
        myAllValues = allValues;
        myRelevantValues = relevantValues;
        myRelevantValues.getEventSource().addAWTListener(lifespan, new ScalarModel.Adapter<AListModel<ItemKey>>() {
            public void onScalarChanged(ScalarModelEvent<AListModel<ItemKey>> event) {
                if (lifespan.isEnded())
                    return;
                AListModel<ItemKey> model = event.getNewValue();
                boolean relevantDiffers = model != null && !equalModels(model, EnumAttributeForm.this.myAllValues);
                if (model == null)
                    model = myAllValues;
                if (myShowRelevant.isSelected()) {
                    myEnum.setCollectionModel(model, true);
                    myEnum.getSelectionAccessor().ensureSelectionExists();
                }
                myShowRelevant.setVisible(relevantDiffers);
            }
        });
        ActionListener listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                switchModel(myShowRelevant.isSelected());
            }
        };
        listener.actionPerformed(null);
        lifespan.add(UIUtil.addActionListener(myShowRelevant, listener));
        lifespan.add(myEnum.getClearModelDetach());
    }

    private boolean equalModels(AListModel<ItemKey> model1, AListModel<ItemKey> model2) {
        if (model1.getSize() != model2.getSize())
            return false;
        Set<ItemKey> set = Collections15.hashSet(model1.toList());
        set.removeAll(model2.toList());
        return set.size() == 0;
    }

    private void switchModel(boolean relevant) {
        if (myShowingRelevant == null || myShowingRelevant != relevant) {
            myShowingRelevant = relevant;
            AListModel<ItemKey> model = myShowingRelevant ? myRelevantValues.getValue() : myAllValues;
            if (model == null)
                model = myAllValues;
            myEnum.setCollectionModel(model, true);
            myEnum.getSelectionAccessor().ensureSelectionExists();
        }
    }

    public void setReadOnly() {
        myAll.setVisible(false);
        myInvert.setVisible(false);
        myNone.setVisible(false);
        myEnum.getScrollable().setEnabled(false);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        myWholePanel = new JPanel();
        myWholePanel.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
        myAll = new JButton();
        myAll.setText("All");
        myAll.setMnemonic('L');
        myAll.setDisplayedMnemonicIndex(1);
        myWholePanel.add(myAll, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myNone = new JButton();
        myNone.setText("None");
        myNone.setMnemonic('N');
        myNone.setDisplayedMnemonicIndex(0);
        myWholePanel.add(myNone, new com.intellij.uiDesigner.core.GridConstraints(1, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myInvert = new JButton();
        myInvert.setText("Invert");
        myInvert.setMnemonic('I');
        myInvert.setDisplayedMnemonicIndex(0);
        myWholePanel.add(myInvert, new com.intellij.uiDesigner.core.GridConstraints(2, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        myWholePanel.add(scrollPane1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 4, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(100, -1), null, null, 0, false));
        myEnum = new ACheckboxList();
        scrollPane1.setViewportView(myEnum);
        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        myWholePanel.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(3, 1, 2, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, 1, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myShowRelevant = new JCheckBox();
        myShowRelevant.setText("Hide irrelevant options");
        myShowRelevant.setMnemonic('H');
        myShowRelevant.setDisplayedMnemonicIndex(0);
        myWholePanel.add(myShowRelevant, new com.intellij.uiDesigner.core.GridConstraints(4, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myWholePanel;
    }
}
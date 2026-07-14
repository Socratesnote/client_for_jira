package com.almworks.export;

import com.almworks.util.AppBook;
import com.almworks.util.ErrorHunt;
import com.almworks.util.advmodel.AComboboxModel;
import com.almworks.util.advmodel.SelectionInListModel;
import com.almworks.util.advmodel.SelectionListener;
import com.almworks.util.components.AActionButton;
import com.almworks.util.components.AComboBox;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.i18n.LText;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.ui.UIComponentWrapper2;
import com.almworks.util.ui.UIComponentWrapper2Support;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.AnAction;
import com.almworks.util.ui.actions.EnableState;
import com.almworks.util.ui.actions.UpdateContext;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifecycle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.EditorKit;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;

class ExportForm extends UIComponentWrapper2Support {
    private static final String UNSELECTED_KEY = "---unselected---";
    private static final String X = "ExportForm.";
    private static final LText SELECT_EXPORTER = AppBook.text(X + "selectExporter", "Please select export type.");

    private AActionButton myExportAttributes;
    private AComboBox<Exporter> myExporters;
    private JPanel myWholePanel;
    private JEditorPane myExportDetails;
    private AActionButton myFormats;
    private JPanel myExporterParameters;
    private JLabel myExportersLabel;

    private final Lifecycle myLife = new Lifecycle();
    private final Lifecycle myExportersLife = new Lifecycle();
    private JScrollPane myExportDetailsScrollpane;

    public ExportForm() {
        AppBook.replaceText("ExportForm.", myWholePanel);
        myExporterParameters.setLayout(new CardLayout());
        myLife.lifespan().add(myExportersLife.getDisposeDetach());
        setupVisual();
    }

    private void setupVisual() {
        myExportDetails.setEditable(false);
        myExportDetailsScrollpane.setPreferredSize(UIUtil.getRelativeDimension(myExportDetails, 40, 7));
        Font font = myExportersLabel.getFont();
        if (font != null) {
            String family = font.getFamily();
            int size = font.getSize();
            EditorKit editorKit = myExportDetails.getEditorKit();
            if (editorKit instanceof HTMLEditorKit) {
                String stylesheet = "body { font-family: " + family + "; font-size: " + size + "pt }";
                ((HTMLEditorKit) editorKit).getStyleSheet().addRule(stylesheet);
            }
        } else
            assert false;

/*
    myExportDetails.setLineWrap(true);
    myExportDetails.setWrapStyleWord(true);
*/
        myExportersLabel.setLabelFor(myExporters);
    }

    public JComponent getComponent() {
        return myWholePanel;
    }

    public Detach getDetach() {
        return myLife.getAnyCycleDetach();
    }

    public void setExportDetails(String details) {
        ErrorHunt.setEditorPaneText(myExportDetails, details);
    }

    public void setExporters(final SelectionInListModel<Exporter> model) {
        myExportersLife.cycle();
        myExporters.setColumns(0);
        myExporters.setMaximumRowCount(5);
        myExporters.setModel(model);
        myExporters.setCanvasRenderer(new CanvasRenderer<Exporter>() {
            public void renderStateOn(CellState state, Canvas canvas, Exporter item) {
                String text = item == null ? "" : item.getDescriptor().getDisplayableName();
                canvas.appendText(text);
            }
        });
        myWholePanel.revalidate();
        myExporterParameters.removeAll();
        for (Exporter exporter : model.toList()) {
            UIComponentWrapper2 wrapper = exporter.getUI();
            myExporterParameters.add(wrapper.getComponent(), exporter.getDescriptor().getKey());
            myExportersLife.lifespan().add(wrapper.getDetach());
        }
        myExporterParameters.add(createUnselectedParametersPanel(), UNSELECTED_KEY);
        SelectionListener.SelectionOnlyAdapter listener = new SelectionListener.SelectionOnlyAdapter() {
            public void onSelectionChanged() {
                Exporter exporter = model.getSelectedItem();
                String key = exporter == null ? UNSELECTED_KEY : exporter.getDescriptor().getKey();
                ((CardLayout) myExporterParameters.getLayout()).show(myExporterParameters, key);
                String name = exporter == null ? "" : exporter.getDescriptor().getDisplayableName();
                ((TitledBorder) myExporterParameters.getBorder()).setTitle(name);
                myExporterParameters.repaint();
            }
        };
        model.addSelectionListener(myExportersLife.lifespan(), listener);
        listener.onSelectionChanged();
    }

    private static Component createUnselectedParametersPanel() {
        JLabel label = new JLabel(SELECT_EXPORTER.format());
        label.setAlignmentX(0.5F);
        label.setAlignmentY(0.5F);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setBorder(new EmptyBorder(19, 11, 19, 11));
        return label;
    }

    public Exporter getSelectedExporter() {
        return myExporters.getModel().getSelectedItem();
    }

    public void updateExportAction(UpdateContext context, StringBuffer errorBuffer) {
        AComboboxModel<Exporter> model = myExporters.getModel();
        context.updateOnChange(model);
        Exporter exporter = model.getSelectedItem();
        if (exporter == null) {
            context.setEnabled(EnableState.DISABLED);
            errorBuffer.append("Exporter is not set");
        } else {
            ScalarModel<String> errorModel = exporter.getUI().getFormErrorModel();
            context.updateOnChange(errorModel);
            String error = errorModel.getValue();
            context.setEnabled(error == null ? EnableState.ENABLED : EnableState.DISABLED);
            if (error != null)
                errorBuffer.append(error);
        }
    }

    public void setAttributesAction(AnAction action) {
        myExportAttributes.setAnAction(action);
    }

    public void setFormatsAction(AnAction action) {
        myFormats.setAnAction(action);
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
        myWholePanel.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(5, 1, new Insets(0, 0, 0, 0), -1, -1));
        myExportDetailsScrollpane = new JScrollPane();
        myWholePanel.add(myExportDetailsScrollpane, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myExportDetails = new JEditorPane();
        myExportDetails.setContentType("text/html");
        myExportDetails.setEditable(false);
        myExportDetailsScrollpane.setViewportView(myExportDetails);
        final JLabel label1 = new JLabel();
        label1.setText(":exportDetails");
        myWholePanel.add(label1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        myWholePanel.add(panel1, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myExportAttributes = new AActionButton();
        myExportAttributes.setText(":exportedAttributes");
        panel1.add(myExportAttributes, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myFormats = new AActionButton();
        myFormats.setText(":formats");
        panel1.add(myFormats, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        panel1.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        myWholePanel.add(panel2, new com.intellij.uiDesigner.core.GridConstraints(3, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myExportersLabel = new JLabel();
        myExportersLabel.setText(":exportTo");
        panel2.add(myExportersLabel, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myExporters = new AComboBox();
        panel2.add(myExporters, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer2 = new com.intellij.uiDesigner.core.Spacer();
        panel2.add(spacer2, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        myExporterParameters = new JPanel();
        myWholePanel.add(myExporterParameters, new com.intellij.uiDesigner.core.GridConstraints(4, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myExporterParameters.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Exporter", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myWholePanel;
    }
}

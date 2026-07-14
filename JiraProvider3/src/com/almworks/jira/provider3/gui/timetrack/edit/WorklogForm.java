package com.almworks.jira.provider3.gui.timetrack.edit;

import com.almworks.api.application.ItemKey;
import com.almworks.items.gui.edit.EditItemModel;
import com.almworks.items.gui.edit.editors.enums.single.DropdownEnumEditor;
import com.almworks.items.gui.edit.editors.scalar.SliderController;
import com.almworks.items.gui.edit.editors.scalar.TimeSpentEditor;
import com.almworks.items.gui.edit.editors.text.ScalarFieldEditor;
import com.almworks.jira.provider3.gui.edit.editors.VisibilityEditor;
import com.almworks.jira.provider3.gui.timetrack.RemainEstimateEditor;
import com.almworks.jira.provider3.schema.Group;
import com.almworks.jira.provider3.schema.ProjectRole;
import com.almworks.jira.provider3.schema.Worklog;
import com.almworks.util.components.AComboBox;
import com.almworks.util.components.ADateField;
import com.almworks.util.components.plaf.LAFUtil;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.speedsearch.TextSpeedSearch;
import com.almworks.util.config.Configuration;
import com.almworks.util.debug.DebugFrame;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.UIUtil;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import org.almworks.util.Const;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class WorklogForm {
    static final TimeSpentEditor TIME_SPENT = new TimeSpentEditor(NameMnemonic.parseString("&Time Spent"), Worklog.TIME_SECONDS, EditWorklogsFeature.DEFAULT_UNIT);
    static final StartTimeEditor START = new StartTimeEditor(NameMnemonic.parseString("&Started"), Worklog.STARTED, TIME_SPENT, Const.SECOND);
    static final ScalarFieldEditor<String> COMMENT = ScalarFieldEditor.textPane(NameMnemonic.parseString("Co&mment"), Worklog.COMMENT);
    static final AdjustmentEditor ADJUSTMENT = AdjustmentEditor.INSTANCE;
    static final DropdownEnumEditor VISIBILITY = VisibilityEditor.create(Worklog.SECURITY);

    private static final String SELECTED_SECURITY_GROUP_SETTING = "selectedSecurityGroup";
    private static final String LAST_COMMENT = "lastWorklog";
    private static final String LAST_TIMESPENT = "hoursSpent";
    private static final String DEFAULT_TIME_SPENT = "1h";

    private JPanel myWholePanel;
    private JTextField mySpentField;
    private JSlider mySliderSlider;
    private JRadioButton myAutoAdjust;
    private JRadioButton myDontAdjust;
    private JRadioButton mySetRemain;
    private JTextField myRemain;
    private JTextArea myWorkLog;
    private ADateField myStartTime;
    private AComboBox<ItemKey> myViewableBy;
    private JLabel myWorkDescriptionLabel;
    private JLabel myTimeSpentLabel;
    private JLabel myStartedLabel;
    private ButtonGroup myAdjustGroup;

    public WorklogForm(final Configuration config) {
        $$$setupUI$$$();
        myWholePanel.setBorder(UIUtil.BORDER_5);
        UIUtil.setDefaultLabelAlignment(myWholePanel);
        Aqua.disableMnemonics(myWholePanel);
        SliderController.setupSlider(mySliderSlider);
        TextSpeedSearch.installCtrlF(myWorkLog);
    }

    public JComponent getComponent() {
        return myWholePanel;
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                LAFUtil.initializeLookAndFeel();
                WorklogForm form = new WorklogForm(Configuration.EMPTY_CONFIGURATION);
                DebugFrame.show(form.myWholePanel);
            }
        });
    }

    private void createUIComponents() {
        // windows slider hack :/
        mySliderSlider = new JSlider() {
            @Override
            public Dimension getPreferredSize() {
                Dimension ps = super.getPreferredSize();
                return new Dimension(ps.width, ps.height + 18);
            }
        };

        myStartTime = new ADateField(ADateField.Precision.DATE_TIME);
    }

    public void attachComment(Lifespan life, EditItemModel model, ScalarFieldEditor<String> editor,
                              @Nullable Configuration config) {
        myWorkDescriptionLabel.setEnabled(true);
        myWorkLog.setEnabled(true);
        if (config != null)
            editor.syncValue(life, model, config, WorklogForm.LAST_COMMENT, "");
        editor.attachComponent(life, model, myWorkLog);
    }

    public void attachVisibility(Lifespan life, EditItemModel model, DropdownEnumEditor editor, @Nullable Configuration config) {
        myViewableBy.setEnabled(true);
        if (config != null)
            editor.syncValue(life, model, config, WorklogForm.SELECTED_SECURITY_GROUP_SETTING, null, ProjectRole.ENUM_TYPE, Group.ENUM_TYPE);
        editor.attachCombo(life, model, myViewableBy);
    }

    public void attachStart(Lifespan life, EditItemModel model, StartTimeEditor editor) {
        myStartedLabel.setEnabled(true);
        myStartTime.setEnabled(true);
        editor.attachField(life, model, myStartTime);
    }

    public void attachTimeSpent(Lifespan life, EditItemModel model, TimeSpentEditor editor, @Nullable Configuration config) {
        myTimeSpentLabel.setEnabled(true);
        mySpentField.setEnabled(true);
        mySliderSlider.setEnabled(true);
        if (config != null)
            editor.syncValue(life, model, config, WorklogForm.LAST_TIMESPENT, WorklogForm.DEFAULT_TIME_SPENT);
        editor.attach(life, model, mySpentField, mySliderSlider);
    }

    public void attachAdjustment(Lifespan life, EditItemModel model, RemainEstimateEditor estimate,
                                 RemainingAdjustmentEditor adjustment) {
        estimate.attachComponent(life, model, myRemain);
        adjustment.attach(life, model, myRemain, myAdjustGroup, myAutoAdjust, myDontAdjust, mySetRemain);
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        myWholePanel = new JPanel();
        myWholePanel.setLayout(new FormLayout("fill:d:noGrow,left:4dlu:noGrow,fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:max(d;40dlu):grow", "center:max(p;4px):noGrow,top:9dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):grow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow"));
        ((FormLayout) myWholePanel.getLayout()).setRowGroups(new int[][]{new int[]{3, 5, 9, 11, 13, 15}});
        myStartedLabel = new JLabel();
        myStartedLabel.setEnabled(false);
        myStartedLabel.setText("Started:");
        myStartedLabel.setDisplayedMnemonic('S');
        myStartedLabel.setDisplayedMnemonicIndex(0);
        CellConstraints cc = new CellConstraints();
        myWholePanel.add(myStartedLabel, cc.xy(1, 5));
        myStartTime.setColumns(10);
        myStartTime.setEnabled(false);
        myWholePanel.add(myStartTime, cc.xy(3, 5, CellConstraints.FILL, CellConstraints.DEFAULT));
        mySpentField = new JTextField();
        mySpentField.setColumns(10);
        mySpentField.setEditable(true);
        mySpentField.setEnabled(false);
        mySpentField.setHorizontalAlignment(11);
        myWholePanel.add(mySpentField, cc.xy(3, 3, CellConstraints.FILL, CellConstraints.DEFAULT));
        myTimeSpentLabel = new JLabel();
        myTimeSpentLabel.setEnabled(false);
        myTimeSpentLabel.setText("Time Spent:");
        myTimeSpentLabel.setDisplayedMnemonic('T');
        myTimeSpentLabel.setDisplayedMnemonicIndex(0);
        myWholePanel.add(myTimeSpentLabel, cc.xy(1, 3));
        myWorkDescriptionLabel = new JLabel();
        myWorkDescriptionLabel.setEnabled(false);
        myWorkDescriptionLabel.setText("Work Description:");
        myWorkDescriptionLabel.setDisplayedMnemonic('D');
        myWorkDescriptionLabel.setDisplayedMnemonicIndex(5);
        myWholePanel.add(myWorkDescriptionLabel, cc.xy(1, 7, CellConstraints.DEFAULT, CellConstraints.TOP));
        final JScrollPane scrollPane1 = new JScrollPane();
        myWholePanel.add(scrollPane1, cc.xyw(3, 7, 3, CellConstraints.FILL, CellConstraints.FILL));
        myWorkLog = new JTextArea();
        myWorkLog.setColumns(20);
        myWorkLog.setEnabled(false);
        myWorkLog.setLineWrap(true);
        myWorkLog.setRows(5);
        myWorkLog.setWrapStyleWord(true);
        scrollPane1.setViewportView(myWorkLog);
        final JLabel label1 = new JLabel();
        label1.setText("Visible To:");
        label1.setDisplayedMnemonic('V');
        label1.setDisplayedMnemonicIndex(0);
        myWholePanel.add(label1, cc.xy(1, 9));
        mySliderSlider.setEnabled(false);
        mySliderSlider.setPaintLabels(true);
        mySliderSlider.setPaintTicks(true);
        mySliderSlider.setPaintTrack(true);
        mySliderSlider.setPreferredSize(new Dimension(350, 32));
        mySliderSlider.setValueIsAdjusting(false);
        mySliderSlider.putClientProperty("JSlider.isFilled", Boolean.TRUE);
        mySliderSlider.putClientProperty("Slider.paintThumbArrowShape", Boolean.TRUE);
        mySliderSlider.putClientProperty("html.disable", Boolean.TRUE);
        myWholePanel.add(mySliderSlider, cc.xyw(3, 1, 3, CellConstraints.FILL, CellConstraints.DEFAULT));
        myViewableBy = new AComboBox();
        myViewableBy.setEnabled(false);
        myWholePanel.add(myViewableBy, cc.xy(3, 9));
        final JLabel label2 = new JLabel();
        label2.setText("Remaining Time:");
        myWholePanel.add(label2, cc.xy(1, 11));
        myAutoAdjust = new JRadioButton();
        myAutoAdjust.setSelected(true);
        myAutoAdjust.setText("Adjust Automatically");
        myAutoAdjust.setMnemonic('A');
        myAutoAdjust.setDisplayedMnemonicIndex(0);
        myWholePanel.add(myAutoAdjust, cc.xyw(3, 11, 3));
        myDontAdjust = new JRadioButton();
        myDontAdjust.setText("Do Not Change");
        myDontAdjust.setMnemonic('N');
        myDontAdjust.setDisplayedMnemonicIndex(3);
        myWholePanel.add(myDontAdjust, cc.xyw(3, 13, 3));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new FormLayout("fill:d:noGrow,fill:d:grow", "center:d:grow"));
        myWholePanel.add(panel1, cc.xy(3, 15, CellConstraints.LEFT, CellConstraints.DEFAULT));
        mySetRemain = new JRadioButton();
        mySetRemain.setText("Set to");
        mySetRemain.setMnemonic('E');
        mySetRemain.setDisplayedMnemonicIndex(1);
        panel1.add(mySetRemain, cc.xy(1, 1));
        myRemain = new JTextField();
        myRemain.setColumns(8);
        myRemain.setHorizontalAlignment(11);
        panel1.add(myRemain, cc.xy(2, 1, CellConstraints.FILL, CellConstraints.DEFAULT));
        myStartedLabel.setLabelFor(myStartTime);
        myTimeSpentLabel.setLabelFor(mySpentField);
        myWorkDescriptionLabel.setLabelFor(myWorkLog);
        label1.setLabelFor(myViewableBy);
        myAdjustGroup = new ButtonGroup();
        myAdjustGroup.add(myAutoAdjust);
        myAdjustGroup.add(myDontAdjust);
        myAdjustGroup.add(mySetRemain);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myWholePanel;
    }
}

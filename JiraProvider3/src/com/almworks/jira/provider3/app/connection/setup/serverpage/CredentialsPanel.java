package com.almworks.jira.provider3.app.connection.setup.serverpage;

import com.almworks.jira.provider3.app.connection.JiraConfiguration;
import com.almworks.restconnector.login.JiraLoginInfo;
import com.almworks.util.LogHelper;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.Convertors;
import com.almworks.util.collections.Equality;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.ALabel;
import com.almworks.util.components.ATextAreaWithExplanation;
import com.almworks.util.components.Link;
import com.almworks.util.components.OpenBrowserAction;
import com.almworks.util.components.completion.CompletingComboBox;
import com.almworks.util.components.completion.CompletingComboBoxController;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.ui.GlobalColors;
import com.almworks.util.ui.InlineLayout;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.swing.DocumentUtil;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

class CredentialsPanel {
    private static final UIUtil.Positioner POPUP_POSITIONER = new UIUtil.IndependentPositioner(UIUtil.ALIGN_START, UIUtil.NOTICE_POPUP_Y);

    private JPanel myWholePanel;
    private ALabel myHeader;
    private JLabel myHeaderComment;
    private CompletingComboBox<String> myUrl;
    private JTextField myUsername;
    private JPasswordField myPassword;
    private JCheckBox myAnonymous;
    private JPanel myMessagePlace;
    private Link myApiTokenLink;
    private Link myLearnMoreCloud;

    private final ATextAreaWithExplanation myInfo;
    private final ATextAreaWithExplanation myError = createErrorArea();

    private final CredentialsTracker myCredentialsTracker = new CredentialsTracker();
    private final ErrorMessage myErrorMessage;
    private boolean myHasError = false;

    public CredentialsPanel(boolean forNewConnection) {
        myInfo = createInfoArea();
        myCredentialsTracker.attachPassword(myPassword);
        myErrorMessage = new ErrorMessage(this);

        myHeaderComment.setVisible(forNewConnection);
        UrlPage.setupHeaderLabel(myHeader);
        UrlPage.fixHeaderComment(myHeaderComment, myWholePanel);
        myHeader.putClientProperty(UIUtil.SET_DEFAULT_LABEL_ALIGNMENT, false);
        myHeaderComment.putClientProperty(UIUtil.SET_DEFAULT_LABEL_ALIGNMENT, false);
        UIUtil.setDefaultLabelAlignment(myWholePanel);
        Aqua.disableMnemonics(myWholePanel);
        configureUrlFieldController();
        configureMessagePlace();

        UIUtil.addChangeListeners(Lifespan.FOREVER, e -> updateCredentialsState(), myAnonymous);


        showInfo(null, null);
        showError(null, null);
        myLearnMoreCloud.setAction(new OpenBrowserAction("https://wiki.almworks.com/display/kb/JIRA+Client+Jira+Cloud+Authentication", true, "How to connect to Jira Cloud"));
        myApiTokenLink.setAction(new OpenBrowserAction("https://id.atlassian.com/manage/api-tokens", true, "API Token:"));
    }

    public CompletingComboBox<String> getUrl() {
        return myUrl;
    }

    public JTextField getUsername() {
        return myUsername;
    }

    public JPasswordField getPassword() {
        return myPassword;
    }

    public JPanel getWholePanel() {
        return myWholePanel;
    }

    public ErrorMessage getErrorMessage() {
        return myErrorMessage;
    }

    public CredentialsTracker getCredentialsTracker() {
        return myCredentialsTracker;
    }

    public boolean isAnonymous() {
        return myAnonymous.isSelected();
    }

    public void loadValuesFromConfiguration(ReadonlyConfiguration config) {
        myUrl.setSelectedItem(Util.NN(JiraConfiguration.getBaseUrl(config)));
        JiraLoginInfo loginInfo = JiraConfiguration.getLoginInfo(config);
        if (loginInfo != null) {
            boolean anonymous = loginInfo.isAnonymous();
            String loginName = Util.NN(loginInfo.getLogin());
            myAnonymous.setSelected(anonymous);
            myUsername.setText(loginName);
            myPassword.setText(loginInfo.getPassword());
            myCredentialsTracker.reset(anonymous, loginName);
        } else LogHelper.error("Failed to restore login info");
    }

    public void showInfo(@Nullable String text, @Nullable String descr) {
        myInfo.setTextAndExplanation(text, descr);
        myInfo.setVisible(text != null);
    }

    private void showError(@Nullable String text, @Nullable String descr) {
        final ATextAreaWithExplanation error = myError;
        error.setTextAndExplanation(text, descr);
        myHasError = text != null;
        error.setVisible(myHasError);
    }

    public boolean hasError() {
        return myHasError;
    }

    public void addCredentialsListener(ChangeListener listener) {
        DocumentUtil.addChangeListener(Lifespan.FOREVER, myUsername.getDocument(), listener);
        DocumentUtil.addChangeListener(Lifespan.FOREVER, myPassword.getDocument(), listener);
        UIUtil.addChangeListener(Lifespan.FOREVER, listener, myAnonymous.getModel());
    }

    private void updateCredentialsState() {
        JCheckBox anonymous = myAnonymous;
        JTextField username = myUsername;
        JPasswordField password = myPassword;
        boolean isAnonymous = anonymous.isSelected();
        if (isAnonymous) {
            username.setEnabled(false);
            password.setEnabled(false);
        } else {
            username.setEnabled(true);
            password.setEnabled(true);
        }
    }

    private void configureMessagePlace() {
        final Border border = myUsername.getBorder();
        final JPanel messagePlace = myMessagePlace;
        if (border != null) {
            final Insets insets = border.getBorderInsets(myUsername);
            messagePlace.setBorder(
                    new EmptyBorder(UIUtil.getLineHeight(myUsername), insets.left, 0, insets.right));
        }
        InlineLayout layout = new InlineLayout(InlineLayout.VERTICAL);
        layout.setCalcInvisible(true);
        messagePlace.setLayout(layout);
        messagePlace.add(myInfo);
        messagePlace.add(myError);
    }

    private void configureUrlFieldController() {
        final CompletingComboBoxController<String> controller = myUrl.getController();
        controller.setFilterFactory(argument -> {
            final String typed = argument.toLowerCase();
            return new Condition<String>() {
                @Override
                public boolean isAccepted(String value) {
                    return value.toLowerCase().contains(typed);
                }
            };
        });
        controller.setConvertors(Convertors.identity(), Convertors.identity(), Equality.general());
        controller.setCanvasRenderer(Renderers.canvasToString());
        controller.setMaxMatchesToShow(10);
        controller.setMinCharsToShow(3);
    }

    @NotNull
    private static ATextAreaWithExplanation createErrorArea() {
        ATextAreaWithExplanation area = new ATextAreaWithExplanation();
        JTextArea errorArea = area.getMain();
        errorArea.setLineWrap(true);
        errorArea.setWrapStyleWord(true);
        errorArea.setOpaque(false);
        errorArea.setColumns(25);
        errorArea.setEditable(false);
        errorArea.setFont(errorArea.getFont().deriveFont(Font.BOLD));
        area.setTextForeground(GlobalColors.ERROR_COLOR);
        area.setPositioner(POPUP_POSITIONER);
        return area;
    }

    @NotNull
    private ATextAreaWithExplanation createInfoArea() {
        ATextAreaWithExplanation area = new ATextAreaWithExplanation();
        JTextArea textArea = area.getMain();
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setColumns(30);
        textArea.setEditable(false);
        area.setBorder(
                new EmptyBorder(0, 0, Math.round(UIUtil.getLineHeight(myUsername) / 4f), 0));
        area.setPositioner(POPUP_POSITIONER);
        return area;
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
        myWholePanel.setLayout(new FormLayout("fill:d:grow", "center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:10dlu:noGrow,center:max(d;4px):grow"));
        myHeader = new ALabel();
        myHeader.setText("Specify URL and Credentials");
        CellConstraints cc = new CellConstraints();
        myWholePanel.add(myHeader, cc.xy(1, 1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new BorderLayout(0, 0));
        myWholePanel.add(panel1, cc.xy(1, 5, CellConstraints.DEFAULT, CellConstraints.FILL));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new FormLayout("fill:d:noGrow,left:4dlu:noGrow,fill:max(d;4px):grow", "center:d:noGrow,top:3dlu:noGrow,center:max(d;4px):noGrow,top:3dlu:noGrow,center:d:noGrow,top:3dlu:noGrow,center:d:noGrow,top:3dlu:noGrow,center:d:noGrow,top:4dlu:noGrow,center:max(d;16dlu):grow"));
        ((FormLayout) panel2.getLayout()).setRowGroups(new int[][]{new int[]{1, 5, 7}});
        panel1.add(panel2, BorderLayout.CENTER);
        final JLabel label1 = new JLabel();
        label1.setText("URL:");
        label1.setDisplayedMnemonic('U');
        label1.setDisplayedMnemonicIndex(0);
        panel2.add(label1, cc.xy(1, 1));
        myUrl = new CompletingComboBox();
        myUrl.setEditable(true);
        panel2.add(myUrl, cc.xy(3, 1, CellConstraints.DEFAULT, CellConstraints.FILL));
        final JLabel label2 = new JLabel();
        label2.setText("Username:");
        label2.setDisplayedMnemonic('M');
        label2.setDisplayedMnemonicIndex(6);
        panel2.add(label2, cc.xy(1, 5));
        myUsername = new JTextField();
        myUsername.setColumns(20);
        panel2.add(myUsername, cc.xy(3, 5, CellConstraints.LEFT, CellConstraints.FILL));
        myPassword = new JPasswordField();
        myPassword.setColumns(20);
        panel2.add(myPassword, cc.xy(3, 7, CellConstraints.LEFT, CellConstraints.FILL));
        myAnonymous = new JCheckBox();
        myAnonymous.setText("Anonymous access");
        myAnonymous.setMnemonic('A');
        myAnonymous.setDisplayedMnemonicIndex(0);
        panel2.add(myAnonymous, cc.xy(3, 9, CellConstraints.DEFAULT, CellConstraints.FILL));
        myMessagePlace = new JPanel();
        myMessagePlace.setLayout(new FormLayout("", "center:d:grow"));
        panel2.add(myMessagePlace, cc.xy(3, 11, CellConstraints.DEFAULT, CellConstraints.TOP));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new FormLayout("left:max(d;4px):grow", "center:d:grow"));
        panel2.add(panel3, cc.xy(3, 3));
        myLearnMoreCloud = new Link();
        myLearnMoreCloud.setSelected(true);
        myLearnMoreCloud.setText("How to connect to Jira Cloud");
        panel3.add(myLearnMoreCloud, new CellConstraints(1, 1, 1, 1, CellConstraints.LEFT, CellConstraints.CENTER, new Insets(2, 0, 0, 0)));
        myApiTokenLink = new Link();
        myApiTokenLink.setText("API Token:");
        panel2.add(myApiTokenLink, cc.xy(1, 7));
        myHeaderComment = new JLabel();
        myHeaderComment.setText("Recommended for most users");
        myWholePanel.add(myHeaderComment, cc.xy(1, 3));
        label1.setLabelFor(myUrl);
        label2.setLabelFor(myUsername);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myWholePanel;
    }

    public static class CredentialsTracker implements ChangeListener {
        private boolean myAnonymous;
        private String myLoginName;
        private boolean myPasswordUpdated = false;

        @SuppressWarnings("SimplifiableIfStatement")
        public boolean isUpdated(String loginName) {
            if (myAnonymous) return true; // was anonymous
            if (!Util.equals(myLoginName, loginName)) return true; // login changed
            return myPasswordUpdated;
        }

        public void reset(boolean anonymous, String loginName) {
            myAnonymous = anonymous;
            myLoginName = loginName;
            myPasswordUpdated = false;
        }

        public void attachPassword(JPasswordField password) {
            UIUtil.addTextListener(Lifespan.FOREVER, password, this);
        }

        @Override
        public void onChange() {
            myPasswordUpdated = true;
        }
    }

    public static class ErrorMessage {
        private final CredentialsPanel myPanel;
        private String myUrlError;
        private String myCredentialsError;

        private ErrorMessage(CredentialsPanel panel) {
            myPanel = panel;
        }

        public void setUrlError(@Nullable String shortError) {
            myUrlError = shortError;
            updateError();
        }

        public void setCredentialsError(@Nullable String credentialsError) {
            myCredentialsError = credentialsError;
            updateError();
        }

        private void updateError() {
            String text;
            if (myUrlError != null) {
                text = myUrlError;
            } else if (myCredentialsError != null) {
                text = myCredentialsError;
            } else {
                text = null;
            }
            myPanel.showError(text, null);
        }
    }
}

package com.almworks.http.ui;

import com.almworks.api.container.ComponentContainer;
import com.almworks.api.gui.DialogBuilder;
import com.almworks.api.gui.DialogManager;
import com.almworks.api.http.auth.HttpAuthChallengeData;
import com.almworks.api.http.auth.HttpAuthCredentials;
import com.almworks.api.http.auth.HttpAuthDialog;
import com.almworks.api.http.auth.HttpAuthPersistOption;
import com.almworks.util.L;
import com.almworks.util.Pair;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.config.Configuration;
import com.almworks.util.config.ConfigurationUtil;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.ui.UIComponentWrapper;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.ActionContext;
import com.almworks.util.ui.actions.AnAbstractAction;
import org.almworks.util.Log;
import org.almworks.util.Util;

import javax.swing.*;
import java.awt.*;

public class HttpAuthDialogImpl implements UIComponentWrapper, HttpAuthDialog {
    private static final String OUR_HOST_LABEL = "<html>The server has requested HTTP authentication.<br>" + "<br>" +
            "These username and password you usually enter into web browser to get through to " +
            "the issue tracker web site.<br><br></html>";
    private static final String OUR_PROXY_LABEL = "<html>The server has requested HTTP PROXY authentication.<br>" +
            "<br>" + "These username and password you usually enter into web browser " + "to access proxy server." +
            "<br><br></html>";

    private JPanel myWholePanel;
    private JRadioButton myRememberInMemoryButton;
    private JRadioButton myAskEachTimeButton;
    private JRadioButton myRememberOnDiskButton;
    private JPasswordField myPassword;
    private JTextField myUsername;
    private JTextField myRealm;
    private JTextField myURL;

    private final ButtonGroup myRadioGroup;
    private final DialogManager myDialogManager;
    private final Configuration myConfiguration;

    private boolean myShowing = false;
    private JLabel myURLLabel;
    private JLabel myBannerLabel;
    private JLabel myUsernameLabel;
    private JLabel myPasswordLabel;

    public HttpAuthDialogImpl(DialogManager dialogManager, Configuration configuration) {
        myDialogManager = dialogManager;
        myConfiguration = configuration;

        myRadioGroup = new ButtonGroup();
        myRadioGroup.add(myRememberOnDiskButton);
        myRadioGroup.add(myRememberInMemoryButton);
        myRadioGroup.add(myAskEachTimeButton);
        UIUtil.setupButtonGroup(myRadioGroup, configuration, "persistOption");
        myUsernameLabel.setLabelFor(myUsername);
        myPasswordLabel.setLabelFor(myPassword);

        myBannerLabel.putClientProperty(UIUtil.SET_DEFAULT_LABEL_ALIGNMENT, false);
        UIUtil.setDefaultLabelAlignment(myWholePanel);
        Aqua.disableMnemonics(myWholePanel);
        if (Aqua.isAqua()) {
            myWholePanel.setBorder(UIUtil.BORDER_5);
        }
    }

    public static HttpAuthDialogImpl create(ComponentContainer provider) {
        return provider.instantiate(HttpAuthDialogImpl.class);
    }

    public void dispose() {
    }

    public JComponent getComponent() {
        return myWholePanel;
    }

    private Pair<HttpAuthCredentials, HttpAuthPersistOption> showModal(HttpAuthChallengeData data,
                                                                       HttpAuthCredentials failed, boolean proxy) {

        setData(data, failed, proxy);
        final BasicScalarModel<Boolean> myResult = BasicScalarModel.create(false, false);
        DialogBuilder builder = myDialogManager.createBuilder("httpAuth");
        builder.setOkAction(new AnAbstractAction(L.actionName("OK")) {
            public void perform(ActionContext context) {
                myResult.setValue(Boolean.TRUE);
            }
        });
        builder.setCancelAction(new AnAbstractAction(L.actionName("Cancel")) {
            public void perform(ActionContext context) {
                myResult.setValue(Boolean.FALSE);
            }
        });
        builder.setContent(this);
        builder.setModal(true);
        builder.setTitle(L.dialog("HTTP Authentication"));
        builder.setIgnoreStoredSize(true);
        builder.setInitialFocusOwner(myUsername);
        builder.showWindow();
        Boolean b = myResult.getValue();
        boolean yes = b != null && b.booleanValue();
        return yes ? getData() : null;
    }

    private Pair<HttpAuthCredentials, HttpAuthPersistOption> getData() {
        HttpAuthCredentials credentials = new HttpAuthCredentials(myUsername.getText(), myPassword.getText());
        HttpAuthPersistOption option = HttpAuthPersistOption.DONT_KEEP;
        if (myRememberInMemoryButton.isSelected())
            option = HttpAuthPersistOption.KEEP_IN_MEMORY;
        if (myRememberOnDiskButton.isSelected())
            option = HttpAuthPersistOption.KEEP_ON_DISK;
        return Pair.create(credentials, option);
    }

    private void setData(HttpAuthChallengeData data, HttpAuthCredentials failed, boolean proxy) {
        myURL.setText(proxy ? data.getHost() + ":" + data.getPort() : data.getHost());
        String scheme = Util.upper(Util.NN(data.getAuthScheme()));
        String realm = Util.NN(data.getRealm(), "N/A");
        myRealm.setText(realm + " (" + scheme + " Authentication)");
        myUsername.setText(failed == null ? "" : failed.getUsername());
        myPassword.setText(failed == null ? "" : failed.getPassword());
        myBannerLabel.setText(proxy ? OUR_PROXY_LABEL : OUR_HOST_LABEL);
        myURLLabel.setText(proxy ? "Proxy Server:" : "URL:");
        myWholePanel.revalidate();
    }

    public Pair<HttpAuthCredentials, HttpAuthPersistOption> show(HttpAuthChallengeData data, HttpAuthCredentials failed,
                                                                 boolean proxy) {
        if (myShowing) {
            HttpAuthDialogImpl temp = new HttpAuthDialogImpl(myDialogManager, ConfigurationUtil.copy(myConfiguration));
            if (temp.myShowing) {
                assert false;
                // wtf?
                Log.warn("HADI.show");
                return null;
            }
            return temp.show(data, failed, proxy);
        }
        myShowing = true;
        try {
            return showModal(data, failed, proxy);
        } finally {
            myShowing = false;
        }
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
        myWholePanel.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(9, 2, new Insets(0, 0, 0, 0), -1, -1));
        myURL = new JTextField();
        myURL.setEditable(false);
        myURL.setFocusable(false);
        myWholePanel.add(myURL, new com.intellij.uiDesigner.core.GridConstraints(1, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 20), new Dimension(150, -1), null, 0, false));
        myURLLabel = new JLabel();
        myURLLabel.setText("URL:");
        myWholePanel.add(myURLLabel, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Security Realm:");
        myWholePanel.add(label1, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myRealm = new JTextField();
        myRealm.setEditable(false);
        myRealm.setFocusable(false);
        myWholePanel.add(myRealm, new com.intellij.uiDesigner.core.GridConstraints(2, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 20), new Dimension(150, -1), null, 0, false));
        myUsername = new JTextField();
        myWholePanel.add(myUsername, new com.intellij.uiDesigner.core.GridConstraints(3, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 20), new Dimension(150, -1), null, 0, false));
        myPassword = new JPasswordField();
        myWholePanel.add(myPassword, new com.intellij.uiDesigner.core.GridConstraints(4, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 20), new Dimension(150, -1), null, 0, false));
        myPasswordLabel = new JLabel();
        myPasswordLabel.setText("Password:");
        myPasswordLabel.setDisplayedMnemonic('W');
        myPasswordLabel.setDisplayedMnemonicIndex(4);
        myWholePanel.add(myPasswordLabel, new com.intellij.uiDesigner.core.GridConstraints(4, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myUsernameLabel = new JLabel();
        myUsernameLabel.setText("Username:");
        myUsernameLabel.setDisplayedMnemonic('N');
        myUsernameLabel.setDisplayedMnemonicIndex(4);
        myWholePanel.add(myUsernameLabel, new com.intellij.uiDesigner.core.GridConstraints(3, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myRememberOnDiskButton = new JRadioButton();
        myRememberOnDiskButton.setHorizontalAlignment(10);
        myRememberOnDiskButton.setLabel("Remember password");
        myRememberOnDiskButton.setSelected(true);
        myRememberOnDiskButton.setText("Remember password");
        myRememberOnDiskButton.setMnemonic('R');
        myRememberOnDiskButton.setDisplayedMnemonicIndex(0);
        myWholePanel.add(myRememberOnDiskButton, new com.intellij.uiDesigner.core.GridConstraints(5, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 20), null, null, 0, false));
        myRememberInMemoryButton = new JRadioButton();
        myRememberInMemoryButton.setLabel("Remember password until application closes");
        myRememberInMemoryButton.setText("Remember password until application closes");
        myRememberInMemoryButton.setMnemonic('U');
        myRememberInMemoryButton.setDisplayedMnemonicIndex(18);
        myWholePanel.add(myRememberInMemoryButton, new com.intellij.uiDesigner.core.GridConstraints(6, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 20), null, null, 0, false));
        myAskEachTimeButton = new JRadioButton();
        myAskEachTimeButton.setLabel("Ask for a password each time synchronization is done");
        myAskEachTimeButton.setText("Ask for a password each time synchronization is done");
        myAskEachTimeButton.setMnemonic('A');
        myAskEachTimeButton.setDisplayedMnemonicIndex(0);
        myWholePanel.add(myAskEachTimeButton, new com.intellij.uiDesigner.core.GridConstraints(7, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 20), null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        myWholePanel.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(8, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, 1, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myBannerLabel = new JLabel();
        myBannerLabel.setText("<html>The server has requested HTTP authentication.<br><br>The username and password required by HTTP auth may be different from your Bugzilla username and password. These are credentials that you usually enter into web browser to get through to the Bugzilla site.<br><br></html>");
        myBannerLabel.setVerticalAlignment(1);
        myBannerLabel.setVerticalTextPosition(0);
        myWholePanel.add(myBannerLabel, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 2, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 80), new Dimension(400, 80), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myWholePanel;
    }
}

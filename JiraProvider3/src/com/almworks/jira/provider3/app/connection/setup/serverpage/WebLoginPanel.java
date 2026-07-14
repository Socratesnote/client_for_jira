package com.almworks.jira.provider3.app.connection.setup.serverpage;

import com.almworks.gui.Wizard;
import com.almworks.jira.provider3.app.connection.JiraConfiguration;
import com.almworks.jira.provider3.app.connection.JiraConnection3;
import com.almworks.jira.provider3.app.connection.setup.JiraConnectionWizard;
import com.almworks.jira.provider3.app.connection.setup.ServerConfig;
import com.almworks.jira.provider3.app.connection.setup.weblogin.ReLogin;
import com.almworks.jira.provider3.app.connection.setup.weblogin.ServerFilter;
import com.almworks.jira.provider3.app.connection.setup.weblogin.WebLoginConfig;
import com.almworks.jira.provider3.app.connection.setup.weblogin.WebLoginParams;
import com.almworks.restconnector.CookieJiraCredentials;
import com.almworks.spi.provider.wizard.ConnectionWizard;
import com.almworks.util.components.ALabel;
import com.almworks.util.components.URLLink;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.config.Configuration;
import com.almworks.util.i18n.text.LocalizedAccessor;
import com.almworks.util.ui.UIUtil;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import org.almworks.util.Util;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

class WebLoginPanel implements UrlPage.PagePanel {
    private static final LocalizedAccessor I18N = UrlPage.I18N;

    private final UrlPage myUrlPage;
    private JPanel myWholePanel;
    private ALabel myWebLoginHeader;
    private JButton myWebLoginButton;
    private JLabel myWebComment;
    private JPanel myConnectionInfoPanel;
    private JTextField myConnectionURL;
    private JTextField myConnectionAccount;
    private URLLink myLearMore;

    public WebLoginPanel(UrlPage urlPage) {
        myUrlPage = urlPage;
        UrlPage.setupHeaderLabel(myWebLoginHeader);
        myWebLoginHeader.putClientProperty(UIUtil.SET_DEFAULT_LABEL_ALIGNMENT, false);
        myWebComment.putClientProperty(UIUtil.SET_DEFAULT_LABEL_ALIGNMENT, false);

        Configuration config = urlPage.getWizard().getWizardConfig();
        TitledBorder titledBorder = new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED), I18N.getString("panel.connectionDetails.border"));
        myConnectionInfoPanel.setBorder(new CompoundBorder(titledBorder, new EmptyBorder(5, 20, 5, 20)));
        myLearMore.setUrlText(I18N.getString("panel.learnMore.text"));
        if (JiraConfiguration.getWebLogin(config) == null || urlPage.isNewConnection()) {
            UrlPage.fixHeaderComment(myWebComment, myWholePanel);
            myWebComment.setText(I18N.getString("panel.comment.newConnection"));
            myConnectionInfoPanel.setVisible(false);
            myLearMore.setUrl(I18N.getString("panel.learnMore.url.newConnection"));
        } else {
            myWebComment.setText(I18N.messageStr("panel.comment.editConnection").formatMessage(Wizard.NEXT_TEXT));
            myConnectionInfoPanel.setVisible(true);
            myConnectionURL.setText(JiraConfiguration.getBaseUrl(config));
            String displayName = JiraConfiguration.getDisplayName(config);
            if (displayName == null) {
                displayName = "Anonymous";
                myConnectionAccount.setFont(myConnectionAccount.getFont().deriveFont(Font.ITALIC));
            } else myConnectionAccount.setFont(myConnectionAccount.getFont().deriveFont(Font.PLAIN));
            myConnectionAccount.setText(displayName);
            myLearMore.setUrl(I18N.getString("panel.learnMore.url.editConnection"));
        }
        myWebLoginButton.addActionListener(e -> {
            openBrowser(urlPage);
        });
        UIUtil.setDefaultLabelAlignment(myWholePanel);
        Aqua.disableMnemonics(myWholePanel);
    }

    public static void openBrowser(UrlPage urlPage) {
        Configuration wizardConfig = urlPage.getWizard().getWizardConfig();
        WebLoginConfig webLogin = JiraConfiguration.getWebLogin(wizardConfig);
        String baseUrl = JiraConfiguration.getBaseUrl(wizardConfig);
        String initialUrl;
        if (baseUrl != null && !baseUrl.trim().isEmpty()) initialUrl = baseUrl;
        else initialUrl = urlPage.getRawUrlValue();
        JiraConnection3 editedConnection = Util.castNullable(JiraConnection3.class, urlPage.getWizard().getEditedConnection());
        String purpose = editedConnection == null ? "Configure new connection" : "Edit connection '" + editedConnection.getName() + "'";
        String configuredUrl = editedConnection != null ? JiraConfiguration.getBaseUrl(wizardConfig) : null;
        ServerFilter serverFilter = configuredUrl == null ? null
                : new ServerFilter().checkUrl(configuredUrl, ReLogin.WRONG_JIRA);
        new WebLoginParams(urlPage.getWebLoginDependencies(), urlPage.getWizard().isIgnoreProxy(), purpose)
                .setWindowTitle(I18N.getString(editedConnection != null ? "browser.window.title.edit" : "browser.window.title.new"))
                .initialUrl(initialUrl)
                .config(webLogin)
                .serverFilter(serverFilter)
                .setConnectHint(I18N.getString("browser.canConnect.popup"))
                .setConnectPopup(server -> server.getAccountId() != null)
                .showBrowser(
                        window -> {
                            if (window != null) urlPage.getWizard().getWindowController().hide();
                        },
                        login -> {
                            urlPage.getWizard().getWindowController().show();
                            ServerConfig serverConfig = login != null ? login.getServerConfig() : null;
                            if (serverConfig != null) {
                                urlPage.getWizard().setExtConfig(serverConfig);
                                urlPage.getWizard().showPage(ConnectionWizard.TEST_PAGE_ID);
                            }
                        });

    }

    public JPanel getWholePanel() {
        return myWholePanel;
    }

    public void onAboutToDisplay() {
        JiraConnectionWizard wizard = myUrlPage.getWizard();
        if (wizard.getEditedConnection() == null) return;
        Configuration config = wizard.getWizardConfig();
        WebLoginConfig webLogin = JiraConfiguration.getWebLogin(config);
        String accountId = JiraConfiguration.getAccountId(config);
        String baseUrl = JiraConfiguration.getBaseUrl(config);
        if (webLogin == null || baseUrl == null) {
            myUrlPage.setServerConfig(null, UrlPage.M_WEB_LOGIN);
        } else {
            CookieJiraCredentials credentials = CookieJiraCredentials.connected(accountId, webLogin.getCookies().getAllCookies(), null, null, null, null);
            ServerConfig serverConfig = new ServerConfig(baseUrl, credentials, wizard.isIgnoreProxy(), true);
            myUrlPage.setServerConfig(serverConfig, UrlPage.M_WEB_LOGIN);
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
        myWholePanel.setLayout(new FormLayout("fill:d:grow", "center:d:noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:15dlu:noGrow,center:max(d;4px):noGrow,top:10dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:grow,center:max(d;4px):noGrow"));
        myWholePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        myWebLoginHeader = new ALabel();
        myWebLoginHeader.setText("Connect with Web Browser");
        CellConstraints cc = new CellConstraints();
        myWholePanel.add(myWebLoginHeader, cc.xy(1, 1));
        myWebLoginButton = new JButton();
        myWebLoginButton.setText("Open Browser");
        myWebLoginButton.setMnemonic('B');
        myWebLoginButton.setDisplayedMnemonicIndex(5);
        myWebLoginButton.setVerticalAlignment(0);
        myWholePanel.add(myWebLoginButton, cc.xy(1, 9, CellConstraints.CENTER, CellConstraints.DEFAULT));
        myWebComment = new JLabel();
        myWebComment.setText("If you cannot connect with credentials");
        myWholePanel.add(myWebComment, cc.xy(1, 3));
        final JLabel label1 = new JLabel();
        label1.setIcon(new ImageIcon(getClass().getResource("/com/almworks/jira/provider3/app/connection/setup/weblogin_logo.png")));
        label1.setText("");
        myWholePanel.add(label1, cc.xy(1, 7, CellConstraints.CENTER, CellConstraints.DEFAULT));
        myConnectionInfoPanel = new JPanel();
        myConnectionInfoPanel.setLayout(new FormLayout("fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:d:grow", "center:d:grow,top:4dlu:noGrow,center:d:grow"));
        myWholePanel.add(myConnectionInfoPanel, cc.xy(1, 5, CellConstraints.FILL, CellConstraints.DEFAULT));
        final JLabel label2 = new JLabel();
        label2.setText("Jira URL:");
        myConnectionInfoPanel.add(label2, cc.xy(1, 1));
        final JLabel label3 = new JLabel();
        label3.setText("Account:");
        myConnectionInfoPanel.add(label3, cc.xy(1, 3));
        myConnectionURL = new JTextField();
        myConnectionURL.setEditable(false);
        myConnectionInfoPanel.add(myConnectionURL, cc.xy(3, 1, CellConstraints.FILL, CellConstraints.DEFAULT));
        myConnectionAccount = new JTextField();
        myConnectionAccount.setEditable(false);
        myConnectionInfoPanel.add(myConnectionAccount, cc.xy(3, 3, CellConstraints.FILL, CellConstraints.DEFAULT));
        myLearMore = new URLLink();
        myLearMore.setText("Learn more");
        myLearMore.setUnderlined(true);
        myLearMore.setUrl("https://wiki.almworks.com/display/jc16/Connect+with+Web+Browser");
        myLearMore.setUrlText("Learn more");
        myWholePanel.add(myLearMore, cc.xy(1, 11, CellConstraints.RIGHT, CellConstraints.DEFAULT));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myWholePanel;
    }
}

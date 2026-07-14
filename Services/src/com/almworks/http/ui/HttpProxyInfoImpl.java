package com.almworks.http.ui;

import com.almworks.api.gui.DialogBuilder;
import com.almworks.api.gui.DialogManager;
import com.almworks.api.http.HttpProxyInfo;
import com.almworks.util.WeakEncryption;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.commons.Lazy;
import com.almworks.util.config.Configuration;
import com.almworks.util.ui.actions.ActionContext;
import com.almworks.util.ui.actions.AnActionListener;
import com.almworks.util.ui.actions.CantPerformException;
import org.almworks.util.Util;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.GeneralSecurityException;

public class HttpProxyInfoImpl extends SimpleModifiable implements HttpProxyInfo {
  private final Configuration myConfiguration;

  private static final String USE_PROXY = "useProxy";
  private static final String PROXY_HOST = "proxyHost";
  private static final String PROXY_PORT = "proxyPort";
  private static final String USE_PROXY_AUTH = "proxyAuth";
  private static final String PROXY_USER = "proxyUser";
  private static final String PROXY_PASSWORD = "proxyPassword";

  private Lazy<Form> myForm = new Lazy<Form>() {
    @NotNull
    public Form instantiate() {
      return new Form();
    }
  };

  public HttpProxyInfoImpl(Configuration configuration) {
    myConfiguration = configuration;
  }

  public void editProxySettings(ActionContext context) throws CantPerformException {
    DialogManager manager = context.getSourceObject(DialogManager.ROLE);
    DialogBuilder builder = manager.createBuilder("proxyInfo");
    builder.setTitle("Configure Proxy");
    builder.setContent(myForm.get().myWholePanel);
    builder.setEmptyCancelAction();
    builder.setEmptyOkAction();
    builder.addOkListener(new AnActionListener() {
      public void perform(ActionContext context) throws CantPerformException {
        changeSettings();
      }
    });
    builder.setModal(true);

    myForm.get().setValues(this);
    builder.showWindow();
  }

  public String getProxyUser() {
    return myConfiguration.getSetting(PROXY_USER, null);
  }

  public String getProxyPassword() {
    String setting = myConfiguration.getSetting(PROXY_PASSWORD, null);
    if (setting == null || setting.trim().length() == 0)
      return null;
    try {
      return WeakEncryption.decryptString(setting);
    } catch (GeneralSecurityException e) {
      return null;
    }
  }

  public Modifiable getModifiable() {
    return this;
  }

  private void changeSettings() {
    Form form = myForm.get();
    myConfiguration.setSetting(PROXY_USER, Util.NN(form.myUsername.getText()));
    myConfiguration.setSetting(PROXY_PASSWORD, getPassword(form));
    myConfiguration.setSetting(USE_PROXY_AUTH, form.myAuthRequired.isSelected());
    myConfiguration.setSetting(PROXY_HOST, Util.NN(form.myHost.getText()));
    myConfiguration.setSetting(PROXY_PORT, getPort(form));
    myConfiguration.setSetting(USE_PROXY, form.myUseProxyButton.isSelected());
    fireChanged();
  }

  private String getPassword(Form form) {
    String plain = Util.NN(form.myPassword.getText());
    if (plain.length() == 0)
      return "";
    else
      return WeakEncryption.encryptString(plain);
  }

  private String getPort(Form form) {
    String port = form.myPort.getText();
    int nport;
    try {
      nport = Integer.parseInt(port);
    } catch (NumberFormatException e) {
      nport = -1;
    }
    if (nport <= 0 || nport > 65536)
      nport = -1;
    return port;
  }

  public boolean isUsingProxy() {
    return myConfiguration.getBooleanSetting(USE_PROXY, false);
  }

  public boolean isAuthenticatedProxy() {
    return myConfiguration.getBooleanSetting(USE_PROXY_AUTH, false);
  }

  public String getProxyHost() {
    return myConfiguration.getSetting(PROXY_HOST, null);
  }

  public int getProxyPort() {
    return myConfiguration.getIntegerSetting(PROXY_PORT, -1);
  }


    static class Form implements ActionListener {
      private JPanel myWholePanel;
      private JTextField myPort;
      private JTextField myHost;
      private JCheckBox myUseProxyButton;
      private JCheckBox myAuthRequired;
      private JTextField myUsername;
      private JPasswordField myPassword;
      private JLabel myHostLabel;
      private JLabel myPortLabel;
      private JLabel myUsernameLabel;
      private JLabel myPasswordLabel;

      public Form() {
          myWholePanel.setBorder(new EmptyBorder(9, 9, 9, 9));
          myUseProxyButton.addActionListener(this);
          myAuthRequired.addActionListener(this);
          enableControls();
          setupVisual();
      }

      public void actionPerformed(ActionEvent e) {
          enableControls();
      }

      private void setupVisual() {
          myHostLabel.setLabelFor(myHost);
          myPortLabel.setLabelFor(myPort);
          myUsernameLabel.setLabelFor(myUsername);
          myPasswordLabel.setLabelFor(myPassword);
      }

      private void enableControls() {
          boolean use = myUseProxyButton.isSelected();
          myHost.setEnabled(use);
          myPort.setEnabled(use);
          myAuthRequired.setEnabled(use);
          boolean auth = use && myAuthRequired.isSelected();
          myUsername.setEnabled(auth);
          myPassword.setEnabled(auth);
      }

      void setValues(HttpProxyInfo info) {
          myHost.setText(info.getProxyHost());
          int port = info.getProxyPort();
          myPort.setText(port > 0 ? Integer.toString(port) : "");
          myUseProxyButton.setSelected(info.isUsingProxy());
          myUsername.setText(Util.NN(info.getProxyUser()));
          myPassword.setText(Util.NN(info.getProxyPassword()));
          myAuthRequired.setSelected(info.isAuthenticatedProxy());
          enableControls();
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
            myWholePanel.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(8, 2, new Insets(0, 0, 0, 0), -1, -1));
            final JLabel label1 = new JLabel();
            label1.setText("<html>Configure default proxy settings for HTTP connections:<br>");
            myWholePanel.add(label1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 2, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            myUseProxyButton = new JCheckBox();
            myUseProxyButton.setText("Use HTTP Proxy");
            myUseProxyButton.setMnemonic('U');
            myUseProxyButton.setDisplayedMnemonicIndex(0);
            myWholePanel.add(myUseProxyButton, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 2, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            myAuthRequired = new JCheckBox();
            myAuthRequired.setText("Proxy Server Requires Authentication");
            myAuthRequired.setMnemonic('A');
            myAuthRequired.setDisplayedMnemonicIndex(22);
            myWholePanel.add(myAuthRequired, new com.intellij.uiDesigner.core.GridConstraints(5, 0, 1, 2, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
            myWholePanel.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(7, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, 1, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
            final com.intellij.uiDesigner.core.Spacer spacer2 = new com.intellij.uiDesigner.core.Spacer();
            myWholePanel.add(spacer2, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, 1, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 9), null, 0, false));
            final com.intellij.uiDesigner.core.Spacer spacer3 = new com.intellij.uiDesigner.core.Spacer();
            myWholePanel.add(spacer3, new com.intellij.uiDesigner.core.GridConstraints(4, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, 1, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 9), null, 0, false));
            final JPanel panel1 = new JPanel();
            panel1.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 5, new Insets(0, 0, 0, 0), 2, -1));
            myWholePanel.add(panel1, new com.intellij.uiDesigner.core.GridConstraints(3, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
            myHostLabel = new JLabel();
            myHostLabel.setText("Host:");
            myHostLabel.setDisplayedMnemonic('H');
            myHostLabel.setDisplayedMnemonicIndex(0);
            panel1.add(myHostLabel, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_EAST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            myHost = new JTextField();
            panel1.add(myHost, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
            myPortLabel = new JLabel();
            myPortLabel.setText("Port:");
            myPortLabel.setDisplayedMnemonic('P');
            myPortLabel.setDisplayedMnemonicIndex(0);
            panel1.add(myPortLabel, new com.intellij.uiDesigner.core.GridConstraints(0, 3, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_EAST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            myPort = new JTextField();
            myPort.setColumns(8);
            panel1.add(myPort, new com.intellij.uiDesigner.core.GridConstraints(0, 4, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            final com.intellij.uiDesigner.core.Spacer spacer4 = new com.intellij.uiDesigner.core.Spacer();
            panel1.add(spacer4, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, 1, null, new Dimension(9, -1), null, 0, false));
            final JPanel panel2 = new JPanel();
            panel2.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 5, new Insets(0, 0, 0, 0), 2, -1));
            myWholePanel.add(panel2, new com.intellij.uiDesigner.core.GridConstraints(6, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
            myUsername = new JTextField();
            panel2.add(myUsername, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            myPasswordLabel = new JLabel();
            myPasswordLabel.setText("Password:");
            myPasswordLabel.setDisplayedMnemonic('W');
            myPasswordLabel.setDisplayedMnemonicIndex(4);
            panel2.add(myPasswordLabel, new com.intellij.uiDesigner.core.GridConstraints(0, 3, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            myPassword = new JPasswordField();
            panel2.add(myPassword, new com.intellij.uiDesigner.core.GridConstraints(0, 4, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            final com.intellij.uiDesigner.core.Spacer spacer5 = new com.intellij.uiDesigner.core.Spacer();
            panel2.add(spacer5, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, 1, null, new Dimension(9, -1), null, 0, false));
            myUsernameLabel = new JLabel();
            myUsernameLabel.setText("Username:");
            myUsernameLabel.setDisplayedMnemonic('N');
            myUsernameLabel.setDisplayedMnemonicIndex(4);
            panel2.add(myUsernameLabel, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            final com.intellij.uiDesigner.core.Spacer spacer6 = new com.intellij.uiDesigner.core.Spacer();
            myWholePanel.add(spacer6, new com.intellij.uiDesigner.core.GridConstraints(6, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, 1, null, new Dimension(21, -1), null, 0, false));
        }

        /**
         * @noinspection ALL
         */
        public JComponent $$$getRootComponent$$$() {
            return myWholePanel;
        }
    }
}

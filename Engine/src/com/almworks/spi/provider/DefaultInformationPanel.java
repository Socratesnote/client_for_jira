package com.almworks.spi.provider;

import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.Connection;
import com.almworks.api.engine.ConnectionState;
import com.almworks.api.engine.ConnectionViews;
import com.almworks.api.engine.InitializationState;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.platform.ProductInformation;
import com.almworks.items.api.DBEvent;
import com.almworks.items.api.DBFilter;
import com.almworks.items.api.DBLiveQuery;
import com.almworks.items.api.DBReader;
import com.almworks.util.AppBook;
import com.almworks.util.English;
import com.almworks.util.Terms;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.components.ALabel;
import com.almworks.util.components.Link;
import com.almworks.util.components.ScrollablePanel;
import com.almworks.util.components.URLLink;
import com.almworks.util.components.plaf.LinkUI;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.files.FileActions;
import com.almworks.util.i18n.LText2;
import com.almworks.util.i18n.Local;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import com.almworks.util.ui.DocumentFormAugmentor;
import com.almworks.util.ui.UIComponentWrapper;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.*;
import com.almworks.util.ui.swing.AwtUtil;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import org.almworks.util.StringUtil;
import org.almworks.util.detach.DetachComposite;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.Locale;

public abstract class DefaultInformationPanel implements UIComponentWrapper {
  private static final String PREFIX = "InformationPanel.";
  private static final LText2<Long, Long> REPORT = AppBook.text(PREFIX + "REPORT",
    "<html><body>{0,number,######} total<br>" +
    "{1,number,######} modified", 0L, 0L);

  private final DetachComposite myDetach = new DetachComposite();

  private final JScrollPane myWholePanel = new JScrollPane() {
    public void addNotify() {
      super.addNotify();
      attach();
    }
  };

  protected Form myForm;
  protected final Connection myConnection;
  private final ScalarModel<?> myConfigurationModel;

  public DefaultInformationPanel(Connection connection, ScalarModel<?> configurationModel) {
    myConnection = connection;
    myConfigurationModel = configurationModel;
  }

  public Connection getConnection() {
    return myConnection;
  }

  protected void setupForm() {
    WorkArea workArea = myConnection.getContext().getContainer().getActor(WorkArea.APPLICATION_WORK_AREA);
    File workspaceDir = workArea != null ? workArea.getRootDir() : null;
    myForm = new Form(workspaceDir);
    JPanel p = myForm.myFormPanel;
    p.setAlignmentX(0F);
    p.setAlignmentY(0F);
    p.setBorder(new EmptyBorder(19, 19, 19, 19));
    ScrollablePanel scrollable = new ScrollablePanel(p);
    new DocumentFormAugmentor().augmentForm(myDetach, scrollable, true);
    myWholePanel.setViewportView(scrollable);
    Aqua.setLightNorthBorder(myWholePanel);
    Aero.cleanScrollPaneBorder(myWholePanel);
  }

  protected void attach() {
    myForm.calculateStats();
    final Modifiable modifiable = getConnectionModifiable(myDetach);
    modifiable.addAWTChangeListener(myDetach, new ChangeListener() {
      @Override
      public void onChange() {
        myForm.updateInfo(getConnectionInfo());
      }
    });
     myForm.updateInfo(getConnectionInfo());
  }

  public void dispose() {
    myDetach.detach();
  }

  public JComponent getComponent() {
    return myWholePanel;
  }

  protected Modifiable getConnectionModifiable(Lifespan life) {
    final SimpleModifiable modifiable = new SimpleModifiable();
    ((ScalarModel<Object>) myConfigurationModel).getEventSource().addAWTListener(life, new ScalarModel.Adapter<Object>() {
      @Override
      public void onScalarChanged(ScalarModelEvent<Object> event) {
        modifiable.fireChanged();
      }
    });
    return modifiable;
  }

  protected abstract ConnectionInfo getConnectionInfo();

  protected static UIComponentWrapper getLazyWrapper(
    final ComponentContainer container,
    final Class<? extends DefaultInformationPanel> clazz)
  {
    return new LazyWrapper() {
      protected UIComponentWrapper initialize() {
        return container.instantiate(clazz);
      }
    };
  }

  protected String extractStatus() {
    ProductInformation pi = Context.require(ProductInformation.class);
    return "";
//    final ConnectionSyncMode syncMode = myConnection.getContext().getSyncMode();
//    if(syncMode.isSyncDenied()) {
//      return "The connection is offline, synchronization is disabled.";
//    } else if(Boolean.TRUE.equals(syncMode.isLightMode())) {
//      return "The connection is subject to " + pi.getName() + " limitations.";
//    } else if(syncMode.isUploadDenied()) {
//      return Local.parse("The connection is read-only, no changes to " + Terms.ref_artifacts + " are allowed.");
//    } else {
//      return "The connection is online.";
//    }
  }

    class Form {
      private JLabel myStateValue;
      private JPanel myFormPanel;
      private JLabel myBugsLabel;
      private JLabel myProductsLabel;
      private JLabel myLoginLabel;
      private ALabel myNameLabel;
      private URLLink myUrlLink;
      private JLabel myLoginValue;
      private ALabel myBugsValue;
      private ALabel myProductsValue;
      private JLabel myStatusValue;
      private Link myWorkspaceUrl;

      public Form(final File workspaceDir) {
          setupFonts();
          setupAlignments();
          listenConnection();
          if (workspaceDir == null) myWorkspaceUrl.setVisible(false);
          else {
              myWorkspaceUrl.setDisabledLook(LinkUI.NormalPaint.createDefault());
              myWorkspaceUrl.setPresentationMapping(Action.SHORT_DESCRIPTION, PresentationMapping.GET_SHORT_DESCRIPTION);
              myWorkspaceUrl.setAnAction(new SimpleAction() {
                  @Override
                  protected void customUpdate(UpdateContext context) throws CantPerformException {
                      context.setEnabled(FileActions.isSupported(FileActions.Action.OPEN_CONTAINING_FOLDER));
                      context.putPresentationProperty(PresentationKey.NAME, workspaceDir.getAbsolutePath());
                  }

                  @Override
                  protected void doPerform(ActionContext context) throws CantPerformException {
                      FileActions.openContainingFolder(workspaceDir, context.getComponent());
                  }
              });
          }
      }

      public void updateInfo(ConnectionInfo info) {
          myNameLabel.setText(info.connectionName);
          myUrlLink.setUrl(info.connectionUrl, false);
          myStatusValue.setText(info.status);
          myLoginLabel.setText(info.loginName);
          myLoginValue.setText(info.loginValue);
          if (info.productsName != null) {
              myProductsLabel.setText(info.productsName);
              myProductsValue.setText("<html>" + StringUtil.implode(info.productsValue, "<br>"));
          }
          myBugsLabel.setText(Local.text(Terms.key_Artifacts) + ":");
      }

      public void calculateStats() {
          final ConnectionViews views = myConnection.getViews();
          final DBFilter total = views.getConnectionItems();
          final DBFilter changed = views.getOutbox();

          final DBLiveQuery.Listener updater = new DBLiveQuery.Listener() {
              @Override
              public void onICNPassed(long icn) {
              }

              @Override
              public void onDatabaseChanged(DBEvent event, DBReader reader) {
                  final long totalCount = total.query(reader).count();
                  final long changedCount = changed.query(reader).count();
                  ThreadGate.AWT.execute(new Runnable() {
                      @Override
                      public void run() {
                          myBugsValue.setText(Local.parse(REPORT.format(totalCount, changedCount)));
                      }
                  });
              }
          };

          total.liveQuery(myDetach, updater);
          changed.liveQuery(myDetach, updater);
      }

      private void listenConnection() {
          final ScalarModel.Consumer updater = new ScalarModel.Adapter() {
              @Override
              public void onScalarChanged(ScalarModelEvent objectScalarModelEvent) {
                  final ConnectionState cState = myConnection.getState().getValue();
                  final InitializationState iState = myConnection.getInitializationState().getValue();
                  myStateValue.setText(getStateText(cState, iState));
              }
          };
          myConnection.getState().getEventSource().addAWTListener(myDetach, updater);
          myConnection.getInitializationState().getEventSource().addAWTListener(myDetach, updater);
      }

      private String getStateText(ConnectionState cState, InitializationState iState) {
          final String text;
          if (cState == null || iState == null) {
              text = "";
          } else if (cState.isDegrading()) {
              text = cState.getName();
          } else if (!iState.isInitialized()) {
              text = iState.getName();
          } else if (iState == InitializationState.REINITIALIZING) {
              text = iState.getName();
          } else if (iState == InitializationState.REINITIALIZATION_REQUIRED && cState == ConnectionState.READY) {
              text = cState.getName() + ", " + iState;
          } else {
              text = cState.getName();
          }
          return English.humanizeEnumerable(text);
      }

      private void setupFonts() {
          myNameLabel.setBorder(UIUtil.createSouthBevel(AwtUtil.getPanelBackground()));
          UIUtil.adjustFont(myNameLabel, 1.35F, Font.BOLD, true);
      }

      private void setupAlignments() {
          myUrlLink.setAlignmentY(0f);
          myWorkspaceUrl.setHorizontalAlignment(SwingConstants.LEADING);
          protect(myNameLabel, myStatusValue, myLoginValue, myProductsValue, myBugsValue, myStateValue, myWorkspaceUrl);
          UIUtil.setDefaultLabelAlignment(myFormPanel);
      }

      private void protect(JComponent... labels) {
          for (final JComponent c : labels) {
              c.putClientProperty(UIUtil.SET_DEFAULT_LABEL_ALIGNMENT, false);
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
            myFormPanel = new JPanel();
            myFormPanel.setLayout(new FormLayout("fill:max(d;4px):noGrow,left:8dlu:noGrow,fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:d:grow", "top:max(d;4px):noGrow,top:3dlu:noGrow,center:max(d;4px):noGrow,top:6dlu:noGrow,top:max(d;4px):noGrow,top:4dlu:noGrow,top:max(d;4px):noGrow,top:3dlu:noGrow,top:max(d;4px):noGrow,top:3dlu:noGrow,top:max(d;4px):noGrow,top:3dlu:noGrow,top:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:d:grow"));
            final JLabel label1 = new JLabel();
            Font label1Font = this.$$$getFont$$$(null, -1, -1, label1.getFont());
            if (label1Font != null) label1.setFont(label1Font);
            label1.setText("State:");
            CellConstraints cc = new CellConstraints();
            myFormPanel.add(label1, cc.xy(1, 13));
            myStateValue = new JLabel();
            Font myStateValueFont = this.$$$getFont$$$(null, Font.PLAIN, -1, myStateValue.getFont());
            if (myStateValueFont != null) myStateValue.setFont(myStateValueFont);
            myStateValue.setText("Connection State");
            myFormPanel.add(myStateValue, cc.xy(3, 13, CellConstraints.LEFT, CellConstraints.DEFAULT));
            final JLabel label2 = new JLabel();
            label2.setText("URL:");
            myFormPanel.add(label2, cc.xy(1, 5));
            myLoginLabel = new JLabel();
            myLoginLabel.setText("Login:");
            myFormPanel.add(myLoginLabel, cc.xy(1, 7));
            myProductsLabel = new JLabel();
            myProductsLabel.setText("Products:");
            myFormPanel.add(myProductsLabel, cc.xy(1, 9));
            myBugsLabel = new JLabel();
            myBugsLabel.setText("Bugs:");
            myFormPanel.add(myBugsLabel, cc.xy(1, 11));
            myNameLabel = new ALabel();
            myNameLabel.setText("Name");
            myFormPanel.add(myNameLabel, cc.xyw(1, 1, 5));
            myUrlLink = new URLLink();
            Font myUrlLinkFont = this.$$$getFont$$$(null, Font.PLAIN, -1, myUrlLink.getFont());
            if (myUrlLinkFont != null) myUrlLink.setFont(myUrlLinkFont);
            myFormPanel.add(myUrlLink, cc.xy(3, 5, CellConstraints.LEFT, CellConstraints.DEFAULT));
            myLoginValue = new JLabel();
            Font myLoginValueFont = this.$$$getFont$$$(null, Font.PLAIN, -1, myLoginValue.getFont());
            if (myLoginValueFont != null) myLoginValue.setFont(myLoginValueFont);
            myLoginValue.setText("Label");
            myFormPanel.add(myLoginValue, cc.xy(3, 7, CellConstraints.LEFT, CellConstraints.DEFAULT));
            myBugsValue = new ALabel();
            Font myBugsValueFont = this.$$$getFont$$$(null, Font.PLAIN, -1, myBugsValue.getFont());
            if (myBugsValueFont != null) myBugsValue.setFont(myBugsValueFont);
            myFormPanel.add(myBugsValue, cc.xy(3, 11, CellConstraints.LEFT, CellConstraints.DEFAULT));
            myProductsValue = new ALabel();
            Font myProductsValueFont = this.$$$getFont$$$(null, Font.PLAIN, -1, myProductsValue.getFont());
            if (myProductsValueFont != null) myProductsValue.setFont(myProductsValueFont);
            myFormPanel.add(myProductsValue, cc.xy(3, 9, CellConstraints.LEFT, CellConstraints.DEFAULT));
            myStatusValue = new JLabel();
            Font myStatusValueFont = this.$$$getFont$$$(null, Font.PLAIN, -1, myStatusValue.getFont());
            if (myStatusValueFont != null) myStatusValue.setFont(myStatusValueFont);
            myStatusValue.setText("Label");
            myFormPanel.add(myStatusValue, cc.xyw(1, 3, 5));
            final JLabel label3 = new JLabel();
            label3.setText("Workspace:");
            myFormPanel.add(label3, cc.xy(1, 15));
            myWorkspaceUrl = new Link();
            Font myWorkspaceUrlFont = this.$$$getFont$$$(null, Font.PLAIN, -1, myWorkspaceUrl.getFont());
            if (myWorkspaceUrlFont != null) myWorkspaceUrl.setFont(myWorkspaceUrlFont);
            myFormPanel.add(myWorkspaceUrl, cc.xyw(3, 15, 3, CellConstraints.DEFAULT, CellConstraints.CENTER));
            final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
            myFormPanel.add(spacer1, cc.xy(5, 17, CellConstraints.DEFAULT, CellConstraints.FILL));
        }

        /**
         * @noinspection ALL
         */
        private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
            if (currentFont == null) return null;
            String resultName;
            if (fontName == null) {
                resultName = currentFont.getName();
            } else {
                Font testFont = new Font(fontName, Font.PLAIN, 10);
                if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                    resultName = fontName;
                } else {
                    resultName = currentFont.getName();
                }
            }
            Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
            boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
            Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
            return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
        }

        /**
         * @noinspection ALL
         */
        public JComponent $$$getRootComponent$$$() {
            return myFormPanel;
        }
    }

  protected static class ConnectionInfo {
    public final String connectionName;
    public final String connectionUrl;
    public final String status;
    public final String loginName;
    public final String loginValue;
    public final String productsName;
    public final Collection<String> productsValue;

    public ConnectionInfo(
      String connectionName, String connectionUrl, String status,
      String loginName, String loginValue,
      String productsName, Collection<String> productsValue)
    {
      this.connectionName = connectionName;
      this.connectionUrl = connectionUrl;
      this.status = status;
      this.loginName = loginName;
      this.loginValue = loginValue;
      this.productsName = productsName;
      this.productsValue = productsValue;
    }
  }
}

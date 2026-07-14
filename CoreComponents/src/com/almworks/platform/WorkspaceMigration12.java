package com.almworks.platform;

import com.almworks.api.install.Setup;
import com.almworks.util.files.FileUtil;
import com.almworks.util.ui.UIUtil;
import org.almworks.util.ExceptionUtil;
import org.almworks.util.Failure;
import org.almworks.util.Log;
import org.almworks.util.RuntimeInterruptedException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.prefs.Preferences;

public class WorkspaceMigration12 {
    private static final String PREFERENCE = "workarea.migrate12.dontask";

    private JTextField myCurrentWorkspace;
    private JTextField myNewWorkspace;
    private JCheckBox myDontAsk;
    private JPanel myWholePanel;

    private final File myDir10;
    private final File myDir12;
    private static final String TITLE = "Workspace Migration [1.2]";
    private final JProgressBar myProgress = new JProgressBar(0, 300);

    private JDialog myProgressDialog;
    private Frame myParent;

    private float myLastProgressTarget = 0F;
    private float myLastProgressSource = 0F;

    public WorkspaceMigration12(File dir10, File dir12) {
        myDir10 = dir10;
        myDir12 = dir12;
        myDontAsk.setSelected(true);
        myCurrentWorkspace.setText(dir10.getAbsolutePath());
        myNewWorkspace.setText(dir12.getAbsolutePath());
    }

    boolean run() {
        setupParent();
        try {
            int option = askUser();
            if (option == JOptionPane.YES_OPTION) {
                Log.debug("migrating workspace from " + myDir10 + " to " + myDir12);
                doMigrate();
                saveDontAsk();
                return true;
            } else if (option == JOptionPane.NO_OPTION) {
                Log.debug("workspace is not migrated");
                saveDontAsk();
                return false;
            } else {
                throw new RuntimeException("Startup cancelled");
            }
        } finally {
            myProgressDialog = null;
            myParent = null;
        }
    }

    private int askUser() {
        JOptionPane pane = new JOptionPane(myWholePanel, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION);
        JDialog dialog = pane.createDialog(myParent, TITLE);
        pane.selectInitialValue();
        UIUtil.centerWindow(dialog);
        dialog.show();
        dialog.dispose();
        Object selectedValue = pane.getValue();
        int option = selectedValue instanceof Integer ? (Integer) selectedValue : JOptionPane.CLOSED_OPTION;
        return option;
    }

    private void setupParent() {
        if (myParent == null) {
            myParent = JOptionPane.getRootFrame();
        }
    }

    private void saveDontAsk() {
        Preferences preferences = Setup.getUserPreferences();
        if (myDontAsk.isSelected()) {
            preferences.putBoolean(PREFERENCE, true);
        }
    }

    private void doMigrate() {
        boolean success = false;
        try {
            showMigrationDialog();

            setProgressTarget(0.05F);
            createTargetDir();

            setProgressTarget(0.10F);
            migrateConfigFile();

            setProgressTarget(0.45F);
            createSubdirCopy(Setup.DIR_WORKSPACE_DATABASE);

            setProgressTarget(0.60F);
            createSubdirCopy(Setup.DIR_WORKSPACE_STATE);

            setProgressTarget(0.75F);
            createSubdirCopy(Setup.DIR_WORKSPACE_UPLOAD);

            setProgressTarget(0.90F);
            createSubdirCopy(Setup.DIR_WORKSPACE_DOWNLOAD);

            setProgressTarget(0.95F);
            migrateEtc();

            setProgressTarget(1F);
            renameOldWorkspace();

            setProgress(1F);

            Setup.workspaceMigrated(myDir12);
            success = true;

        } catch (InterruptedException e) {
            throw new RuntimeInterruptedException(e);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(myParent,
                    "<html>Workspace migration failed.<br>" + e.getMessage() + "<br>The application will now exit.",
                    "Failed - " + TITLE, JOptionPane.ERROR_MESSAGE);
            throw new Failure("Migration Failed: ", e);
        } finally {
            try {
                disposeMigrateionDialog();
            } catch (Exception e) {
                // ignore
            }
            if (!success) {
                try {
                    renameFailedWorkspace();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private void renameFailedWorkspace() throws IOException, InterruptedException {
        File failedDir = renameToSuffixed(myDir12, ".failed");
        FileUtil.writeFile(new File(failedDir, "README"),
                "This directory is the result of a failed " + Setup.getProductName() + "'s attempt\n" +
                        "to move workspace from " + myDir10 + " to " + myDir12 + "\n" +
                        "You can delete this directory.\n");
    }

    private void disposeMigrateionDialog() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    JDialog dialog = myProgressDialog;
                    if (dialog != null) {
                        dialog.dispose();
                        myProgressDialog = null;
                    }
                }
            });
        } catch (Exception e) {
            // ignore
        }
    }

    private void setProgressTarget(float target) {
        assert target > myLastProgressTarget;
        setProgress(myLastProgressTarget);
        myLastProgressSource = myLastProgressTarget;
        myLastProgressTarget = target;
    }

    private void setProgress(float progress) {
        if (progress < 0)
            progress = 0F;
        else if (progress > 1)
            progress = 1F;
        final int value = myProgress.getMinimum() + (int) (progress * (myProgress.getMaximum() - myProgress.getMinimum()));
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                myProgress.setValue(value);
            }
        });
    }

    private void setSubProgress(float subprogress) {
        setProgress(subprogress * (myLastProgressTarget - myLastProgressSource) + myLastProgressSource);
    }

    private void showMigrationDialog() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    JPanel panel = new JPanel(new BorderLayout(0, 5));
                    panel.add(new JLabel("Copying workspace\u2026"), BorderLayout.NORTH);
                    panel.add(myProgress, BorderLayout.CENTER);
                    panel.setBorder(new EmptyBorder(9, 9, 9, 9));

                    myProgressDialog = new JDialog(myParent, TITLE, false);
                    myProgressDialog.getContentPane().add(panel);
                    myProgressDialog.pack();
                    UIUtil.centerWindow(myProgressDialog);
                    myProgressDialog.show();
                }
            });
        } catch (InterruptedException e) {
            throw new RuntimeInterruptedException(e);
        } catch (InvocationTargetException e) {
            ExceptionUtil.rethrow(e.getTargetException());
        }
    }

    private void renameOldWorkspace() throws IOException, InterruptedException {
        File renameTo = renameToSuffixed(myDir10, ".migrated");
        if (renameTo == null)
            return;
        File readme = new File(renameTo, "README");
        FileUtil.writeFile(readme,
                "This workspace has been moved to: " + myDir12.getAbsolutePath() + "\nYou can remove this directory.\n");
    }

    private File renameToSuffixed(File file, String defaultSuffix) throws IOException, InterruptedException {
        File home = file.getParentFile();
        if (file.getParentFile() == null) {
            assert false : file;
            return null;
        }
        String name = file.getName();
        File renameTo;
        int movedCount = 0;
        while (true) {
            String suffix = defaultSuffix;
            if (movedCount > 0)
                suffix += "-" + movedCount;
            renameTo = new File(home, name + suffix);
            if (!renameTo.exists()) {
                break;
            }
            movedCount = movedCount + 1 + (movedCount % 10) * (WorkspaceMigration12.class.hashCode() % 17);
            if (movedCount > 9999) {
                throw new IOException("cannot rename " + file);
            }
        }

        renameTo(file, renameTo);
        return renameTo;
    }

    private void renameTo(File source, File target) throws IOException, InterruptedException {
        Log.debug("renaming " + source + " to " + target);
        FileUtil.renameTo(source, target, 6);
    }

    private void migrateEtc() throws IOException, InterruptedException {
        FileUtil.mkdir(new File(myDir12, Setup.DIR_ETC), 6);
    }

    private void createSubdirCopy(String subdir) throws IOException, InterruptedException {
        File oldDb = new File(myDir10, subdir);
        File newDb = new File(myDir12, subdir);
        assert !newDb.exists();
        FileUtil.mkdir(newDb, 6);
        if (oldDb.isDirectory()) {
            File[] files = oldDb.listFiles();
            if (files != null && files.length > 0) {
                float step = 1F / files.length;
                int i = 0;
                for (File file : files) {
                    if (file.isFile()) {
                        File newFile = new File(newDb, file.getName());
                        copyFile(file, newFile);
                    }
                    setSubProgress(step * ++i);
                }
            }
        } else {
            assert !oldDb.exists();
        }
    }

    private void migrateConfigFile() throws IOException {
        File oldConfig = new File(myDir10, Setup.FILE_WORKSPACE_CONFIG);
        File newConfig = new File(myDir12, Setup.FILE_WORKSPACE_CONFIG);
        if (oldConfig.isFile()) {
            copyFile(oldConfig, newConfig);
        } else {
            // strange! what to do?
        }
    }

    private void copyFile(File oldConfig, File newConfig) throws IOException {
        Log.debug("copying " + oldConfig + " to " + newConfig);
        FileUtil.copyFile(oldConfig, newConfig);
    }

    private void createTargetDir() throws IOException, InterruptedException {
        FileUtil.mkdir(myDir12, 6);
        setSubProgress(1F);
    }

    public static boolean migrate(final File dir10, final File dir12) {
        Preferences preferences = Setup.getUserPreferences();
        boolean dontask = preferences.getBoolean(PREFERENCE, false);
        if (dontask)
            return false;
        if (!dir10.isDirectory()) {
            assert false : dir10;
            return false;
        }
        final boolean result[] = {false};
        result[0] = new WorkspaceMigration12(dir10, dir12).run();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                }
            });
        } catch (InvocationTargetException e) {
            ExceptionUtil.rethrow(e.getTargetException());
        } catch (Exception e) {
            throw new Failure(e);
        }
        return result[0];
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
        myWholePanel.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(7, 2, new Insets(0, 0, 0, 0), -1, -1));
        final JLabel label1 = new JLabel();
        label1.setText("<html>Starting from Deskzilla 1.2, Deskzilla workspace by default is located in the user's<br>home directory. Deskzilla can now copy the workspace to its new default location.<br>Old workspace directory will be renamed to \"workspace.migrated\".<br><br>Workspace contains your configuration, downloaded files and issue database.");
        myWholePanel.add(label1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 2, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCurrentWorkspace = new JTextField();
        myCurrentWorkspace.setColumns(30);
        myCurrentWorkspace.setEditable(false);
        myWholePanel.add(myCurrentWorkspace, new com.intellij.uiDesigner.core.GridConstraints(2, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Current workspace directory:");
        myWholePanel.add(label2, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myNewWorkspace = new JTextField();
        myNewWorkspace.setColumns(30);
        myNewWorkspace.setEditable(false);
        myWholePanel.add(myNewWorkspace, new com.intellij.uiDesigner.core.GridConstraints(3, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("New workspace directory:");
        myWholePanel.add(label3, new com.intellij.uiDesigner.core.GridConstraints(3, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("<html>Would you like Deskzilla to copy the workspace to the new default location?<br><b>YES</b> - Copy the workspace and start Deskzilla;<br><b>NO</b> - Keep workspace at the old location and start Deskzilla;<br><b>Cancel</b> - Exit the application");
        myWholePanel.add(label4, new com.intellij.uiDesigner.core.GridConstraints(5, 0, 1, 2, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myDontAsk = new JCheckBox();
        myDontAsk.setText("Don't ask me again");
        myWholePanel.add(myDontAsk, new com.intellij.uiDesigner.core.GridConstraints(6, 0, 1, 2, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        myWholePanel.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(4, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, 1, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 21), null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer2 = new com.intellij.uiDesigner.core.Spacer();
        myWholePanel.add(spacer2, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, 1, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 21), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myWholePanel;
    }
}

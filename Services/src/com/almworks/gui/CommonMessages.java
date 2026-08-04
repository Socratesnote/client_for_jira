package com.almworks.gui;

import com.almworks.util.i18n.text.CurrentLocale;
import com.almworks.util.i18n.text.LocalizedAccessor;

public class CommonMessages {
  private static final LocalizedAccessor I18N  = CurrentLocale.createAccessor(CommonMessages.class.getClassLoader(), "com/almworks/gui/message");

  /** Name of the action that opens an issue in the external browser. */
  public static final LocalizedAccessor.Value OPEN_IN_BROWSER = I18N.getFactory("action.openInBrowser.name");
  /** Title of the dialog reporting that the external browser could not be opened. Deliberately names no subject, since the failure is the same for any of them. */
  public static final LocalizedAccessor.Value OPEN_IN_BROWSER_TITLE = I18N.getFactory("dialog.openInBrowser.title");
}

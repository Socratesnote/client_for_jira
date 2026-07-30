package com.almworks.items.gui.edit.editors.text;

import org.jetbrains.annotations.Nullable;

/**
 * Converts between the rich form a server holds and the text an editor shows.<br>
 * The editors in this module are plain text components and know nothing about any particular server's rich
 * text, so the conversion is supplied from outside. Implementations must be deterministic: the same source
 * has to produce the same editable text every time, since that is what lets the reverse conversion line up
 * with what the user was shown.
 */
public interface RichTextTransform {
  /**
   * @param plainText the stored plain text, used when there is no rich source.
   * @param rawSource the server's own rich value, or null when the field never had one.
   * @return what the editor should display and let the user edit.
   */
  String toEditable(@Nullable String plainText, @Nullable String rawSource);

  /**
   * Rebuilds the rich value from edited text.
   * @param originalRawSource the rich value the editor started from, needed to restore anything the editable
   * form stands in for rather than spells out.
   */
  Result fromEditable(@Nullable String editedText, @Nullable String originalRawSource);

  /**
   * Reports what an edit would destroy, for a warning shown before anything is written.
   * @return null when nothing would be lost.
   */
  @Nullable
  String checkLoss(@Nullable String editedText, @Nullable String originalRawSource);

  /**
   * The two halves written back: the plain text every existing consumer reads, and the rich value beside it.
   */
  class Result {
    private final String myPlainText;
    private final String myRawSource;

    public Result(@Nullable String plainText, @Nullable String rawSource) {
      myPlainText = plainText;
      myRawSource = rawSource;
    }

    @Nullable
    public String getPlainText() {
      return myPlainText;
    }

    @Nullable
    public String getRawSource() {
      return myRawSource;
    }
  }
}

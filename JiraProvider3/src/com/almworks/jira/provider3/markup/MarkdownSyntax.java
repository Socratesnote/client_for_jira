package com.almworks.jira.provider3.markup;

import java.util.regex.Pattern;

/**
 * Escaping shared by {@link AdfToMarkdown} and {@link MarkdownToAdf}.<br>
 * Text that happens to contain markup characters must come back as the same text, so every character with a
 * meaning is escaped on the way out and restored on the way in.
 */
class MarkdownSyntax {
  /** Characters that start or end inline markup, plus the placeholder brackets. */
  private static final String INLINE_SPECIAL = "\\*_`~[]" + PlaceholderTable.OPEN + PlaceholderTable.CLOSE;

  /**
   * A line beginning with one of these would be read as a block rather than as text, so such a line is
   * escaped when it is really a paragraph.
   */
  private static final Pattern BLOCK_START = Pattern.compile("^(#{1,6} |> |---$|```|- |\\d+\\. )");

  static String escape(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (INLINE_SPECIAL.indexOf(c) >= 0) out.append('\\');
      out.append(c);
    }
    return out.toString();
  }

  /**
   * Reverses {@link #escape}. A backslash before any character yields that character literally; a trailing
   * backslash is kept as itself.
   */
  static String unescape(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\' && i + 1 < text.length()) {
        out.append(text.charAt(++i));
        continue;
      }
      out.append(c);
    }
    return out.toString();
  }

  /**
   * Protects a paragraph whose text would otherwise be read as a heading, quote, rule, fence or list item.
   */
  static String escapeBlockStart(String line) {
    return BLOCK_START.matcher(line).find() ? "\\" + line : line;
  }

  /**
   * Recognizes the escape written by {@link #escapeBlockStart}, so the line is treated as text.
   */
  static boolean isEscapedBlockStart(String line) {
    return line.startsWith("\\") && BLOCK_START.matcher(line.substring(1)).find();
  }

  private MarkdownSyntax() {}
}

package com.almworks.jira.provider3.markup;

import org.almworks.util.Collections15;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds the ADF nodes that markup cannot express, so the editor can show a short token in their place and the
 * original node can be put back on the way out.<br>
 * This is what lets someone edit the prose around an image, a mention or a table without destroying it: the
 * token can be moved or deleted, but its contents cannot be corrupted by typing.
 */
public class PlaceholderTable {
  /**
   * Mathematical white square brackets. Chosen because they are absent from ordinary keyboards, carry no
   * meaning in Markdown, and survive a round trip through the converter untouched.
   */
  public static final char OPEN = '⟦';
  public static final char CLOSE = '⟧';

  /** Matches a token and captures kind, index and display text. Display may not contain the closing bracket. */
  public static final Pattern TOKEN = Pattern.compile(OPEN + "([a-z]+):(\\d+):([^" + CLOSE + "]*)" + CLOSE);

  /**
   * Kind of placeholder representing a whole block. Every other kind is inline and may sit inside a
   * paragraph, so only this one is resolved when a line consists of nothing but a token.
   */
  public static final String BLOCK_KIND = "block";

  private final List<Entry> myEntries = Collections15.arrayList();

  /**
   * Registers a node and returns the token it represents.
   * @param display short human-readable text shown inside the token, so the user can tell what it is
   */
  public String add(String kind, JSONObject node, @Nullable String display) {
    Entry entry = new Entry(kind, node, sanitize(display));
    myEntries.add(entry);
    return OPEN + entry.myKind + ":" + myEntries.size() + ":" + entry.myDisplay + CLOSE;
  }

  /**
   * Resolves a token back to its original node.
   * @return null when the token names no entry here, or when its display text was edited. Both cases mean the
   * text is no longer a faithful stand-in for the node, so it is treated as literal text rather than silently
   * restoring something the user appears to have rewritten.
   */
  @Nullable
  public JSONObject resolve(String kind, int index, String display) {
    if (index < 1 || index > myEntries.size()) return null;
    Entry entry = myEntries.get(index - 1);
    if (!entry.myKind.equals(kind) || !entry.myDisplay.equals(display)) return null;
    return entry.myNode;
  }

  /**
   * Describes the entries whose tokens are missing from the given text, for the warning shown before a commit.
   */
  public List<String> describeMissing(String text) {
    List<String> missing = Collections15.arrayList();
    boolean[] seen = new boolean[myEntries.size()];
    Matcher matcher = TOKEN.matcher(text);
    while (matcher.find()) {
      int index = parseIndex(matcher.group(2));
      if (index >= 1 && index <= seen.length && resolve(matcher.group(1), index, matcher.group(3)) != null) seen[index - 1] = true;
    }
    for (int i = 0; i < myEntries.size(); i++) if (!seen[i]) missing.add(myEntries.get(i).describe());
    return missing;
  }

  public boolean isEmpty() {
    return myEntries.isEmpty();
  }

  public int size() {
    return myEntries.size();
  }

  public static int parseIndex(String text) {
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /**
   * Keeps display text on one line and free of the closing bracket, so a token can never be split or ended
   * early by the text inside it.
   */
  private static String sanitize(@Nullable String display) {
    if (display == null || display.isEmpty()) return "";
    String result = display.replace('\n', ' ').replace('\r', ' ').replace(CLOSE, ' ').replace(OPEN, ' ').trim();
    return result.length() <= MAX_DISPLAY ? result : result.substring(0, MAX_DISPLAY - 1).trim() + "…";
  }

  private static final int MAX_DISPLAY = 40;

  private static class Entry {
    private final String myKind;
    private final JSONObject myNode;
    private final String myDisplay;

    Entry(String kind, JSONObject node, String display) {
      myKind = kind;
      myNode = node;
      myDisplay = display;
    }

    String describe() {
      return myDisplay.isEmpty() ? myKind : myKind + " \"" + myDisplay + "\"";
    }
  }
}

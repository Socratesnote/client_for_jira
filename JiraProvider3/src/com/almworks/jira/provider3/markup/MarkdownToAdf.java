package com.almworks.jira.provider3.markup;

import org.almworks.util.Collections15;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Markdown shown in the editor back into an ADF document - the inverse of {@link AdfToMarkdown}.<br>
 * Tokens are resolved against the {@link PlaceholderTable} built when the text was rendered, which is how a
 * node the editor cannot express survives being edited around. A token whose text was altered, or that names
 * no entry, is treated as ordinary text rather than being silently restored as something the user rewrote.
 */
public class MarkdownToAdf {
  private static final Pattern HEADING = Pattern.compile("^(#{1,6}) (.*)$");
  private static final Pattern LIST_ITEM = Pattern.compile("^( *)(?:- |(\\d+)\\. )(.*)$");
  private static final Pattern FENCE = Pattern.compile("^```(.*)$");

  public static JSONObject parse(@Nullable String markdown, @Nullable PlaceholderTable table) {
    PlaceholderTable resolved = table != null ? table : new PlaceholderTable();
    String[] lines = splitLines(markdown == null ? "" : markdown);
    JSONArray content = parseBlocks(lines, new int[]{0}, lines.length, 0, resolved);
    JSONObject doc = new JSONObject();
    doc.put("type", "doc");
    doc.put("version", 1);
    doc.put("content", content);
    return doc;
  }

  /**
   * Reads blocks until the end of the range, or until a line is less indented than this level.
   * @param cursor single-element array used as a by-reference index into the lines
   */
  @SuppressWarnings("unchecked")
  private static JSONArray parseBlocks(String[] lines, int[] cursor, int end, int indent, PlaceholderTable table) {
    JSONArray content = new JSONArray();
    while (cursor[0] < end) {
      String line = strip(lines[cursor[0]], indent);
      if (line == null) break;
      JSONObject block = parseBlock(lines, cursor, end, indent, line, table);
      if (block != null) content.add(block);
    }
    return content;
  }

  @Nullable
  @SuppressWarnings("unchecked")
  private static JSONObject parseBlock(String[] lines, int[] cursor, int end, int indent, String line, PlaceholderTable table) {
    Matcher fence = FENCE.matcher(line);
    if (fence.matches()) return parseCodeBlock(lines, cursor, end, indent, fence.group(1), table);
    if (MarkdownSyntax.isEscapedBlockStart(line)) return parseParagraph(lines, cursor, end, indent, table);
    Matcher heading = HEADING.matcher(line);
    if (heading.matches()) {
      cursor[0]++;
      JSONObject node = node("heading");
      node.put("attrs", attrs("level", (long) heading.group(1).length()));
      putContent(node, parseInline(heading.group(2), table));
      return node;
    }
    if (line.startsWith("> ") || line.equals(">")) return parseBlockquote(lines, cursor, end, indent, table);
    if (line.equals("---")) {
      cursor[0]++;
      return node("rule");
    }
    Matcher item = LIST_ITEM.matcher(line);
    if (item.matches() && item.group(1).isEmpty()) return parseList(lines, cursor, end, indent, item.group(2) != null, table);
    JSONObject whole = wholeLineToken(line, table);
    if (whole != null) {
      cursor[0]++;
      return whole;
    }
    return parseParagraph(lines, cursor, end, indent, table);
  }

  /**
   * A line that is exactly one block token restores the node it stands for, with its JSON untouched.<br>
   * Only block placeholders qualify. An inline one - a mention, an emoji, an image - can be the whole of a
   * paragraph, and resolving it here would silently drop the paragraph around it.
   */
  @Nullable
  private static JSONObject wholeLineToken(String line, PlaceholderTable table) {
    Matcher matcher = PlaceholderTable.TOKEN.matcher(line);
    if (!matcher.matches() || !PlaceholderTable.BLOCK_KIND.equals(matcher.group(1))) return null;
    return table.resolve(matcher.group(1), PlaceholderTable.parseIndex(matcher.group(2)), matcher.group(3));
  }

  @SuppressWarnings("unchecked")
  private static JSONObject parseCodeBlock(String[] lines, int[] cursor, int end, int indent, String language, PlaceholderTable table) {
    cursor[0]++;
    StringBuilder code = new StringBuilder();
    boolean first = true;
    while (cursor[0] < end) {
      String line = strip(lines[cursor[0]], indent);
      if (line == null) break;
      cursor[0]++;
      if (line.equals("```")) break;
      if (!first) code.append('\n');
      first = false;
      code.append(line);
    }
    JSONObject node = node("codeBlock");
    if (!language.isEmpty()) node.put("attrs", attrs("language", language));
    if (code.length() > 0) putContent(node, singleText(code.toString()));
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject parseBlockquote(String[] lines, int[] cursor, int end, int indent, PlaceholderTable table) {
    List<String> quoted = Collections15.arrayList();
    while (cursor[0] < end) {
      String line = strip(lines[cursor[0]], indent);
      if (line == null) break;
      if (line.startsWith("> ")) quoted.add(line.substring(2));
      else if (line.equals(">")) quoted.add("");
      else break;
      cursor[0]++;
    }
    String[] inner = quoted.toArray(new String[quoted.size()]);
    JSONObject node = node("blockquote");
    putContent(node, parseBlocks(inner, new int[]{0}, inner.length, 0, table));
    return node;
  }

  /**
   * Reads consecutive items at this indent. An item's body is its own marker line plus every following line
   * indented one level further, parsed as blocks - which is how nested lists come back without special cases.
   */
  @SuppressWarnings("unchecked")
  private static JSONObject parseList(String[] lines, int[] cursor, int end, int indent, boolean ordered, PlaceholderTable table) {
    JSONArray items = new JSONArray();
    Long firstNumber = null;
    while (cursor[0] < end) {
      String line = strip(lines[cursor[0]], indent);
      if (line == null) break;
      Matcher item = LIST_ITEM.matcher(line);
      if (!item.matches() || !item.group(1).isEmpty()) break;
      boolean itemOrdered = item.group(2) != null;
      if (itemOrdered != ordered) break;
      if (firstNumber == null && itemOrdered) firstNumber = Long.valueOf(item.group(2));
      cursor[0]++;
      // The remainder of the marker line, then anything indented under it, form the item's blocks.
      List<String> body = Collections15.arrayList();
      body.add(item.group(3));
      while (cursor[0] < end) {
        String next = strip(lines[cursor[0]], indent + AdfToMarkdown.INDENT.length());
        if (next == null) break;
        body.add(next);
        cursor[0]++;
      }
      String[] bodyLines = body.toArray(new String[body.size()]);
      JSONObject listItem = node("listItem");
      putContent(listItem, parseBlocks(bodyLines, new int[]{0}, bodyLines.length, 0, table));
      items.add(listItem);
    }
    JSONObject node = node(ordered ? "orderedList" : "bulletList");
    // Only a list that does not start at one needs the attribute, matching what the server sends.
    if (ordered && firstNumber != null && firstNumber != 1L) node.put("attrs", attrs("order", firstNumber));
    node.put("content", items);
    return node;
  }

  /**
   * A paragraph is one line, unless a line ends with a backslash, which is a hard break carrying on to the next.
   */
  @SuppressWarnings("unchecked")
  private static JSONObject parseParagraph(String[] lines, int[] cursor, int end, int indent, PlaceholderTable table) {
    JSONArray content = new JSONArray();
    while (cursor[0] < end) {
      String line = strip(lines[cursor[0]], indent);
      if (line == null) break;
      cursor[0]++;
      boolean hardBreak = endsWithHardBreak(line);
      if (hardBreak) line = line.substring(0, line.length() - 1);
      if (MarkdownSyntax.isEscapedBlockStart(line)) line = line.substring(1);
      content.addAll(parseInline(line, table));
      if (!hardBreak) break;
      content.add(node("hardBreak"));
    }
    JSONObject node = node("paragraph");
    // An empty paragraph must carry no content key at all: ADF forbids an empty text node and JIRA rejects it.
    if (!content.isEmpty()) node.put("content", content);
    return node;
  }

  /**
   * True when the line ends with an unescaped backslash. An even number of trailing backslashes means the
   * last one is itself escaped text, not a break.
   */
  private static boolean endsWithHardBreak(String line) {
    int count = 0;
    for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) count++;
    return count % 2 == 1;
  }

  /**
   * Turns inline markup into text nodes carrying marks, resolving any token it meets on the way.
   */
  private static JSONArray parseInline(String text, PlaceholderTable table) {
    return new InlineParser(table).parse(text, new JSONArray());
  }

  private static class InlineParser {
    private final PlaceholderTable myTable;

    InlineParser(PlaceholderTable table) {
      myTable = table;
    }

    @SuppressWarnings("unchecked")
    JSONArray parse(String text, JSONArray marks) {
      JSONArray out = new JSONArray();
      StringBuilder literal = new StringBuilder();
      int i = 0;
      while (i < text.length()) {
        char c = text.charAt(i);
        if (c == '\\' && i + 1 < text.length()) {
          literal.append(text.charAt(i + 1));
          i += 2;
          continue;
        }
        if (c == PlaceholderTable.OPEN) {
          Matcher matcher = PlaceholderTable.TOKEN.matcher(text);
          if (matcher.find(i) && matcher.start() == i) {
            JSONObject node = myTable.resolve(matcher.group(1), PlaceholderTable.parseIndex(matcher.group(2)), matcher.group(3));
            if (node != null) {
              flush(out, literal, marks);
              out.add(node);
              i = matcher.end();
              continue;
            }
          }
        }
        String delimiter = delimiterAt(text, i);
        if (delimiter != null) {
          int close = findClose(text, i + delimiter.length(), delimiter);
          if (close > 0) {
            flush(out, literal, marks);
            String inner = text.substring(i + delimiter.length(), close);
            out.addAll(parse(inner, withMark(marks, markFor(delimiter))));
            i = close + delimiter.length();
            continue;
          }
        }
        if (c == '[') {
          int[] link = findLink(text, i);
          if (link != null) {
            flush(out, literal, marks);
            JSONObject mark = node("link");
            mark.put("attrs", attrs("href", MarkdownSyntax.unescape(text.substring(link[1] + 2, link[2]))));
            out.addAll(parse(text.substring(i + 1, link[0]), withMark(marks, mark)));
            i = link[2] + 1;
            continue;
          }
        }
        literal.append(c);
        i++;
      }
      flush(out, literal, marks);
      return out;
    }

    @SuppressWarnings("unchecked")
    private void flush(JSONArray out, StringBuilder literal, JSONArray marks) {
      if (literal.length() == 0) return;
      JSONObject node = node("text");
      node.put("text", literal.toString());
      if (!marks.isEmpty()) node.put("marks", marks);
      out.add(node);
      literal.setLength(0);
    }
  }

  @Nullable
  private static String delimiterAt(String text, int index) {
    if (text.startsWith("**", index)) return "**";
    if (text.startsWith("~~", index)) return "~~";
    char c = text.charAt(index);
    if (c == '_') return "_";
    if (c == '`') return "`";
    return null;
  }

  private static JSONObject markFor(String delimiter) {
    switch (delimiter) {
    case "**": return node("strong");
    case "_": return node("em");
    case "~~": return node("strike");
    default: return node("code");
    }
  }

  /**
   * Finds the matching closing delimiter, skipping escaped characters.
   * @return -1 when the markup is never closed, in which case the delimiter is ordinary text
   */
  private static int findClose(String text, int from, String delimiter) {
    for (int i = from; i <= text.length() - delimiter.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\') {
        i++;
        continue;
      }
      if (text.startsWith(delimiter, i)) return i == from ? -1 : i;
    }
    return -1;
  }

  /**
   * Locates a link of the form [label](href).
   * @return the label end, the bracket close and the paren close, or null when this is not a link
   */
  @Nullable
  private static int[] findLink(String text, int start) {
    int depth = 0;
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\') {
        i++;
        continue;
      }
      if (c == '[') depth++;
      else if (c == ']') {
        depth--;
        if (depth == 0) {
          if (i + 1 >= text.length() || text.charAt(i + 1) != '(') return null;
          int close = text.indexOf(')', i + 2);
          if (close < 0) return null;
          return new int[]{i, i, close};
        }
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static JSONArray withMark(JSONArray marks, JSONObject mark) {
    JSONArray result = new JSONArray();
    result.addAll(marks);
    result.add(mark);
    return result;
  }

  /**
   * Removes one level of indentation.
   * @return null when the line is not indented that far, which ends the enclosing block
   */
  @Nullable
  private static String strip(String line, int indent) {
    if (indent == 0) return line;
    if (line.isEmpty()) return line;
    if (line.length() < indent || !line.substring(0, indent).trim().isEmpty()) return null;
    return line.substring(indent);
  }

  @SuppressWarnings("unchecked")
  private static JSONArray singleText(String text) {
    JSONArray content = new JSONArray();
    JSONObject node = node("text");
    node.put("text", text);
    content.add(node);
    return content;
  }

  @SuppressWarnings("unchecked")
  private static void putContent(JSONObject node, JSONArray content) {
    if (!content.isEmpty()) node.put("content", content);
  }

  @SuppressWarnings("unchecked")
  private static JSONObject node(String type) {
    JSONObject node = new JSONObject();
    node.put("type", type);
    return node;
  }

  @SuppressWarnings("unchecked")
  private static JSONObject attrs(String name, Object value) {
    JSONObject attrs = new JSONObject();
    attrs.put(name, value);
    return attrs;
  }

  private static String[] splitLines(String text) {
    return text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
  }

  private MarkdownToAdf() {}
}

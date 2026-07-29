package com.almworks.jira.provider3.markup;

import org.almworks.util.Util;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.jetbrains.annotations.Nullable;

/**
 * Renders an ADF document as Markdown shown in the editor.<br>
 * Anything Markdown cannot express becomes an opaque token backed by {@link PlaceholderTable}, so the original
 * node survives an edit instead of being flattened away. The inverse is {@link MarkdownToAdf}, and the two are
 * only correct together: rendering then parsing must give back the same document.
 * <p>
 * One block per line, matching how the client shows these fields, rather than the blank-line
 * paragraph separation of ordinary Markdown. A blank line therefore means an empty paragraph, which is what makes the round trip exact.
 */
public class AdfToMarkdown {
  public static class Rendered {
    private final String myMarkdown;
    private final PlaceholderTable myTable;

    Rendered(String markdown, PlaceholderTable table) {
      myMarkdown = markdown;
      myTable = table;
    }

    public String getMarkdown() {
      return myMarkdown;
    }

    public PlaceholderTable getTable() {
      return myTable;
    }
  }

  public static Rendered render(@Nullable JSONObject doc) {
    PlaceholderTable table = new PlaceholderTable();
    if (doc == null) return new Rendered("", table);
    StringBuilder out = new StringBuilder();
    JSONArray content = Util.castNullable(JSONArray.class, doc.get("content"));
    if (content != null) {
      boolean first = true;
      for (Object child : content) {
        JSONObject node = Util.castNullable(JSONObject.class, child);
        if (node == null) continue;
        if (!first) out.append('\n');
        first = false;
        out.append(renderBlock(node, table));
      }
    }
    return new Rendered(out.toString(), table);
  }

  private static String renderBlock(JSONObject node, PlaceholderTable table) {
    String type = Util.castNullable(String.class, node.get("type"));
    if (type == null) return table.add(PlaceholderTable.BLOCK_KIND, node, "?");
    switch (type) {
    case "paragraph":
      // Text that reads like a heading, quote or list item is escaped, so it comes back as the text it is.
      return MarkdownSyntax.escapeBlockStart(renderInline(node, table));
    case "heading":
      return repeat('#', headingLevel(node)) + " " + renderInline(node, table);
    case "codeBlock":
      return renderCodeBlock(node, table);
    case "blockquote":
      return prefixLines(renderBlocks(node, table), "> ");
    case "rule":
      return "---";
    case "bulletList":
    case "orderedList":
      return renderList(node, table, "bulletList".equals(type), startNumber(node));
    default:
      // Everything else keeps its exact JSON and shows as a token: tables, panels, media,
      // and any node Atlassian adds later. Preserved safely rather than erroneously interpreted.
      return table.add(PlaceholderTable.BLOCK_KIND, node, describe(type, node));
    }
  }

  /**
   * Renders the children of a container as blocks, one per line.
   */
  private static String renderBlocks(JSONObject node, PlaceholderTable table) {
    JSONArray content = Util.castNullable(JSONArray.class, node.get("content"));
    if (content == null) return "";
    StringBuilder out = new StringBuilder();
    boolean first = true;
    for (Object child : content) {
      JSONObject block = Util.castNullable(JSONObject.class, child);
      if (block == null) continue;
      if (!first) out.append('\n');
      first = false;
      out.append(renderBlock(block, table));
    }
    return out.toString();
  }

  private static String renderCodeBlock(JSONObject node, PlaceholderTable table) {
    String language = attrString(node, "language");
    StringBuilder out = new StringBuilder("```");
    if (language != null) out.append(language);
    out.append('\n');
    // Code content is literal: no escaping, no marks, no placeholders inside.
    out.append(plainText(node));
    out.append("\n```");
    return out.toString();
  }

  private static String renderList(JSONObject list, PlaceholderTable table, boolean bullet, int start) {
    JSONArray content = Util.castNullable(JSONArray.class, list.get("content"));
    if (content == null) return "";
    StringBuilder out = new StringBuilder();
    int number = start;
    boolean first = true;
    for (Object child : content) {
      JSONObject item = Util.castNullable(JSONObject.class, child);
      if (item == null || !"listItem".equals(item.get("type"))) continue;
      if (!first) out.append('\n');
      first = false;
      String marker = bullet ? "- " : number + ". ";
      number++;
      // The item's own blocks are rendered, then indented under the marker. Nested lists indent further,
      // which is how arbitrary nesting comes out without special handling here.
      String body = renderBlocks(item, table);
      out.append(marker).append(indentContinuation(body, INDENT));
    }
    return out.toString();
  }

  private static String renderInline(JSONObject node, PlaceholderTable table) {
    JSONArray content = Util.castNullable(JSONArray.class, node.get("content"));
    if (content == null) return "";
    StringBuilder out = new StringBuilder();
    for (Object child : content) {
      JSONObject inline = Util.castNullable(JSONObject.class, child);
      if (inline == null) continue;
      out.append(renderInlineNode(inline, table));
    }
    return out.toString();
  }

  private static String renderInlineNode(JSONObject node, PlaceholderTable table) {
    String type = Util.castNullable(String.class, node.get("type"));
    if ("text".equals(type)) return applyMarks(node, table);
    if ("hardBreak".equals(type)) return "\\\n";
    // Mentions, emoji, media and the rest carry ids that must survive exactly, so they are never markup.
    return table.add(inlineKind(type), node, describe(type, node));
  }

  /**
   * Wraps text in its marks, innermost first, so the nesting is deterministic and parses back to the same set.
   * Marks with no Markdown form become a token around the visible text rather than being dropped.
   */
  private static String applyMarks(JSONObject node, PlaceholderTable table) {
    String text = MarkdownSyntax.escape(Util.NN(Util.castNullable(String.class, node.get("text"))));
    JSONArray marks = Util.castNullable(JSONArray.class, node.get("marks"));
    if (marks == null || marks.isEmpty()) return text;
    String link = null;
    boolean strong = false;
    boolean em = false;
    boolean strike = false;
    boolean code = false;
    boolean unsupported = false;
    for (Object each : marks) {
      JSONObject mark = Util.castNullable(JSONObject.class, each);
      if (mark == null) continue;
      String markType = Util.castNullable(String.class, mark.get("type"));
      if (markType == null) continue;
      switch (markType) {
      case "strong": strong = true; break;
      case "em": em = true; break;
      case "strike": strike = true; break;
      case "code": code = true; break;
      case "link": link = attrString(mark, "href"); break;
      default: unsupported = true; break;
      }
    }
    // A mark this converter cannot write must not be silently lost, so the whole node becomes a token.
    if (unsupported || (link == null && hasLinkMark(marks))) return table.add("mark", node, Util.NN(Util.castNullable(String.class, node.get("text"))));
    String result = text;
    if (code) result = "`" + result + "`";
    if (strike) result = "~~" + result + "~~";
    // Underscore rather than a single asterisk, so bold-and-italic writes as **_x_** instead of ***x***,
    // which no delimiter-run parser can read back unambiguously.
    if (em) result = "_" + result + "_";
    if (strong) result = "**" + result + "**";
    if (link != null) result = "[" + result + "](" + link + ")";
    return result;
  }

  private static boolean hasLinkMark(JSONArray marks) {
    for (Object each : marks) {
      JSONObject mark = Util.castNullable(JSONObject.class, each);
      if (mark != null && "link".equals(mark.get("type"))) return true;
    }
    return false;
  }

  static final String INDENT = "  ";

  /**
   * Indents every line after the first, so a list marker stays on the first line and the rest lines up under it.
   */
  private static String indentContinuation(String text, String indent) {
    if (text.indexOf('\n') < 0) return text;
    String[] lines = text.split("\n", -1);
    StringBuilder out = new StringBuilder(lines[0]);
    for (int i = 1; i < lines.length; i++) out.append('\n').append(indent).append(lines[i]);
    return out.toString();
  }

  private static String prefixLines(String text, String prefix) {
    String[] lines = text.split("\n", -1);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) out.append('\n');
      out.append(prefix).append(lines[i]);
    }
    return out.toString();
  }

  private static int headingLevel(JSONObject node) {
    JSONObject attrs = Util.castNullable(JSONObject.class, node.get("attrs"));
    Number level = attrs == null ? null : Util.castNullable(Number.class, attrs.get("level"));
    int value = level == null ? 1 : level.intValue();
    return value < 1 ? 1 : Math.min(value, 6);
  }

  private static int startNumber(JSONObject node) {
    JSONObject attrs = Util.castNullable(JSONObject.class, node.get("attrs"));
    Number order = attrs == null ? null : Util.castNullable(Number.class, attrs.get("order"));
    return order == null ? 1 : order.intValue();
  }

  @Nullable
  private static String attrString(JSONObject node, String name) {
    JSONObject attrs = Util.castNullable(JSONObject.class, node.get("attrs"));
    return attrs == null ? null : Util.castNullable(String.class, attrs.get(name));
  }

  /**
   * The plain text of a node's children, used for code blocks where nothing is marked up.
   */
  private static String plainText(JSONObject node) {
    JSONArray content = Util.castNullable(JSONArray.class, node.get("content"));
    if (content == null) return "";
    StringBuilder out = new StringBuilder();
    for (Object child : content) {
      JSONObject text = Util.castNullable(JSONObject.class, child);
      if (text == null) continue;
      String value = Util.castNullable(String.class, text.get("text"));
      if (value != null) out.append(value);
    }
    return out.toString();
  }

  /**
   * Short text shown inside a token, so the user can tell what the token represents.
   */
  private static String describe(@Nullable String type, JSONObject node) {
    if (type == null) return "?";
    String text = attrString(node, "text");
    if (text != null) return text;
    String url = attrString(node, "url");
    if (url != null) return url;
    switch (type) {
    case "media":
    case "mediaSingle":
    case "mediaGroup":
    case "mediaInline":
      String alt = attrString(node, "alt");
      return alt != null ? alt : "attachment";
    case "table":
      return "table";
    case "panel":
      String panelType = attrString(node, "panelType");
      return panelType != null ? "panel (" + panelType + ")" : "panel";
    default:
      return type;
    }
  }

  private static String inlineKind(@Nullable String type) {
    if (type == null) return "node";
    switch (type) {
    case "mention": return "mention";
    case "emoji": return "emoji";
    case "status": return "status";
    case "date": return "date";
    case "inlineCard": return "link";
    case "media":
    case "mediaInline": return "media";
    default: return "node";
    }
  }

  private static String repeat(char c, int count) {
    StringBuilder out = new StringBuilder(count);
    for (int i = 0; i < count; i++) out.append(c);
    return out.toString();
  }

  private AdfToMarkdown() {}
}

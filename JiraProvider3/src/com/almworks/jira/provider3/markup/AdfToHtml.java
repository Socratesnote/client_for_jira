package com.almworks.jira.provider3.markup;

import org.almworks.util.Util;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Renders an ADF document as HTML for the read-only viewers.<br>
 * The target is Swing's Java 8 {@code HTMLEditorKit}, which is roughly HTML 3.2: CSS support is weak and
 * unreliable, so this deliberately emits old presentational tags ({@code b}, {@code i}, {@code tt},
 * {@code font}) rather than styled spans. Anything fancier tends to render worse, not better.
 * <p>
 * Display only. Nothing here ever travels back to the server - the editable form is Markdown
 * ({@link AdfToMarkdown}) and the stored form is the document itself, so this converter is free to be lossy
 * where HTML cannot express a construct.
 */
public class AdfToHtml {
  /**
   * @return an HTML fragment, without the surrounding html or body tags. Callers pass it through
   * {@code TextUtil.preprocessHtml}, which supplies those.
   */
  public static String render(@Nullable JSONObject doc) {
    StringBuilder out = new StringBuilder();
    if (doc != null) appendBlocks(doc, out);
    return out.toString();
  }

  /**
   * Renders plain text as HTML, for values that have no stored document - anything downloaded before the
   * companion attribute existed, or a field that was never rich. Keeps the viewer on one code path.
   */
  public static String renderPlainText(@Nullable String text) {
    if (text == null || text.isEmpty()) return "";
    StringBuilder out = new StringBuilder();
    for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
      out.append("<p>").append(escape(line)).append("</p>");
    }
    return out.toString();
  }

  private static void appendBlocks(JSONObject node, StringBuilder out) {
    JSONArray content = Util.castNullable(JSONArray.class, node.get("content"));
    if (content == null) return;
    for (Object child : content) {
      JSONObject block = Util.castNullable(JSONObject.class, child);
      if (block != null) appendBlock(block, out);
    }
  }

  private static void appendBlock(JSONObject node, StringBuilder out) {
    String type = Util.castNullable(String.class, node.get("type"));
    if (type == null) return;
    switch (type) {
    case "paragraph":
      out.append("<p>");
      appendInlines(node, out);
      out.append("</p>");
      return;
    case "heading":
      String tag = "h" + headingLevel(node);
      out.append('<').append(tag).append('>');
      appendInlines(node, out);
      out.append("</").append(tag).append('>');
      return;
    case "blockquote":
      out.append("<blockquote>");
      appendBlocks(node, out);
      out.append("</blockquote>");
      return;
    case "codeBlock":
      // An empty code block would collapse to nothing and simply disappear from the view, so it keeps a
      // non-breaking space and stays visible as the empty block it is.
      String code = plainText(node);
      out.append("<pre><tt>").append(code.isEmpty() ? "&nbsp;" : escape(code)).append("</tt></pre>");
      return;
    case "rule":
      out.append("<hr>");
      return;
    case "bulletList":
      appendList(node, out, "ul");
      return;
    case "orderedList":
      appendList(node, out, "ol");
      return;
    case "listItem":
      out.append("<li>");
      appendBlocks(node, out);
      out.append("</li>");
      return;
    case "table":
      // border=1 rather than CSS: HTMLEditorKit honours the attribute and largely ignores the stylesheet.
      out.append("<table border=\"1\" cellpadding=\"3\" cellspacing=\"0\">");
      appendBlocks(node, out);
      out.append("</table>");
      return;
    case "tableRow":
      out.append("<tr>");
      appendBlocks(node, out);
      out.append("</tr>");
      return;
    case "tableHeader":
      out.append("<th>");
      appendBlocks(node, out);
      out.append("</th>");
      return;
    case "tableCell":
      out.append("<td>");
      appendBlocks(node, out);
      out.append("</td>");
      return;
    case "panel":
      appendPanel(node, out);
      return;
    case "expand":
    case "nestedExpand":
      // No disclosure widget in this renderer, so the title is shown and the body left open.
      String title = attrString(node, "title");
      out.append("<blockquote><b>").append(escape(title != null ? title : "Details")).append("</b>");
      appendBlocks(node, out);
      out.append("</blockquote>");
      return;
    case "mediaSingle":
    case "mediaGroup":
      appendBlocks(node, out);
      return;
    case "media":
    case "mediaInline":
      // Media is a block child of mediaSingle and mediaGroup as well as an inline node, so it needs handling
      // in both places. On its own line it gets a paragraph so it cannot run into adjacent text.
      out.append("<p>");
      appendMedia(node, out);
      out.append("</p>");
      return;
    default:
      // Unknown block: salvage whatever it contains rather than dropping it silently.
      appendBlocks(node, out);
    }
  }

  /**
   * Panels have no HTML 3.2 equivalent, so they become a bordered one-cell table tinted by type.
   */
  private static void appendPanel(JSONObject node, StringBuilder out) {
    String panelType = Util.NN(attrString(node, "panelType"), "info");
    out.append("<table border=\"1\" cellpadding=\"4\" cellspacing=\"0\" width=\"100%\"><tr><td bgcolor=\"")
      .append(panelColor(panelType)).append("\">");
    appendBlocks(node, out);
    out.append("</td></tr></table>");
  }

  private static String panelColor(String panelType) {
    switch (panelType) {
    case "note": return "#eae6ff";
    case "success": return "#e3fcef";
    case "warning": return "#fffae6";
    case "error": return "#ffebe6";
    default: return "#deebff";
    }
  }

  private static void appendList(JSONObject node, StringBuilder out, String tag) {
    out.append('<').append(tag).append('>');
    appendBlocks(node, out);
    out.append("</").append(tag).append('>');
  }

  private static void appendInlines(JSONObject node, StringBuilder out) {
    JSONArray content = Util.castNullable(JSONArray.class, node.get("content"));
    if (content == null) return;
    for (Object child : content) {
      JSONObject inline = Util.castNullable(JSONObject.class, child);
      if (inline != null) appendInline(inline, out);
    }
  }

  private static void appendInline(JSONObject node, StringBuilder out) {
    String type = Util.castNullable(String.class, node.get("type"));
    if (type == null) return;
    switch (type) {
    case "text":
      appendMarkedText(node, out);
      return;
    case "hardBreak":
      out.append("<br>");
      return;
    case "mention":
      // Shown as the name rather than a link: the mention carries an account id, not a URL.
      out.append("<b>").append(escape(Util.NN(attrString(node, "text"), "@?"))).append("</b>");
      return;
    case "emoji":
      out.append(escape(Util.NN(attrString(node, "text"), attrString(node, "shortName"))));
      return;
    case "status":
      out.append('[').append(escape(Util.NN(attrString(node, "text"), "?"))).append(']');
      return;
    case "date":
      // A date node carries only a timestamp, and the viewer has no locale-aware formatter here.
      out.append(escape(Util.NN(attrString(node, "timestamp"), "")));
      return;
    case "inlineCard":
      String url = attrString(node, "url");
      if (url != null) out.append("<a href=\"").append(escape(url)).append("\">").append(escape(url)).append("</a>");
      return;
    case "media":
    case "mediaInline":
      appendMedia(node, out);
      return;
    default:
      appendInlines(node, out);
    }
  }

  /**
   * Images cannot be shown: {@code TextUtil.preprocessHtml} strips every img tag before rendering, and the
   * bytes would need fetching through the authenticated session anyway. A visible label is better than a gap,
   * since the attachment itself is listed in the Attachments formlet.
   */
  private static void appendMedia(JSONObject node, StringBuilder out) {
    String name = attrString(node, "alt");
    if (name == null) name = attrString(node, "id");
    out.append("<i>[attachment").append(name != null ? ": " + escape(name) : "").append("]</i>");
  }

  private static void appendMarkedText(JSONObject node, StringBuilder out) {
    String text = Util.castNullable(String.class, node.get("text"));
    if (text == null) return;
    JSONArray marks = Util.castNullable(JSONArray.class, node.get("marks"));
    StringBuilder open = new StringBuilder();
    StringBuilder close = new StringBuilder();
    if (marks != null) {
      for (Object each : marks) {
        JSONObject mark = Util.castNullable(JSONObject.class, each);
        if (mark == null) continue;
        String markType = Util.castNullable(String.class, mark.get("type"));
        if (markType == null) continue;
        switch (markType) {
        case "strong": wrap(open, close, "b"); break;
        case "em": wrap(open, close, "i"); break;
        case "code": wrap(open, close, "tt"); break;
        case "strike": wrap(open, close, "s"); break;
        case "underline": wrap(open, close, "u"); break;
        case "subsup":
          wrap(open, close, "sup".equals(attrString(mark, "type")) ? "sup" : "sub");
          break;
        case "textColor":
          String color = attrString(mark, "color");
          if (color != null) {
            open.append("<font color=\"").append(escape(color)).append("\">");
            close.insert(0, "</font>");
          }
          break;
        case "link":
          String href = attrString(mark, "href");
          if (href != null) {
            open.append("<a href=\"").append(escape(href)).append("\">");
            close.insert(0, "</a>");
          }
          break;
        default:
          break;
        }
      }
    }
    out.append(open).append(escape(text)).append(close);
  }

  private static void wrap(StringBuilder open, StringBuilder close, String tag) {
    open.append('<').append(tag).append('>');
    close.insert(0, "</" + tag + ">");
  }

  private static int headingLevel(JSONObject node) {
    JSONObject attrs = Util.castNullable(JSONObject.class, node.get("attrs"));
    Number level = attrs == null ? null : Util.castNullable(Number.class, attrs.get("level"));
    int value = level == null ? 1 : level.intValue();
    return value < 1 ? 1 : value > 6 ? 6 : value;
  }

  @Nullable
  private static String attrString(JSONObject node, String name) {
    JSONObject attrs = Util.castNullable(JSONObject.class, node.get("attrs"));
    Object value = attrs == null ? null : attrs.get(name);
    if (value == null) return null;
    return value instanceof String ? (String) value : String.valueOf(value);
  }

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
   * Escapes the four characters that would otherwise be read as markup. Text arrives from the server, so this
   * is what keeps an issue description from injecting tags into the viewer.
   */
  static String escape(@Nullable String text) {
    if (text == null) return "";
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
      case '&': out.append("&amp;"); break;
      case '<': out.append("&lt;"); break;
      case '>': out.append("&gt;"); break;
      case '"': out.append("&quot;"); break;
      default: out.append(c);
      }
    }
    return out.toString();
  }

  private AdfToHtml() {}
}

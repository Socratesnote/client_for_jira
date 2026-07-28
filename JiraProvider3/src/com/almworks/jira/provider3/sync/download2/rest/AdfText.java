package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.restconnector.json.JsonKey;
import com.almworks.util.LogHelper;
import com.almworks.util.collections.Convertor;
import org.almworks.util.Util;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Extracts plain text from Atlassian Document Format (ADF) values.<br>
 * JIRA REST API v3 returns rich-text fields (issue description, environment, comment body, worklog comment,
 * textarea custom fields) as ADF documents instead of plain strings.
 * See https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/
 */
public class AdfText {
  /**
   * Passes plain strings through unchanged (non-rich fields, pre-ADF servers); extracts plain text from an ADF doc object.
   */
  public static final Convertor<Object, String> TO_TEXT = new Convertor<Object, String>() {
    @Override
    public String convert(Object value) {
      if (value == null) return null;
      String str = Util.castNullable(String.class, value);
      if (str != null) return str;
      JSONObject doc = Util.castNullable(JSONObject.class, value);
      if (doc != null && "doc".equals(doc.get("type"))) {
        StringBuilder out = new StringBuilder();
        appendContent(doc, out);
        return out.toString();
      }
      LogHelper.error("Expected text or ADF document", value);
      return null;
    }
  };

  /**
   * Wraps an existing text convertor so it accepts ADF documents as well as plain strings.
   */
  public static Convertor<Object, String> adfAware(final Convertor<Object, String> textConvertor) {
    return new Convertor<Object, String>() {
      @Override
      public String convert(Object value) {
        return textConvertor.convert(TO_TEXT.convert(value));
      }
    };
  }

  /**
   * Mirror of {@link JsonKey#textTrimLines(String)} accepting ADF values.
   */
  public static JsonKey<String> textTrimLines(String name) {
    return new JsonKey<>(name, JsonKey.emptyTextToNull(adfAware(JsonKey.TEXT_TRIM_LINES)));
  }

  private static void appendNode(JSONObject node, StringBuilder out) {
    String type = Util.castNullable(String.class, node.get("type"));
    if (type == null) {
      LogHelper.warning("AdfText: ADF node without type", node);
      return;
    }
    switch (type) {
    case "text":
      //TODO: Investigate whether the JIRA Client can actually display this text in its original form as much as possible, instead of just plain text.

      // Handled: plain text content. "marks" (bold/italic/link/code/...) are deliberately dropped - this is a plain-text extraction.
      String text = Util.castNullable(String.class, node.get("text"));
      if (text != null) out.append(text);
      return;
    case "hardBreak":
      // Handled: explicit line break within a paragraph.
      out.append('\n');
      return;
    case "mention":
    case "emoji":
    case "status":
      // Handled: rendered as their display text ("attrs.text"; mention text already includes '@').
      appendAttrText(node, out);
      return;
    case "inlineCard":
      // Handled: smart link reduced to its URL.
      JSONObject cardAttrs = Util.castNullable(JSONObject.class, node.get("attrs"));
      String url = cardAttrs == null ? null : Util.castNullable(String.class, cardAttrs.get("url"));
      if (url != null) out.append(url);
      return;
    case "media":
    case "mediaSingle":
    case "mediaGroup":
    case "mediaInline":
      // TODO: Any way to display this media in the client?

      // Rejected: embedded files/images have no text representation in this client.
      return;
    case "rule":
      // Passed through as separation: horizontal rule has no text, keep the line break it implies.
      out.append('\n');
      return;
    case "doc":
    case "bulletList":
    case "orderedList":
    case "table":
    case "tableHeader":
    case "tableCell":
    case "panel":
    case "expand":
    case "nestedExpand":
      // Passed through: pure containers, structure is flattened - just recurse into children.
      appendContent(node, out);
      return;
    case "paragraph":
    case "heading":
    case "codeBlock":
    case "blockquote":
    case "listItem":
    case "tableRow":
      // Passed through: block-level containers - recurse into children, then break the line so blocks stay separated.
      appendContent(node, out);
      out.append('\n');
      return;
    default:
      // Unknown ADF construct: log it so it can be revisited, and salvage any nested text.
      // Log this as Warning, not Error, because that would make it look more severe than it is and turns this into a
      // test failure. Since Atlassian may change nodes without warning it is expected to run into unknown types from
      // time to time.
      LogHelper.warning("AdfText: unhandled ADF node type", type);
      appendContent(node, out);
    }
  }

  private static void appendContent(JSONObject node, StringBuilder out) {
    JSONArray content = Util.castNullable(JSONArray.class, node.get("content"));
    if (content == null) return;
    for (Object child : content) {
      JSONObject childNode = Util.castNullable(JSONObject.class, child);
      if (childNode != null) appendNode(childNode, out);
    }
  }

  private static void appendAttrText(JSONObject node, StringBuilder out) {
    JSONObject attrs = Util.castNullable(JSONObject.class, node.get("attrs"));
    String text = attrs == null ? null : Util.castNullable(String.class, attrs.get("text"));
    if (text != null) out.append(text);
  }
}

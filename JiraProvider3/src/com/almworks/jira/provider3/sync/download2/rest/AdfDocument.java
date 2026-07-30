package com.almworks.jira.provider3.sync.download2.rest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Builds Atlassian Document Format (ADF) values from plain text - the write-side inverse of {@link AdfText}.<br>
 * JIRA REST API v3 requires rich-text fields (issue description, environment, comment body, worklog comment,
 * textarea custom fields) to be sent as ADF documents rather than plain strings.
 * See https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/
 */
public class AdfDocument {
  /**
   * Converts plain text to the minimal ADF document that {@link AdfText} reads back as the same text.<br>
   * Each line becomes its own paragraph, because {@link AdfText} appends a line break after every block-level
   * node - a single paragraph with hardBreak nodes would not round-trip.
   * @return null when there is no text to send. Callers must omit the field or send JSON null rather than
   * an empty document: JIRA rejects rich-text values that contain no content.
   */
  @SuppressWarnings("unchecked")
  public static JSONObject fromText(String text) {
    if (text == null || text.isEmpty()) return null;
    JSONArray content = new JSONArray();
    for (String line : splitLines(text)) content.add(paragraph(line));
    JSONObject doc = new JSONObject();
    doc.put("type", "doc");
    doc.put("version", 1);
    doc.put("content", content);
    return doc;
  }

  /**
   * An empty line becomes a paragraph with no content. The ADF spec allows a paragraph to hold zero nodes but
   * forbids an empty text node, and JIRA rejects the whole request when it finds one.
   */
  @SuppressWarnings("unchecked")
  private static JSONObject paragraph(String line) {
    JSONObject paragraph = new JSONObject();
    paragraph.put("type", "paragraph");
    if (!line.isEmpty()) {
      JSONObject text = new JSONObject();
      text.put("type", "text");
      text.put("text", line);
      JSONArray content = new JSONArray();
      content.add(text);
      paragraph.put("content", content);
    }
    return paragraph;
  }

  /**
   * Splits on line breaks, normalizing the Windows and classic Mac forms, and keeps trailing empty lines
   * (which String.split drops).
   */
  private static String[] splitLines(String text) {
    return text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
  }

  private AdfDocument() {}
}

package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.util.LogHelper;
import org.almworks.util.Util;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Compares Atlassian Document Format (ADF) values by structure rather than by text.<br>
 * Two documents that mean the same thing routinely differ as strings: key order is not significant in JSON,
 * org.json.simple parses whole numbers as Long while {@link AdfDocument} builds them as Integer, and JIRA may
 * echo an empty "content" array where the client omitted the key. A string comparison would report every
 * field as changed, so both sides are reduced to a canonical form first.
 */
public class AdfCanonical {
  /**
   * Keys whose value carries no meaning when empty, so an absent key and an empty one compare equal.
   * Deliberately not applied to every key: elsewhere an empty value may be a real difference.
   */
  private static final String CONTENT = "content";
  private static final String MARKS = "marks";
  private static final List<String> COLLAPSE_WHEN_EMPTY = Arrays.asList(CONTENT, MARKS, "attrs");

  /**
   * Reduces a parsed JSON value to a form where equal structures give equal strings.<br>
   * Text is left exactly as it stands: the stored value is the server's own document, and normalizing
   * whitespace here would hide a real formatting change.
   */
  public static String canonicalize(@Nullable Object json) {
    StringBuilder out = new StringBuilder();
    append(json, out);
    return out.toString();
  }

  public static boolean areEqual(@Nullable Object a, @Nullable Object b) {
    if (a == null || b == null) return a == null && b == null;
    return canonicalize(a).equals(canonicalize(b));
  }

  /**
   * Compares two serialized ADF documents. Values that cannot be parsed are compared as plain strings, so
   * malformed stored data degrades to the previous behavior instead of reporting a spurious difference.
   */
  public static boolean areEqualRaw(@Nullable String a, @Nullable String b) {
    if (a == null || b == null) return a == null && b == null;
    if (a.equals(b)) return true;
    JSONObject parsedA = parse(a);
    JSONObject parsedB = parse(b);
    if (parsedA == null || parsedB == null) return false;
    return areEqual(parsedA, parsedB);
  }

  /**
   * Parses a stored ADF document.
   * @return null when the value is absent or not a JSON object. Logs at warning level rather than error
   * because stored data can predate any given format change, and a SEVERE record fails the test suite.
   */
  @Nullable
  public static JSONObject parse(@Nullable String json) {
    if (json == null || json.isEmpty()) return null;
    Object parsed;
    try {
      parsed = new JSONParser().parse(json);
    } catch (org.json.simple.parser.ParseException e) {
      LogHelper.warning("AdfCanonical: cannot parse stored ADF", e.getMessage());
      return null;
    }
    JSONObject object = Util.castNullable(JSONObject.class, parsed);
    if (object == null) LogHelper.warning("AdfCanonical: stored ADF is not an object", parsed);
    return object;
  }

  private static void append(@Nullable Object value, StringBuilder out) {
    append(value, out, false);
  }

  private static void append(@Nullable Object value, StringBuilder out, boolean unordered) {
    if (value == null) {
      out.append('~');
      return;
    }
    JSONObject object = Util.castNullable(JSONObject.class, value);
    if (object != null) {
      appendObject(object, out);
      return;
    }
    JSONArray array = Util.castNullable(JSONArray.class, value);
    if (array != null) {
      appendArray(array, out, unordered);
      return;
    }
    Number number = Util.castNullable(Number.class, value);
    if (number != null) {
      appendNumber(number, out);
      return;
    }
    Boolean bool = Util.castNullable(Boolean.class, value);
    if (bool != null) {
      out.append(bool ? "b1" : "b0");
      return;
    }
    appendString(String.valueOf(value), out);
  }

  /**
   * Arrays keep their order, since content order is meaning, with one exception: a node's marks are a set, so
   * text that is bold-then-italic must compare equal to the same text italic-then-bold. Without this any
   * converter that rebuilds a document would report a difference purely from the order it emits marks in.
   */
  private static void appendArray(JSONArray array, StringBuilder out, boolean unordered) {
    List<String> parts = new ArrayList<String>(array.size());
    for (Object child : array) {
      StringBuilder childOut = new StringBuilder();
      append(child, childOut);
      parts.add(childOut.toString());
    }
    if (unordered) Collections.sort(parts);
    out.append('[');
    for (String part : parts) out.append(part).append(';');
    out.append(']');
  }

  /**
   * Keys are emitted in sorted order, so the same document written by different producers compares equal.
   */
  @SuppressWarnings("unchecked")
  private static void appendObject(JSONObject object, StringBuilder out) {
    List<String> keys = new ArrayList<String>();
    for (Object key : ((Map<Object, Object>) object).keySet()) if (key != null) keys.add(String.valueOf(key));
    Collections.sort(keys);
    out.append('{');
    for (String key : keys) {
      Object value = object.get(key);
      if (value == null) continue;
      if (isEmptyCollapsible(key, value)) continue;
      appendString(key, out);
      out.append('=');
      if (CONTENT.equals(key)) append(mergeAdjacentText(value), out, false);
      else append(value, out, MARKS.equals(key));
      out.append(';');
    }
    out.append('}');
  }

  /**
   * Merges neighbouring text nodes that carry the same marks.<br>
   * A server document often splits a run of identically formatted text across several nodes, while anything
   * rebuilding that document emits one node per run. The two are the same document, so they must compare
   * equal - otherwise every rebuilt field would look changed.
   */
  @SuppressWarnings("unchecked")
  private static Object mergeAdjacentText(Object content) {
    JSONArray array = Util.castNullable(JSONArray.class, content);
    if (array == null) return content;
    JSONArray merged = new JSONArray();
    JSONObject pending = null;
    for (Object child : array) {
      JSONObject node = Util.castNullable(JSONObject.class, child);
      if (node == null || !isPlainTextNode(node)) {
        if (pending != null) merged.add(pending);
        pending = null;
        merged.add(child);
        continue;
      }
      if (pending != null && sameMarks(pending, node)) {
        JSONObject combined = new JSONObject();
        combined.putAll(pending);
        combined.put("text", String.valueOf(pending.get("text")) + node.get("text"));
        pending = combined;
      } else {
        if (pending != null) merged.add(pending);
        pending = node;
      }
    }
    if (pending != null) merged.add(pending);
    return merged;
  }

  private static boolean isPlainTextNode(JSONObject node) {
    return "text".equals(node.get("type")) && node.get("text") instanceof String;
  }

  private static boolean sameMarks(JSONObject a, JSONObject b) {
    StringBuilder outA = new StringBuilder();
    StringBuilder outB = new StringBuilder();
    append(a.get(MARKS) == null ? new JSONArray() : a.get(MARKS), outA, true);
    append(b.get(MARKS) == null ? new JSONArray() : b.get(MARKS), outB, true);
    return outA.toString().equals(outB.toString());
  }

  private static boolean isEmptyCollapsible(String key, Object value) {
    if (!COLLAPSE_WHEN_EMPTY.contains(key)) return false;
    JSONArray array = Util.castNullable(JSONArray.class, value);
    if (array != null) return array.isEmpty();
    JSONObject object = Util.castNullable(JSONObject.class, value);
    return object != null && object.isEmpty();
  }

  /**
   * Whole numbers collapse to one form regardless of the width the JSON library chose for them.
   */
  private static void appendNumber(Number number, StringBuilder out) {
    double asDouble = number.doubleValue();
    if (asDouble == Math.floor(asDouble) && !Double.isInfinite(asDouble)) out.append('n').append(number.longValue());
    else out.append('d').append(asDouble);
  }

  /**
   * Length-prefixed so that no text content can imitate the surrounding structure.
   */
  private static void appendString(String value, StringBuilder out) {
    out.append('s').append(value.length()).append(':').append(value);
  }

  private AdfCanonical() {}
}

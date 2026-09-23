/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr;

import android.graphics.RectF;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.experimental.UtilityClass;

/**
 * Utility to parse a compact JSON representation of OCR words ("words_json") into a list of
 * RecognizedWord instances.
 *
 * <p>Primary format (array of objects): [ { "text": "Hello", "left": 10.5, "top": 20.0, "right":
 * 50.0, "bottom": 35.0, "confidence": 0.92 }, ... ]
 *
 * <p>Tolerant variants supported (best-effort): - Root can be an array or an object wrapping the
 * array under one of: "words", "data", "items". - Bounding boxes: - Flat fields:
 * left/top/right/bottom (numbers or numeric strings). - bbox object with left/top/right/bottom or
 * short l/t/r/b. - bbox as array [left,top,right,bottom] or [x,y,w,h]. - Flat x/y/w/h (or
 * width/height) → right=x+w, bottom=y+h. - xmin/xmax/ymin/ymax. - Confidence can be 0..1 or 0..100
 * (int/float/string). Values are clamped to [0,1]. - Malformed entries are skipped; parser never
 * throws.
 */
@UtilityClass
public final class WordsJson {

  public static List<RecognizedWord> parseFile(File file) throws IOException {
    byte[] data;
    try (FileInputStream fis = new FileInputStream(file)) {
      data = readAllBytesCompat(fis);
    }
    String json = new String(data, StandardCharsets.UTF_8);
    return parse(json);
  }

  public static List<RecognizedWord> parse(String json) {
    List<RecognizedWord> out = new ArrayList<>();
    try {
      JsonElement root = com.google.gson.JsonParser.parseString(json);
      JsonArray arr = extractArrayRoot(root);
      if (arr == null) return out;
      for (JsonElement el : arr) {
        if (el == null || !el.isJsonObject()) continue;
        JsonObject o = el.getAsJsonObject();
        String text = optString(o, "text", "");
        // Decode any numeric HTML entities (e.g., &#39; for apostrophe)
        text = OCRHelper.decodeNumericEntities(text);
        float conf = normalizeConfidence(optNumber(o, "confidence", null));

        RectF rect = extractRect(o);
        if (rect == null) continue; // skip if no rectangle found

        try {
          RecognizedWord w = new RecognizedWord(text, rect, conf);
          out.add(w);
        } catch (Throwable ignore) {
          // skip malformed item
        }
      }
    } catch (Throwable ignore) {
      // return partially parsed results (possibly empty)
    }
    return out;
  }

  // ---- tolerant extraction helpers ----

  private static JsonArray extractArrayRoot(JsonElement root) {
    try {
      if (root == null || root.isJsonNull()) return null;
      if (root.isJsonArray()) return root.getAsJsonArray();
      if (root.isJsonObject()) {
        JsonObject obj = root.getAsJsonObject();
        // common container keys
        String[] keys = {"words", "data", "items", "list"};
        for (String k : keys) {
          if (obj.has(k) && obj.get(k).isJsonArray()) return obj.getAsJsonArray(k);
        }
        // sometimes payload is directly under "result" or similar
        if (obj.has("result") && obj.get("result").isJsonArray())
          return obj.getAsJsonArray("result");
      }
    } catch (Throwable ignore) {
      // Best-effort; failure is non-critical
    }
    return null;
  }

  private static RectF extractRect(JsonObject o) {
    try {
      // 1) Flat left/top/right/bottom
      Float L = optNumber(o, "left", null);
      Float T = optNumber(o, "top", null);
      Float R = optNumber(o, "right", null);
      Float B = optNumber(o, "bottom", null);
      if (isRectComplete(L, T, R, B)) return makeRect(L, T, R, B);

      // 2) bbox object
      if (o.has("bbox")) {
        JsonElement bb = o.get("bbox");
        if (bb.isJsonObject()) {
          JsonObject box = bb.getAsJsonObject();
          L = orElse(optNumber(box, "left", null), optNumber(box, "l", null));
          T = orElse(optNumber(box, "top", null), optNumber(box, "t", null));
          R = orElse(optNumber(box, "right", null), optNumber(box, "r", null));
          B = orElse(optNumber(box, "bottom", null), optNumber(box, "b", null));
          if (isRectComplete(L, T, R, B)) return makeRect(L, T, R, B);
          // bbox with x/y/w/h
          Float x = optNumber(box, "x", null);
          Float y = optNumber(box, "y", null);
          Float w = orElse(optNumber(box, "w", null), optNumber(box, "width", null));
          Float h = orElse(optNumber(box, "h", null), optNumber(box, "height", null));
          if (x != null && y != null && w != null && h != null) return makeRect(x, y, x + w, y + h);
          // bbox array [l,t,r,b] or [x,y,w,h]
          if (!bb.isJsonObject() && bb.isJsonArray()) {
            // handled below, but JsonElement bb is object here; ignore
          }
        } else if (bb.isJsonArray()) {
          RectF r = rectFromArray(bb.getAsJsonArray());
          if (r != null) return r;
        }
      }

      // 3) Flat x/y/w/h on the item
      Float x = optNumber(o, "x", null);
      Float y = optNumber(o, "y", null);
      Float w = orElse(optNumber(o, "w", null), optNumber(o, "width", null));
      Float h = orElse(optNumber(o, "h", null), optNumber(o, "height", null));
      if (x != null && y != null && w != null && h != null) return makeRect(x, y, x + w, y + h);

      // 4) xmin/xmax/ymin/ymax
      Float xmin = optNumber(o, "xmin", null);
      Float xmax = optNumber(o, "xmax", null);
      Float ymin = optNumber(o, "ymin", null);
      Float ymax = optNumber(o, "ymax", null);
      if (isRectComplete(xmin, ymin, xmax, ymax)) return makeRect(xmin, ymin, xmax, ymax);

      // 5) bbox array present directly on item
      if (o.has("bbox") && o.get("bbox").isJsonArray()) {
        RectF r = rectFromArray(o.getAsJsonArray("bbox"));
        if (r != null) return r;
      }
    } catch (Throwable ignore) {
      // Best-effort; failure is non-critical
    }
    return null;
  }

  private static RectF rectFromArray(JsonArray arr) {
    try {
      if (arr == null) return null;
      if (arr.size() < 4) return null;
      Float a0 = toFloat(arr.get(0));
      Float a1 = toFloat(arr.get(1));
      Float a2 = toFloat(arr.get(2));
      Float a3 = toFloat(arr.get(3));
      // Attempt [l,t,r,b]
      if (a0 != null && a1 != null && a2 != null && a3 != null) {
        // Heuristic: if a2>a0 and a3>a1 it can be either; if a2<=a0 or a3<=a1 but a2+a0 etc., fall
        // back to x,y,w,h
        if (a2 > a0 && a3 > a1) return makeRect(a0, a1, a2, a3);
        // Treat as [x,y,w,h]
        return makeRect(a0, a1, a0 + a2, a1 + a3);
      }
    } catch (Throwable ignore) {
      // Best-effort; failure is non-critical
    }
    return null;
  }

  private static RectF makeRect(Float l, Float t, Float r, Float b) {
    if (!isFinite(l) || !isFinite(t) || !isFinite(r) || !isFinite(b)) return null;
    return new RectF(l, t, r, b);
  }

  private static boolean isRectComplete(Float l, Float t, Float r, Float b) {
    return l != null && t != null && r != null && b != null;
  }

  private static Float orElse(Float a, Float b) {
    return a != null ? a : b;
  }

  private static float normalizeConfidence(Float c) {
    try {
      if (c == null) return 0f;
      float v = c;
      if (Float.isNaN(v) || Float.isInfinite(v)) return 0f;
      if (v > 1f) v = v / 100f; // accept 0..100
      if (v < 0f) v = 0f;
      if (v > 1f) v = 1f;
      return v;
    } catch (Throwable ignore) {
      return 0f;
    }
  }

  private static String optString(JsonObject o, String key, String def) {
    try {
      return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    } catch (Throwable e) {
      return def;
    }
  }

  private static Float optNumber(JsonObject o, String key, Float def) {
    try {
      if (!o.has(key) || o.get(key).isJsonNull()) return def;
      JsonElement el = o.get(key);
      if (el.isJsonPrimitive()) {
        if (el.getAsJsonPrimitive().isNumber()) return el.getAsFloat();
        if (el.getAsJsonPrimitive().isString()) return parseFloatSafe(el.getAsString(), def);
        if (el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean() ? 1f : 0f;
      }
      return def;
    } catch (Throwable e) {
      return def;
    }
  }

  private static Float toFloat(JsonElement el) {
    try {
      if (el == null || el.isJsonNull()) return null;
      if (el.isJsonPrimitive()) {
        if (el.getAsJsonPrimitive().isNumber()) return el.getAsFloat();
        if (el.getAsJsonPrimitive().isString()) return parseFloatSafe(el.getAsString(), null);
        if (el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean() ? 1f : 0f;
      }
    } catch (Throwable ignore) {
      // Best-effort; failure is non-critical
    }
    return null;
  }

  private static Float parseFloatSafe(String s, Float def) {
    try {
      if (s == null) return def;
      String t = s.trim();
      if (t.isEmpty()) return def;
      return Float.parseFloat(t);
    } catch (Throwable ignore) {
      return def;
    }
  }

  private static boolean isFinite(float f) {
    return !Float.isNaN(f) && !Float.isInfinite(f);
  }

  private static byte[] readAllBytesCompat(FileInputStream fis) throws IOException {
    byte[] buffer = new byte[8192];
    int read;
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    while ((read = fis.read(buffer)) != -1) {
      baos.write(buffer, 0, read);
    }
    return baos.toByteArray();
  }

  public static String toWordsJson(List<RecognizedWord> words) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    if (words != null) {
      boolean first = true;
      for (RecognizedWord w : words) {
        if (w == null) continue;
        RectF r = w.getBoundingBox();
        if (!first) sb.append(',');
        first = false;
        float conf = 0f; // confidence is 0..1
        try {
          conf = w.getConfidence();
        } catch (Throwable ignore) {
          // Best-effort; failure is non-critical
        }
        sb.append('{')
            .append("\"text\":")
            .append(escapeJsonString(w.getText()))
            .append(',')
            .append("\"left\":")
            .append(formatFloat(r.left))
            .append(',')
            .append("\"top\":")
            .append(formatFloat(r.top))
            .append(',')
            .append("\"right\":")
            .append(formatFloat(r.right))
            .append(',')
            .append("\"bottom\":")
            .append(formatFloat(r.bottom))
            .append(',')
            .append("\"confidence\":")
            .append(formatFloat(conf))
            .append('}');
      }
    }
    sb.append(']');
    return sb.toString();
  }

  public static String escapeJsonString(String s) {
    if (s == null) return "\"\"";
    StringBuilder out = new StringBuilder();
    out.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':
          out.append("\\\"");
          break;
        case '\\':
          out.append("\\\\");
          break;
        case '\b':
          out.append("\\b");
          break;
        case '\f':
          out.append("\\f");
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\t':
          out.append("\\t");
          break;
        default:
          if (c < 0x20) {
            out.append(String.format(Locale.US, "\\u%04x", (int) c));
          } else {
            out.append(c);
          }
      }
    }
    out.append('"');
    return out.toString();
  }

  public static String formatFloat(float f) {
    // Use US locale to ensure dot decimal separator
    return String.format(Locale.US, "%.6f", f);
  }
}

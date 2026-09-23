/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.ocr.review.store;

import android.util.Log;
import de.schliweb.makeacopy.ui.ocr.review.model.OcrDoc;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Simple JSON store for the editable OCR document (schema 1). Uses a compact representation per the
 * concept document.
 */
public class OcrJsonStore {
  private static final String TAG = "OcrJsonStore";

  public static OcrDoc load(File file) {
    if (file == null || !file.exists()) return new OcrDoc();
    try (BufferedReader br =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) sb.append(line);
      JSONObject root = new JSONObject(sb.toString());
      return parse(root);
    } catch (Exception e) {
      Log.w(TAG, "Failed to load OCR JSON: " + e.getMessage());
      return new OcrDoc();
    }
  }

  public static boolean save(File file, OcrDoc doc) {
    if (file == null || doc == null) return false;
    try (FileOutputStream fos = new FileOutputStream(file)) {
      JSONObject root = toJson(doc);
      byte[] data = root.toString().getBytes(StandardCharsets.UTF_8);
      fos.write(data);
      return true;
    } catch (Exception e) {
      Log.w(TAG, "Failed to save OCR JSON: " + e.getMessage());
      return false;
    }
  }

  private static OcrDoc parse(JSONObject root) throws JSONException {
    OcrDoc doc = new OcrDoc();
    doc.schema = root.optInt("schema", 1);
    JSONObject img = root.optJSONObject("imageSize");
    if (img != null) {
      doc.imageSize.w = img.optInt("w", 0);
      doc.imageSize.h = img.optInt("h", 0);
    }
    JSONArray words = root.optJSONArray("words");
    if (words != null) {
      for (int i = 0; i < words.length(); i++) {
        JSONObject w = words.getJSONObject(i);
        OcrDoc.Word word = new OcrDoc.Word();
        word.id = w.optInt("id", i + 1);
        word.t = w.optString("t", "");
        JSONArray bb = w.optJSONArray("b");
        if (bb != null && bb.length() == 4) {
          for (int j = 0; j < 4; j++) word.b[j] = bb.optInt(j, 0);
        }
        word.c = (float) w.optDouble("c", 0.0);
        word.e = w.optBoolean("e", false);
        word.l = w.optInt("l", 0);
        word.k = w.optInt("k", 0);
        word.lang = w.optString("lang", null);
        doc.words.add(word);
      }
    }
    JSONArray lines = root.optJSONArray("lines");
    if (lines != null) {
      for (int i = 0; i < lines.length(); i++) {
        JSONObject ln = lines.getJSONObject(i);
        OcrDoc.Line line = new OcrDoc.Line();
        line.id = ln.optInt("id", i + 1);
        JSONArray wi = ln.optJSONArray("w");
        if (wi != null) {
          line.w = new int[wi.length()];
          for (int j = 0; j < wi.length(); j++) line.w[j] = wi.optInt(j, 0);
        }
        JSONArray bb = ln.optJSONArray("b");
        if (bb != null && bb.length() == 4) {
          for (int j = 0; j < 4; j++) line.b[j] = bb.optInt(j, 0);
        }
        doc.lines.add(line);
      }
    }
    JSONArray blocks = root.optJSONArray("blocks");
    if (blocks != null) {
      for (int i = 0; i < blocks.length(); i++) {
        JSONObject bl = blocks.getJSONObject(i);
        OcrDoc.Block block = new OcrDoc.Block();
        block.id = bl.optInt("id", i + 1);
        JSONArray li = bl.optJSONArray("l");
        if (li != null) {
          block.l = new int[li.length()];
          for (int j = 0; j < li.length(); j++) block.l[j] = li.optInt(j, 0);
        }
        JSONArray bb = bl.optJSONArray("b");
        if (bb != null && bb.length() == 4) {
          for (int j = 0; j < 4; j++) block.b[j] = bb.optInt(j, 0);
        }
        doc.blocks.add(block);
      }
    }
    return doc;
  }

  private static JSONObject toJson(OcrDoc doc) throws JSONException {
    JSONObject root = new JSONObject();
    root.put("schema", doc.schema);
    JSONObject img = new JSONObject();
    img.put("w", doc.imageSize.w);
    img.put("h", doc.imageSize.h);
    root.put("imageSize", img);

    JSONArray words = new JSONArray();
    for (OcrDoc.Word w : doc.words) {
      JSONObject jw = new JSONObject();
      jw.put("id", w.id);
      jw.put("t", w.t == null ? "" : w.t);
      JSONArray bb = new JSONArray();
      for (int j = 0; j < 4; j++) bb.put(w.b[j]);
      jw.put("b", bb);
      jw.put("c", (double) w.c);
      jw.put("e", w.e);
      jw.put("l", w.l);
      jw.put("k", w.k);
      if (w.lang != null) jw.put("lang", w.lang);
      words.put(jw);
    }
    root.put("words", words);

    JSONArray lines = new JSONArray();
    for (OcrDoc.Line ln : doc.lines) {
      JSONObject jl = new JSONObject();
      jl.put("id", ln.id);
      JSONArray wi = new JSONArray();
      if (ln.w != null) for (int v : ln.w) wi.put(v);
      jl.put("w", wi);
      JSONArray bb = new JSONArray();
      for (int j = 0; j < 4; j++) bb.put(ln.b[j]);
      jl.put("b", bb);
      lines.put(jl);
    }
    root.put("lines", lines);

    JSONArray blocks = new JSONArray();
    for (OcrDoc.Block bl : doc.blocks) {
      JSONObject jb = new JSONObject();
      jb.put("id", bl.id);
      JSONArray li = new JSONArray();
      if (bl.l != null) for (int v : bl.l) li.put(v);
      jb.put("l", li);
      JSONArray bb = new JSONArray();
      for (int j = 0; j < 4; j++) bb.put(bl.b[j]);
      jb.put("b", bb);
      blocks.put(jb);
    }
    root.put("blocks", blocks);

    // meta optional omitted for MVP
    return root;
  }
}

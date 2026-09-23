/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.ocr.review.model;

import java.util.ArrayList;

/** Utilities for working with OcrDoc (deep copy for undo/redo). */
public final class OcrDocUtils {
  private OcrDocUtils() {}

  public static OcrDoc deepCopy(OcrDoc src) {
    if (src == null) return new OcrDoc();
    OcrDoc d = new OcrDoc();
    d.schema = src.schema;
    if (src.imageSize != null) {
      d.imageSize.w = src.imageSize.w;
      d.imageSize.h = src.imageSize.h;
    }
    // words
    if (src.words != null) {
      d.words = new ArrayList<>(src.words.size());
      for (OcrDoc.Word w : src.words) {
        if (w == null) {
          d.words.add(null);
          continue;
        }
        OcrDoc.Word nw = new OcrDoc.Word();
        nw.id = w.id;
        nw.t = w.t;
        nw.b = new int[] {w.b[0], w.b[1], w.b[2], w.b[3]};
        nw.c = w.c;
        nw.e = w.e;
        nw.l = w.l;
        nw.k = w.k;
        nw.lang = w.lang;
        d.words.add(nw);
      }
    }
    // lines
    if (src.lines != null) {
      d.lines = new ArrayList<>(src.lines.size());
      for (OcrDoc.Line ln : src.lines) {
        if (ln == null) {
          d.lines.add(null);
          continue;
        }
        OcrDoc.Line nl = new OcrDoc.Line();
        nl.id = ln.id;
        if (ln.w != null) {
          nl.w = new int[ln.w.length];
          System.arraycopy(ln.w, 0, nl.w, 0, ln.w.length);
        }
        nl.b = new int[] {ln.b[0], ln.b[1], ln.b[2], ln.b[3]};
        d.lines.add(nl);
      }
    }
    // blocks
    if (src.blocks != null) {
      d.blocks = new ArrayList<>(src.blocks.size());
      for (OcrDoc.Block bl : src.blocks) {
        if (bl == null) {
          d.blocks.add(null);
          continue;
        }
        OcrDoc.Block nb = new OcrDoc.Block();
        nb.id = bl.id;
        if (bl.l != null) {
          nb.l = new int[bl.l.length];
          System.arraycopy(bl.l, 0, nb.l, 0, bl.l.length);
        }
        nb.b = new int[] {bl.b[0], bl.b[1], bl.b[2], bl.b[3]};
        d.blocks.add(nb);
      }
    }
    return d;
  }
}

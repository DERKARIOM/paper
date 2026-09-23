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
import java.util.List;

/**
 * Minimal data model for editable OCR (schema 1) used by the Review screen. This is intentionally
 * decoupled from utils.RecognizedWord to avoid breaking existing code.
 */
public class OcrDoc {
  public int schema = 1;
  public ImageSize imageSize = new ImageSize();
  public List<Word> words = new ArrayList<>();
  public List<Line> lines = new ArrayList<>();
  public List<Block> blocks = new ArrayList<>();

  public static class ImageSize {
    public int w;
    public int h;

    public ImageSize() {}

    public ImageSize(int w, int h) {
      this.w = w;
      this.h = h;
    }
  }

  public static class Word {
    public int id;
    public String t; // text
    public int[] b = new int[4]; // [x,y,w,h] in image px
    public float c; // confidence 0..1
    public boolean e; // edited flag
    public int l; // lineId
    public int k; // blockId
    public String lang; // optional language
  }

  public static class Line {
    public int id;
    public int[] w; // word ids
    public int[] b = new int[4]; // bbox [x,y,w,h]
  }

  public static class Block {
    public int id;
    public int[] l; // line ids
    public int[] b = new int[4];
  }
}

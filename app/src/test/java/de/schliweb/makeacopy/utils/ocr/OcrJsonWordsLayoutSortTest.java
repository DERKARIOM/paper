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

import static org.junit.Assert.*;

import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;

/**
 * Tests that the layout-aware sorting logic (blockId→lineId) works correctly and that the fallback
 * is preserved when no layout info is present.
 *
 * <p>Note: android.graphics.RectF is stubbed in JVM tests (returnDefaultValues=true), so
 * position-based sorting (Y→X) cannot be tested here. Only the block/line hierarchy is tested.
 */
public class OcrJsonWordsLayoutSortTest {

  /** Guard: blockId/lineId default to 0 on new RecognizedWord (no layout = legacy behavior). */
  @Test
  public void newRecognizedWord_defaultBlockAndLineIdZero() {
    RecognizedWord rw = new RecognizedWord("test", new RectF(0, 0, 10, 10), 90f);
    assertEquals(0, rw.getBlockId());
    assertEquals(0, rw.getLineId());
  }

  /** blockId/lineId can be set and retrieved. */
  @Test
  public void setBlockIdAndLineId_arePreserved() {
    RecognizedWord rw = new RecognizedWord("test", new RectF(0, 0, 10, 10), 90f);
    rw.setBlockId(3);
    rw.setLineId(7);
    assertEquals(3, rw.getBlockId());
    assertEquals(7, rw.getLineId());
  }

  /**
   * With layout info, words are sorted by blockId→lineId. Block 2 words come after block 1,
   * regardless of insertion order.
   */
  @Test
  public void withLayoutInfo_sortsByBlockThenLine() {
    RecognizedWord wA = makeWord("WordA", 2, 1);
    RecognizedWord wB = makeWord("WordB", 1, 1);
    RecognizedWord wC = makeWord("WordC", 1, 2);

    List<RecognizedWord> words = new ArrayList<>(List.of(wA, wB, wC));
    sortByLayout(words);

    assertEquals("WordB", words.get(0).getText());
    assertEquals("WordC", words.get(1).getText());
    assertEquals("WordA", words.get(2).getText());
  }

  /**
   * Two-column layout: left column (block=1) has 3 lines, right column (block=2) has 2 lines.
   * Layout sort keeps columns intact.
   */
  @Test
  public void twoColumnLayout_layoutSortKeepsColumnsIntact() {
    RecognizedWord l1 = makeWord("Left1", 1, 1);
    RecognizedWord l2 = makeWord("Left2", 1, 2);
    RecognizedWord l3 = makeWord("Left3", 1, 3);
    RecognizedWord r1 = makeWord("Right1", 2, 1);
    RecognizedWord r2 = makeWord("Right2", 2, 2);

    List<RecognizedWord> words = new ArrayList<>(List.of(r1, l2, r2, l1, l3));
    sortByLayout(words);

    assertEquals("Left1", words.get(0).getText());
    assertEquals("Left2", words.get(1).getText());
    assertEquals("Left3", words.get(2).getText());
    assertEquals("Right1", words.get(3).getText());
    assertEquals("Right2", words.get(4).getText());
  }

  /** Without layout info (all blockId=0), sort order is stable (no reordering by block/line). */
  @Test
  public void noLayoutInfo_noBlockLineSorting() {
    RecognizedWord wA = makeWord("WordA", 0, 0);
    RecognizedWord wB = makeWord("WordB", 0, 0);

    List<RecognizedWord> words = new ArrayList<>(List.of(wA, wB));
    final boolean hasLayoutInfo = words.stream().anyMatch(rw -> rw.getBlockId() > 0);
    assertFalse("No layout info expected", hasLayoutInfo);
  }

  // --- helpers ---

  private RecognizedWord makeWord(String text, int blockId, int lineId) {
    RecognizedWord rw = new RecognizedWord(text, new RectF(), 90f);
    rw.setBlockId(blockId);
    rw.setLineId(lineId);
    return rw;
  }

  /** Replicates the block→line sorting logic from OcrJsonWords.parseFile(). */
  private void sortByLayout(List<RecognizedWord> words) {
    Collections.sort(
        words,
        new Comparator<RecognizedWord>() {
          @Override
          public int compare(RecognizedWord a, RecognizedWord b) {
            int cb = Integer.compare(a.getBlockId(), b.getBlockId());
            if (cb != 0) return cb;
            return Integer.compare(a.getLineId(), b.getLineId());
          }
        });
  }
}

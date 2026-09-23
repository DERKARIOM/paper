/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for the focus-quality normalization, decay, and segment mapping logic. */
public class FocusQualityMeterTest {

  @Test
  public void firstSampleBecomesRollingMaxAndScoresFull() {
    FocusQualityMeter meter = new FocusQualityMeter();
    double score = meter.update(500.0, 0L);
    // First sample defines the rolling max → normalized score 1.0 (EMA initialized directly).
    assertEquals(1.0, score, 1e-9);
    assertEquals(500.0, meter.rollingMax(), 1e-9);
  }

  @Test
  public void lowerValuesScoreRelativeToRollingMax() {
    FocusQualityMeter meter = new FocusQualityMeter();
    meter.update(1000.0, 0L);
    // Feed a much blurrier value repeatedly; EMA converges towards 0.1.
    double score = 0;
    for (int i = 1; i <= 50; i++) {
      score = meter.update(100.0, i); // 1 ms steps → negligible decay
    }
    assertEquals(0.1, score, 0.01);
  }

  @Test
  public void scoreIsAlwaysWithinZeroAndOne() {
    FocusQualityMeter meter = new FocusQualityMeter();
    long ts = 0;
    double[] samples = {0.0, 1e-9, 5.0, 1e6, 3.0, 1e7, 0.0};
    for (double s : samples) {
      double score = meter.update(s, ts);
      ts += 200;
      assertTrue("score out of range: " + score, score >= 0.0 && score <= 1.0);
    }
  }

  @Test
  public void rollingMaxDecaysSoIndicatorCannotGetStuck() {
    FocusQualityMeter meter = new FocusQualityMeter();
    meter.update(1000.0, 0L);
    // After one half-life the stale peak should have decayed to ~500.
    meter.update(0.0, FocusQualityMeter.MAX_DECAY_HALF_LIFE_MS);
    assertEquals(500.0, meter.rollingMax(), 1.0);
    // After many half-lives the max decays down to the floor.
    meter.update(0.0, 20 * FocusQualityMeter.MAX_DECAY_HALF_LIFE_MS);
    assertEquals(FocusQualityMeter.VARIANCE_FLOOR, meter.rollingMax(), 1e-6);
  }

  @Test
  public void decayRestoresResponsivenessAfterSceneChange() {
    FocusQualityMeter meter = new FocusQualityMeter();
    meter.update(10_000.0, 0L); // text-dense scene peak
    // New, sparser scene: same moderate sharpness reads low at first…
    double early = meter.update(500.0, 1000L);
    // …but after the old peak decays, the same value scores clearly higher.
    double late = early;
    for (int i = 0; i < 200; i++) {
      late = meter.update(500.0, 1000L + (i + 1) * 500L);
    }
    assertTrue("late score should exceed early score", late > early);
    assertTrue("late score should approach 1.0, was " + late, late > 0.9);
  }

  @Test
  public void emaSmoothesSuddenJumps() {
    FocusQualityMeter meter = new FocusQualityMeter();
    meter.update(1000.0, 0L); // score EMA = 1.0
    // A single blurry outlier must not crash the score to its raw value immediately.
    double score = meter.update(0.0, 200L);
    assertTrue("EMA should damp the jump, was " + score, score > 0.5);
  }

  @Test
  public void resetClearsAllState() {
    FocusQualityMeter meter = new FocusQualityMeter();
    meter.update(1000.0, 0L);
    meter.reset();
    assertEquals(FocusQualityMeter.VARIANCE_FLOOR, meter.rollingMax(), 1e-9);
    // After reset the next sample again defines the maximum.
    assertEquals(1.0, meter.update(123.0, 5L), 1e-9);
  }

  @Test
  public void segmentMappingCoversAllLevels() {
    assertEquals(0, FocusQualityMeter.segmentsForScore(0.0));
    assertEquals(0, FocusQualityMeter.segmentsForScore(-1.0));
    assertEquals(0, FocusQualityMeter.segmentsForScore(Double.NaN));
    assertEquals(1, FocusQualityMeter.segmentsForScore(0.01));
    assertEquals(1, FocusQualityMeter.segmentsForScore(0.2));
    assertEquals(2, FocusQualityMeter.segmentsForScore(0.4));
    assertEquals(3, FocusQualityMeter.segmentsForScore(0.6));
    assertEquals(4, FocusQualityMeter.segmentsForScore(0.8));
    assertEquals(5, FocusQualityMeter.segmentsForScore(0.81));
    assertEquals(5, FocusQualityMeter.segmentsForScore(1.0));
    assertEquals(5, FocusQualityMeter.segmentsForScore(2.0));
  }

  @Test
  public void bandMappingMatchesSegments() {
    assertEquals(FocusQualityMeter.Band.LOW, FocusQualityMeter.bandForSegments(0));
    assertEquals(FocusQualityMeter.Band.LOW, FocusQualityMeter.bandForSegments(1));
    assertEquals(FocusQualityMeter.Band.LOW, FocusQualityMeter.bandForSegments(2));
    assertEquals(FocusQualityMeter.Band.GOOD, FocusQualityMeter.bandForSegments(3));
    assertEquals(FocusQualityMeter.Band.GOOD, FocusQualityMeter.bandForSegments(4));
    assertEquals(FocusQualityMeter.Band.EXCELLENT, FocusQualityMeter.bandForSegments(5));
  }

  @Test
  public void varianceFloorPreventsNoiseFromScoringSharp() {
    FocusQualityMeter meter = new FocusQualityMeter();
    // Tiny variances on featureless scenes are normalized against the floor, not themselves.
    double score = meter.update(0.5, 0L);
    assertTrue("near-zero variance should score low, was " + score, score < 0.2);
  }
}

package de.schliweb.makeacopy;

import static org.junit.Assert.assertEquals;

import de.schliweb.makeacopy.ui.camera.ExposureCompensationHelper;
import org.junit.Test;

public class ExposureCompensationHelperTest {

  // --- clampIndex ---

  @Test
  public void clampIndex_withinRange_returnsUnchanged() {
    assertEquals(3, ExposureCompensationHelper.clampIndex(3, -6, 6));
  }

  @Test
  public void clampIndex_belowRange_clampsToLower() {
    assertEquals(-6, ExposureCompensationHelper.clampIndex(-10, -6, 6));
  }

  @Test
  public void clampIndex_aboveRange_clampsToUpper() {
    assertEquals(6, ExposureCompensationHelper.clampIndex(12, -6, 6));
  }

  @Test
  public void clampIndex_atBoundaries_returnsExact() {
    assertEquals(-6, ExposureCompensationHelper.clampIndex(-6, -6, 6));
    assertEquals(6, ExposureCompensationHelper.clampIndex(6, -6, 6));
  }

  @Test
  public void clampIndex_smallRange_clampsCorrectly() {
    // Device with range -2..2
    assertEquals(-2, ExposureCompensationHelper.clampIndex(-5, -2, 2));
    assertEquals(2, ExposureCompensationHelper.clampIndex(5, -2, 2));
    assertEquals(0, ExposureCompensationHelper.clampIndex(0, -2, 2));
  }

  // --- indexToEv ---

  @Test
  public void indexToEv_halfStep() {
    assertEquals(1.5f, ExposureCompensationHelper.indexToEv(3, 0.5f), 0.001f);
    assertEquals(-1.0f, ExposureCompensationHelper.indexToEv(-2, 0.5f), 0.001f);
  }

  @Test
  public void indexToEv_thirdStep() {
    float step = 1f / 3f;
    assertEquals(1.0f, ExposureCompensationHelper.indexToEv(3, step), 0.01f);
    assertEquals(-2.0f, ExposureCompensationHelper.indexToEv(-6, step), 0.01f);
  }

  @Test
  public void indexToEv_zero() {
    assertEquals(0f, ExposureCompensationHelper.indexToEv(0, 0.5f), 0.001f);
  }

  // --- formatEv ---

  @Test
  public void formatEv_positive() {
    assertEquals("+1.5", ExposureCompensationHelper.formatEv(1.5f));
  }

  @Test
  public void formatEv_negative() {
    assertEquals("-2.0", ExposureCompensationHelper.formatEv(-2.0f));
  }

  @Test
  public void formatEv_zero() {
    assertEquals("+0.0", ExposureCompensationHelper.formatEv(0f));
  }

  // --- progressToIndex / indexToProgress ---

  @Test
  public void progressToIndex_standardRange() {
    // range -6..6, seekbar 0..12
    assertEquals(-6, ExposureCompensationHelper.progressToIndex(0, -6));
    assertEquals(0, ExposureCompensationHelper.progressToIndex(6, -6));
    assertEquals(6, ExposureCompensationHelper.progressToIndex(12, -6));
  }

  @Test
  public void indexToProgress_standardRange() {
    assertEquals(0, ExposureCompensationHelper.indexToProgress(-6, -6));
    assertEquals(6, ExposureCompensationHelper.indexToProgress(0, -6));
    assertEquals(12, ExposureCompensationHelper.indexToProgress(6, -6));
  }

  @Test
  public void progressAndIndex_roundTrip() {
    int lower = -4;
    for (int idx = -4; idx <= 4; idx++) {
      int progress = ExposureCompensationHelper.indexToProgress(idx, lower);
      assertEquals(idx, ExposureCompensationHelper.progressToIndex(progress, lower));
    }
  }

  // --- clampRangeLower / clampRangeUpper ---

  @Test
  public void clampRangeLower_restrictsWideRange() {
    // Device range -24..24 with step 1/6 → ±4 EV; MAX_EV=2.0 → minIndex = round(-2.0/0.1667) = -12
    float step = 1f / 6f;
    assertEquals(-12, ExposureCompensationHelper.clampRangeLower(-24, step));
  }

  @Test
  public void clampRangeUpper_restrictsWideRange() {
    float step = 1f / 6f;
    assertEquals(12, ExposureCompensationHelper.clampRangeUpper(24, step));
  }

  @Test
  public void clampRangeLower_narrowRangeUnchanged() {
    // Device range -4..4 with step 0.5 → MAX_EV=2.0 → minIndex = -4; device already -4
    assertEquals(-4, ExposureCompensationHelper.clampRangeLower(-4, 0.5f));
  }

  @Test
  public void clampRangeUpper_narrowRangeUnchanged() {
    assertEquals(4, ExposureCompensationHelper.clampRangeUpper(4, 0.5f));
  }

  @Test
  public void clampRangeLower_halfStep() {
    // step=0.5, MAX_EV=2.0 → minIndex = round(-4) = -4
    assertEquals(-4, ExposureCompensationHelper.clampRangeLower(-6, 0.5f));
  }

  @Test
  public void clampRangeUpper_halfStep() {
    assertEquals(4, ExposureCompensationHelper.clampRangeUpper(6, 0.5f));
  }

  @Test
  public void clampRangeLower_thirdStep() {
    // step=1/3, MAX_EV=2.0 → minIndex = round(-6) = -6
    float step = 1f / 3f;
    assertEquals(-6, ExposureCompensationHelper.clampRangeLower(-12, step));
  }

  @Test
  public void clampRangeUpper_thirdStep() {
    float step = 1f / 3f;
    assertEquals(6, ExposureCompensationHelper.clampRangeUpper(12, step));
  }

  @Test
  public void clampRangeLower_zeroStep_returnsDeviceValue() {
    assertEquals(-24, ExposureCompensationHelper.clampRangeLower(-24, 0f));
  }

  @Test
  public void clampRangeUpper_zeroStep_returnsDeviceValue() {
    assertEquals(24, ExposureCompensationHelper.clampRangeUpper(24, 0f));
  }
}

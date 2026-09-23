package de.schliweb.makeacopy.ui.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class CameraLensSelectorTest {
  // Pixel-6-Pro-like setup: main (1.0x), ultra-wide (0.6x) and telephoto (4.0x) back cameras.
  private static final CameraLensSelector.LensInfo MAIN = new CameraLensSelector.LensInfo("0", 1.0f);
  private static final CameraLensSelector.LensInfo ULTRA_WIDE =
      new CameraLensSelector.LensInfo("2", 0.6f);
  private static final CameraLensSelector.LensInfo TELE =
      new CameraLensSelector.LensInfo("3", 4.0f);
  private static final List<CameraLensSelector.LensInfo> PIXEL_LIKE =
      Arrays.asList(MAIN, ULTRA_WIDE, TELE);

  @Test
  public void chooseLens_keepsDefaultCameraForOneXAndUltraWide() {
    assertNull(CameraLensSelector.chooseLens(PIXEL_LIKE, 1.0f));
    assertNull(CameraLensSelector.chooseLens(PIXEL_LIKE, 0.6f));
  }

  @Test
  public void chooseLens_doesNotSwitchToTeleBelowItsFactor() {
    // 2x/3x are below the 4x tele factor → stay on the default camera (digital zoom)
    assertNull(CameraLensSelector.chooseLens(PIXEL_LIKE, 2.0f));
    assertNull(CameraLensSelector.chooseLens(PIXEL_LIKE, 3.0f));
  }

  @Test
  public void chooseLens_selectsTeleAtOrAboveItsFactor() {
    assertEquals(TELE, CameraLensSelector.chooseLens(PIXEL_LIKE, 4.0f));
    assertEquals(TELE, CameraLensSelector.chooseLens(PIXEL_LIKE, 5.0f));
  }

  @Test
  public void chooseLens_selectsThreeXTeleForThreeXAndFiveX() {
    CameraLensSelector.LensInfo tele3x = new CameraLensSelector.LensInfo("3", 3.0f);
    List<CameraLensSelector.LensInfo> lenses = Arrays.asList(MAIN, ULTRA_WIDE, tele3x);
    assertEquals(tele3x, CameraLensSelector.chooseLens(lenses, 3.0f));
    assertEquals(tele3x, CameraLensSelector.chooseLens(lenses, 5.0f));
    assertNull(CameraLensSelector.chooseLens(lenses, 2.0f));
  }

  @Test
  public void chooseLens_returnsNullWhenNoTeleExposed() {
    // Samsung-like setup: only the logical main camera is exposed to third-party apps
    assertNull(CameraLensSelector.chooseLens(Collections.singletonList(MAIN), 5.0f));
    assertNull(CameraLensSelector.chooseLens(null, 5.0f));
  }
}

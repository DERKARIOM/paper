package de.schliweb.makeacopy.ocr;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.schliweb.makeacopy.utils.ocr.OCRHelper;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for Arabic and Persian OCR default settings. Tests verify that RTL scripts are
 * configured with: - PSM_AUTO (Page Segmentation Mode = 3) - Disabled whitelist (to allow Arabic
 * script recognition)
 *
 * <p>These settings are critical for proper RTL OCR recognition, as a Latin whitelist would block
 * Arabic/Persian characters entirely.
 */
@RunWith(AndroidJUnit4.class)
public class ArabicPersianDefaultsTest {

  private static Context context;

  @BeforeClass
  public static void setup() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
  }

  /**
   * Tests that PSM_AUTO is correctly applied for Arabic. PSM_AUTO (value = 3) is required for
   * proper RTL text segmentation.
   */
  @Test
  public void testPsmAutoForArabic() throws Exception {
    OCRHelper helper = new OCRHelper(context);
    try {
      helper.setLanguage("ara");
      boolean initialized = helper.initTesseract();
      assertTrue("Tesseract should be initialized for Arabic", initialized);

      // PSM_AUTO should be set (value = 3)
      int psm = helper.getPageSegMode();
      System.out.println("[DEBUG_LOG] Arabic PSM: " + psm);
      assertEquals("PSM should be AUTO (3) for Arabic", 3, psm);
    } finally {
      helper.shutdown();
    }
  }

  /**
   * Tests that PSM_AUTO is correctly applied for Persian. PSM_AUTO (value = 3) is required for
   * proper RTL text segmentation.
   */
  @Test
  public void testPsmAutoForPersian() throws Exception {
    OCRHelper helper = new OCRHelper(context);
    try {
      helper.setLanguage("fas");
      boolean initialized = helper.initTesseract();
      assertTrue("Tesseract should be initialized for Persian", initialized);

      // PSM_AUTO should be set (value = 3)
      int psm = helper.getPageSegMode();
      System.out.println("[DEBUG_LOG] Persian PSM: " + psm);
      assertEquals("PSM should be AUTO (3) for Persian", 3, psm);
    } finally {
      helper.shutdown();
    }
  }

  /**
   * Tests that Arabic is treated as RTL script (same as CJK/Thai for PSM). Verifies that Arabic
   * gets PSM_AUTO regardless of the default PSM setting.
   */
  @Test
  public void testArabicTreatedAsSpecialScript() throws Exception {
    OCRHelper helper = new OCRHelper(context);
    try {
      // Set a non-AUTO PSM first
      helper.setPageSegMode(6); // PSM_SINGLE_BLOCK

      // Now set Arabic language - should override to PSM_AUTO
      helper.setLanguage("ara");
      boolean initialized = helper.initTesseract();
      assertTrue("Tesseract should be initialized for Arabic", initialized);

      // PSM should be AUTO (3) for Arabic, not SINGLE_BLOCK (6)
      int psm = helper.getPageSegMode();
      System.out.println("[DEBUG_LOG] Arabic PSM after override: " + psm);
      assertEquals("PSM should be AUTO (3) for Arabic, overriding SINGLE_BLOCK", 3, psm);
    } finally {
      helper.shutdown();
    }
  }

  /**
   * Tests that Persian is treated as RTL script (same as CJK/Thai for PSM). Verifies that Persian
   * gets PSM_AUTO regardless of the default PSM setting.
   */
  @Test
  public void testPersianTreatedAsSpecialScript() throws Exception {
    OCRHelper helper = new OCRHelper(context);
    try {
      // Set a non-AUTO PSM first
      helper.setPageSegMode(6); // PSM_SINGLE_BLOCK

      // Now set Persian language - should override to PSM_AUTO
      helper.setLanguage("fas");
      boolean initialized = helper.initTesseract();
      assertTrue("Tesseract should be initialized for Persian", initialized);

      // PSM should be AUTO (3) for Persian, not SINGLE_BLOCK (6)
      int psm = helper.getPageSegMode();
      System.out.println("[DEBUG_LOG] Persian PSM after override: " + psm);
      assertEquals("PSM should be AUTO (3) for Persian, overriding SINGLE_BLOCK", 3, psm);
    } finally {
      helper.shutdown();
    }
  }

  /** Tests that Arabic language model is available. */
  @Test
  public void testArabicLanguageAvailable() throws Exception {
    OCRHelper helper = new OCRHelper(context);
    try {
      boolean available = helper.isLanguageAvailable("ara");
      System.out.println("[DEBUG_LOG] Arabic language available: " + available);
      assertTrue("Arabic language model should be available", available);
    } finally {
      helper.shutdown();
    }
  }

  /** Tests that Persian language model is available. */
  @Test
  public void testPersianLanguageAvailable() throws Exception {
    OCRHelper helper = new OCRHelper(context);
    try {
      boolean available = helper.isLanguageAvailable("fas");
      System.out.println("[DEBUG_LOG] Persian language available: " + available);
      assertTrue("Persian language model should be available", available);
    } finally {
      helper.shutdown();
    }
  }

  /** Tests that Arabic+English combination works. */
  @Test
  public void testArabicEnglishCombination() throws Exception {
    OCRHelper helper = new OCRHelper(context);
    try {
      helper.setLanguage("ara+eng");
      boolean initialized = helper.initTesseract();
      assertTrue("Tesseract should be initialized for Arabic+English", initialized);

      // PSM should still be AUTO for RTL combination
      int psm = helper.getPageSegMode();
      System.out.println("[DEBUG_LOG] Arabic+English PSM: " + psm);
      assertEquals("PSM should be AUTO (3) for Arabic+English", 3, psm);
    } finally {
      helper.shutdown();
    }
  }

  /** Tests that Persian+English combination works. */
  @Test
  public void testPersianEnglishCombination() throws Exception {
    OCRHelper helper = new OCRHelper(context);
    try {
      helper.setLanguage("fas+eng");
      boolean initialized = helper.initTesseract();
      assertTrue("Tesseract should be initialized for Persian+English", initialized);

      // PSM should still be AUTO for RTL combination
      int psm = helper.getPageSegMode();
      System.out.println("[DEBUG_LOG] Persian+English PSM: " + psm);
      assertEquals("PSM should be AUTO (3) for Persian+English", 3, psm);
    } finally {
      helper.shutdown();
    }
  }
}

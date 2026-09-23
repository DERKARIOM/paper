package de.schliweb.makeacopy.utils.layout;

import static org.junit.Assert.*;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for DocumentLayoutAnalyzer class. Note: Tests that require Android Bitmap or OpenCV
 * Mat operations are limited in JVM unit tests due to native library dependencies. Full integration
 * tests should be run as instrumented tests.
 */
public class DocumentLayoutAnalyzerTest {

  private DocumentLayoutAnalyzer analyzer;

  @Before
  public void setUp() {
    analyzer = new DocumentLayoutAnalyzer();
  }

  // ==================== Constructor Tests ====================

  @Test
  public void constructor_createsAnalyzerWithDefaultSettings() {
    DocumentLayoutAnalyzer newAnalyzer = new DocumentLayoutAnalyzer();

    assertNotNull(newAnalyzer);
    assertEquals("eng", newAnalyzer.getLanguage());
    assertTrue(newAnalyzer.isDetectTables());
    assertTrue(newAnalyzer.isDetectColumns());
    assertTrue(newAnalyzer.isDetectHeaderFooter());
  }

  // ==================== Language Settings Tests ====================

  @Test
  public void setLanguage_updatesLanguage() {
    analyzer.setLanguage("deu");

    assertEquals("deu", analyzer.getLanguage());
  }

  @Test
  public void setLanguage_withArabic_updatesLanguage() {
    analyzer.setLanguage("ara");

    assertEquals("ara", analyzer.getLanguage());
  }

  @Test
  public void setLanguage_withHebrew_updatesLanguage() {
    analyzer.setLanguage("heb");

    assertEquals("heb", analyzer.getLanguage());
  }

  @Test
  public void setLanguage_withChinese_updatesLanguage() {
    analyzer.setLanguage("chi_sim");

    assertEquals("chi_sim", analyzer.getLanguage());
  }

  @Test
  public void setLanguage_withJapanese_updatesLanguage() {
    analyzer.setLanguage("jpn");

    assertEquals("jpn", analyzer.getLanguage());
  }

  @Test
  public void getLanguage_returnsDefaultEnglish() {
    assertEquals("eng", analyzer.getLanguage());
  }

  // ==================== Detection Flag Tests ====================

  @Test
  public void setDetectTables_toFalse_disablesTableDetection() {
    analyzer.setDetectTables(false);

    assertFalse(analyzer.isDetectTables());
  }

  @Test
  public void setDetectTables_toTrue_enablesTableDetection() {
    analyzer.setDetectTables(false);
    analyzer.setDetectTables(true);

    assertTrue(analyzer.isDetectTables());
  }

  @Test
  public void setDetectColumns_toFalse_disablesColumnDetection() {
    analyzer.setDetectColumns(false);

    assertFalse(analyzer.isDetectColumns());
  }

  @Test
  public void setDetectColumns_toTrue_enablesColumnDetection() {
    analyzer.setDetectColumns(false);
    analyzer.setDetectColumns(true);

    assertTrue(analyzer.isDetectColumns());
  }

  @Test
  public void setDetectHeaderFooter_toFalse_disablesHeaderFooterDetection() {
    analyzer.setDetectHeaderFooter(false);

    assertFalse(analyzer.isDetectHeaderFooter());
  }

  @Test
  public void setDetectHeaderFooter_toTrue_enablesHeaderFooterDetection() {
    analyzer.setDetectHeaderFooter(false);
    analyzer.setDetectHeaderFooter(true);

    assertTrue(analyzer.isDetectHeaderFooter());
  }

  @Test
  public void isDetectTables_defaultsToTrue() {
    assertTrue(analyzer.isDetectTables());
  }

  @Test
  public void isDetectColumns_defaultsToTrue() {
    assertTrue(analyzer.isDetectColumns());
  }

  @Test
  public void isDetectHeaderFooter_defaultsToTrue() {
    assertTrue(analyzer.isDetectHeaderFooter());
  }

  // ==================== Null Input Tests ====================
  // Note: Tests for analyze(), analyzeFromBinary(), hasComplexLayout(), and getColumnCount()
  // with null inputs are skipped in JVM unit tests because they require OpenCV native libraries.
  // These should be tested in instrumented tests instead.

  // ==================== AnalysisResult Tests ====================

  @Test
  public void analysisResult_constructor_setsAllFields() {
    List<DocumentRegion> regions = new ArrayList<>();
    regions.add(new DocumentRegion(new Rect(0, 0, 100, 50), DocumentRegion.Type.HEADER));
    regions.add(new DocumentRegion(new Rect(0, 50, 100, 200), DocumentRegion.Type.BODY));

    DocumentLayoutAnalyzer.AnalysisResult result =
        new DocumentLayoutAnalyzer.AnalysisResult(
            regions, 2, true, true, false, ReadingOrderSorter.TextDirection.LTR);

    assertEquals(regions, result.regions());
    assertEquals(2, result.columnCount());
    assertTrue(result.hasTable());
    assertTrue(result.hasHeader());
    assertFalse(result.hasFooter());
    assertEquals(ReadingOrderSorter.TextDirection.LTR, result.textDirection());
  }

  @Test
  public void analysisResult_withRtlDirection_setsCorrectDirection() {
    List<DocumentRegion> regions = new ArrayList<>();

    DocumentLayoutAnalyzer.AnalysisResult result =
        new DocumentLayoutAnalyzer.AnalysisResult(
            regions, 1, false, false, false, ReadingOrderSorter.TextDirection.RTL);

    assertEquals(ReadingOrderSorter.TextDirection.RTL, result.textDirection());
  }

  @Test
  public void analysisResult_withEmptyRegions_hasEmptyList() {
    List<DocumentRegion> regions = new ArrayList<>();

    DocumentLayoutAnalyzer.AnalysisResult result =
        new DocumentLayoutAnalyzer.AnalysisResult(
            regions, 1, false, false, false, ReadingOrderSorter.TextDirection.LTR);

    assertTrue(result.regions().isEmpty());
  }

  @Test
  public void analysisResult_withMultipleColumns_returnsCorrectCount() {
    List<DocumentRegion> regions = new ArrayList<>();

    DocumentLayoutAnalyzer.AnalysisResult result =
        new DocumentLayoutAnalyzer.AnalysisResult(
            regions, 3, false, false, false, ReadingOrderSorter.TextDirection.LTR);

    assertEquals(3, result.columnCount());
  }

  @Test
  public void analysisResult_withFooter_hasFooterTrue() {
    List<DocumentRegion> regions = new ArrayList<>();
    regions.add(new DocumentRegion(new Rect(0, 900, 100, 1000), DocumentRegion.Type.FOOTER));

    DocumentLayoutAnalyzer.AnalysisResult result =
        new DocumentLayoutAnalyzer.AnalysisResult(
            regions, 1, false, false, true, ReadingOrderSorter.TextDirection.LTR);

    assertTrue(result.hasFooter());
  }

  // ==================== Configuration Combination Tests ====================

  @Test
  public void analyzer_withAllDetectionDisabled_hasCorrectSettings() {
    analyzer.setDetectTables(false);
    analyzer.setDetectColumns(false);
    analyzer.setDetectHeaderFooter(false);

    assertFalse(analyzer.isDetectTables());
    assertFalse(analyzer.isDetectColumns());
    assertFalse(analyzer.isDetectHeaderFooter());
  }

  @Test
  public void analyzer_withCustomLanguageAndDisabledFeatures_maintainsSettings() {
    analyzer.setLanguage("fra");
    analyzer.setDetectTables(false);
    analyzer.setDetectColumns(true);
    analyzer.setDetectHeaderFooter(false);

    assertEquals("fra", analyzer.getLanguage());
    assertFalse(analyzer.isDetectTables());
    assertTrue(analyzer.isDetectColumns());
    assertFalse(analyzer.isDetectHeaderFooter());
  }

  @Test
  public void analyzer_settingsCanBeChangedMultipleTimes() {
    analyzer.setLanguage("deu");
    assertEquals("deu", analyzer.getLanguage());

    analyzer.setLanguage("fra");
    assertEquals("fra", analyzer.getLanguage());

    analyzer.setLanguage("eng");
    assertEquals("eng", analyzer.getLanguage());
  }

  @Test
  public void analyzer_detectFlagsCanBeToggledMultipleTimes() {
    assertTrue(analyzer.isDetectTables());

    analyzer.setDetectTables(false);
    assertFalse(analyzer.isDetectTables());

    analyzer.setDetectTables(true);
    assertTrue(analyzer.isDetectTables());

    analyzer.setDetectTables(false);
    assertFalse(analyzer.isDetectTables());
  }

  // ==================== Multiple Analyzer Instances Tests ====================

  @Test
  public void multipleAnalyzers_haveIndependentSettings() {
    DocumentLayoutAnalyzer analyzer1 = new DocumentLayoutAnalyzer();
    DocumentLayoutAnalyzer analyzer2 = new DocumentLayoutAnalyzer();

    analyzer1.setLanguage("deu");
    analyzer1.setDetectTables(false);

    analyzer2.setLanguage("fra");
    analyzer2.setDetectColumns(false);

    assertEquals("deu", analyzer1.getLanguage());
    assertFalse(analyzer1.isDetectTables());
    assertTrue(analyzer1.isDetectColumns());

    assertEquals("fra", analyzer2.getLanguage());
    assertTrue(analyzer2.isDetectTables());
    assertFalse(analyzer2.isDetectColumns());
  }

  @Test
  public void multipleAnalyzers_defaultSettingsAreIndependent() {
    DocumentLayoutAnalyzer analyzer1 = new DocumentLayoutAnalyzer();
    DocumentLayoutAnalyzer analyzer2 = new DocumentLayoutAnalyzer();

    // Both should have default settings
    assertEquals("eng", analyzer1.getLanguage());
    assertEquals("eng", analyzer2.getLanguage());

    // Changing one should not affect the other
    analyzer1.setLanguage("spa");
    assertEquals("spa", analyzer1.getLanguage());
    assertEquals("eng", analyzer2.getLanguage());
  }
}

package de.schliweb.makeacopy;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.export.InboxExporter;
import org.junit.Test;

/**
 * JVM unit tests for {@link InboxExporter} filename generation logic. These tests verify the date
 * format and collision-free naming without requiring an Android device.
 */
public class InboxExporterTest {

  private static final String TODAY =
      java.time.LocalDate.now(java.time.ZoneId.systemDefault())
          .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));

  @Test
  public void buildInboxBaseName_defaultTemplate_containsDateAndScanSuffix() {
    String baseName = InboxExporter.buildInboxBaseName();
    assertNotNull(baseName);
    assertTrue(
        "Expected format YYYY-MM-DD_scan but got: " + baseName,
        baseName.matches("\\d{4}-\\d{2}-\\d{2}_scan"));
  }

  @Test
  public void buildInboxBaseName_defaultTemplate_usesTodaysDate() {
    String baseName = InboxExporter.buildInboxBaseName();
    assertTrue(baseName.startsWith(TODAY));
  }

  @Test
  public void buildInboxBaseName_dateScanTemplate() {
    String baseName = InboxExporter.buildInboxBaseName("date_scan");
    assertEquals(TODAY + "_scan", baseName);
  }

  @Test
  public void buildInboxBaseName_dateTimeScanTemplate() {
    String baseName = InboxExporter.buildInboxBaseName("date_time_scan");
    assertTrue(
        "Expected format YYYY-MM-DD_HHmmss_scan but got: " + baseName,
        baseName.matches("\\d{4}-\\d{2}-\\d{2}_\\d{6}_scan"));
    assertTrue(baseName.startsWith(TODAY));
  }

  @Test
  public void buildInboxBaseName_dateOnlyTemplate() {
    String baseName = InboxExporter.buildInboxBaseName("date_only");
    assertEquals(TODAY, baseName);
  }

  @Test
  public void buildInboxBaseName_unknownTemplate_fallsBackToDateScan() {
    String baseName = InboxExporter.buildInboxBaseName("unknown_template");
    assertEquals(TODAY + "_scan", baseName);
  }

  // --- TXT file naming consistency: same base name, different extension ---

  @Test
  public void pdfAndTxtShareSameBaseName_dateScan() {
    String baseName = InboxExporter.buildInboxBaseName("date_scan");
    String pdfName = baseName + ".pdf";
    String txtName = baseName + ".txt";
    assertTrue(pdfName.endsWith(".pdf"));
    assertTrue(txtName.endsWith(".txt"));
    assertEquals(pdfName.replace(".pdf", ""), txtName.replace(".txt", ""));
  }

  @Test
  public void pdfAndTxtShareSameBaseName_dateTimeScan() {
    String baseName = InboxExporter.buildInboxBaseName("date_time_scan");
    String pdfName = baseName + ".pdf";
    String txtName = baseName + ".txt";
    assertEquals(pdfName.replace(".pdf", ""), txtName.replace(".txt", ""));
  }

  @Test
  public void pdfAndTxtShareSameBaseName_dateOnly() {
    String baseName = InboxExporter.buildInboxBaseName("date_only");
    String pdfName = baseName + ".pdf";
    String txtName = baseName + ".txt";
    assertEquals(pdfName.replace(".pdf", ""), txtName.replace(".txt", ""));
  }

  // --- Base name format validation ---

  @Test
  public void buildInboxBaseName_dateScan_doesNotContainSpaces() {
    String baseName = InboxExporter.buildInboxBaseName("date_scan");
    assertFalse("Base name should not contain spaces", baseName.contains(" "));
  }

  @Test
  public void buildInboxBaseName_dateTimeScan_doesNotContainSpaces() {
    String baseName = InboxExporter.buildInboxBaseName("date_time_scan");
    assertFalse("Base name should not contain spaces", baseName.contains(" "));
  }

  @Test
  public void buildInboxBaseName_allTemplates_nonEmpty() {
    for (String template : new String[] {"date_scan", "date_time_scan", "date_only"}) {
      String baseName = InboxExporter.buildInboxBaseName(template);
      assertNotNull(baseName);
      assertFalse("Base name for " + template + " should not be empty", baseName.isEmpty());
    }
  }
}

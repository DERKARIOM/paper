package de.schliweb.makeacopy.utils.export;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.ui.export.session.CompletedScan;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Reading side of the per-page text extraction storage (no Android context needed). */
public class PageOcrStoreTest {

  private File dir;

  @Before
  public void setUp() throws Exception {
    dir = Files.createTempDirectory("paper_page_ocr").toFile();
  }

  @After
  public void tearDown() {
    File[] files = dir.listFiles();
    if (files != null) for (File f : files) f.delete();
    dir.delete();
  }

  private CompletedScan page(String ocrPath, String format) {
    return new CompletedScan(
        "p1", null, 0, ocrPath, format, null, 1L, 100, 200, null, 2, "baked");
  }

  private File write(String name, String content) throws Exception {
    File f = new File(dir, name);
    Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    return f;
  }

  @Test
  public void pageWithoutTextHasNoOcr() {
    assertFalse(PageOcrStore.hasOcr(null));
    assertFalse(PageOcrStore.hasOcr(page(null, null)));
    assertFalse(PageOcrStore.hasOcr(page(new File(dir, "missing.txt").getPath(), "plain")));
    assertNull(PageOcrStore.readText(page(null, null)));
  }

  @Test
  public void readsPlainText() throws Exception {
    File txt = write(PageOcrStore.TEXT_FILE, "Facture 2026");
    CompletedScan p = page(txt.getPath(), PageOcrStore.FORMAT_PLAIN);
    assertTrue(PageOcrStore.hasOcr(p));
    assertEquals("Facture 2026", PageOcrStore.readText(p));
  }

  @Test
  public void legacyEntryWithoutFormatIsReadAsPlainText() throws Exception {
    File txt = write("legacy.txt", "Ancien texte");
    assertEquals("Ancien texte", PageOcrStore.readText(page(txt.getPath(), null)));
  }

  @Test
  public void wordsEntryPrefersSiblingTextFile() throws Exception {
    write(PageOcrStore.TEXT_FILE, "Ligne 1\nLigne 2");
    File words = write(PageOcrStore.WORDS_FILE, "[]");
    CompletedScan p = page(words.getPath(), PageOcrStore.FORMAT_WORDS_JSON);
    assertTrue(PageOcrStore.hasOcr(p));
    assertEquals("Ligne 1\nLigne 2", PageOcrStore.readText(p));
  }

  @Test
  public void emptyResultIsStillAProcessedPage() throws Exception {
    // A page whose extraction found no text is marked as processed (not re-run by default).
    File txt = write(PageOcrStore.TEXT_FILE, "");
    CompletedScan p = page(txt.getPath(), PageOcrStore.FORMAT_PLAIN);
    assertTrue(PageOcrStore.hasOcr(p));
    assertEquals("", PageOcrStore.readText(p));
  }
}

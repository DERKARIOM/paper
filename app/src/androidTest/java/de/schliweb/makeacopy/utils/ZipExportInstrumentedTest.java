package de.schliweb.makeacopy.utils;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.schliweb.makeacopy.utils.export.jpeg.JpegExportOptions;
import de.schliweb.makeacopy.utils.export.jpeg.JpegExporter;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies ZIP export content and naming schema consistent with
 * ExportFragment.performJpegZipExport(): - Entries are named page_001.jpg, page_002.jpg, ... -
 * Number of entries equals number of pages - Each entry contains a valid JPEG decodable by
 * BitmapFactory
 */
@RunWith(AndroidJUnit4.class)
public class ZipExportInstrumentedTest {

  @Test
  public void testZipContentAndNamingSchema() throws Exception {
    Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

    // Create two simple bitmaps
    Bitmap p1 = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888);
    Bitmap p2 = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888);
    p1.eraseColor(Color.WHITE);
    p2.eraseColor(Color.WHITE);

    List<Bitmap> pages = Arrays.asList(p1, p2);

    File out = new File(ctx.getCacheDir(), "zip_export_test.zip");
    if (out.exists()) // noinspection ResultOfMethodCallIgnored
    out.delete();

    try (ZipOutputStream zos = new ZipOutputStream(new java.io.FileOutputStream(out))) {
      int idx = 1;
      for (Bitmap pageBmp : pages) {
        String name = String.format(Locale.getDefault(), "page_%03d.jpg", idx);
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        JpegExportOptions options = new JpegExportOptions(); // defaults
        boolean ok = JpegExporter.exportToStream(ctx, pageBmp, options, zos);
        zos.closeEntry();
        assertTrue("JPEG encoding failed for entry " + name, ok);
        idx++;
      }
      zos.finish();
      zos.flush();
    }

    assertTrue("ZIP not created", out.exists() && out.length() > 0);

    // Inspect ZIP entries
    try (ZipFile zip = new ZipFile(out)) {
      Enumeration<? extends ZipEntry> e = zip.entries();
      Map<String, ZipEntry> entries = new LinkedHashMap<>();
      while (e.hasMoreElements()) {
        ZipEntry ze = e.nextElement();
        entries.put(ze.getName(), ze);
      }
      assertEquals("Expected 2 entries", 2, entries.size());
      assertTrue("Missing page_001.jpg", entries.containsKey("page_001.jpg"));
      assertTrue("Missing page_002.jpg", entries.containsKey("page_002.jpg"));

      // Validate entries are JPEG-decodable
      for (String name : entries.keySet()) {
        ZipEntry ze = entries.get(name);
        try (InputStream is = zip.getInputStream(ze)) {
          android.graphics.Bitmap decoded = android.graphics.BitmapFactory.decodeStream(is);
          assertNotNull("Entry not decodable as JPEG: " + name, decoded);
          // optional: basic dimension sanity
          assertTrue(decoded.getWidth() > 0 && decoded.getHeight() > 0);
          decoded.recycle();
        }
      }
    } finally {
      // recycle bitmaps
      if (p1 != null && !p1.isRecycled()) p1.recycle();
      if (p2 != null && !p2.isRecycled()) p2.recycle();
    }
  }
}

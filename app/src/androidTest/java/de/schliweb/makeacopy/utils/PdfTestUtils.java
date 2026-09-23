package de.schliweb.makeacopy.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Utility class for working with PDF files in Android. Provides helper methods for rendering PDF
 * pages to bitmap images.
 */
public class PdfTestUtils {

  /**
   * Renders a specific page from a PDF file stored in the application's assets directory to a
   * bitmap image.
   *
   * @param ctx The application context used to access assets and cache directory.
   * @param assetPath The path to the PDF file within the assets directory.
   * @param pageIndex The index of the page to be rendered (0-based).
   * @return A Bitmap containing the rendered PDF page as an image.
   * @throws Exception If an error occurs during file handling, rendering, or bitmap creation.
   */
  public static Bitmap renderPdfAssetToBitmap(Context ctx, String assetPath, int pageIndex)
      throws Exception {
    InputStream in = ctx.getAssets().open(assetPath);
    File tmp = File.createTempFile("testpdf_", ".pdf", ctx.getCacheDir());
    try (FileOutputStream fos = new FileOutputStream(tmp)) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
    }
    try (ParcelFileDescriptor pfd =
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer renderer = new PdfRenderer(pfd);
        PdfRenderer.Page page = renderer.openPage(pageIndex)) {

      Bitmap bmp = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bmp);
      canvas.drawColor(0xFFFFFFFF);
      page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
      return bmp;
    }
  }
}

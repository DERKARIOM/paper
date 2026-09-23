package de.schliweb.makeacopy.a11y.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Programmatic synthetic frames for analyzer-driven tests. Avoids storing binary assets in VCS
 * while keeping deterministic inputs.
 */
public final class TestFrames {
  private TestFrames() {}

  /** Returns a small noise bitmap (no document). */
  public static Bitmap noise(int w, int h, long seed) {
    Bitmap bmp = Bitmap.createBitmap(Math.max(64, w), Math.max(64, h), Bitmap.Config.ARGB_8888);
    java.util.Random rnd = new java.util.Random(seed);
    int[] px = new int[bmp.getWidth() * bmp.getHeight()];
    for (int i = 0; i < px.length; i++) {
      int g = 32 + rnd.nextInt(160); // mid gray noise
      px[i] = Color.rgb(g, g, g);
    }
    bmp.setPixels(px, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());
    // Add faint gradients to avoid flat fields
    Canvas c = new Canvas(bmp);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(Color.argb(16, 0, 0, 0));
    c.drawRect(0, 0, bmp.getWidth(), bmp.getHeight(), p);
    return bmp;
  }

  /**
   * Returns a centered, white quadrilateral on dark background to resemble a document. Useful for
   * positive detection cases.
   */
  public static Bitmap centeredDoc(int w, int h) {
    // Prefer analyzer-friendly resolution for crisp edges (match analyzer stream ~1280x960)
    int W = Math.max(1280, w);
    int H = Math.max(960, h);
    Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    c.drawColor(Color.rgb(16, 16, 16));
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setStyle(Paint.Style.FILL);
    p.setColor(Color.WHITE);
    // Larger page footprint (~84-86% of the short side)
    float margin = Math.min(W, H) * 0.07f;
    RectF r = new RectF(margin, margin, W - margin, H - margin);
    Path path = new Path();
    // Slight perspective quadrilateral
    path.moveTo(r.left + r.width() * 0.05f, r.top);
    path.lineTo(r.right, r.top + r.height() * 0.08f);
    path.lineTo(r.right - r.width() * 0.05f, r.bottom);
    path.lineTo(r.left, r.bottom - r.height() * 0.08f);
    path.close();
    c.drawPath(path, p);
    // Add crisp borders (double-stroke) to help edge detectors after any scaling
    Paint inner = new Paint();
    inner.setAntiAlias(false);
    inner.setStyle(Paint.Style.STROKE);
    inner.setStrokeWidth(2f);
    inner.setColor(Color.WHITE);
    c.drawPath(path, inner);
    Paint border = new Paint();
    border.setAntiAlias(false);
    border.setStyle(Paint.Style.STROKE);
    border.setStrokeWidth(8f);
    border.setColor(Color.BLACK);
    c.drawPath(path, border);
    return bmp;
  }

  /**
   * Returns a document-like quadrilateral that is shifted to the left side to simulate off-center
   * framing. Useful to trigger directional guidance in future tests.
   */
  public static Bitmap offLeftDoc(int w, int h) {
    int W = Math.max(1280, w);
    int H = Math.max(960, h);
    Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    c.drawColor(Color.rgb(16, 16, 16));
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setStyle(Paint.Style.FILL);
    p.setColor(Color.WHITE);
    float margin = Math.min(W, H) * 0.07f;
    float shift = Math.min(W, H) * 0.30f; // stronger shift left for MOVE_RIGHT (raise dxNorm)
    RectF r = new RectF(margin - shift, margin, W - margin - shift, H - margin);
    Path path = new Path();
    path.moveTo(r.left + r.width() * 0.06f, r.top);
    path.lineTo(r.right, r.top + r.height() * 0.08f);
    path.lineTo(r.right - r.width() * 0.04f, r.bottom);
    path.lineTo(r.left, r.bottom - r.height() * 0.06f);
    path.close();
    c.drawPath(path, p);
    Paint inner = new Paint();
    inner.setAntiAlias(false);
    inner.setStyle(Paint.Style.STROKE);
    inner.setStrokeWidth(2f);
    inner.setColor(Color.WHITE);
    c.drawPath(path, inner);
    Paint border = new Paint();
    border.setAntiAlias(false);
    border.setStyle(Paint.Style.STROKE);
    border.setStrokeWidth(8f);
    border.setColor(Color.BLACK);
    c.drawPath(path, border);
    return bmp;
  }

  /**
   * Returns a strongly skewed quadrilateral to stress tilt/movement detection. Uses high-contrast
   * edges and large footprint for stable corner detection.
   */
  public static Bitmap extremeSkewDoc(int w, int h) {
    int W = Math.max(1280, w);
    int H = Math.max(960, h);
    Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    c.drawColor(Color.rgb(16, 16, 16));

    float margin = Math.min(W, H) * 0.07f;
    // Create a trapezoid with aggressive skew to the right-top
    RectF r = new RectF(margin, margin, W - margin, H - margin);

    Path path = new Path();
    // Push top-left far inward, top-right slightly outward, bottom-left slightly outward
    path.moveTo(r.left + r.width() * 0.35f, r.top); // TL inwards
    path.lineTo(r.right, r.top + r.height() * 0.15f); // TR down a bit
    path.lineTo(r.right - r.width() * 0.05f, r.bottom); // BR near bottom-right
    path.lineTo(r.left, r.bottom - r.height() * 0.05f); // BL near bottom-left
    path.close();

    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    fill.setStyle(Paint.Style.FILL);
    fill.setColor(Color.WHITE);
    c.drawPath(path, fill);

    // Double border to help edge detectors
    Paint inner = new Paint();
    inner.setAntiAlias(false);
    inner.setStyle(Paint.Style.STROKE);
    inner.setStrokeWidth(2f);
    inner.setColor(Color.WHITE);
    c.drawPath(path, inner);

    Paint border = new Paint();
    border.setAntiAlias(false);
    border.setStyle(Paint.Style.STROKE);
    border.setStrokeWidth(8f);
    border.setColor(Color.BLACK);
    c.drawPath(path, border);

    return bmp;
  }

  /**
   * Returns a low-contrast document-like frame. The page is light gray over a dark gray background,
   * with a crisp border so that corners remain detectable while the interior stays low-contrast.
   */
  public static Bitmap lowContrastDoc(int w, int h) {
    int W = Math.max(1280, w);
    int H = Math.max(960, h);
    Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    c.drawColor(Color.rgb(24, 24, 24)); // slightly brighter than offLeft/centered to reduce SNR

    float margin = Math.min(W, H) * 0.07f;
    RectF r = new RectF(margin, margin, W - margin, H - margin);

    Path path = new Path();
    // Mild perspective, mostly centered
    path.moveTo(r.left + r.width() * 0.08f, r.top + r.height() * 0.02f);
    path.lineTo(r.right - r.width() * 0.02f, r.top + r.height() * 0.06f);
    path.lineTo(r.right - r.width() * 0.06f, r.bottom - r.height() * 0.02f);
    path.lineTo(r.left + r.width() * 0.02f, r.bottom - r.height() * 0.06f);
    path.close();

    // Low-contrast fill: light gray instead of pure white
    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    fill.setStyle(Paint.Style.FILL);
    fill.setColor(Color.rgb(200, 200, 200));
    c.drawPath(path, fill);

    // Keep strong, crisp border so contours are still detectable
    Paint inner = new Paint();
    inner.setAntiAlias(false);
    inner.setStyle(Paint.Style.STROKE);
    inner.setStrokeWidth(2f);
    inner.setColor(Color.WHITE);
    c.drawPath(path, inner);

    Paint border = new Paint();
    border.setAntiAlias(false);
    border.setStyle(Paint.Style.STROKE);
    border.setStrokeWidth(8f);
    border.setColor(Color.BLACK);
    c.drawPath(path, border);

    return bmp;
  }

  /**
   * Optionally overlays faint horizontal stripes to hint text orientation (kept unused by default).
   */
  public static void addTextStripes(Bitmap bmp, int lines, int alpha) {
    if (bmp == null || lines <= 0 || alpha <= 0) return;
    Canvas c = new Canvas(bmp);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(Color.argb(Math.min(64, alpha), 160, 160, 160));
    p.setStrokeWidth(1f);
    float step = bmp.getHeight() / (float) (lines + 1);
    for (int i = 1; i <= lines; i++) {
      float y = step * i;
      c.drawLine(bmp.getWidth() * 0.15f, y, bmp.getWidth() * 0.85f, y, p);
    }
  }

  /**
   * Returns a document-like quadrilateral with a forward tilt (top narrower than bottom) to
   * simulate tilt guidance.
   */
  public static Bitmap tiltForwardDoc(int w, int h) {
    int W = Math.max(1280, w);
    int H = Math.max(960, h);
    Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    c.drawColor(Color.rgb(16, 16, 16));
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setStyle(Paint.Style.FILL);
    p.setColor(Color.WHITE);
    float margin = Math.min(W, H) * 0.07f;
    RectF r = new RectF(margin, margin, W - margin, H - margin);
    Path path = new Path();
    // Stronger perspective to suggest forward tilt (top edge shorter)
    float topInset = r.width() * 0.22f; // ~56% top width
    float bottomInset = r.width() * 0.04f; // ~92% bottom width
    path.moveTo(r.left + topInset, r.top);
    path.lineTo(r.right - topInset, r.top + r.height() * 0.02f);
    path.lineTo(r.right - bottomInset, r.bottom);
    path.lineTo(r.left + bottomInset, r.bottom - r.height() * 0.02f);
    path.close();
    c.drawPath(path, p);
    Paint inner = new Paint();
    inner.setAntiAlias(false);
    inner.setStyle(Paint.Style.STROKE);
    inner.setStrokeWidth(2f);
    inner.setColor(Color.WHITE);
    c.drawPath(path, inner);
    Paint border = new Paint();
    border.setAntiAlias(false);
    border.setStyle(Paint.Style.STROKE);
    border.setStrokeWidth(8f);
    border.setColor(Color.BLACK);
    c.drawPath(path, border);
    return bmp;
  }

  /**
   * Returns a partially occluded document where a rectangular mask covers a chunk of the page.
   * Useful for exercising guidance when corners are missing.
   */
  public static Bitmap occludedDoc(int w, int h) {
    int W = Math.max(320, w);
    int H = Math.max(240, h);
    Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    c.drawColor(Color.rgb(28, 28, 28));
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setStyle(Paint.Style.FILL);
    p.setColor(Color.WHITE);
    float margin = Math.min(W, H) * 0.12f;
    RectF r = new RectF(margin, margin, W - margin, H - margin);
    Path path = new Path();
    path.moveTo(r.left + r.width() * 0.06f, r.top + r.height() * 0.02f);
    path.lineTo(r.right - r.width() * 0.02f, r.top + r.height() * 0.07f);
    path.lineTo(r.right - r.width() * 0.04f, r.bottom - r.height() * 0.03f);
    path.lineTo(r.left + r.width() * 0.02f, r.bottom - r.height() * 0.09f);
    path.close();
    c.drawPath(path, p);

    // Occlude with a dark rectangle covering a quarter of the page
    p.setColor(Color.rgb(28, 28, 28));
    c.drawRect(
        r.centerX() - r.width() * 0.15f,
        r.centerY() - r.height() * 0.30f,
        r.centerX() + r.width() * 0.35f,
        r.centerY() + r.height() * 0.10f,
        p);
    return bmp;
  }
}

package de.schliweb.makeacopy.ml.corners;

import java.util.Random;

/**
 * Renders synthetic scan scenes for JVM tests: a page (convex quad, anti-aliased by 4x4
 * supersampling) with text-like dark lines, on a textured background, with sensor noise.
 */
final class SyntheticScene {
  final int w, h;
  final float[] luma;

  private SyntheticScene(int w, int h) {
    this.w = w;
    this.h = h;
    this.luma = new float[w * h];
  }

  LumaImage image() {
    return new LumaImage(w, h, luma);
  }

  /**
   * @param quad page corners TL, TR, BR, BL (or null for an empty scene)
   * @param paper page gray level
   * @param background mean background gray level
   * @param texture amplitude of a wood-like background texture
   * @param noise sensor noise sigma
   */
  static SyntheticScene render(
      int w,
      int h,
      double[][] quad,
      double paper,
      double background,
      double texture,
      double noise,
      long seed) {
    return render(w, h, quad, paper, background, texture, noise, seed, 0);
  }

  /**
   * @param printedFrame when &gt; 0, a dark printed frame (2% wide) is drawn at this fraction
   *     inside the page border (bordered flyers, forms)
   */
  static SyntheticScene render(
      int w,
      int h,
      double[][] quad,
      double paper,
      double background,
      double texture,
      double noise,
      long seed,
      double printedFrame) {
    SyntheticScene s = new SyntheticScene(w, h);
    Random rnd = new Random(seed);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        double bg =
            background + texture * Math.sin(x / 9.0 + 3 * Math.sin(y / 60.0)); // wood-like grain
        double v = bg;
        if (quad != null) {
          int in = 0;
          for (int sy = 0; sy < 4; sy++) {
            for (int sx = 0; sx < 4; sx++) {
              if (QuadGeometry.contains(quad, x + (sx + 0.5) / 4.0, y + (sy + 0.5) / 4.0)) in++;
            }
          }
          if (in > 0) {
            double page = paper;
            if (in == 16 && isText(quad, x, y)) page = paper * 0.15; // dark text strokes
            if (in == 16 && printedFrame > 0 && inFrame(quad, x, y, printedFrame)) page = 60;
            double cov = in / 16.0;
            v = bg * (1 - cov) + page * cov;
          }
        }
        v += rnd.nextGaussian() * noise;
        s.luma[y * w + x] = (float) Math.max(0, Math.min(255, v));
      }
    }
    return s;
  }

  /** Text lines in page coordinates (bilinear u,v inside the quad), away from the page border. */
  private static boolean isText(double[][] q, double x, double y) {
    double[] uv = inverseBilinear(q, x, y);
    if (uv == null) return false;
    double u = uv[0], v = uv[1];
    if (u < 0.08 || u > 0.92 || v < 0.06 || v > 0.94) return false;
    double row = v * 40;
    double frac = row - Math.floor(row);
    if (frac > 0.25) return false;
    // words: gaps every few characters
    double col = u * 30 + Math.floor(row) * 1.7;
    return (col - Math.floor(col)) < 0.8;
  }

  private static boolean inFrame(double[][] q, double x, double y, double inset) {
    double[] uv = inverseBilinear(q, x, y);
    if (uv == null) return false;
    double d = Math.min(Math.min(uv[0], 1 - uv[0]), Math.min(uv[1], 1 - uv[1]));
    return d >= inset && d <= inset + 0.02;
  }

  /** Approximate (u,v) of a point inside the quad by Newton iterations on the bilinear map. */
  private static double[] inverseBilinear(double[][] q, double x, double y) {
    double u = 0.5, v = 0.5;
    for (int it = 0; it < 8; it++) {
      double a = (1 - u) * (1 - v), b = u * (1 - v), c = u * v, d = (1 - u) * v;
      double px = a * q[0][0] + b * q[1][0] + c * q[2][0] + d * q[3][0];
      double py = a * q[0][1] + b * q[1][1] + c * q[2][1] + d * q[3][1];
      double dxu = -(1 - v) * q[0][0] + (1 - v) * q[1][0] + v * q[2][0] - v * q[3][0];
      double dyu = -(1 - v) * q[0][1] + (1 - v) * q[1][1] + v * q[2][1] - v * q[3][1];
      double dxv = -(1 - u) * q[0][0] - u * q[1][0] + u * q[2][0] + (1 - u) * q[3][0];
      double dyv = -(1 - u) * q[0][1] - u * q[1][1] + u * q[2][1] + (1 - u) * q[3][1];
      double det = dxu * dyv - dxv * dyu;
      if (Math.abs(det) < 1e-9) return null;
      double ex = x - px, ey = y - py;
      u += (ex * dyv - ey * dxv) / det;
      v += (dxu * ey - dyu * ex) / det;
    }
    return new double[] {u, v};
  }

  /** A4-like page rotated by {@code deg} around the image center, with a mild perspective. */
  static double[][] page(int w, int h, double deg, double scale, double persp) {
    double ph = scale * h, pw = ph / 1.414;
    double[][] p = {{-pw / 2, -ph / 2}, {pw / 2, -ph / 2}, {pw / 2, ph / 2}, {-pw / 2, ph / 2}};
    p[0][0] *= 1 - persp; // top narrower
    p[1][0] *= 1 - persp;
    double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
    double[][] out = new double[4][2];
    for (int i = 0; i < 4; i++) {
      out[i][0] = w / 2.0 + p[i][0] * c - p[i][1] * s;
      out[i][1] = h / 2.0 + p[i][0] * s + p[i][1] * c;
    }
    return QuadGeometry.orderClockwise(out);
  }

  static double maxError(double[][] a, double[][] b) {
    return QuadGeometry.maxCornerDistance(a, b);
  }

  static double[][] perturb(double[][] q, double amount, long seed) {
    Random r = new Random(seed);
    double[][] out = new double[4][2];
    for (int i = 0; i < 4; i++) {
      out[i][0] = q[i][0] + (r.nextDouble() * 2 - 1) * amount;
      out[i][1] = q[i][1] + (r.nextDouble() * 2 - 1) * amount;
    }
    return out;
  }
}

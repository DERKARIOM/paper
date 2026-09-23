/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.layout;

import android.graphics.Rect;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Mat;

/**
 * Detects columns in multi-column document layouts using vertical projection analysis. This class
 * analyzes binary document images to identify column boundaries for proper reading order
 * reconstruction.
 */
public class ColumnDetector {

  private static final String TAG = "ColumnDetector";

  /**
   * Minimum width of a valley (gap between columns) as fraction of image width. Valleys narrower
   * than this are ignored.
   */
  private static final double MIN_VALLEY_WIDTH_RATIO = 0.015;

  /**
   * Minimum column width as fraction of image width. Columns narrower than this are merged with
   * neighbors.
   */
  private static final double MIN_COLUMN_WIDTH_RATIO = 0.15;

  /**
   * Threshold for valley detection as fraction of maximum projection value. Higher values detect
   * more valleys (more sensitive).
   */
  private static final double VALLEY_THRESHOLD_RATIO = 0.35;

  /**
   * Threshold for local minimum detection as fraction of neighboring peaks. Used for detecting
   * valleys that are local minima but not absolute minima.
   */
  private static final double LOCAL_MINIMUM_RATIO = 0.5;

  /**
   * Margin to exclude from analysis (as fraction of image width). Helps ignore page borders and
   * binding areas.
   */
  private static final double MARGIN_RATIO = 0.05;

  /**
   * Expected column gap width as fraction of image width. Typical gap between columns in a 2-column
   * layout.
   */

  /**
   * Detects columns in a binary document image.
   *
   * @param binaryImage binary (black text on white background) Mat image
   * @return list of DocumentRegion objects representing detected columns, or a single region
   *     covering the entire image if no columns detected
   */
  public List<DocumentRegion> detectColumns(Mat binaryImage) {
    if (binaryImage == null || binaryImage.empty()) {
      Log.w(TAG, "detectColumns: empty or null image");
      return createSingleColumnResult(0, 0);
    }

    int width = binaryImage.cols();
    int height = binaryImage.rows();

    if (width < 100 || height < 100) {
      Log.d(TAG, "detectColumns: image too small for column detection");
      return createSingleColumnResult(width, height);
    }

    try {
      // 1. Compute vertical projection (count black pixels per column)
      int[] projection = computeVerticalProjection(binaryImage);

      // 2. Smooth the projection to reduce noise
      int[] smoothed = smoothProjection(projection, Math.max(5, width / 100));

      // 3. Find valleys (gaps between columns)
      List<int[]> valleys = findValleys(smoothed, width);

      // 4. Create column regions from valleys
      List<DocumentRegion> columns = createColumnsFromValleys(valleys, width, height);

      // 5. Filter and validate columns
      columns = filterColumns(columns, width, height);

      Log.d(TAG, "detectColumns: detected " + columns.size() + " column(s)");
      return columns;

    } catch (Exception e) {
      Log.e(TAG, "detectColumns: error during detection", e);
      return createSingleColumnResult(width, height);
    }
  }

  /**
   * Computes the vertical projection of a binary image. For each x-coordinate, counts the number of
   * foreground (text) pixels.
   *
   * @param binary binary image - after THRESH_BINARY_INV: 255 = text, 0 = background
   * @return array of pixel counts per column
   */
  private int[] computeVerticalProjection(Mat binary) {
    int width = binary.cols();
    int height = binary.rows();
    int[] projection = new int[width];

    // For efficiency, process row by row
    byte[] rowData = new byte[width];

    for (int y = 0; y < height; y++) {
      binary.get(y, 0, rowData);
      for (int x = 0; x < width; x++) {
        // After THRESH_BINARY_INV: 255 = text (foreground), 0 = background
        // Count white pixels (text) - non-zero values
        if ((rowData[x] & 0xFF) != 0) {
          projection[x]++;
        }
      }
    }

    return projection;
  }

  /**
   * Smooths the projection array using a moving average filter.
   *
   * @param projection original projection values
   * @param windowSize size of the smoothing window
   * @return smoothed projection array
   */
  private int[] smoothProjection(int[] projection, int windowSize) {
    if (windowSize < 1) windowSize = 1;
    int[] smoothed = new int[projection.length];
    int halfWindow = windowSize / 2;

    for (int i = 0; i < projection.length; i++) {
      int sum = 0;
      int count = 0;
      for (int j = Math.max(0, i - halfWindow);
          j <= Math.min(projection.length - 1, i + halfWindow);
          j++) {
        sum += projection[j];
        count++;
      }
      smoothed[i] = sum / count;
    }

    return smoothed;
  }

  /**
   * Finds valleys (low points) in the projection that indicate column boundaries. Uses multiple
   * detection strategies: 1. Absolute threshold detection for clear gaps 2. Local minimum detection
   * for subtle column separations 3. Center-region analysis for typical 2-column layouts
   *
   * @param projection smoothed projection values
   * @param imageWidth width of the image
   * @return list of valley ranges as [start, end] pairs
   */
  private List<int[]> findValleys(int[] projection, int imageWidth) {
    List<int[]> valleys = new ArrayList<>();

    // Find maximum and average projection values
    int maxProjection = 0;
    long sumProjection = 0;
    int margin = (int) (imageWidth * MARGIN_RATIO);

    // Use a larger margin for valley detection to avoid edge artifacts
    int valleyMargin = (int) (imageWidth * 0.15); // 15% margin on each side

    for (int i = margin; i < projection.length - margin; i++) {
      if (projection[i] > maxProjection) {
        maxProjection = projection[i];
      }
      sumProjection += projection[i];
    }

    if (maxProjection == 0) {
      Log.d(TAG, "findValleys: maxProjection=0, empty image");
      return valleys; // Empty image
    }

    int effectiveWidth = projection.length - 2 * margin;
    double avgProjection = (double) sumProjection / effectiveWidth;

    int threshold = (int) (maxProjection * VALLEY_THRESHOLD_RATIO);
    int minValleyWidth = (int) (imageWidth * MIN_VALLEY_WIDTH_RATIO);

    Log.d(
        TAG,
        "findValleys: imageWidth="
            + imageWidth
            + ", maxProjection="
            + maxProjection
            + ", avgProjection="
            + String.format("%.1f", avgProjection)
            + ", threshold="
            + threshold
            + ", minValleyWidth="
            + minValleyWidth);

    // Strategy 1: Find valleys using absolute threshold (with larger margin to avoid edges)
    valleys = findValleysWithThreshold(projection, threshold, minValleyWidth, valleyMargin);
    Log.d(TAG, "findValleys: Strategy 1 (threshold) found " + valleys.size() + " valleys");

    // Filter and merge valleys to get the most significant ones
    if (valleys.size() > 1) {
      valleys = filterAndMergeValleys(valleys, projection, imageWidth);
      Log.d(TAG, "findValleys: After filtering/merging: " + valleys.size() + " valleys");
    }

    // Strategy 2: If no valleys found, look for local minima in center region
    if (valleys.isEmpty()) {
      valleys = findLocalMinimumValleys(projection, imageWidth, valleyMargin, avgProjection);
      Log.d(TAG, "findValleys: Strategy 2 (local minima) found " + valleys.size() + " valleys");
    }

    // Strategy 3: If still no valleys, analyze center region for 2-column layout
    if (valleys.isEmpty()) {
      int[] centerValley = findCenterValley(projection, imageWidth, margin);
      if (centerValley != null) {
        valleys.add(centerValley);
        Log.d(
            TAG,
            "findValleys: Strategy 3 (center) found valley at "
                + centerValley[0]
                + "-"
                + centerValley[1]);
      } else {
        Log.d(TAG, "findValleys: Strategy 3 (center) found no valley");
      }
    }

    return valleys;
  }

  /**
   * Filters and merges valleys to keep only the most significant column separations. For typical
   * 2-column layouts, this should result in a single valley in the center.
   */
  private List<int[]> filterAndMergeValleys(List<int[]> valleys, int[] projection, int imageWidth) {
    if (valleys.isEmpty()) {
      return valleys;
    }

    // For 2-column detection, find the best valley (deepest and most centered)
    int centerX = imageWidth / 2;
    int bestValleyIdx = -1;
    double bestScore = -1;

    for (int i = 0; i < valleys.size(); i++) {
      int[] valley = valleys.get(i);
      int valleyCenter = (valley[0] + valley[1]) / 2;
      int valleyWidth = valley[1] - valley[0];

      // Calculate minimum projection value in this valley
      int minProj = Integer.MAX_VALUE;
      for (int x = valley[0]; x < valley[1]; x++) {
        if (projection[x] < minProj) {
          minProj = projection[x];
        }
      }

      // Score based on: depth (lower is better), width (wider is better),
      // and proximity to center (closer is better)
      double distanceFromCenter = Math.abs(valleyCenter - centerX) / (double) imageWidth;
      double depthScore = 1.0 / (1.0 + minProj); // Lower projection = higher score
      double widthScore = valleyWidth / (double) imageWidth;
      double centerScore = 1.0 - distanceFromCenter; // Closer to center = higher score

      // Weight center proximity heavily for 2-column detection
      double score = depthScore * 0.3 + widthScore * 0.2 + centerScore * 0.5;

      Log.d(
          TAG,
          "filterAndMergeValleys: valley "
              + i
              + " at "
              + valley[0]
              + "-"
              + valley[1]
              + ", center="
              + valleyCenter
              + ", minProj="
              + minProj
              + ", score="
              + String.format("%.3f", score));

      if (score > bestScore) {
        bestScore = score;
        bestValleyIdx = i;
      }
    }

    // Return only the best valley for clean 2-column detection
    List<int[]> result = new ArrayList<>();
    if (bestValleyIdx >= 0) {
      result.add(valleys.get(bestValleyIdx));
      Log.d(
          TAG,
          "filterAndMergeValleys: selected valley "
              + bestValleyIdx
              + " with score "
              + String.format("%.3f", bestScore));
    }

    return result;
  }

  /** Finds valleys using absolute threshold detection. */
  private List<int[]> findValleysWithThreshold(
      int[] projection, int threshold, int minValleyWidth, int margin) {
    List<int[]> valleys = new ArrayList<>();
    boolean inValley = false;
    int valleyStart = 0;

    for (int i = margin; i < projection.length - margin; i++) {
      if (projection[i] < threshold) {
        if (!inValley) {
          inValley = true;
          valleyStart = i;
        }
      } else {
        if (inValley) {
          inValley = false;
          int valleyEnd = i;
          int valleyWidth = valleyEnd - valleyStart;

          if (valleyWidth >= minValleyWidth) {
            valleys.add(new int[] {valleyStart, valleyEnd});
          }
        }
      }
    }

    // Handle valley extending to end
    if (inValley) {
      int valleyEnd = projection.length - margin;
      int valleyWidth = valleyEnd - valleyStart;
      if (valleyWidth >= minValleyWidth) {
        valleys.add(new int[] {valleyStart, valleyEnd});
      }
    }

    return valleys;
  }

  /** Finds valleys by detecting local minima that are significantly lower than neighbors. */
  private List<int[]> findLocalMinimumValleys(
      int[] projection, int imageWidth, int margin, double avgProjection) {
    List<int[]> valleys = new ArrayList<>();

    // Look for significant dips in the projection
    int windowSize = (int) (imageWidth * 0.1); // 10% of width for local comparison
    int minValleyWidth = (int) (imageWidth * MIN_VALLEY_WIDTH_RATIO);

    for (int i = margin + windowSize; i < projection.length - margin - windowSize; i++) {
      // Find local minimum
      int localMin = projection[i];

      // Check if this is a local minimum in a small window
      boolean isLocalMin = true;
      for (int j = i - 5; j <= i + 5 && j < projection.length - margin; j++) {
        if (j >= margin && projection[j] < localMin) {
          isLocalMin = false;
          break;
        }
      }

      if (!isLocalMin) continue;

      // Calculate average of left and right neighbors
      int leftSum = 0, rightSum = 0;
      int sampleSize = windowSize / 2;

      for (int j = 0; j < sampleSize; j++) {
        int leftIdx = i - windowSize / 2 - j;
        int rightIdx = i + windowSize / 2 + j;
        if (leftIdx >= margin) leftSum += projection[leftIdx];
        if (rightIdx < projection.length - margin) rightSum += projection[rightIdx];
      }

      double leftAvg = (double) leftSum / sampleSize;
      double rightAvg = (double) rightSum / sampleSize;
      double neighborAvg = (leftAvg + rightAvg) / 2;

      // Check if this point is significantly lower than neighbors
      if (localMin < neighborAvg * LOCAL_MINIMUM_RATIO && localMin < avgProjection * 0.8) {

        // Find the extent of this valley
        int valleyStart = i;
        int valleyEnd = i;

        // Expand left
        while (valleyStart > margin && projection[valleyStart - 1] < neighborAvg * 0.7) {
          valleyStart--;
        }

        // Expand right
        while (valleyEnd < projection.length - margin - 1
            && projection[valleyEnd + 1] < neighborAvg * 0.7) {
          valleyEnd++;
        }

        int valleyWidth = valleyEnd - valleyStart;
        if (valleyWidth >= minValleyWidth) {
          valleys.add(new int[] {valleyStart, valleyEnd});
          // Skip past this valley
          i = valleyEnd + windowSize;
        }
      }
    }

    return valleys;
  }

  /**
   * Analyzes the center region of the document to find a column gap. This is specifically designed
   * for 2-column layouts where the gap is typically in the center third of the page.
   */
  private int[] findCenterValley(int[] projection, int imageWidth, int margin) {
    // Focus on center third of the document
    int centerStart = projection.length / 3;
    int centerEnd = 2 * projection.length / 3;

    // Find the minimum in the center region
    int minValue = Integer.MAX_VALUE;
    int minPos = -1;

    for (int i = centerStart; i < centerEnd; i++) {
      if (projection[i] < minValue) {
        minValue = projection[i];
        minPos = i;
      }
    }

    if (minPos < 0) return null;

    // Calculate average projection in left and right halves
    long leftSum = 0, rightSum = 0;
    int leftCount = 0, rightCount = 0;

    for (int i = margin; i < minPos - imageWidth * 0.05; i++) {
      leftSum += projection[i];
      leftCount++;
    }

    for (int i = minPos + (int) (imageWidth * 0.05); i < projection.length - margin; i++) {
      rightSum += projection[i];
      rightCount++;
    }

    if (leftCount == 0 || rightCount == 0) return null;

    double leftAvg = (double) leftSum / leftCount;
    double rightAvg = (double) rightSum / rightCount;

    // Check if both sides have significant content and center is a gap
    double minThreshold = Math.min(leftAvg, rightAvg) * LOCAL_MINIMUM_RATIO;

    if (minValue < minThreshold && leftAvg > 0 && rightAvg > 0) {
      // Find the extent of the valley around the minimum
      int valleyStart = minPos;
      int valleyEnd = minPos;

      double gapThreshold = Math.min(leftAvg, rightAvg) * 0.6;

      // Expand left
      while (valleyStart > margin && projection[valleyStart - 1] < gapThreshold) {
        valleyStart--;
      }

      // Expand right
      while (valleyEnd < projection.length - margin - 1
          && projection[valleyEnd + 1] < gapThreshold) {
        valleyEnd++;
      }

      int valleyWidth = valleyEnd - valleyStart;
      int minValleyWidth = (int) (imageWidth * MIN_VALLEY_WIDTH_RATIO);

      if (valleyWidth >= minValleyWidth) {
        Log.d(
            TAG,
            "findCenterValley: found center gap at "
                + valleyStart
                + "-"
                + valleyEnd
                + " (width="
                + valleyWidth
                + ", minValue="
                + minValue
                + ", leftAvg="
                + leftAvg
                + ", rightAvg="
                + rightAvg
                + ")");
        return new int[] {valleyStart, valleyEnd};
      }
    }

    return null;
  }

  /**
   * Creates column regions from detected valleys.
   *
   * @param valleys list of valley ranges
   * @param width image width
   * @param height image height
   * @return list of column regions
   */
  private List<DocumentRegion> createColumnsFromValleys(
      List<int[]> valleys, int width, int height) {
    List<DocumentRegion> columns = new ArrayList<>();

    if (valleys.isEmpty()) {
      // No valleys found - single column document
      return createSingleColumnResult(width, height);
    }

    int columnStart = 0;
    int columnIndex = 0;

    for (int[] valley : valleys) {
      int valleyCenter = (valley[0] + valley[1]) / 2;

      // Create column from columnStart to valley center
      if (valleyCenter > columnStart) {
        Rect bounds = new Rect(columnStart, 0, valleyCenter, height);
        DocumentRegion column = new DocumentRegion(bounds, DocumentRegion.Type.COLUMN);
        column.setColumnIndex(columnIndex);
        column.setReadingOrder(columnIndex);
        columns.add(column);
        columnIndex++;
      }

      columnStart = valleyCenter;
    }

    // Create final column from last valley to end
    if (columnStart < width) {
      Rect bounds = new Rect(columnStart, 0, width, height);
      DocumentRegion column = new DocumentRegion(bounds, DocumentRegion.Type.COLUMN);
      column.setColumnIndex(columnIndex);
      column.setReadingOrder(columnIndex);
      columns.add(column);
    }

    return columns;
  }

  /**
   * Filters columns to remove invalid or too-narrow columns.
   *
   * @param columns detected columns
   * @param width image width
   * @param height image height
   * @return filtered list of valid columns
   */
  private List<DocumentRegion> filterColumns(List<DocumentRegion> columns, int width, int height) {
    if (columns.size() <= 1) {
      return columns;
    }

    int minColumnWidth = (int) (width * MIN_COLUMN_WIDTH_RATIO);
    List<DocumentRegion> filtered = new ArrayList<>();

    for (DocumentRegion column : columns) {
      if (column.getBounds().width() >= minColumnWidth) {
        filtered.add(column);
      } else {
        Log.d(TAG, "filterColumns: removing narrow column: " + column.getBounds().width() + "px");
      }
    }

    // If all columns were filtered, return single column
    if (filtered.isEmpty()) {
      return createSingleColumnResult(width, height);
    }

    // Re-index columns after filtering
    for (int i = 0; i < filtered.size(); i++) {
      filtered.get(i).setColumnIndex(i);
      filtered.get(i).setReadingOrder(i);
    }

    return filtered;
  }

  /**
   * Creates a single-column result covering the entire image.
   *
   * @param width image width
   * @param height image height
   * @return list containing a single column region
   */
  private List<DocumentRegion> createSingleColumnResult(int width, int height) {
    List<DocumentRegion> result = new ArrayList<>();
    if (width > 0 && height > 0) {
      Rect bounds = new Rect(0, 0, width, height);
      DocumentRegion column = new DocumentRegion(bounds, DocumentRegion.Type.COLUMN);
      column.setColumnIndex(0);
      column.setReadingOrder(0);
      result.add(column);
    }
    return result;
  }

  /**
   * Estimates the number of columns in a document without full detection. Useful for quick
   * classification of document type.
   *
   * @param binaryImage binary document image
   * @return estimated number of columns (1, 2, 3, or more)
   */
  public int estimateColumnCount(Mat binaryImage) {
    List<DocumentRegion> columns = detectColumns(binaryImage);
    return columns.size();
  }

  /**
   * Checks if the document appears to be multi-column.
   *
   * @param binaryImage binary document image
   * @return true if document has 2 or more columns
   */
  public boolean isMultiColumn(Mat binaryImage) {
    return estimateColumnCount(binaryImage) > 1;
  }
}

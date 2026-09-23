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

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.imgproc.Imgproc;

/**
 * Main class for document layout analysis. Combines column detection, table detection, and region
 * classification to provide comprehensive document structure analysis for improved OCR.
 */
public class DocumentLayoutAnalyzer {

  private static final String TAG = "DocumentLayoutAnalyzer";

  /** Header region detection: top percentage of page to consider as header. */
  private static final double HEADER_REGION_RATIO = 0.12;

  /** Footer region detection: bottom percentage of page to consider as footer. */
  private static final double FOOTER_REGION_RATIO = 0.08;

  /** Minimum content density to consider a region as containing text. */
  private static final double MIN_CONTENT_DENSITY = 0.01;

  /** Maximum content density (above this is likely an image/figure). */
  private static final double MAX_TEXT_DENSITY = 0.5;

  private final ColumnDetector columnDetector;
  private final TableDetector tableDetector;

  @Getter @Setter private String language = "eng";

  @Getter @Setter private boolean detectTables = true;

  @Getter @Setter private boolean detectColumns = true;

  @Getter @Setter private boolean detectHeaderFooter = true;

  /** Creates a new DocumentLayoutAnalyzer with default settings. */
  public DocumentLayoutAnalyzer() {
    this.columnDetector = new ColumnDetector();
    this.tableDetector = new TableDetector();
  }

  /**
   * Analyzes the layout of a document image.
   *
   * @param bitmap input document image
   * @return list of detected document regions sorted by reading order
   */
  public List<DocumentRegion> analyze(Bitmap bitmap) {
    if (bitmap == null || bitmap.isRecycled()) {
      Log.w(TAG, "analyze: null or recycled bitmap");
      return new ArrayList<>();
    }

    List<DocumentRegion> regions = new ArrayList<>();
    Mat rgba = new Mat();
    Mat gray = new Mat();
    Mat binary = new Mat();

    try {
      // Convert bitmap to OpenCV Mat
      Utils.bitmapToMat(bitmap, rgba);
      Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);

      // Binarize for analysis
      Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

      int width = binary.cols();
      int height = binary.rows();

      // 1. Detect header and footer regions
      if (detectHeaderFooter) {
        DocumentRegion header = detectHeader(binary, width, height);
        if (header != null) {
          regions.add(header);
        }

        DocumentRegion footer = detectFooter(binary, width, height);
        if (footer != null) {
          regions.add(footer);
        }
      }

      // 2. Detect tables
      List<DocumentRegion> tables = new ArrayList<>();
      if (detectTables) {
        tables = tableDetector.detectTables(binary);
        regions.addAll(tables);
      }

      // 3. Detect columns in remaining area (excluding header, footer, tables)
      if (detectColumns) {
        List<DocumentRegion> columns = detectColumnsExcluding(binary, regions, width, height);
        regions.addAll(columns);
      }

      // 4. Detect figures/images
      List<DocumentRegion> figures = detectFigures(binary, regions, width, height);
      regions.addAll(figures);

      // 5. If no specific regions found, create a single body region
      if (regions.isEmpty()) {
        Rect bodyBounds = new Rect(0, 0, width, height);
        DocumentRegion body = new DocumentRegion(bodyBounds, DocumentRegion.Type.BODY);
        regions.add(body);
      }

      // 6. Sort regions by reading order
      ReadingOrderSorter.TextDirection direction =
          ReadingOrderSorter.getDirectionForLanguage(language);

      // Check if we have multiple columns - use column-aware sorting
      long columnCount =
          regions.stream().filter(r -> r.getType() == DocumentRegion.Type.COLUMN).count();

      if (columnCount > 1) {
        // Multi-column layout: sort by columns first (left column before right)
        ReadingOrderSorter.sortByColumns(regions, direction);
      } else {
        // Single column or no columns: use hierarchical sorting
        ReadingOrderSorter.sortHierarchical(regions, direction);
      }

      Log.d(TAG, "analyze: detected " + regions.size() + " region(s)");

    } catch (Exception e) {
      Log.e(TAG, "analyze: error during layout analysis", e);
    } finally {
      rgba.release();
      gray.release();
      binary.release();
    }

    return regions;
  }

  /**
   * Analyzes layout from an already binarized Mat image.
   *
   * @param binaryImage binary (black text on white) Mat image
   * @return list of detected document regions
   */
  public List<DocumentRegion> analyzeFromBinary(Mat binaryImage) {
    if (binaryImage == null || binaryImage.empty()) {
      Log.w(TAG, "analyzeFromBinary: null or empty image");
      return new ArrayList<>();
    }

    List<DocumentRegion> regions = new ArrayList<>();

    try {
      int width = binaryImage.cols();
      int height = binaryImage.rows();

      // 1. Detect header and footer
      if (detectHeaderFooter) {
        DocumentRegion header = detectHeader(binaryImage, width, height);
        if (header != null) {
          regions.add(header);
        }

        DocumentRegion footer = detectFooter(binaryImage, width, height);
        if (footer != null) {
          regions.add(footer);
        }
      }

      // 2. Detect tables
      List<DocumentRegion> tables = new ArrayList<>();
      if (detectTables) {
        tables = tableDetector.detectTables(binaryImage);
        regions.addAll(tables);
      }

      // 3. Detect columns
      if (detectColumns) {
        List<DocumentRegion> columns = detectColumnsExcluding(binaryImage, regions, width, height);
        regions.addAll(columns);
      }

      // 4. Detect figures
      List<DocumentRegion> figures = detectFigures(binaryImage, regions, width, height);
      regions.addAll(figures);

      // 5. Fallback to single body region
      if (regions.isEmpty()) {
        Rect bodyBounds = new Rect(0, 0, width, height);
        DocumentRegion body = new DocumentRegion(bodyBounds, DocumentRegion.Type.BODY);
        regions.add(body);
      }

      // 6. Sort by reading order
      ReadingOrderSorter.TextDirection direction =
          ReadingOrderSorter.getDirectionForLanguage(language);

      // Check if we have multiple columns - use column-aware sorting
      long columnCount =
          regions.stream().filter(r -> r.getType() == DocumentRegion.Type.COLUMN).count();

      if (columnCount > 1) {
        // Multi-column layout: sort by columns first (left column before right)
        ReadingOrderSorter.sortByColumns(regions, direction);
      } else {
        // Single column or no columns: use hierarchical sorting
        ReadingOrderSorter.sortHierarchical(regions, direction);
      }

    } catch (Exception e) {
      Log.e(TAG, "analyzeFromBinary: error", e);
    }

    return regions;
  }

  /**
   * Detects the header region at the top of the page.
   *
   * @param binary binary image
   * @param width image width
   * @param height image height
   * @return header region or null if not detected
   */
  private DocumentRegion detectHeader(Mat binary, int width, int height) {
    int headerHeight = (int) (height * HEADER_REGION_RATIO);
    if (headerHeight < 20) return null;

    // Extract header region
    org.opencv.core.Rect headerRect = new org.opencv.core.Rect(0, 0, width, headerHeight);
    Mat headerRegion = binary.submat(headerRect);

    // Check if region has content
    double density = calculateContentDensity(headerRegion);
    headerRegion.release();

    if (density > MIN_CONTENT_DENSITY && density < MAX_TEXT_DENSITY) {
      Rect bounds = new Rect(0, 0, width, headerHeight);
      DocumentRegion header = new DocumentRegion(bounds, DocumentRegion.Type.HEADER);
      header.setConfidence((float) Math.min(1.0, density * 10));
      return header;
    }

    return null;
  }

  /**
   * Detects the footer region at the bottom of the page.
   *
   * @param binary binary image
   * @param width image width
   * @param height image height
   * @return footer region or null if not detected
   */
  private DocumentRegion detectFooter(Mat binary, int width, int height) {
    int footerHeight = (int) (height * FOOTER_REGION_RATIO);
    if (footerHeight < 20) return null;

    int footerTop = height - footerHeight;

    // Extract footer region
    org.opencv.core.Rect footerRect = new org.opencv.core.Rect(0, footerTop, width, footerHeight);
    Mat footerRegion = binary.submat(footerRect);

    // Check if region has content
    double density = calculateContentDensity(footerRegion);
    footerRegion.release();

    if (density > MIN_CONTENT_DENSITY && density < MAX_TEXT_DENSITY) {
      Rect bounds = new Rect(0, footerTop, width, height);
      DocumentRegion footer = new DocumentRegion(bounds, DocumentRegion.Type.FOOTER);
      footer.setConfidence((float) Math.min(1.0, density * 10));
      return footer;
    }

    return null;
  }

  /**
   * Detects columns in the document, excluding already detected regions. For multi-column layouts,
   * also detects footer regions that span across columns.
   *
   * @param binary binary image
   * @param existingRegions already detected regions to exclude (will be modified to add footer if
   *     detected)
   * @param width image width
   * @param height image height
   * @return list of column regions
   */
  private List<DocumentRegion> detectColumnsExcluding(
      Mat binary, List<DocumentRegion> existingRegions, int width, int height) {
    // Calculate body area (excluding header and footer)
    int bodyTop = 0;
    int bodyBottom = height;
    boolean hasFooter = false;

    for (DocumentRegion region : existingRegions) {
      if (region.getType() == DocumentRegion.Type.HEADER) {
        bodyTop = Math.max(bodyTop, region.getBounds().bottom);
      } else if (region.getType() == DocumentRegion.Type.FOOTER) {
        bodyBottom = Math.min(bodyBottom, region.getBounds().top);
        hasFooter = true;
      }
    }

    if (bodyBottom <= bodyTop) {
      return new ArrayList<>();
    }

    // Extract body region for column detection
    org.opencv.core.Rect bodyRect =
        new org.opencv.core.Rect(0, bodyTop, width, bodyBottom - bodyTop);
    Mat bodyRegion = binary.submat(bodyRect);

    // Detect columns
    List<DocumentRegion> columns = columnDetector.detectColumns(bodyRegion);
    bodyRegion.release();

    // Adjust column bounds to absolute coordinates
    for (DocumentRegion column : columns) {
      Rect bounds = column.getBounds();
      column.setBounds(
          new Rect(bounds.left, bounds.top + bodyTop, bounds.right, bounds.bottom + bodyTop));
    }

    // For multi-column layouts without a detected footer, check for a common footer area
    // This handles footnotes that span across the bottom of all columns
    if (columns.size() >= 2 && !hasFooter) {
      DocumentRegion footerFromColumns =
          detectFooterInMultiColumnLayout(binary, columns, width, height, bodyBottom);
      if (footerFromColumns != null) {
        existingRegions.add(footerFromColumns);
        // Adjust column heights to exclude the footer area
        int footerTop = footerFromColumns.getBounds().top;
        for (DocumentRegion column : columns) {
          Rect bounds = column.getBounds();
          if (bounds.bottom > footerTop) {
            column.setBounds(new Rect(bounds.left, bounds.top, bounds.right, footerTop));
          }
        }
      }
    }

    // Remove columns that overlap with tables
    List<DocumentRegion> filteredColumns = new ArrayList<>();
    for (DocumentRegion column : columns) {
      boolean overlapsTable = false;
      for (DocumentRegion existing : existingRegions) {
        if (existing.getType() == DocumentRegion.Type.TABLE && column.intersects(existing)) {
          overlapsTable = true;
          break;
        }
      }
      if (!overlapsTable) {
        filteredColumns.add(column);
      }
    }

    return filteredColumns;
  }

  /**
   * Detects a footer region in multi-column layouts by analyzing the bottom area that spans across
   * all columns. This is useful for detecting footnotes.
   *
   * <p>The detection works by: 1. Analyzing the bottom portion of the page for content that spans
   * the full width 2. Looking for text lines that extend beyond individual column boundaries 3.
   * Detecting a horizontal gap between column content and footer content 4. Looking for text at the
   * bottom that starts at the left margin
   *
   * @param binary binary image
   * @param columns detected columns
   * @param width image width
   * @param height image height
   * @param bodyBottom bottom of the body area
   * @return footer region or null if not detected
   */
  private DocumentRegion detectFooterInMultiColumnLayout(
      Mat binary, List<DocumentRegion> columns, int width, int height, int bodyBottom) {
    if (columns.isEmpty() || columns.size() < 2) {
      return null;
    }

    // Strategy 1: Look for full-width content at the bottom of the page
    // Footnotes typically span the entire page width, not just one column
    DocumentRegion fullWidthFooter =
        detectFullWidthFooter(binary, columns, width, height, bodyBottom);
    if (fullWidthFooter != null) {
      return fullWidthFooter;
    }

    // Strategy 2: Look for text at the bottom that starts at the left margin
    // and extends into or beyond the column gap area
    DocumentRegion bottomFooter = detectBottomFooter(binary, columns, width, height, bodyBottom);
    if (bottomFooter != null) {
      return bottomFooter;
    }

    // Strategy 3: Find the bottom-most content in each column using vertical projection
    // and check for a gap below all columns
    int minColumnBottom = bodyBottom;
    for (DocumentRegion column : columns) {
      Rect bounds = column.getBounds();
      // Analyze the bottom portion of each column to find where content ends
      int columnBottom = findContentBottom(binary, bounds);
      if (columnBottom < minColumnBottom) {
        minColumnBottom = columnBottom;
      }
    }

    // Check if there's a significant gap between column content and page bottom
    // that could contain a footer/footnote
    int potentialFooterHeight = bodyBottom - minColumnBottom;
    int minFooterHeight = (int) (height * 0.03); // At least 3% of page height
    int maxFooterHeight = (int) (height * 0.15); // At most 15% of page height

    if (potentialFooterHeight >= minFooterHeight && potentialFooterHeight <= maxFooterHeight) {
      // Check if there's content in this footer area
      org.opencv.core.Rect footerRect =
          new org.opencv.core.Rect(0, minColumnBottom, width, potentialFooterHeight);
      Mat footerRegion = binary.submat(footerRect);
      double density = calculateContentDensity(footerRegion);
      footerRegion.release();

      if (density > MIN_CONTENT_DENSITY && density < MAX_TEXT_DENSITY) {
        Rect bounds = new Rect(0, minColumnBottom, width, bodyBottom);
        DocumentRegion footer = new DocumentRegion(bounds, DocumentRegion.Type.FOOTER);
        footer.setConfidence((float) Math.min(1.0, density * 10));
        Log.d(
            TAG,
            "detectFooterInMultiColumnLayout: detected footer at y="
                + minColumnBottom
                + " to "
                + bodyBottom
                + " (height="
                + potentialFooterHeight
                + ")");
        return footer;
      }
    }

    return null;
  }

  /**
   * Detects a footer that spans the full page width in a multi-column layout. This is specifically
   * designed to detect footnotes that appear below all columns.
   *
   * <p>Detection strategies: 1. Look for content in the gap between columns (traditional approach)
   * 2. Look for text lines at the bottom that span the full page width 3. Detect a horizontal gap
   * between column content and footer content
   *
   * @param binary binary image
   * @param columns detected columns
   * @param width image width
   * @param height image height
   * @param bodyBottom bottom of the body area
   * @return footer region or null if not detected
   */
  private DocumentRegion detectFullWidthFooter(
      Mat binary, List<DocumentRegion> columns, int width, int height, int bodyBottom) {
    // Find the boundaries of left and right columns
    int leftColumnLeft = width;
    int leftColumnRight = 0;
    int rightColumnLeft = width;
    int rightColumnRight = 0;

    for (DocumentRegion column : columns) {
      Rect bounds = column.getBounds();
      // Left column (left half of page)
      if (bounds.left < width / 2) {
        leftColumnLeft = Math.min(leftColumnLeft, bounds.left);
        leftColumnRight = Math.max(leftColumnRight, bounds.right);
      }
      // Right column (right half of page)
      if (bounds.right > width / 2) {
        rightColumnLeft = Math.min(rightColumnLeft, bounds.left);
        rightColumnRight = Math.max(rightColumnRight, bounds.right);
      }
    }

    // The gap between columns
    int gapStart = leftColumnRight;
    int gapEnd = rightColumnLeft;
    int gapWidth = gapEnd - gapStart;

    // Scan from bottom of page upward
    int scanHeight = (int) (height * 0.20); // Scan bottom 20% of page
    int scanStart = Math.max(0, bodyBottom - scanHeight);

    int footerTop = -1;
    byte[] rowData = new byte[width];

    // Strategy 1: Look for rows where content spans the full page width
    // (content exists both in left margin area AND right margin area, or spans the gap)
    for (int y = bodyBottom - 1; y >= scanStart; y--) {
      if (y < 0 || y >= binary.rows()) continue;

      binary.get(y, 0, rowData);

      // Check for content spanning full width
      // A footer line typically starts near the left margin and extends significantly
      int leftContentStart = -1;
      int rightContentEnd = -1;

      // Find leftmost content
      for (int x = 0; x < width; x++) {
        if ((rowData[x] & 0xFF) != 0) {
          leftContentStart = x;
          break;
        }
      }

      // Find rightmost content
      for (int x = width - 1; x >= 0; x--) {
        if ((rowData[x] & 0xFF) != 0) {
          rightContentEnd = x;
          break;
        }
      }

      // Check if this row has full-width content
      // Full-width means: starts in left margin area AND ends in right margin area
      // OR: content exists in the gap between columns
      boolean isFullWidthLine = false;

      if (leftContentStart >= 0 && rightContentEnd >= 0) {
        int contentWidth = rightContentEnd - leftContentStart;

        // Check if content spans more than 60% of page width (likely a footer)
        if (contentWidth > width * 0.6) {
          isFullWidthLine = true;
        }

        // Also check if content exists in the gap area
        if (gapWidth > width * 0.02) {
          int gapContentCount = 0;
          for (int x = gapStart; x < gapEnd && x < width; x++) {
            if ((rowData[x] & 0xFF) != 0) {
              gapContentCount++;
            }
          }
          if (gapContentCount > gapWidth * 0.05) {
            isFullWidthLine = true;
          }
        }
      }

      if (isFullWidthLine) {
        // Found content that spans full width - this is likely a footer line
        footerTop = y;
      } else if (footerTop > 0) {
        // We found the top of the footer region (first row without full-width content)
        // But we need to check if there's a gap between this row and the footer
        // to avoid including column content

        // Check if there's a significant vertical gap above the footer
        int gapAboveFooter = 0;
        for (int checkY = y; checkY >= Math.max(scanStart, y - 50); checkY--) {
          if (checkY < 0 || checkY >= binary.rows()) continue;
          binary.get(checkY, 0, rowData);

          int rowContent = 0;
          for (int x = 0; x < width; x++) {
            if ((rowData[x] & 0xFF) != 0) {
              rowContent++;
            }
          }

          if (rowContent < width * 0.01) {
            gapAboveFooter++;
          } else {
            break;
          }
        }

        // If there's a gap of at least 10 pixels, use the bottom of the gap as footer top
        if (gapAboveFooter >= 10) {
          footerTop = y + 1;
        }
        break;
      }
    }

    if (footerTop > 0 && footerTop < bodyBottom) {
      int footerHeight = bodyBottom - footerTop;
      int minFooterHeight = (int) (height * 0.01); // At least 1% of page height
      int maxFooterHeight = (int) (height * 0.15); // At most 15% of page height

      if (footerHeight >= minFooterHeight && footerHeight <= maxFooterHeight) {
        // Verify there's actual content in this footer area
        org.opencv.core.Rect footerRect =
            new org.opencv.core.Rect(0, footerTop, width, footerHeight);
        Mat footerRegion = binary.submat(footerRect);
        double density = calculateContentDensity(footerRegion);
        footerRegion.release();

        if (density > MIN_CONTENT_DENSITY && density < MAX_TEXT_DENSITY) {
          Rect bounds = new Rect(0, footerTop, width, bodyBottom);
          DocumentRegion footer = new DocumentRegion(bounds, DocumentRegion.Type.FOOTER);
          footer.setConfidence((float) Math.min(1.0, density * 10));
          Log.d(
              TAG,
              "detectFullWidthFooter: detected full-width footer at y="
                  + footerTop
                  + " to "
                  + bodyBottom
                  + " (height="
                  + footerHeight
                  + ")");
          return footer;
        }
      }
    }

    return null;
  }

  /**
   * Detects a footer at the bottom of the page by looking for text lines that start at the left
   * margin and extend beyond the left column boundary. This is useful for detecting footnotes in
   * multi-column layouts.
   *
   * @param binary binary image
   * @param columns detected columns
   * @param width image width
   * @param height image height
   * @param bodyBottom bottom of the body area
   * @return footer region or null if not detected
   */
  private DocumentRegion detectBottomFooter(
      Mat binary, List<DocumentRegion> columns, int width, int height, int bodyBottom) {
    if (columns.isEmpty() || columns.size() < 2) {
      return null;
    }

    // Find the left column's right boundary (the gap starts here)
    int leftColumnRight = 0;
    for (DocumentRegion column : columns) {
      Rect bounds = column.getBounds();
      if (bounds.left < width / 2) {
        leftColumnRight = Math.max(leftColumnRight, bounds.right);
      }
    }

    // Scan the bottom 10% of the page
    int scanHeight = (int) (height * 0.10);
    int scanStart = Math.max(0, bodyBottom - scanHeight);
    byte[] rowData = new byte[width];
    int footerTop = -1;
    int consecutiveFooterRows = 0;
    int requiredConsecutiveRows = 3; // Need at least 3 consecutive rows to confirm footer

    // Scan from bottom upward looking for text that starts at left margin
    // and extends into or beyond the column gap
    for (int y = bodyBottom - 1; y >= scanStart; y--) {
      if (y < 0 || y >= binary.rows()) continue;

      binary.get(y, 0, rowData);

      // Find leftmost and rightmost content in this row
      int leftContent = -1;
      int rightContent = -1;

      for (int x = 0; x < width; x++) {
        if ((rowData[x] & 0xFF) != 0) {
          if (leftContent < 0) leftContent = x;
          rightContent = x;
        }
      }

      // Check if this row has content that:
      // 1. Starts near the left margin (within 10% of page width)
      // 2. Extends beyond the left column's right boundary (into the gap)
      boolean isFooterLine = false;
      if (leftContent >= 0 && rightContent >= 0) {
        boolean startsAtLeftMargin = leftContent < (int) (width * 0.10);
        boolean extendsBeyondLeftColumn = rightContent > leftColumnRight;
        int contentWidth = rightContent - leftContent;
        boolean hasSignificantWidth = contentWidth > width * 0.3; // At least 30% of page width

        if (startsAtLeftMargin && extendsBeyondLeftColumn && hasSignificantWidth) {
          isFooterLine = true;
        }
      }

      if (isFooterLine) {
        consecutiveFooterRows++;
        if (consecutiveFooterRows >= requiredConsecutiveRows) {
          footerTop = y;
        }
      } else if (consecutiveFooterRows >= requiredConsecutiveRows) {
        // We found the top of the footer region
        break;
      } else {
        // Reset if we haven't found enough consecutive rows yet
        consecutiveFooterRows = 0;
      }
    }

    if (footerTop > 0 && footerTop < bodyBottom) {
      int footerHeight = bodyBottom - footerTop;
      int minFooterHeight = (int) (height * 0.01); // At least 1% of page height
      int maxFooterHeight = (int) (height * 0.12); // At most 12% of page height

      if (footerHeight >= minFooterHeight && footerHeight <= maxFooterHeight) {
        // Verify there's actual content in this footer area
        org.opencv.core.Rect footerRect =
            new org.opencv.core.Rect(0, footerTop, width, footerHeight);
        Mat footerRegion = binary.submat(footerRect);
        double density = calculateContentDensity(footerRegion);
        footerRegion.release();

        if (density > MIN_CONTENT_DENSITY && density < MAX_TEXT_DENSITY) {
          Rect bounds = new Rect(0, footerTop, width, bodyBottom);
          DocumentRegion footer = new DocumentRegion(bounds, DocumentRegion.Type.FOOTER);
          footer.setConfidence((float) Math.min(1.0, density * 10));
          Log.d(
              TAG,
              "detectBottomFooter: detected footer at y="
                  + footerTop
                  + " to "
                  + bodyBottom
                  + " (height="
                  + footerHeight
                  + ")");
          return footer;
        }
      }
    }

    return null;
  }

  /**
   * Finds the bottom-most y-coordinate with significant content in a region. Uses vertical
   * projection to detect where text content ends.
   *
   * @param binary binary image
   * @param bounds region bounds to analyze
   * @return y-coordinate of the bottom of content
   */
  private int findContentBottom(Mat binary, Rect bounds) {
    // Clamp bounds to image dimensions
    int left = Math.max(0, bounds.left);
    int top = Math.max(0, bounds.top);
    int right = Math.min(binary.cols(), bounds.right);
    int bottom = Math.min(binary.rows(), bounds.bottom);

    if (right <= left || bottom <= top) {
      return bounds.bottom;
    }

    org.opencv.core.Rect roi = new org.opencv.core.Rect(left, top, right - left, bottom - top);
    Mat region = binary.submat(roi);

    int regionHeight = region.rows();
    int regionWidth = region.cols();

    // Compute horizontal projection (sum of pixels per row)
    int[] horizontalProjection = new int[regionHeight];
    byte[] rowData = new byte[regionWidth];

    for (int y = 0; y < regionHeight; y++) {
      region.get(y, 0, rowData);
      int sum = 0;
      for (int x = 0; x < regionWidth; x++) {
        if ((rowData[x] & 0xFF) != 0) {
          sum++;
        }
      }
      horizontalProjection[y] = sum;
    }
    region.release();

    // Find the last row with significant content (scanning from bottom)
    int threshold = (int) (regionWidth * 0.01); // At least 1% of width should have content
    int contentBottom = regionHeight;

    // Look for a gap (multiple consecutive rows with low content) from the bottom
    int gapCount = 0;
    int requiredGap = (int) (regionHeight * 0.02); // 2% of region height as gap
    if (requiredGap < 5) requiredGap = 5;

    for (int y = regionHeight - 1; y >= 0; y--) {
      if (horizontalProjection[y] < threshold) {
        gapCount++;
        if (gapCount >= requiredGap) {
          // Found a significant gap, content ends above this
          contentBottom = y + gapCount;
          break;
        }
      } else {
        gapCount = 0;
      }
    }

    return top + contentBottom;
  }

  /**
   * Detects figure/image regions in the document.
   *
   * @param binary binary image
   * @param existingRegions already detected regions
   * @param width image width
   * @param height image height
   * @return list of figure regions
   */
  private List<DocumentRegion> detectFigures(
      Mat binary, List<DocumentRegion> existingRegions, int width, int height) {
    List<DocumentRegion> figures = new ArrayList<>();

    // Find large contours that might be figures
    List<MatOfPoint> contours = new ArrayList<>();
    Mat hierarchy = new Mat();
    Imgproc.findContours(
        binary.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

    double minFigureArea = width * height * 0.02; // At least 2% of page
    double maxFigureArea = width * height * 0.5; // At most 50% of page

    for (MatOfPoint contour : contours) {
      org.opencv.core.Rect boundingRect = Imgproc.boundingRect(contour);
      double area = boundingRect.width * boundingRect.height;

      if (area >= minFigureArea && area <= maxFigureArea) {
        // Check aspect ratio (figures are usually not too elongated)
        double aspectRatio = (double) boundingRect.width / boundingRect.height;
        if (aspectRatio > 0.2 && aspectRatio < 5.0) {
          // Check content density (figures have high density)
          Mat figureRegion = binary.submat(boundingRect);
          double density = calculateContentDensity(figureRegion);
          figureRegion.release();

          if (density > MAX_TEXT_DENSITY) {
            Rect bounds =
                new Rect(
                    boundingRect.x,
                    boundingRect.y,
                    boundingRect.x + boundingRect.width,
                    boundingRect.y + boundingRect.height);

            // Check if overlaps with existing regions
            DocumentRegion figure = new DocumentRegion(bounds, DocumentRegion.Type.FIGURE);
            boolean overlaps = false;
            for (DocumentRegion existing : existingRegions) {
              if (figure.intersects(existing)) {
                overlaps = true;
                break;
              }
            }

            if (!overlaps) {
              figure.setConfidence((float) density);
              figures.add(figure);
            }
          }
        }
      }
      contour.release();
    }
    hierarchy.release();

    return figures;
  }

  /**
   * Calculates the content density of a binary region.
   *
   * @param region binary image region
   * @return density value (0.0 to 1.0)
   */
  private double calculateContentDensity(Mat region) {
    if (region.empty()) return 0.0;

    int nonZero = Core.countNonZero(region);
    int total = region.rows() * region.cols();
    return (double) nonZero / total;
  }

  /**
   * Quick check if document has complex layout (multiple columns or tables).
   *
   * @param bitmap input document image
   * @return true if complex layout detected
   */
  public boolean hasComplexLayout(Bitmap bitmap) {
    if (bitmap == null || bitmap.isRecycled()) {
      return false;
    }

    Mat rgba = new Mat();
    Mat gray = new Mat();
    Mat binary = new Mat();

    try {
      Utils.bitmapToMat(bitmap, rgba);
      Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
      Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

      // Check for multiple columns
      boolean multiColumn = columnDetector.isMultiColumn(binary);

      // Check for tables
      boolean hasTables = tableDetector.containsTables(binary);

      return multiColumn || hasTables;

    } catch (Exception e) {
      Log.e(TAG, "hasComplexLayout: error", e);
      return false;
    } finally {
      rgba.release();
      gray.release();
      binary.release();
    }
  }

  /**
   * Gets the estimated number of columns in the document.
   *
   * @param bitmap input document image
   * @return number of columns (1 for single column)
   */
  public int getColumnCount(Bitmap bitmap) {
    if (bitmap == null || bitmap.isRecycled()) {
      return 1;
    }

    Mat rgba = new Mat();
    Mat gray = new Mat();
    Mat binary = new Mat();

    try {
      Utils.bitmapToMat(bitmap, rgba);
      Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
      Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

      return columnDetector.estimateColumnCount(binary);

    } catch (Exception e) {
      Log.e(TAG, "getColumnCount: error", e);
      return 1;
    } finally {
      rgba.release();
      gray.release();
      binary.release();
    }
  }

  /**
   * Performs full analysis and returns detailed results.
   *
   * @param bitmap input document image
   * @return analysis result with regions and metadata
   */
  public AnalysisResult analyzeWithMetadata(Bitmap bitmap) {
    List<DocumentRegion> regions = analyze(bitmap);

    int columnCount = 1;
    boolean hasTable = false;
    boolean hasHeader = false;
    boolean hasFooter = false;

    for (DocumentRegion region : regions) {
      switch (region.getType()) {
        case COLUMN:
          columnCount = Math.max(columnCount, region.getColumnIndex() + 1);
          break;
        case TABLE:
          hasTable = true;
          break;
        case HEADER:
          hasHeader = true;
          break;
        case FOOTER:
          hasFooter = true;
          break;
        default:
          break;
      }
    }

    ReadingOrderSorter.TextDirection direction =
        ReadingOrderSorter.getDirectionForLanguage(language);

    return new AnalysisResult(regions, columnCount, hasTable, hasHeader, hasFooter, direction);
  }

  /** Result class containing analysis results with additional metadata. */
  public record AnalysisResult(
      List<DocumentRegion> regions,
      int columnCount,
      boolean hasTable,
      boolean hasHeader,
      boolean hasFooter,
      ReadingOrderSorter.TextDirection textDirection) {}
}

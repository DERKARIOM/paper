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
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Detects tables in document images using line detection and structural analysis. This class
 * identifies table boundaries and cell structures for improved OCR processing.
 */
public class TableDetector {

  private static final String TAG = "TableDetector";

  /** Minimum line length as fraction of image dimension. */

  /** Minimum number of horizontal lines to consider a region as a table. */
  private static final int MIN_HORIZONTAL_LINES = 2;

  /** Minimum number of vertical lines to consider a region as a table. */
  private static final int MIN_VERTICAL_LINES = 2;

  /** Minimum table area as fraction of image area. */
  private static final double MIN_TABLE_AREA_RATIO = 0.01;

  /** Maximum gap between lines to consider them part of the same table. */

  /**
   * Detects tables in a binary document image.
   *
   * @param binaryImage binary (black text on white background) Mat image
   * @return list of detected tables as DocumentRegion objects
   */
  public List<DocumentRegion> detectTables(Mat binaryImage) {
    List<DocumentRegion> tableRegions = new ArrayList<>();

    if (binaryImage == null || binaryImage.empty()) {
      Log.w(TAG, "detectTables: empty or null image");
      return tableRegions;
    }

    int width = binaryImage.cols();
    int height = binaryImage.rows();

    if (width < 100 || height < 100) {
      Log.d(TAG, "detectTables: image too small for table detection");
      return tableRegions;
    }

    try {
      // Detect tables using line-based approach
      List<DetectedTable> tables = detectTablesWithLines(binaryImage);

      // Convert to DocumentRegion objects
      for (int i = 0; i < tables.size(); i++) {
        DetectedTable table = tables.get(i);
        DocumentRegion region = new DocumentRegion(table.getBounds(), DocumentRegion.Type.TABLE);
        region.setConfidence(table.getConfidence());
        region.setReadingOrder(i);

        // Add cells as children
        for (TableCell cell : table.getCells()) {
          DocumentRegion cellRegion =
              new DocumentRegion(cell.getBounds(), DocumentRegion.Type.BODY);
          cellRegion.setRowIndex(cell.getRow());
          cellRegion.setColumnIndex(cell.getCol());
          region.addChild(cellRegion);
        }

        tableRegions.add(region);
      }

      Log.d(TAG, "detectTables: detected " + tableRegions.size() + " table(s)");

    } catch (Exception e) {
      Log.e(TAG, "detectTables: error during detection", e);
    }

    return tableRegions;
  }

  /**
   * Detects tables using horizontal and vertical line detection.
   *
   * @param binary binary image
   * @return list of detected tables with structure
   */
  private List<DetectedTable> detectTablesWithLines(Mat binary) {
    List<DetectedTable> tables = new ArrayList<>();

    int width = binary.cols();
    int height = binary.rows();

    // Detect horizontal lines
    Mat horizontal = detectHorizontalLines(binary);

    // Detect vertical lines
    Mat vertical = detectVerticalLines(binary);

    // Combine lines to find table regions
    Mat combined = new Mat();
    Core.add(horizontal, vertical, combined);

    // Find contours of combined line regions
    List<MatOfPoint> contours = new ArrayList<>();
    Mat hierarchy = new Mat();
    Imgproc.findContours(
        combined, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

    // Analyze each contour as potential table
    for (MatOfPoint contour : contours) {
      org.opencv.core.Rect boundingRect = Imgproc.boundingRect(contour);

      // Check minimum size
      double areaRatio = (double) (boundingRect.width * boundingRect.height) / (width * height);
      if (areaRatio < MIN_TABLE_AREA_RATIO) {
        continue;
      }

      // Extract region and analyze structure
      Mat regionHorizontal = horizontal.submat(boundingRect);
      Mat regionVertical = vertical.submat(boundingRect);

      int hLineCount = countLines(regionHorizontal, true);
      int vLineCount = countLines(regionVertical, false);

      // Check if region has enough lines to be a table
      if (hLineCount >= MIN_HORIZONTAL_LINES && vLineCount >= MIN_VERTICAL_LINES) {
        Rect tableBounds =
            new Rect(
                boundingRect.x,
                boundingRect.y,
                boundingRect.x + boundingRect.width,
                boundingRect.y + boundingRect.height);
        DetectedTable table = new DetectedTable(tableBounds);

        // Estimate rows and columns
        table.setRows(hLineCount - 1);
        table.setCols(vLineCount - 1);

        // Calculate confidence based on line regularity
        float confidence =
            calculateTableConfidence(regionHorizontal, regionVertical, hLineCount, vLineCount);
        table.setConfidence(confidence);

        // Detect cells
        List<TableCell> cells = detectCells(regionHorizontal, regionVertical, boundingRect);
        table.setCells(cells);

        // Check for header (first row often has different styling)
        table.setHasHeader(detectHeader(cells));

        tables.add(table);
      }

      regionHorizontal.release();
      regionVertical.release();
    }

    // Cleanup
    horizontal.release();
    vertical.release();
    combined.release();
    hierarchy.release();
    for (MatOfPoint contour : contours) {
      contour.release();
    }

    return tables;
  }

  /**
   * Detects horizontal lines in a binary image using morphological operations.
   *
   * @param binary binary image
   * @return Mat containing only horizontal lines
   */
  private Mat detectHorizontalLines(Mat binary) {
    Mat horizontal = binary.clone();

    // Create horizontal structuring element
    int horizontalSize = Math.max(binary.cols() / 30, 10);
    Mat horizontalStructure =
        Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(horizontalSize, 1));

    // Apply morphological operations to extract horizontal lines
    Imgproc.erode(horizontal, horizontal, horizontalStructure);
    Imgproc.dilate(horizontal, horizontal, horizontalStructure);

    horizontalStructure.release();
    return horizontal;
  }

  /**
   * Detects vertical lines in a binary image using morphological operations.
   *
   * @param binary binary image
   * @return Mat containing only vertical lines
   */
  private Mat detectVerticalLines(Mat binary) {
    Mat vertical = binary.clone();

    // Create vertical structuring element
    int verticalSize = Math.max(binary.rows() / 30, 10);
    Mat verticalStructure =
        Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, verticalSize));

    // Apply morphological operations to extract vertical lines
    Imgproc.erode(vertical, vertical, verticalStructure);
    Imgproc.dilate(vertical, vertical, verticalStructure);

    verticalStructure.release();
    return vertical;
  }

  /**
   * Counts the number of distinct lines in a line image.
   *
   * @param lineImage image containing only lines
   * @param horizontal true for horizontal lines, false for vertical
   * @return number of distinct lines
   */
  private int countLines(Mat lineImage, boolean horizontal) {
    int[] projection;

    if (horizontal) {
      // Horizontal projection for horizontal lines
      projection = new int[lineImage.rows()];
      for (int y = 0; y < lineImage.rows(); y++) {
        int count = 0;
        for (int x = 0; x < lineImage.cols(); x++) {
          if (lineImage.get(y, x)[0] > 0) {
            count++;
          }
        }
        projection[y] = count;
      }
    } else {
      // Vertical projection for vertical lines
      projection = new int[lineImage.cols()];
      for (int x = 0; x < lineImage.cols(); x++) {
        int count = 0;
        for (int y = 0; y < lineImage.rows(); y++) {
          if (lineImage.get(y, x)[0] > 0) {
            count++;
          }
        }
        projection[x] = count;
      }
    }

    // Count peaks in projection
    int threshold = (horizontal ? lineImage.cols() : lineImage.rows()) / 10;
    int lineCount = 0;
    boolean inLine = false;

    for (int value : projection) {
      if (value > threshold) {
        if (!inLine) {
          lineCount++;
          inLine = true;
        }
      } else {
        inLine = false;
      }
    }

    return lineCount;
  }

  /**
   * Calculates confidence score for table detection based on line regularity.
   *
   * @param horizontal horizontal line image
   * @param vertical vertical line image
   * @param hCount horizontal line count
   * @param vCount vertical line count
   * @return confidence score (0.0 to 1.0)
   */
  @SuppressWarnings("UnusedVariable") // horizontal/vertical reserved for future regularity analysis
  private float calculateTableConfidence(Mat horizontal, Mat vertical, int hCount, int vCount) {
    // Base confidence from line counts
    float lineConfidence = Math.min(1.0f, (hCount + vCount) / 10.0f);

    // Check line regularity (spacing should be relatively uniform)
    // This is a simplified check - could be enhanced
    float regularityConfidence = 0.8f; // Default to reasonable confidence

    return (lineConfidence + regularityConfidence) / 2.0f;
  }

  /**
   * Detects individual cells within a table region.
   *
   * @param horizontal horizontal line image
   * @param vertical vertical line image
   * @param tableBounds bounding rectangle of the table
   * @return list of detected cells
   */
  private List<TableCell> detectCells(
      Mat horizontal, Mat vertical, org.opencv.core.Rect tableBounds) {
    List<TableCell> cells = new ArrayList<>();

    // Find horizontal line positions
    List<Integer> hLines = findLinePositions(horizontal, true);

    // Find vertical line positions
    List<Integer> vLines = findLinePositions(vertical, false);

    // Create cells from line intersections
    for (int row = 0; row < hLines.size() - 1; row++) {
      for (int col = 0; col < vLines.size() - 1; col++) {
        int x1 = vLines.get(col) + tableBounds.x;
        int y1 = hLines.get(row) + tableBounds.y;
        int x2 = vLines.get(col + 1) + tableBounds.x;
        int y2 = hLines.get(row + 1) + tableBounds.y;

        Rect cellBounds = new Rect(x1, y1, x2, y2);
        TableCell cell = new TableCell(row, col, cellBounds);
        cells.add(cell);
      }
    }

    return cells;
  }

  /**
   * Finds positions of lines in a line image.
   *
   * @param lineImage image containing lines
   * @param horizontal true for horizontal lines
   * @return sorted list of line positions
   */
  private List<Integer> findLinePositions(Mat lineImage, boolean horizontal) {
    List<Integer> positions = new ArrayList<>();
    int size = horizontal ? lineImage.rows() : lineImage.cols();
    int threshold = (horizontal ? lineImage.cols() : lineImage.rows()) / 10;

    boolean inLine = false;
    int lineStart = 0;

    for (int i = 0; i < size; i++) {
      int count = 0;
      if (horizontal) {
        for (int x = 0; x < lineImage.cols(); x++) {
          if (lineImage.get(i, x)[0] > 0) count++;
        }
      } else {
        for (int y = 0; y < lineImage.rows(); y++) {
          if (lineImage.get(y, i)[0] > 0) count++;
        }
      }

      if (count > threshold) {
        if (!inLine) {
          lineStart = i;
          inLine = true;
        }
      } else {
        if (inLine) {
          positions.add((lineStart + i) / 2); // Center of line
          inLine = false;
        }
      }
    }

    // Handle line at end
    if (inLine) {
      positions.add((lineStart + size) / 2);
    }

    // Add boundaries if not present
    if (positions.isEmpty() || positions.get(0) > 5) {
      positions.add(0, 0);
    }
    if (positions.isEmpty() || positions.get(positions.size() - 1) < size - 5) {
      positions.add(size);
    }

    Collections.sort(positions);
    return positions;
  }

  /**
   * Detects if the table has a header row.
   *
   * @param cells list of table cells
   * @return true if header is detected
   */
  private boolean detectHeader(List<TableCell> cells) {
    if (cells.isEmpty()) return false;

    // Simple heuristic: check if first row cells are different
    // (e.g., taller, different background)
    // For now, assume tables have headers if they have multiple rows
    int maxRow = 0;
    for (TableCell cell : cells) {
      if (cell.getRow() > maxRow) {
        maxRow = cell.getRow();
      }
    }
    return maxRow > 0;
  }

  /**
   * Checks if a region likely contains a table.
   *
   * @param binaryImage binary document image
   * @return true if tables are likely present
   */
  public boolean containsTables(Mat binaryImage) {
    List<DocumentRegion> tables = detectTables(binaryImage);
    return !tables.isEmpty();
  }

  /**
   * Gets the detailed table structure for a detected table region.
   *
   * @param binaryImage binary image
   * @param tableRegion region containing the table
   * @return DetectedTable with full structure, or null if not a valid table
   */
  public DetectedTable getTableStructure(Mat binaryImage, DocumentRegion tableRegion) {
    if (tableRegion == null || tableRegion.getType() != DocumentRegion.Type.TABLE) {
      return null;
    }

    Rect bounds = tableRegion.getBounds();
    org.opencv.core.Rect cvBounds =
        new org.opencv.core.Rect(bounds.left, bounds.top, bounds.width(), bounds.height());

    // Extract table region
    Mat tableImage = binaryImage.submat(cvBounds);

    // Re-analyze structure
    List<DetectedTable> tables = detectTablesWithLines(tableImage);

    tableImage.release();

    if (tables.isEmpty()) {
      return null;
    }

    // Adjust bounds to absolute coordinates
    DetectedTable table = tables.get(0);
    Rect adjustedBounds =
        new Rect(
            table.getBounds().left + bounds.left,
            table.getBounds().top + bounds.top,
            table.getBounds().right + bounds.left,
            table.getBounds().bottom + bounds.top);
    table.setBounds(adjustedBounds);

    for (TableCell cell : table.getCells()) {
      Rect cellBounds = cell.getBounds();
      cell.setBounds(
          new Rect(
              cellBounds.left + bounds.left,
              cellBounds.top + bounds.top,
              cellBounds.right + bounds.left,
              cellBounds.bottom + bounds.top));
    }

    return table;
  }

  /** Represents a detected table with its structure. */
  @Getter
  @Setter
  public static class DetectedTable {
    private Rect bounds;
    private int rows;
    private int cols;
    private List<TableCell> cells;
    private boolean hasHeader;
    private float confidence;

    public DetectedTable(Rect bounds) {
      this.bounds = bounds;
      this.cells = new ArrayList<>();
      this.confidence = 1.0f;
    }
  }

  /** Represents a single cell within a table. */
  @Getter
  @Setter
  public static class TableCell {
    private int row;
    private int col;
    private int rowSpan;
    private int colSpan;
    private Rect bounds;
    private String text;
    private float confidence;

    public TableCell(int row, int col, Rect bounds) {
      this.row = row;
      this.col = col;
      this.bounds = bounds;
      this.rowSpan = 1;
      this.colSpan = 1;
      this.confidence = 1.0f;
    }
  }
}

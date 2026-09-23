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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts document regions into proper reading order based on their positions and the document's text
 * direction (LTR or RTL).
 */
public class ReadingOrderSorter {

  /** Tolerance for considering regions to be on the same line (as fraction of average height). */
  private static final double LINE_TOLERANCE_RATIO = 0.5;

  /**
   * Sorts a list of document regions into reading order. Uses left-to-right, top-to-bottom ordering
   * by default.
   *
   * @param regions list of regions to sort
   */
  public static void sort(List<DocumentRegion> regions) {
    sort(regions, TextDirection.LTR);
  }

  /**
   * Sorts a list of document regions into reading order with specified text direction.
   *
   * @param regions list of regions to sort
   * @param direction text direction (LTR or RTL)
   */
  public static void sort(List<DocumentRegion> regions, TextDirection direction) {
    if (regions == null || regions.size() <= 1) {
      return;
    }

    // Calculate average height for line tolerance
    int totalHeight = 0;
    for (DocumentRegion region : regions) {
      if (region.getBounds() != null) {
        totalHeight += region.getBounds().height();
      }
    }
    final int avgHeight = totalHeight / regions.size();
    final int lineTolerance = (int) (avgHeight * LINE_TOLERANCE_RATIO);

    // Sort by position
    final TextDirection dir = direction;
    Collections.sort(
        regions,
        new Comparator<DocumentRegion>() {
          @Override
          public int compare(DocumentRegion r1, DocumentRegion r2) {
            Rect b1 = r1.getBounds();
            Rect b2 = r2.getBounds();

            if (b1 == null && b2 == null) return 0;
            if (b1 == null) return 1;
            if (b2 == null) return -1;

            // Check if regions are on the same line (within tolerance)
            int y1 = b1.top;
            int y2 = b2.top;

            if (Math.abs(y1 - y2) <= lineTolerance) {
              // Same line - sort by x position based on text direction
              if (dir == TextDirection.RTL) {
                return Integer.compare(b2.left, b1.left); // Right to left
              } else {
                return Integer.compare(b1.left, b2.left); // Left to right
              }
            } else {
              // Different lines - sort by y position (top to bottom)
              return Integer.compare(y1, y2);
            }
          }
        });

    // Update reading order indices
    for (int i = 0; i < regions.size(); i++) {
      regions.get(i).setReadingOrder(i);
    }
  }

  /**
   * Sorts regions within columns, maintaining column-first reading order. Each column is read
   * top-to-bottom before moving to the next column. Headers are always placed first, footers are
   * always placed last.
   *
   * @param regions list of regions to sort
   * @param direction text direction (LTR or RTL)
   */
  public static void sortByColumns(List<DocumentRegion> regions, TextDirection direction) {
    if (regions == null || regions.size() <= 1) {
      return;
    }

    // Separate headers, footers, columns, and other regions
    List<DocumentRegion> headers = new ArrayList<>();
    List<DocumentRegion> footers = new ArrayList<>();
    List<DocumentRegion> otherUnassigned = new ArrayList<>();

    // Group regions by column index
    List<List<DocumentRegion>> columnGroups = new ArrayList<>();
    int maxColumnIndex = -1;

    for (DocumentRegion region : regions) {
      int colIdx = region.getColumnIndex();
      if (colIdx >= 0) {
        maxColumnIndex = Math.max(maxColumnIndex, colIdx);
      }
    }

    // Initialize column groups
    for (int i = 0; i <= maxColumnIndex; i++) {
      columnGroups.add(new ArrayList<>());
    }

    // Categorize regions: headers, footers, columns, and other
    for (DocumentRegion region : regions) {
      if (region.getType() == DocumentRegion.Type.HEADER) {
        headers.add(region);
      } else if (region.getType() == DocumentRegion.Type.FOOTER) {
        footers.add(region);
      } else {
        int colIdx = region.getColumnIndex();
        if (colIdx >= 0 && colIdx < columnGroups.size()) {
          columnGroups.get(colIdx).add(region);
        } else {
          otherUnassigned.add(region);
        }
      }
    }

    // Determine the top of the column area to split unassigned regions
    int columnTop = Integer.MAX_VALUE;
    int columnBottom = Integer.MIN_VALUE;
    for (List<DocumentRegion> group : columnGroups) {
      for (DocumentRegion r : group) {
        if (r.getBounds() != null) {
          columnTop = Math.min(columnTop, r.getBounds().top);
          columnBottom = Math.max(columnBottom, r.getBounds().bottom);
        }
      }
    }

    // Split unassigned into before-columns and after-columns based on position
    List<DocumentRegion> beforeColumns = new ArrayList<>();
    List<DocumentRegion> afterColumns = new ArrayList<>();
    for (DocumentRegion r : otherUnassigned) {
      if (r.getBounds() != null && r.getBounds().bottom <= columnTop) {
        beforeColumns.add(r);
      } else if (r.getBounds() != null && r.getBounds().top >= columnBottom) {
        afterColumns.add(r);
      } else {
        // Overlaps with column area - use center Y to decide
        int centerY = (r.getBounds().top + r.getBounds().bottom) / 2;
        if (centerY < columnTop) {
          beforeColumns.add(r);
        } else {
          afterColumns.add(r);
        }
      }
    }

    // Sort headers by y position (top to bottom)
    Collections.sort(
        headers,
        new Comparator<DocumentRegion>() {
          @Override
          public int compare(DocumentRegion r1, DocumentRegion r2) {
            Rect b1 = r1.getBounds();
            Rect b2 = r2.getBounds();
            if (b1 == null && b2 == null) return 0;
            if (b1 == null) return 1;
            if (b2 == null) return -1;
            return Integer.compare(b1.top, b2.top);
          }
        });

    // Sort each column group by y position
    for (List<DocumentRegion> group : columnGroups) {
      Collections.sort(
          group,
          new Comparator<DocumentRegion>() {
            @Override
            public int compare(DocumentRegion r1, DocumentRegion r2) {
              Rect b1 = r1.getBounds();
              Rect b2 = r2.getBounds();
              if (b1 == null && b2 == null) return 0;
              if (b1 == null) return 1;
              if (b2 == null) return -1;
              return Integer.compare(b1.top, b2.top);
            }
          });
    }

    // Sort before/after column regions by position
    sort(beforeColumns, direction);
    sort(afterColumns, direction);

    // Sort footers by y position
    Collections.sort(
        footers,
        new Comparator<DocumentRegion>() {
          @Override
          public int compare(DocumentRegion r1, DocumentRegion r2) {
            Rect b1 = r1.getBounds();
            Rect b2 = r2.getBounds();
            if (b1 == null && b2 == null) return 0;
            if (b1 == null) return 1;
            if (b2 == null) return -1;
            return Integer.compare(b1.top, b2.top);
          }
        });

    // Rebuild the list in correct reading order:
    // 1. Headers first
    // 2. Unassigned regions above columns
    // 3. Columns (left to right or right to left)
    // 4. Unassigned regions below columns
    // 5. Footers last
    regions.clear();
    int readingOrder = 0;

    // 1. Add headers first
    for (DocumentRegion header : headers) {
      header.setReadingOrder(readingOrder++);
      regions.add(header);
    }

    // 2. Add regions above columns
    for (DocumentRegion region : beforeColumns) {
      region.setReadingOrder(readingOrder++);
      regions.add(region);
    }

    // 3. Add columns in order based on text direction
    if (direction == TextDirection.RTL) {
      for (int i = columnGroups.size() - 1; i >= 0; i--) {
        for (DocumentRegion region : columnGroups.get(i)) {
          region.setReadingOrder(readingOrder++);
          regions.add(region);
        }
      }
    } else {
      for (List<DocumentRegion> group : columnGroups) {
        for (DocumentRegion region : group) {
          region.setReadingOrder(readingOrder++);
          regions.add(region);
        }
      }
    }

    // 4. Add regions below columns
    for (DocumentRegion region : afterColumns) {
      region.setReadingOrder(readingOrder++);
      regions.add(region);
    }

    // 5. Add footers last
    for (DocumentRegion footer : footers) {
      footer.setReadingOrder(readingOrder++);
      regions.add(footer);
    }
  }

  /**
   * Sorts table cells in row-major order (left-to-right, top-to-bottom within table).
   *
   * @param cells list of table cell regions
   * @param direction text direction
   */
  public static void sortTableCells(List<DocumentRegion> cells, TextDirection direction) {
    if (cells == null || cells.size() <= 1) {
      return;
    }

    final TextDirection dir = direction;
    Collections.sort(
        cells,
        new Comparator<DocumentRegion>() {
          @Override
          public int compare(DocumentRegion c1, DocumentRegion c2) {
            // First sort by row
            int rowCompare = Integer.compare(c1.getRowIndex(), c2.getRowIndex());
            if (rowCompare != 0) {
              return rowCompare;
            }

            // Then by column (respecting text direction)
            if (dir == TextDirection.RTL) {
              return Integer.compare(c2.getColumnIndex(), c1.getColumnIndex());
            } else {
              return Integer.compare(c1.getColumnIndex(), c2.getColumnIndex());
            }
          }
        });

    // Update reading order
    for (int i = 0; i < cells.size(); i++) {
      cells.get(i).setReadingOrder(i);
    }
  }

  /**
   * Sorts regions hierarchically: HEADER regions are placed first, FOOTER regions last, and all
   * other regions are sorted purely by position (top-to-bottom, then left-to-right or right-to-left
   * based on text direction). This ensures that sidebars, tables, and other non-body regions are
   * sorted by their actual position rather than by type priority.
   *
   * @param regions list of regions to sort
   * @param direction text direction
   */
  public static void sortHierarchical(List<DocumentRegion> regions, TextDirection direction) {
    if (regions == null || regions.size() <= 1) {
      return;
    }

    // Separate structural anchors (header/footer) from content regions
    List<DocumentRegion> headers = new ArrayList<>();
    List<DocumentRegion> footers = new ArrayList<>();
    List<DocumentRegion> content = new ArrayList<>();

    for (DocumentRegion region : regions) {
      if (region.getType() == DocumentRegion.Type.HEADER) {
        headers.add(region);
      } else if (region.getType() == DocumentRegion.Type.FOOTER) {
        footers.add(region);
      } else {
        content.add(region);
      }
    }

    // Sort headers by y position (top to bottom)
    Comparator<DocumentRegion> byTop =
        (r1, r2) -> {
          Rect b1 = r1.getBounds();
          Rect b2 = r2.getBounds();
          if (b1 == null && b2 == null) return 0;
          if (b1 == null) return 1;
          if (b2 == null) return -1;
          return Integer.compare(b1.top, b2.top);
        };
    Collections.sort(headers, byTop);
    Collections.sort(footers, byTop);

    // Sort content regions purely by position
    sort(content, direction);

    // Rebuild list: headers first, then content, then footers
    regions.clear();
    int readingOrder = 0;
    for (DocumentRegion h : headers) {
      h.setReadingOrder(readingOrder++);
      regions.add(h);
    }
    for (DocumentRegion c : content) {
      c.setReadingOrder(readingOrder++);
      regions.add(c);
    }
    for (DocumentRegion f : footers) {
      f.setReadingOrder(readingOrder++);
      regions.add(f);
    }
  }

  /**
   * Determines the likely text direction based on language code.
   *
   * @param languageCode ISO 639-1 or 639-2 language code
   * @return detected text direction
   */
  public static TextDirection getDirectionForLanguage(String languageCode) {
    if (languageCode == null || languageCode.isEmpty()) {
      return TextDirection.LTR;
    }

    String lang = languageCode.toLowerCase(java.util.Locale.ROOT);

    // RTL languages
    if (lang.equals("ar")
        || lang.equals("ara")
        || // Arabic
        lang.equals("he")
        || lang.equals("heb")
        || // Hebrew
        lang.equals("fa")
        || lang.equals("fas")
        || // Persian/Farsi
        lang.equals("ur")
        || lang.equals("urd")
        || // Urdu
        lang.equals("yi")
        || lang.equals("yid")
        || // Yiddish
        lang.equals("ps")
        || lang.equals("pus")
        || // Pashto
        lang.equals("sd")
        || lang.equals("snd")
        || // Sindhi
        lang.equals("ug")
        || lang.equals("uig")) { // Uyghur
      return TextDirection.RTL;
    }

    return TextDirection.LTR;
  }

  /**
   * Recursively sorts regions and their children.
   *
   * @param regions list of regions to sort
   * @param direction text direction
   */
  public static void sortRecursive(List<DocumentRegion> regions, TextDirection direction) {
    if (regions == null || regions.isEmpty()) {
      return;
    }

    // Sort top-level regions
    sort(regions, direction);

    // Recursively sort children
    for (DocumentRegion region : regions) {
      if (region.hasChildren()) {
        List<DocumentRegion> children = region.getChildren();

        // Use appropriate sorting based on region type
        if (region.getType() == DocumentRegion.Type.TABLE) {
          sortTableCells(children, direction);
        } else if (region.getType() == DocumentRegion.Type.COLUMN) {
          sort(children, direction);
        } else {
          sortRecursive(children, direction);
        }
      }
    }
  }

  /** Text direction for reading order determination. */
  public enum TextDirection {
    /** Left-to-right reading (e.g., English, German, French) */
    LTR,

    /** Right-to-left reading (e.g., Arabic, Hebrew, Persian) */
    RTL
  }
}

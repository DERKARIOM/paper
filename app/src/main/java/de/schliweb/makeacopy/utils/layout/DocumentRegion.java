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
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a detected region within a document image. Used for layout analysis to identify
 * different structural elements like headers, body text, tables, figures, etc.
 */
@Getter
@Setter
public class DocumentRegion {

  /** Bounding rectangle in image pixel coordinates */
  private Rect bounds;

  /** Type of this document region */
  private Type type;

  /** Confidence score for the region detection (0.0 to 1.0) */
  private float confidence;

  /** Reading order index for proper text flow reconstruction */
  private int readingOrder;

  /** Nested child regions (e.g., columns within body, cells within table) */
  private List<DocumentRegion> children;

  /** Parent region reference (null for top-level regions) */
  private DocumentRegion parent;

  /** Optional metadata: detected language for this region */
  private String language;

  /** Column index for multi-column layouts (0-based, -1 if not applicable) */
  private int columnIndex = -1;

  /** Row index for table cells (0-based, -1 if not applicable) */
  private int rowIndex = -1;

  /**
   * Creates a new DocumentRegion with the specified bounds and type.
   *
   * @param bounds bounding rectangle in image coordinates
   * @param type region type
   */
  public DocumentRegion(Rect bounds, Type type) {
    this.bounds = bounds;
    this.type = type;
    this.confidence = 1.0f;
    this.readingOrder = 0;
    this.children = new ArrayList<>();
  }

  /**
   * Creates a new DocumentRegion with full parameters.
   *
   * @param bounds bounding rectangle in image coordinates
   * @param type region type
   * @param confidence detection confidence (0.0 to 1.0)
   * @param readingOrder reading order index
   */
  public DocumentRegion(Rect bounds, Type type, float confidence, int readingOrder) {
    this.bounds = bounds;
    this.type = type;
    this.confidence = confidence;
    this.readingOrder = readingOrder;
    this.children = new ArrayList<>();
  }

  /**
   * Adds a child region to this region.
   *
   * @param child the child region to add
   */
  public void addChild(DocumentRegion child) {
    if (children == null) {
      children = new ArrayList<>();
    }
    child.setParent(this);
    children.add(child);
  }

  /**
   * Checks if this region has any child regions.
   *
   * @return true if this region has children
   */
  public boolean hasChildren() {
    return children != null && !children.isEmpty();
  }

  /**
   * Returns the area of this region in pixels.
   *
   * @return area in square pixels
   */
  public int getArea() {
    if (bounds == null) return 0;
    return bounds.width() * bounds.height();
  }

  /**
   * Checks if this region contains the specified point.
   *
   * @param x x-coordinate
   * @param y y-coordinate
   * @return true if the point is within this region's bounds
   */
  public boolean contains(int x, int y) {
    return bounds != null && bounds.contains(x, y);
  }

  /**
   * Checks if this region intersects with another region.
   *
   * @param other the other region to check
   * @return true if the regions intersect
   */
  public boolean intersects(DocumentRegion other) {
    if (bounds == null || other.bounds == null) return false;
    return Rect.intersects(bounds, other.bounds);
  }

  /**
   * Returns the optimal Tesseract Page Segmentation Mode (PSM) for this region type.
   *
   * @return recommended PSM value for OCR processing
   */
  public int getOptimalPsm() {
    switch (type) {
      case HEADER:
        return 6; // PSM_SINGLE_BLOCK - often a single text block
      case BODY:
        return 3; // PSM_AUTO - automatic segmentation
      case FOOTER:
        return 7; // PSM_SINGLE_LINE - usually single line
      case TABLE:
        // PSM_SINGLE_BLOCK - treat the table region as one uniform block.
        // PSM_SPARSE_TEXT (11) consistently underperformed on real tables: it
        // assumes non-contiguous, isolated tokens and yields very low word
        // counts on actual table bodies (see OCR log analysis 2026-04-27).
        return 6;
      case CAPTION:
        return 7; // PSM_SINGLE_LINE - short description
      case SIDEBAR:
        return 6; // PSM_SINGLE_BLOCK
      case MARGIN_NOTE:
        return 6; // PSM_SINGLE_BLOCK
      case COLUMN:
        return 4; // PSM_SINGLE_COLUMN - better for isolated column regions
      default:
        return 3; // PSM_AUTO - fallback
    }
  }

  @Override
  public String toString() {
    return "DocumentRegion{"
        + "type="
        + type
        + ", bounds="
        + bounds
        + ", confidence="
        + confidence
        + ", readingOrder="
        + readingOrder
        + ", children="
        + (children != null ? children.size() : 0)
        + '}';
  }

  /** Types of document regions that can be detected. */
  public enum Type {
    /** Header region (letterhead, logo area) - typically top 10-15% of page */
    HEADER,

    /** Main body text region */
    BODY,

    /** Footer region (page numbers, footnotes) - typically bottom 5-10% of page */
    FOOTER,

    /** Table region with structured cell layout */
    TABLE,

    /** Figure or image region (non-text graphical content) */
    FIGURE,

    /** Caption text (usually below figures or tables) */
    CAPTION,

    /** Sidebar region (margin content, pull quotes) */
    SIDEBAR,

    /** Margin note or annotation */
    MARGIN_NOTE,

    /** Column of text in a multi-column layout */
    COLUMN,

    /** Unknown or unclassified region */
    UNKNOWN
  }
}

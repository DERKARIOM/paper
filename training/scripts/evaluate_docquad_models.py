#!/usr/bin/env python3
"""
Evaluation script for DocQuadNet models.

Compares one or more trained ONNX or ORT models on a fixed real-world test set.
Produces a JSON report with per-model metrics and pairwise comparisons.

Supports two evaluation modes:
  --mode raw      Measure raw model quality (default). No post-processing repairs.
  --mode product  Measure product-level behavior with full post-processing pipeline
                  IDENTICAL to the MakeACopy Android app (DocQuadPostprocessor.java).

See training/EVALUATION.md for full documentation.
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any

import numpy as np
from PIL import Image

try:
    import onnxruntime as ort
except ImportError:
    ort = None  # type: ignore


# ============================================================================
# Evaluation Mode
# ============================================================================

class EvalMode(Enum):
    """Evaluation mode determines how model outputs are processed before metrics."""
    RAW = "raw"
    PRODUCT = "product"

    def __str__(self) -> str:
        return self.value


# ============================================================================
# Letterbox (mirrors DocQuadLetterbox.java)
# ============================================================================

@dataclass
class Letterbox:
    """
    Letterbox transformation for mapping source rectangle to destination rectangle
    while maintaining aspect ratio. Mirrors DocQuadLetterbox.java from MakeACopy app.
    """
    src_w: int
    src_h: int
    dst_w: int
    dst_h: int
    scale: float
    offset_x: float
    offset_y: float

    @classmethod
    def create(cls, src_w: int, src_h: int, dst_w: int = 256, dst_h: int = 256) -> "Letterbox":
        """Create letterbox transformation (mirrors DocQuadLetterbox.create)."""
        if src_w <= 0 or src_h <= 0 or dst_w <= 0 or dst_h <= 0:
            raise ValueError("All dimensions must be > 0")
        
        scale = min(dst_w / src_w, dst_h / src_h)
        new_w = src_w * scale
        new_h = src_h * scale
        offset_x = (dst_w - new_w) / 2.0
        offset_y = (dst_h - new_h) / 2.0
        
        return cls(src_w, src_h, dst_w, dst_h, scale, offset_x, offset_y)

    def forward(self, x: float, y: float) -> tuple[float, float]:
        """Transform from source space to destination space."""
        return (x * self.scale + self.offset_x, y * self.scale + self.offset_y)

    def inverse(self, x: float, y: float) -> tuple[float, float]:
        """Transform from destination space back to source space."""
        return ((x - self.offset_x) / self.scale, (y - self.offset_y) / self.scale)


# ============================================================================
# Geometry helpers
# ============================================================================

def _is_number(x: Any) -> bool:
    return isinstance(x, (int, float)) and not (isinstance(x, float) and (math.isnan(x) or math.isinf(x)))


def _shoelace_area(pts: list[tuple[float, float]]) -> float:
    """Positive scalar area (abs), independent of CW/CCW."""
    s = 0.0
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % n]
        s += x1 * y2 - y1 * x2
    return abs(s) * 0.5


def _cross(ax: float, ay: float, bx: float, by: float, cx: float, cy: float) -> float:
    """Cross product of AB x AC."""
    return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)


def _is_convex_quad(pts: list[tuple[float, float]], eps: float = 1e-9) -> bool:
    """Convex if all non-zero cross products have the same sign."""
    signs: list[int] = []
    n = len(pts)
    for i in range(n):
        ax, ay = pts[i]
        bx, by = pts[(i + 1) % n]
        cx, cy = pts[(i + 2) % n]
        z = _cross(ax, ay, bx, by, cx, cy)
        if abs(z) <= eps:
            continue
        signs.append(1 if z > 0.0 else -1)
    if not signs:
        return False
    return all(s == signs[0] for s in signs)


def _on_segment(ax: float, ay: float, bx: float, by: float, px: float, py: float, eps: float) -> bool:
    if min(ax, bx) - eps <= px <= max(ax, bx) + eps and min(ay, by) - eps <= py <= max(ay, by) + eps:
        return abs(_cross(ax, ay, bx, by, px, py)) <= eps
    return False


def _segments_intersect(a: tuple[float, float], b: tuple[float, float], c: tuple[float, float], d: tuple[float, float], eps: float = 1e-9) -> bool:
    ax, ay = a
    bx, by = b
    cx, cy = c
    dx, dy = d

    o1 = _cross(ax, ay, bx, by, cx, cy)
    o2 = _cross(ax, ay, bx, by, dx, dy)
    o3 = _cross(cx, cy, dx, dy, ax, ay)
    o4 = _cross(cx, cy, dx, dy, bx, by)

    def _sgn(z: float) -> int:
        if abs(z) <= eps:
            return 0
        return 1 if z > 0.0 else -1

    s1, s2, s3, s4 = _sgn(o1), _sgn(o2), _sgn(o3), _sgn(o4)

    if s1 != 0 and s2 != 0 and s3 != 0 and s4 != 0:
        return (s1 != s2) and (s3 != s4)

    if s1 == 0 and _on_segment(ax, ay, bx, by, cx, cy, eps):
        return True
    if s2 == 0 and _on_segment(ax, ay, bx, by, dx, dy, eps):
        return True
    if s3 == 0 and _on_segment(cx, cy, dx, dy, ax, ay, eps):
        return True
    if s4 == 0 and _on_segment(cx, cy, dx, dy, bx, by, eps):
        return True

    return False


def _is_self_intersecting_quad(pts: list[tuple[float, float]]) -> bool:
    """Check if quad edges intersect (non-adjacent pairs)."""
    e01 = (pts[0], pts[1])
    e12 = (pts[1], pts[2])
    e23 = (pts[2], pts[3])
    e30 = (pts[3], pts[0])

    if _segments_intersect(e01[0], e01[1], e23[0], e23[1]):
        return True
    if _segments_intersect(e12[0], e12[1], e30[0], e30[1]):
        return True
    return False


def _bounds_ok(pts: list[tuple[float, float]], w: int, h: int, tolerance: float = 0.0) -> bool:
    """
    Check if all points are within image bounds.
    
    Args:
        pts: List of 4 corner points
        w: Image width
        h: Image height
        tolerance: Fraction of image dimension to allow outside bounds.
                   0.0 = strict bounds [0, w-1] x [0, h-1]
                   0.25 = App-compatible bounds [-w*0.25, w*1.25] x [-h*0.25, h*1.25]
    """
    for x, y in pts:
        min_x = -w * tolerance
        max_x = w * (1.0 + tolerance) - 1.0 if tolerance == 0.0 else w * (1.0 + tolerance)
        min_y = -h * tolerance
        max_y = h * (1.0 + tolerance) - 1.0 if tolerance == 0.0 else h * (1.0 + tolerance)
        if x < min_x or x > max_x or y < min_y or y > max_y:
            return False
    return True


def _edge_length(p1: tuple[float, float], p2: tuple[float, float]) -> float:
    return math.sqrt((p2[0] - p1[0]) ** 2 + (p2[1] - p1[1]) ** 2)


def _is_degenerate_quad(pts: list[tuple[float, float]], min_edge: float = 5.0, min_area: float = 100.0) -> bool:
    """Check if quad is degenerate (too small edges or area)."""
    area = _shoelace_area(pts)
    if area < min_area:
        return True
    for i in range(4):
        if _edge_length(pts[i], pts[(i + 1) % 4]) < min_edge:
            return True
    return False


def _compute_iou(pts_a: list[tuple[float, float]], pts_b: list[tuple[float, float]]) -> float:
    """Compute IoU between two quads using Shapely if available, else approximate."""
    try:
        from shapely.geometry import Polygon
        poly_a = Polygon(pts_a)
        poly_b = Polygon(pts_b)
        if not poly_a.is_valid or not poly_b.is_valid:
            return 0.0
        inter = poly_a.intersection(poly_b).area
        union = poly_a.union(poly_b).area
        if union < 1e-9:
            return 0.0
        return inter / union
    except ImportError:
        # Fallback: approximate IoU using rasterization
        return _compute_iou_raster(pts_a, pts_b)


def _compute_iou_raster(pts_a: list[tuple[float, float]], pts_b: list[tuple[float, float]], resolution: int = 256) -> float:
    """Approximate IoU by rasterizing both quads."""
    all_pts = pts_a + pts_b
    min_x = min(p[0] for p in all_pts)
    max_x = max(p[0] for p in all_pts)
    min_y = min(p[1] for p in all_pts)
    max_y = max(p[1] for p in all_pts)

    w = max_x - min_x
    h = max_y - min_y
    if w < 1e-9 or h < 1e-9:
        return 0.0

    scale = resolution / max(w, h)

    def normalize(pts: list[tuple[float, float]]) -> list[tuple[int, int]]:
        return [(int((p[0] - min_x) * scale), int((p[1] - min_y) * scale)) for p in pts]

    from PIL import Image, ImageDraw
    size = (int(w * scale) + 1, int(h * scale) + 1)

    mask_a = Image.new("1", size, 0)
    mask_b = Image.new("1", size, 0)

    ImageDraw.Draw(mask_a).polygon(normalize(pts_a), fill=1)
    ImageDraw.Draw(mask_b).polygon(normalize(pts_b), fill=1)

    arr_a = np.array(mask_a, dtype=np.uint8)
    arr_b = np.array(mask_b, dtype=np.uint8)

    inter = np.sum(arr_a & arr_b)
    union = np.sum(arr_a | arr_b)

    if union == 0:
        return 0.0
    return float(inter) / float(union)


def _corner_mae_px(pts_pred: list[tuple[float, float]], pts_gt: list[tuple[float, float]]) -> float:
    """Mean absolute error of corner positions in pixels."""
    total = 0.0
    for (px, py), (gx, gy) in zip(pts_pred, pts_gt):
        total += math.sqrt((px - gx) ** 2 + (py - gy) ** 2)
    return total / 4.0


def _corner_mae_px_and_rel(
    pts_pred: list[tuple[float, float]],
    pts_gt: list[tuple[float, float]],
    img_w: int,
    img_h: int,
) -> tuple[float, float]:
    """Mean absolute error of corner positions in pixels and relative to image diagonal.
    
    Returns:
        (mae_px, mae_rel): mae_px is the absolute error in pixels,
                          mae_rel is the error relative to image diagonal (0..1, e.g. 0.02 = 2%).
    """
    mae_px = _corner_mae_px(pts_pred, pts_gt)
    diag = math.sqrt(float(img_w) ** 2 + float(img_h) ** 2)
    mae_rel = mae_px / diag if diag > 0 else 0.0
    return mae_px, mae_rel


# ============================================================================
# PRODUCT mode post-processing (IDENTICAL to MakeACopy app DocQuadPostprocessor.java)
# ============================================================================

def _sigmoid(x: float) -> float:
    """Sigmoid function (mirrors DocQuadPostprocessor.sigmoid)."""
    return 1.0 / (1.0 + math.exp(-x))


def _canonicalize_quad_order_v1(pts: list[tuple[float, float]]) -> list[tuple[float, float]]:
    """
    Canonicalize quad corner order to TL, TR, BR, BL (clockwise from top-left).
    
    EXACT port of DocQuadPostprocessor.canonicalizeQuadOrderV1 from MakeACopy app.
    """
    if len(pts) != 4:
        raise ValueError("pts must have exactly 4 points")
    
    # Compute centroid
    cx = sum(p[0] for p in pts) / 4.0
    cy = sum(p[1] for p in pts) / 4.0
    
    # Sort by angle ascending (tie-break by index) – analogous to Python REORDER_METHOD_V1
    ordered = [0, 1, 2, 3]
    for i in range(4):
        for j in range(i + 1, 4):
            a = ordered[i]
            b = ordered[j]
            ang_a = math.atan2(pts[a][1] - cy, pts[a][0] - cx)
            ang_b = math.atan2(pts[b][1] - cy, pts[b][0] - cx)
            swap = False
            if ang_b < ang_a:
                swap = True
            elif ang_b == ang_a and b < a:
                swap = True
            if swap:
                ordered[i] = b
                ordered[j] = a
    
    # Rotation so that TL comes first (min(x+y); tie-break by position in ordered)
    tl_pos = 0
    best_sum = float('inf')
    for k in range(4):
        idx = ordered[k]
        s = pts[idx][0] + pts[idx][1]
        if s < best_sum or (s == best_sum and k < tl_pos):
            best_sum = s
            tl_pos = k
    
    out = []
    for i in range(4):
        src = ordered[(tl_pos + i) % 4]
        out.append(pts[src])
    return out


def _oob_1d(v: float, min_v: float, max_v: float) -> float:
    """Out-of-bounds distance in 1D (mirrors DocQuadScore.oob1d)."""
    if v < min_v:
        return min_v - v
    if v > max_v:
        return v - max_v
    return 0.0


def _oob_sum(quad: list[tuple[float, float]], w: float, h: float, tol_px: float) -> float:
    """Sum of OOB distances for all 4 points (mirrors DocQuadScore.oobSum)."""
    left = -tol_px
    top = -tol_px
    right = (w - 1.0) + tol_px
    bottom = (h - 1.0) + tol_px
    
    s = 0.0
    for x, y in quad:
        s += _oob_1d(x, left, right) + _oob_1d(y, top, bottom)
    return s


def _oob_max(quad: list[tuple[float, float]], w: float, h: float, tol_px: float) -> float:
    """Max of OOB distances over all 4 points (mirrors DocQuadScore.oobMax)."""
    left = -tol_px
    top = -tol_px
    right = (w - 1.0) + tol_px
    bottom = (h - 1.0) + tol_px
    
    m = 0.0
    for x, y in quad:
        v = _oob_1d(x, left, right) + _oob_1d(y, top, bottom)
        if v > m:
            m = v
    return m


def _edge_length_min(quad: list[tuple[float, float]]) -> float:
    """Minimum edge length (mirrors DocQuadScore.edgeLengthMin)."""
    m = float('inf')
    for i in range(4):
        j = (i + 1) % 4
        d = math.hypot(quad[j][0] - quad[i][0], quad[j][1] - quad[i][1])
        if d < m:
            m = d
    return m


def _edge_length_max(quad: list[tuple[float, float]]) -> float:
    """Maximum edge length (mirrors DocQuadScore.edgeLengthMax)."""
    m = 0.0
    for i in range(4):
        j = (i + 1) % 4
        d = math.hypot(quad[j][0] - quad[i][0], quad[j][1] - quad[i][1])
        if d > m:
            m = d
    return m


def _point_in_poly_inclusive(poly: list[tuple[float, float]], px: float, py: float) -> bool:
    """
    Point-in-polygon test with edge inclusion.
    EXACT port of DocQuadPostprocessor.pointInPolyInclusive.
    """
    # 1) Edge-inclusive: point lies on an edge
    for i in range(4):
        j = (i + 1) % 4
        if _on_segment(poly[i][0], poly[i][1], poly[j][0], poly[j][1], px, py, 1e-9):
            return True
    
    # 2) Ray casting (right)
    inside = False
    j = 3
    for i in range(4):
        xi, yi = poly[i]
        xj, yj = poly[j]
        
        intersect = ((yi > py) != (yj > py)) and (px < (xj - xi) * (py - yi) / (yj - yi) + xi)
        if intersect:
            inside = not inside
        j = i
    return inside


def _quad_penalty_geometry(quad256: list[tuple[float, float]] | None) -> float:
    """
    Compute geometry penalty for a quad in 256-space.
    EXACT port of DocQuadPostprocessor.quadPenaltyGeometry.
    """
    if quad256 is None or len(quad256) != 4:
        return 1e6
    
    for i in range(4):
        if quad256[i] is None or len(quad256[i]) != 2:
            return 1e6
        if not math.isfinite(quad256[i][0]) or not math.isfinite(quad256[i][1]):
            return 1e6
    
    penalty = 0.0
    
    # Bounds penalty in 256-space (frame 0..255) with small tolerance
    w = 256.0
    h = 256.0
    tol = 2.0
    hard = 16.0
    k_soft = 10.0
    k_hard = 1000.0
    
    oob_sum_val = _oob_sum(quad256, w, h, tol)
    if oob_sum_val > 0.0:
        penalty += oob_sum_val * k_soft
    
    oob_max_val = _oob_max(quad256, w, h, tol)
    if oob_max_val > hard:
        penalty += 1e5 + (oob_max_val - hard) * k_hard
    
    if _is_self_intersecting_quad(quad256):
        penalty += 1e6
    
    if not _is_convex_quad(quad256):
        penalty += 1e6
    
    area_abs = _shoelace_area(quad256)
    if not (area_abs > 1.0):
        penalty += 1e6
    
    edge_min = _edge_length_min(quad256)
    edge_max = _edge_length_max(quad256)
    
    if edge_min < 8.0:
        penalty += (8.0 - edge_min) * 1000.0
    
    r = edge_max / max(edge_min, 1e-9)
    if r > 25.0:
        penalty += (r - 25.0) * 100.0
    
    return penalty


def _mask_disagreement_penalty_for_corners(quad_corners256: list[tuple[float, float]], mask_logits: np.ndarray) -> float:
    """
    Compute mask disagreement penalty for corner-based quad.
    EXACT port of DocQuadPostprocessor.maskDisagreementPenaltyForCorners.
    
    Args:
        quad_corners256: Quad in 256-space
        mask_logits: Mask logits array of shape (1, 1, 64, 64)
    """
    # Quad256 -> Quad64
    quad64 = [(x / 4.0, y / 4.0) for x, y in quad_corners256]
    
    grid = [0, 8, 16, 24, 32, 40, 48, 56]
    disagree = 0
    m = mask_logits[0, 0]  # Shape (64, 64)
    
    for gy in grid:
        for gx in grid:
            px = gx + 0.5
            py = gy + 0.5
            in_quad = _point_in_poly_inclusive(quad64, px, py)
            
            prob = _sigmoid(float(m[gy, gx]))
            in_mask = prob > 0.5
            
            if in_quad != in_mask:
                disagree += 1
    
    return float(disagree) * 10.0


def _refine_corners_64_to_256_3x3(corner_heatmaps: np.ndarray) -> list[tuple[float, float]]:
    """
    Subpixel refinement via 3×3 weighted centroid around the argmax peak.
    EXACT port of DocQuadPostprocessor.refineCorners64ToCorners256_3x3.
    
    Args:
        corner_heatmaps: Array of shape (1, 4, 64, 64)
    
    Returns:
        List of 4 corner coordinates in 256-space
    """
    corners256 = []
    
    for c in range(4):
        hm = corner_heatmaps[0, c]  # Shape (64, 64)
        
        # (1) Argmax (deterministic, strict '>')
        best = -float('inf')
        best_x = 0
        best_y = 0
        for y in range(64):
            for x in range(64):
                v = float(hm[y, x])
                if v > best:
                    best = v
                    best_x = x
                    best_y = y
        
        # (2) 3×3 Refinement (clipped window, no wraps)
        x0 = max(0, best_x - 1)
        x1 = min(63, best_x + 1)
        y0 = max(0, best_y - 1)
        y1 = min(63, best_y + 1)
        
        max_logit = -float('inf')
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                v = float(hm[y, x])
                if v > max_logit:
                    max_logit = v
        
        sum_w = 0.0
        sum_x = 0.0
        sum_y = 0.0
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                logit = float(hm[y, x])
                w = math.exp(logit - max_logit)
                sum_w += w
                sum_x += w * (x + 0.5)
                sum_y += w * (y + 0.5)
        
        if sum_w == 0.0 or not math.isfinite(sum_w):
            # Fallback: Argmax pixel center
            x64 = best_x + 0.5
            y64 = best_y + 0.5
        else:
            x64 = sum_x / sum_w
            y64 = sum_y / sum_w
        
        corners256.append((x64 * 4.0, y64 * 4.0))
    
    return corners256


def _quad_from_mask_256(mask_logits: np.ndarray, fallback_corners256: list[tuple[float, float]]) -> tuple[list[tuple[float, float]], bool]:
    """
    Compute quad from mask using PCA-based oriented rectangle.
    EXACT port of DocQuadPostprocessor.quadFromMask256.
    
    Args:
        mask_logits: Array of shape (1, 1, 64, 64)
        fallback_corners256: Fallback corners if mask is degenerate
    
    Returns:
        Tuple of (quad256, used_fallback)
    """
    m = mask_logits[0, 0]  # Shape (64, 64)
    
    mask_count = 0
    sum_x = 0.0
    sum_y = 0.0
    
    for y in range(64):
        for x in range(64):
            prob = _sigmoid(float(m[y, x]))
            if prob > 0.5:
                mask_count += 1
                sum_x += (x + 0.5)
                sum_y += (y + 0.5)
    
    if mask_count == 0:
        return fallback_corners256, True
    
    cx = sum_x / mask_count
    cy = sum_y / mask_count
    if not math.isfinite(cx) or not math.isfinite(cy):
        return fallback_corners256, True
    
    # Covariance matrix (mean over points)
    sxx = 0.0
    sxy = 0.0
    syy = 0.0
    for y in range(64):
        for x in range(64):
            prob = _sigmoid(float(m[y, x]))
            if prob > 0.5:
                dx = (x + 0.5) - cx
                dy = (y + 0.5) - cy
                sxx += dx * dx
                sxy += dx * dy
                syy += dy * dy
    
    sxx /= mask_count
    sxy /= mask_count
    syy /= mask_count
    
    trace = sxx + syy
    if not math.isfinite(trace) or trace < 1e-12:
        return fallback_corners256, True
    
    # Eigenvector v1 (for lambda1) analytically
    det = sxx * syy - sxy * sxy
    disc_arg = trace * trace / 4.0 - det
    disc = math.sqrt(max(0.0, disc_arg))
    lambda1 = trace / 2.0 + disc
    
    eps = 1e-12
    if abs(sxy) > eps:
        v1x = lambda1 - syy
        v1y = sxy
    else:
        if sxx >= syy:
            v1x = 1.0
            v1y = 0.0
        else:
            v1x = 0.0
            v1y = 1.0
    
    n = math.hypot(v1x, v1y)
    if n == 0.0 or not math.isfinite(n):
        return fallback_corners256, True
    v1x /= n
    v1y /= n
    
    # v2 orthogonal (right-handed)
    v2x = -v1y
    v2y = v1x
    
    # Projections (u/v) and extrema
    u_min = float('inf')
    u_max = -float('inf')
    v_min = float('inf')
    v_max = -float('inf')
    
    for y in range(64):
        for x in range(64):
            prob = _sigmoid(float(m[y, x]))
            if prob > 0.5:
                px = (x + 0.5) - cx
                py = (y + 0.5) - cy
                u = px * v1x + py * v1y
                v = px * v2x + py * v2y
                if u < u_min:
                    u_min = u
                if u > u_max:
                    u_max = u
                if v < v_min:
                    v_min = v
                if v > v_max:
                    v_max = v
    
    if not (math.isfinite(u_min) and math.isfinite(u_max) and math.isfinite(v_min) and math.isfinite(v_max)):
        return fallback_corners256, True
    if u_max - u_min < 1e-12 or v_max - v_min < 1e-12:
        return fallback_corners256, True
    
    # Reconstruction of 4 corners (64-space) in v1/v2 coordinates
    quad64 = [
        (cx + u_max * v1x + v_max * v2x, cy + u_max * v1y + v_max * v2y),  # q0
        (cx + u_min * v1x + v_max * v2x, cy + u_min * v1y + v_max * v2y),  # q1
        (cx + u_min * v1x + v_min * v2x, cy + u_min * v1y + v_min * v2y),  # q2
        (cx + u_max * v1x + v_min * v2x, cy + u_max * v1y + v_min * v2y),  # q3
    ]
    
    quad64 = _canonicalize_quad_order_v1(quad64)
    
    # 64→256 mapping
    quad256 = [(x * 4.0, y * 4.0) for x, y in quad64]
    return quad256, False


# Hard penalty threshold: If MASK quad has penalty >= this value, always fall back to CORNERS.
# This prevents choosing MASK quads with severe geometric issues (OOB > 16px, self-intersecting,
# non-convex, degenerate area) even if CORNERS has higher total penalty due to mask disagreement.
# Value 1e5 corresponds to the penalty added when oobMax > hard (16px) in _quad_penalty_geometry.
_HARD_PENALTY_THRESHOLD = 1e5

# Agreement threshold: Maximum allowed corner distance (in 256-space pixels) between
# CORNERS and MASK quads. If any corner differs by more than this, MASK is considered
# unreliable and CORNERS is preferred. This guards against distribution shift in the
# mask head causing poor MASK predictions while CORNERS remains accurate.
# Value 32px in 256-space corresponds to ~12.5% of the frame diagonal.
_AGREEMENT_MAX_CORNER_DIST = 32.0

# Score margin: MASK must have penalty at least this much lower than CORNERS geometry-only
# penalty to be chosen. This prevents MASK from winning solely due to mask disagreement
# penalty on CORNERS when both quads are geometrically similar.
# Value 50.0 corresponds to ~5 grid cells of disagreement (5 * 10.0).
_MASK_SCORE_MARGIN = 50.0

# Experimental product-policy switch used by CLI sweeps. The default keeps the Android production
# behavior exactly unchanged.
_PRODUCT_PATH_POLICY = "production"
_PRODUCT_PEAK_MODE = "refine_3x3"


def _max_corner_distance(quad1: list[tuple[float, float]], quad2: list[tuple[float, float]]) -> float:
    """Compute maximum Euclidean distance between corresponding corners of two quads."""
    if quad1 is None or quad2 is None or len(quad1) != 4 or len(quad2) != 4:
        return float('inf')
    max_dist = 0.0
    for i in range(4):
        dx = quad1[i][0] - quad2[i][0]
        dy = quad1[i][1] - quad2[i][1]
        dist = math.sqrt(dx * dx + dy * dy)
        if dist > max_dist:
            max_dist = dist
    return max_dist


def _argmax_corners_64_to_256(corner_heatmaps: np.ndarray) -> list[tuple[float, float]]:
    """Argmax corner extraction matching DocQuadPostprocessor.argmaxCorners64ToCorners256."""
    corners: list[tuple[float, float]] = []
    for c in range(4):
        hm = corner_heatmaps[0, c]
        idx = int(np.argmax(hm))
        y, x = np.unravel_index(idx, hm.shape)
        corners.append(((float(x) + 0.5) * 4.0, (float(y) + 0.5) * 4.0))
    return corners


def _raw_legacy_argmax_corners_64_to_256(corner_heatmaps: np.ndarray) -> list[tuple[float, float]]:
    """Legacy evaluator RAW argmax mapping kept only for product-logic experiments."""
    corners: list[tuple[float, float]] = []
    for c in range(4):
        hm = corner_heatmaps[0, c]
        idx = int(np.argmax(hm))
        y, x = np.unravel_index(idx, hm.shape)
        corners.append((float(x) * 4.0, float(y) * 4.0))
    return corners


def _refine_corners_64_to_256_5x5_quadratic(corner_heatmaps: np.ndarray) -> list[tuple[float, float]]:
    """Subpixel refinement port of DocQuadPostprocessor.refineCorners64ToCorners256_5x5Quadratic."""
    corners: list[tuple[float, float]] = []
    for c in range(4):
        hm = corner_heatmaps[0, c]
        idx = int(np.argmax(hm))
        best_y, best_x = np.unravel_index(idx, hm.shape)

        dx = 0.0
        dx_valid = False
        if 0 < best_x < 63:
            left = float(hm[best_y, best_x - 1])
            center = float(hm[best_y, best_x])
            right = float(hm[best_y, best_x + 1])
            denom = left - 2.0 * center + right
            if denom < -1e-12:
                dx = 0.5 * (left - right) / denom
                dx = max(-0.5, min(0.5, dx))
                dx_valid = True

        dy = 0.0
        dy_valid = False
        if 0 < best_y < 63:
            top = float(hm[best_y - 1, best_x])
            center = float(hm[best_y, best_x])
            bottom = float(hm[best_y + 1, best_x])
            denom = top - 2.0 * center + bottom
            if denom < -1e-12:
                dy = 0.5 * (top - bottom) / denom
                dy = max(-0.5, min(0.5, dy))
                dy_valid = True

        if dx_valid or dy_valid:
            x64 = float(best_x) + 0.5 + dx
            y64 = float(best_y) + 0.5 + dy
        else:
            x0 = max(0, best_x - 2)
            x1 = min(63, best_x + 2)
            y0 = max(0, best_y - 2)
            y1 = min(63, best_y + 2)
            window = hm[y0:y1 + 1, x0:x1 + 1].astype(np.float64)
            max_logit = float(np.max(window))
            weights = np.exp(window - max_logit)
            sum_w = float(np.sum(weights))
            if sum_w == 0.0 or not math.isfinite(sum_w):
                x64 = float(best_x) + 0.5
                y64 = float(best_y) + 0.5
            else:
                ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
                x64 = float(np.sum(weights * (xs + 0.5)) / sum_w)
                y64 = float(np.sum(weights * (ys + 0.5)) / sum_w)
        corners.append((x64 * 4.0, y64 * 4.0))
    return corners


def _choose_path(
    quad_corners256: list[tuple[float, float]],
    quad_from_mask256: list[tuple[float, float]],
    quad_from_mask_used_fallback: bool,
    mask_logits: np.ndarray
) -> tuple[list[tuple[float, float]], str, float, float]:
    """
    Choose between corner-based and mask-based quad.
    EXACT port of DocQuadPostprocessor.choosePath.
    
    Returns:
        Tuple of (chosen_quad256, chosen_source, penalty_corners, penalty_mask)
    """
    # Compute geometry-only penalty for CORNERS (without mask disagreement)
    p_a_geom = _quad_penalty_geometry(quad_corners256)
    # Full penalty for CORNERS includes mask disagreement
    p_a = p_a_geom + _mask_disagreement_penalty_for_corners(quad_corners256, mask_logits)
    
    if quad_from_mask_used_fallback:
        return quad_corners256, "CORNERS", p_a, float('inf')
    
    p_b = _quad_penalty_geometry(quad_from_mask256)
    
    # Bidirectional hard penalty fallback:
    # 1. If CORNERS has severe geometric issues AND MASK is valid → prefer MASK
    # 2. If MASK has severe geometric issues → prefer CORNERS
    # This reduces FAIL rate by using the geometrically valid quad when available.
    if p_a_geom >= _HARD_PENALTY_THRESHOLD and p_b < _HARD_PENALTY_THRESHOLD:
        return quad_from_mask256, "MASK", p_a, p_b
    if p_b >= _HARD_PENALTY_THRESHOLD:
        return quad_corners256, "CORNERS", p_a, p_b
    
    if _PRODUCT_PATH_POLICY == "corners_only":
        return quad_corners256, "CORNERS", p_a, p_b
    if _PRODUCT_PATH_POLICY == "mask_if_valid":
        return quad_from_mask256, "MASK", p_a, p_b

    # Guardrail 1: Agreement check - if MASK and CORNERS disagree significantly,
    # prefer CORNERS (MASK prediction is unreliable due to distribution shift)
    max_corner_dist = _max_corner_distance(quad_corners256, quad_from_mask256)
    if max_corner_dist > _AGREEMENT_MAX_CORNER_DIST:
        return quad_corners256, "CORNERS", p_a, p_b
    
    # Guardrail 2: Score margin - MASK must be clearly better than CORNERS geometry
    # to overcome the inherent uncertainty in mask-based quad extraction
    if p_b < p_a_geom - _MASK_SCORE_MARGIN:
        return quad_from_mask256, "MASK", p_a, p_b
    
    # Default: prefer CORNERS (more reliable corner detection)
    return quad_corners256, "CORNERS", p_a, p_b


def _map_corners_256_to_original(corners256: list[tuple[float, float]], lb: Letterbox) -> list[tuple[float, float]]:
    """
    Map corners from 256-space to original image space.
    EXACT port of DocQuadPostprocessor.mapCorners256ToOriginal.
    """
    return [lb.inverse(x, y) for x, y in corners256]


@dataclass
class AppPostprocessResult:
    """Result of App-identical postprocessing."""
    corners_original: list[tuple[float, float]]
    chosen_source: str  # "CORNERS" or "MASK"
    penalty_corners: float
    penalty_mask: float
    quad_from_mask_used_fallback: bool


def _apply_product_postprocessing_app(
    corner_heatmaps: np.ndarray,
    mask_logits: np.ndarray,
    lb: Letterbox
) -> AppPostprocessResult:
    """
    Apply PRODUCT mode post-processing IDENTICAL to MakeACopy app.
    
    This is a port of DocQuadPostprocessor.postprocess with configurable PeakMode for experiments.
    
    Args:
        corner_heatmaps: Array of shape (1, 4, 64, 64)
        mask_logits: Array of shape (1, 1, 64, 64)
        lb: Letterbox transformation
    
    Returns:
        AppPostprocessResult with corners in original image space
    """
    # 1. Extract corners from heatmaps with configured refinement
    if _PRODUCT_PEAK_MODE == "raw_argmax":
        corners256 = _raw_legacy_argmax_corners_64_to_256(corner_heatmaps)
    elif _PRODUCT_PEAK_MODE == "argmax":
        corners256 = _argmax_corners_64_to_256(corner_heatmaps)
    elif _PRODUCT_PEAK_MODE == "refine_5x5_quadratic":
        corners256 = _refine_corners_64_to_256_5x5_quadratic(corner_heatmaps)
    else:
        corners256 = _refine_corners_64_to_256_3x3(corner_heatmaps)
    
    # 2. Compute quad from mask (PCA-based)
    quad_from_mask256, used_fallback = _quad_from_mask_256(mask_logits, corners256)
    
    # 3. Choose path (corners vs mask) based on penalties
    chosen_quad256, chosen_source, penalty_corners, penalty_mask = _choose_path(
        corners256, quad_from_mask256, used_fallback, mask_logits
    )
    
    # 4. Map to original image coordinates
    corners_original = _map_corners_256_to_original(chosen_quad256, lb)
    
    return AppPostprocessResult(
        corners_original=corners_original,
        chosen_source=chosen_source,
        penalty_corners=penalty_corners,
        penalty_mask=penalty_mask,
        quad_from_mask_used_fallback=used_fallback
    )


# Legacy simplified functions (kept for backwards compatibility in RAW mode)

def _canonicalize_quad_order(pts: list[tuple[float, float]]) -> list[tuple[float, float]]:
    """Legacy simplified canonicalization (used in RAW mode only)."""
    if len(pts) != 4:
        return pts
    try:
        return _canonicalize_quad_order_v1(pts)
    except Exception:
        return pts


def _clamp_to_bounds(pts: list[tuple[float, float]], w: int, h: int) -> list[tuple[float, float]]:
    """Clamp corner coordinates to image bounds."""
    return [
        (max(0.0, min(float(w - 1), x)), max(0.0, min(float(h - 1), y)))
        for x, y in pts
    ]


def _detect_failure_reasons(
    pts: list[tuple[float, float]], 
    w: int, 
    h: int,
    min_area_frac: float,
    fail_on_oob: bool,
    fail_on_geom: bool,
    fail_on_degenerate: bool,
) -> str | None:
    """
    Detect failure conditions without repairing.
    
    Used in RAW mode to report failures that would be detected.
    Returns the first failure reason found, or None if valid.
    """
    if fail_on_oob and not _bounds_ok(pts, w, h):
        return "oob"
    
    if fail_on_geom:
        if _is_self_intersecting_quad(pts):
            return "self_intersecting"
        if not _is_convex_quad(pts):
            return "non_convex"
    
    if fail_on_degenerate:
        area = _shoelace_area(pts)
        img_area = w * h
        if area < min_area_frac * img_area:
            return "degenerate_area"
        if _is_degenerate_quad(pts):
            return "degenerate"
    
    return None


# ============================================================================
# Data loading
# ============================================================================

def _load_json(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:
        raise ValueError(f"failed to read/parse json: {path} ({e})")


def _extract_corners_px(label: dict[str, Any]) -> list[tuple[float, float]]:
    corners = label.get("corners_px", None)
    if corners is None:
        raise ValueError("missing_field:corners_px")

    if not isinstance(corners, list) or len(corners) != 4:
        raise ValueError("corners_not_4")

    out: list[tuple[float, float]] = []
    for p in corners:
        if not isinstance(p, list) or len(p) != 2:
            raise ValueError("corner_not_pair")
        x, y = p[0], p[1]
        if not _is_number(x) or not _is_number(y):
            raise ValueError("corner_not_numeric")
        out.append((float(x), float(y)))
    return out


@dataclass
class TestSample:
    name: str
    image_path: Path
    width: int
    height: int
    corners_gt: list[tuple[float, float]]


def load_test_set(test_dir: Path, limit: int | None = None) -> list[TestSample]:
    """Load test set from directory with images/ and labels/ subdirs."""
    images_dir = test_dir / "images"
    labels_dir = test_dir / "labels"

    if not images_dir.is_dir():
        raise SystemExit(f"FATAL: images dir missing: {images_dir}")
    if not labels_dir.is_dir():
        raise SystemExit(f"FATAL: labels dir missing: {labels_dir}")

    label_paths = sorted(labels_dir.glob("*.json"), key=lambda p: p.name)
    samples: list[TestSample] = []

    for lp in label_paths:
        if limit is not None and len(samples) >= limit:
            break

        try:
            label = _load_json(lp)
        except Exception as e:
            print(f"WARNING: skipping {lp.name}: {e}", file=sys.stderr)
            continue

        img_field = label.get("image", None)
        if not isinstance(img_field, str) or not img_field:
            print(f"WARNING: skipping {lp.name}: missing image field", file=sys.stderr)
            continue

        img_path = images_dir / img_field
        if not img_path.exists():
            print(f"WARNING: skipping {lp.name}: image not found: {img_path}", file=sys.stderr)
            continue

        try:
            corners_gt = _extract_corners_px(label)
        except ValueError as e:
            print(f"WARNING: skipping {lp.name}: {e}", file=sys.stderr)
            continue

        width = label.get("width", None)
        height = label.get("height", None)
        if not isinstance(width, int) or not isinstance(height, int):
            # Try to get from image
            try:
                with Image.open(img_path) as img:
                    width, height = img.size
            except Exception:
                print(f"WARNING: skipping {lp.name}: cannot determine image size", file=sys.stderr)
                continue

        samples.append(TestSample(
            name=img_field,
            image_path=img_path,
            width=width,
            height=height,
            corners_gt=corners_gt,
        ))

    return samples


# ============================================================================
# Model inference
# ============================================================================

@dataclass
class RawModelOutputs:
    """Raw model outputs for App-identical postprocessing."""
    corner_heatmaps: np.ndarray  # Shape (1, 4, 64, 64)
    mask_logits: np.ndarray  # Shape (1, 1, 64, 64)
    letterbox: Letterbox


@dataclass
class ModelWrapper:
    name: str
    session: Any  # ort.InferenceSession
    input_name: str
    input_shape: tuple[int, ...]  # (N, C, H, W)

    @classmethod
    def load(cls, name: str, model_path: Path, device: str = "cpu") -> "ModelWrapper":
        if ort is None:
            raise SystemExit("FATAL: onnxruntime not installed. Install with: pip install onnxruntime")

        providers = ["CPUExecutionProvider"]
        if device == "cuda":
            providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]

        is_ort = model_path.suffix.lower() == ".ort"
        sess_options = None
        if is_ort:
            sess_options = ort.SessionOptions()
            try:
                sess_options.add_session_config_entry("session.load_model_format", "ORT")
            except Exception as exc:
                raise SystemExit(
                    "FATAL: ORT format not supported by this onnxruntime build. "
                    "Install a build with ORT format support or use .onnx."
                ) from exc

        session = ort.InferenceSession(str(model_path), providers=providers, sess_options=sess_options)
        input_info = session.get_inputs()[0]
        input_name = input_info.name
        input_shape = tuple(input_info.shape)

        return cls(name=name, session=session, input_name=input_name, input_shape=input_shape)

    def _render_letterbox(self, image: Image.Image, lb: Letterbox) -> Image.Image:
        """
        Render image with letterbox transformation.
        EXACT port of DocQuadDetector.renderLetterbox256 from MakeACopy app.
        """
        # Create black canvas
        out = Image.new("RGB", (lb.dst_w, lb.dst_h), (0, 0, 0))
        
        # Calculate destination rectangle
        new_w = int(lb.src_w * lb.scale)
        new_h = int(lb.src_h * lb.scale)
        
        # Resize source image
        img_resized = image.convert("RGB").resize((new_w, new_h), Image.BILINEAR)
        
        # Paste at offset
        out.paste(img_resized, (int(lb.offset_x), int(lb.offset_y)))
        
        return out

    def predict_raw_outputs(self, image: Image.Image) -> RawModelOutputs:
        """
        Run inference and return raw model outputs for App-identical postprocessing.
        
        Uses Letterbox preprocessing IDENTICAL to MakeACopy app (DocQuadDetector).
        Returns corner_heatmaps and mask_logits for use with _apply_product_postprocessing_app.
        """
        orig_w, orig_h = image.size
        
        # Get model input size (assume NCHW format, typically 256x256)
        _, _, model_h, model_w = self.input_shape
        
        # Create letterbox transformation (IDENTICAL to app)
        lb = Letterbox.create(orig_w, orig_h, model_w, model_h)
        
        # Render letterboxed image
        img_letterbox = self._render_letterbox(image, lb)
        
        # Convert to numpy array and normalize (RGB, 0..1, NCHW)
        # EXACT port of DocQuadDetector.bitmapToNchwFloat01
        arr = np.array(img_letterbox, dtype=np.float32) / 255.0
        
        # HWC -> CHW -> NCHW
        arr = arr.transpose(2, 0, 1)
        arr = np.expand_dims(arr, axis=0)
        
        # Run inference
        outputs = self.session.run(None, {self.input_name: arr})
        
        # DocQuadNet-256 outputs:
        # - outputs[0]: corner_heatmaps [1, 4, 64, 64]
        # - outputs[1]: mask_logits [1, 1, 64, 64]
        corner_heatmaps = outputs[0]
        mask_logits = outputs[1] if len(outputs) > 1 else None
        
        # Validate shapes
        if corner_heatmaps.shape != (1, 4, 64, 64):
            raise ValueError(f"Unexpected corner_heatmaps shape: {corner_heatmaps.shape}, expected (1, 4, 64, 64)")
        
        if mask_logits is None:
            # Fallback: create empty mask if model doesn't output it
            mask_logits = np.zeros((1, 1, 64, 64), dtype=np.float32)
        elif mask_logits.shape != (1, 1, 64, 64):
            raise ValueError(f"Unexpected mask_logits shape: {mask_logits.shape}, expected (1, 1, 64, 64)")
        
        return RawModelOutputs(
            corner_heatmaps=corner_heatmaps,
            mask_logits=mask_logits,
            letterbox=lb
        )

    def predict_product(self, image: Image.Image) -> tuple[list[tuple[float, float]], AppPostprocessResult]:
        """
        Run inference with PRODUCT mode postprocessing IDENTICAL to MakeACopy app.
        
        Returns:
            Tuple of (corners_original, app_result)
        """
        raw = self.predict_raw_outputs(image)
        result = _apply_product_postprocessing_app(
            raw.corner_heatmaps,
            raw.mask_logits,
            raw.letterbox
        )
        return result.corners_original, result

    def predict(self, image: Image.Image) -> list[tuple[float, float]]:
        """
        Run inference and return predicted corners in pixel coordinates.
        
        RAW mode: Uses simple argmax on corner heatmaps without App postprocessing.
        For PRODUCT mode, use predict_product() instead.
        """
        orig_w, orig_h = image.size

        # Get model input size (assume NCHW format)
        _, _, model_h, model_w = self.input_shape

        # Use letterbox preprocessing (same as app)
        lb = Letterbox.create(orig_w, orig_h, model_w, model_h)
        img_letterbox = self._render_letterbox(image, lb)

        # Convert to numpy array and normalize
        arr = np.array(img_letterbox, dtype=np.float32) / 255.0

        # HWC -> CHW -> NCHW
        arr = arr.transpose(2, 0, 1)
        arr = np.expand_dims(arr, axis=0)

        # Run inference
        outputs = self.session.run(None, {self.input_name: arr})

        # Parse output - assume output is heatmaps or direct corner predictions
        # This depends on the model architecture. We'll handle common cases.
        output = outputs[0]

        corners_norm = self._parse_output(output, model_w, model_h)

        # Scale back to original image coordinates using letterbox inverse
        corners_px = [lb.inverse(x * model_w, y * model_h) for x, y in corners_norm]

        return corners_px

    def _parse_output(self, output: np.ndarray, model_w: int, model_h: int) -> list[tuple[float, float]]:
        """Parse model output to normalized corner coordinates [0,1]."""
        # Case 1: Direct corner regression (1, 8) or (8,)
        if output.shape[-1] == 8 or (len(output.shape) == 2 and output.shape[1] == 8):
            flat = output.flatten()[:8]
            corners = [(flat[i], flat[i + 1]) for i in range(0, 8, 2)]
            return corners

        # Case 2: Heatmap output (1, 4, H, W) - 4 heatmaps for 4 corners
        if len(output.shape) == 4 and output.shape[1] == 4:
            corners = []
            for i in range(4):
                heatmap = output[0, i]
                # Find argmax
                idx = np.argmax(heatmap)
                y_idx, x_idx = np.unravel_index(idx, heatmap.shape)
                # Normalize to [0, 1]
                x_norm = float(x_idx) / float(heatmap.shape[1])
                y_norm = float(y_idx) / float(heatmap.shape[0])
                corners.append((x_norm, y_norm))
            return corners

        # Case 3: Single heatmap with 4 peaks (1, 1, H, W) or (1, H, W)
        if len(output.shape) >= 3:
            if len(output.shape) == 4:
                heatmap = output[0, 0]
            else:
                heatmap = output[0]

            # Find top 4 peaks
            corners = self._find_top_k_peaks(heatmap, k=4)
            # Normalize
            h, w = heatmap.shape
            corners = [(x / w, y / h) for x, y in corners]
            # Sort by angle from centroid to get TL, TR, BR, BL order
            corners = self._order_corners_clockwise(corners)
            return corners

        raise ValueError(f"Unsupported output shape: {output.shape}")

    def _find_top_k_peaks(self, heatmap: np.ndarray, k: int = 4) -> list[tuple[float, float]]:
        """Find top k peaks in heatmap."""
        h, w = heatmap.shape
        flat = heatmap.flatten()
        indices = np.argpartition(flat, -k)[-k:]
        indices = indices[np.argsort(flat[indices])[::-1]]

        peaks = []
        for idx in indices:
            y, x = np.unravel_index(idx, (h, w))
            peaks.append((float(x), float(y)))
        return peaks

    def _order_corners_clockwise(self, corners: list[tuple[float, float]]) -> list[tuple[float, float]]:
        """Order corners as TL, TR, BR, BL (clockwise from top-left)."""
        # Compute centroid
        cx = sum(p[0] for p in corners) / 4
        cy = sum(p[1] for p in corners) / 4

        # Sort by angle
        def angle(p: tuple[float, float]) -> float:
            return math.atan2(p[1] - cy, p[0] - cx)

        sorted_corners = sorted(corners, key=angle)

        # Find top-left (smallest x+y sum among top two)
        # First, separate into top and bottom
        top_two = sorted([c for c in sorted_corners if c[1] < cy], key=lambda p: p[0])
        bottom_two = sorted([c for c in sorted_corners if c[1] >= cy], key=lambda p: p[0])

        if len(top_two) < 2:
            top_two = sorted(sorted_corners[:2], key=lambda p: p[0])
            bottom_two = sorted(sorted_corners[2:], key=lambda p: p[0])

        if len(top_two) >= 2 and len(bottom_two) >= 2:
            tl, tr = top_two[0], top_two[1]
            bl, br = bottom_two[0], bottom_two[1]
            return [tl, tr, br, bl]

        # Fallback: just return sorted by angle starting from top-left-ish
        return sorted_corners


# ============================================================================
# Evaluation logic
# ============================================================================

@dataclass
class SampleResult:
    name: str
    failed: bool
    fail_reason: str | None
    iou: float | None
    corner_mae_px: float | None
    corner_mae_rel: float | None  # relative to image diagonal (0..1, e.g. 0.02 = 2%)
    corners_pred: list[tuple[float, float]] | None
    repairs_applied: list[str] | None = None  # PRODUCT mode only: list of repairs applied


@dataclass
class ModelMetrics:
    num_samples: int
    fail_count: int
    fail_rate: float
    fail_by_reason: dict[str, int]
    iou_mean: float
    iou_median: float
    iou_pct_ge_thresh: float
    corner_mae_mean: float
    corner_mae_median: float
    corner_mae_pct_le_max: float
    corner_mae_rel_mean: float  # relative to image diagonal (0..1)
    corner_mae_rel_median: float


def evaluate_model(
    model: ModelWrapper,
    samples: list[TestSample],
    iou_thresh: float,
    max_corner_px: float,
    min_area_frac: float,
    fail_on_oob: bool,
    fail_on_geom: bool,
    fail_on_degenerate: bool,
    eval_mode: EvalMode = EvalMode.RAW,
) -> tuple[list[SampleResult], ModelMetrics]:
    """
    Evaluate a single model on all samples.
    
    Args:
        eval_mode: Evaluation mode.
            - RAW: Measure raw model quality. Failures are detected but NOT repaired.
            - PRODUCT: Apply full post-processing pipeline IDENTICAL to MakeACopy app.
    """
    results: list[SampleResult] = []

    for sample in samples:
        try:
            with Image.open(sample.image_path) as img:
                if eval_mode == EvalMode.PRODUCT:
                    # PRODUCT mode: Use App-identical postprocessing pipeline
                    corners_pred, app_result = model.predict_product(img)
                else:
                    # RAW mode: Simple argmax without App postprocessing
                    corners_pred = model.predict(img)
        except Exception as e:
            results.append(SampleResult(
                name=sample.name,
                failed=True,
                fail_reason="inference_error",
                iou=None,
                corner_mae_px=None,
                corner_mae_rel=None,
                corners_pred=None,
                repairs_applied=None,
            ))
            continue

        repairs_applied: list[str] | None = None
        
        if eval_mode == EvalMode.PRODUCT:
            # PRODUCT mode: Record which path was chosen (CORNERS vs MASK)
            repairs_applied = [f"chosen_source:{app_result.chosen_source}"]
            if app_result.quad_from_mask_used_fallback:
                repairs_applied.append("mask_fallback_used")
        
        # Check failure conditions (after any post-processing)
        fail_reason: str | None = None

        # PRODUCT mode uses App-compatible bounds tolerance (±25% of image dimensions)
        # This matches DocQuadDetector.isValidQuad() which allows coordinates in
        # range [-w*0.25, w*1.25] x [-h*0.25, h*1.25]
        oob_tolerance = 0.25 if eval_mode == EvalMode.PRODUCT else 0.0
        
        if fail_on_oob and not _bounds_ok(corners_pred, sample.width, sample.height, oob_tolerance):
            fail_reason = "oob"
        elif fail_on_geom:
            if _is_self_intersecting_quad(corners_pred):
                fail_reason = "self_intersecting"
            elif not _is_convex_quad(corners_pred):
                fail_reason = "non_convex"
        
        if fail_reason is None and fail_on_degenerate:
            area = _shoelace_area(corners_pred)
            img_area = sample.width * sample.height
            if area < min_area_frac * img_area:
                fail_reason = "degenerate_area"
            elif _is_degenerate_quad(corners_pred):
                fail_reason = "degenerate"

        if fail_reason is not None:
            results.append(SampleResult(
                name=sample.name,
                failed=True,
                fail_reason=fail_reason,
                iou=None,
                corner_mae_px=None,
                corner_mae_rel=None,
                corners_pred=corners_pred,
                repairs_applied=repairs_applied,
            ))
            continue

        # Compute metrics
        iou = _compute_iou(corners_pred, sample.corners_gt)
        mae_px, mae_rel = _corner_mae_px_and_rel(corners_pred, sample.corners_gt, sample.width, sample.height)

        results.append(SampleResult(
            name=sample.name,
            failed=False,
            fail_reason=None,
            iou=iou,
            corner_mae_px=mae_px,
            corner_mae_rel=mae_rel,
            corners_pred=corners_pred,
            repairs_applied=repairs_applied,
        ))

    # Aggregate metrics
    fail_count = sum(1 for r in results if r.failed)
    fail_by_reason: dict[str, int] = {}
    for r in results:
        if r.failed and r.fail_reason:
            fail_by_reason[r.fail_reason] = fail_by_reason.get(r.fail_reason, 0) + 1

    valid_results = [r for r in results if not r.failed]
    ious = [r.iou for r in valid_results if r.iou is not None]
    maes = [r.corner_mae_px for r in valid_results if r.corner_mae_px is not None]
    maes_rel = [r.corner_mae_rel for r in valid_results if r.corner_mae_rel is not None]

    def safe_mean(vals: list[float]) -> float:
        return sum(vals) / len(vals) if vals else 0.0

    def safe_median(vals: list[float]) -> float:
        if not vals:
            return 0.0
        s = sorted(vals)
        n = len(s)
        if n % 2 == 1:
            return s[n // 2]
        return (s[n // 2 - 1] + s[n // 2]) / 2.0

    num_samples = len(results)
    metrics = ModelMetrics(
        num_samples=num_samples,
        fail_count=fail_count,
        fail_rate=fail_count / num_samples if num_samples > 0 else 0.0,
        fail_by_reason=fail_by_reason,
        iou_mean=safe_mean(ious),
        iou_median=safe_median(ious),
        iou_pct_ge_thresh=sum(1 for v in ious if v >= iou_thresh) / len(ious) if ious else 0.0,
        corner_mae_mean=safe_mean(maes),
        corner_mae_median=safe_median(maes),
        corner_mae_pct_le_max=sum(1 for v in maes if v <= max_corner_px) / len(maes) if maes else 0.0,
        corner_mae_rel_mean=safe_mean(maes_rel),
        corner_mae_rel_median=safe_median(maes_rel),
    )

    return results, metrics


def compare_models(
    results_a: list[SampleResult],
    results_b: list[SampleResult],
    name_a: str,
    name_b: str,
) -> dict[str, Any]:
    """Compare two models on a per-sample basis."""
    wins_a = 0
    wins_b = 0
    ties = 0
    worse_a: list[str] = []
    worse_b: list[str] = []

    results_b_by_name = {r.name: r for r in results_b}

    for ra in results_a:
        rb = results_b_by_name.get(ra.name)
        if rb is None:
            continue

        # Compare: 1) fewer FAILs, 2) higher IoU, 3) lower MAE
        score_a = 0
        score_b = 0

        # FAIL comparison
        if ra.failed and not rb.failed:
            score_b += 2
        elif not ra.failed and rb.failed:
            score_a += 2
        elif not ra.failed and not rb.failed:
            # IoU comparison
            if ra.iou is not None and rb.iou is not None:
                if ra.iou > rb.iou + 0.01:
                    score_a += 1
                elif rb.iou > ra.iou + 0.01:
                    score_b += 1

            # MAE comparison
            if ra.corner_mae_px is not None and rb.corner_mae_px is not None:
                if ra.corner_mae_px < rb.corner_mae_px - 0.5:
                    score_a += 1
                elif rb.corner_mae_px < ra.corner_mae_px - 0.5:
                    score_b += 1

        if score_a > score_b:
            wins_a += 1
            worse_b.append(ra.name)
        elif score_b > score_a:
            wins_b += 1
            worse_a.append(ra.name)
        else:
            ties += 1

    return {
        "wins": {name_a: wins_a, name_b: wins_b},
        "ties": ties,
        "worse_cases": {name_a: worse_a, name_b: worse_b},
    }


# ============================================================================
# Report generation
# ============================================================================

def generate_report(
    test_dir: str,
    num_samples: int,
    device: str,
    iou_thresh: float,
    max_corner_px: float,
    eval_mode: EvalMode,
    model_results: dict[str, tuple[list[SampleResult], ModelMetrics]],
) -> dict[str, Any]:
    """
    Generate the final JSON report.
    
    The report explicitly documents the evaluation mode to ensure transparency
    about whether metrics reflect raw model quality or product-level behavior.
    """
    report: dict[str, Any] = {
        "meta": {
            "test_dir": test_dir,
            "num_samples": num_samples,
            "device": device,
            "iou_thresh": iou_thresh,
            "max_corner_px": max_corner_px,
            "eval_mode": str(eval_mode),
            "eval_mode_description": (
                "RAW: Measures raw model output quality. Failures detected but NOT repaired."
                if eval_mode == EvalMode.RAW else
                "PRODUCT: Measures product-level behavior with full post-processing pipeline (repairs, clamping)."
            ),
        },
        "models": {},
        "pairwise": {},
    }

    # Per-model metrics
    for name, (results, metrics) in model_results.items():
        # Build per-sample stats
        samples_list = []
        for r in results:
            sample_entry: dict[str, Any] = {
                "image": r.name,
                "fail": r.failed,
            }
            if r.failed and r.fail_reason:
                sample_entry["fail_reason"] = r.fail_reason
            if r.iou is not None:
                sample_entry["iou"] = round(r.iou, 6)
            if r.corner_mae_px is not None:
                sample_entry["corner_mae_px"] = round(r.corner_mae_px, 6)
            # Include repairs_applied for PRODUCT mode transparency
            if r.repairs_applied is not None and len(r.repairs_applied) > 0:
                sample_entry["repairs_applied"] = r.repairs_applied
            samples_list.append(sample_entry)

        report["models"][name] = {
            "num_samples": metrics.num_samples,
            "fail": {
                "count": metrics.fail_count,
                "rate": round(metrics.fail_rate, 6),
                "by_reason": metrics.fail_by_reason,
            },
            "iou": {
                "mean": round(metrics.iou_mean, 6),
                "median": round(metrics.iou_median, 6),
                "pct_ge_thresh": round(metrics.iou_pct_ge_thresh, 6),
            },
            "corner_mae_px": {
                "mean": round(metrics.corner_mae_mean, 6),
                "median": round(metrics.corner_mae_median, 6),
                "pct_le_max": round(metrics.corner_mae_pct_le_max, 6),
            },
            "corner_mae_rel": {
                "mean": round(metrics.corner_mae_rel_mean, 6),
                "median": round(metrics.corner_mae_rel_median, 6),
            },
            "samples": samples_list,
        }

    # Pairwise comparisons
    model_names = list(model_results.keys())
    for i, name_a in enumerate(model_names):
        for name_b in model_names[i + 1:]:
            results_a, _ = model_results[name_a]
            results_b, _ = model_results[name_b]
            comparison = compare_models(results_a, results_b, name_a, name_b)
            report["pairwise"][f"{name_a}_vs_{name_b}"] = comparison

    return report


# ============================================================================
# CLI
# ============================================================================

def parse_model_arg(arg: str) -> tuple[str, Path]:
    """Parse --model name=path argument."""
    if "=" not in arg:
        raise argparse.ArgumentTypeError(f"Invalid model format: {arg}. Expected: name=path.onnx or name=path.ort")
    name, path_str = arg.split("=", 1)
    path = Path(path_str)
    if not path.exists():
        raise argparse.ArgumentTypeError(f"Model file not found: {path}")
    if path.suffix.lower() not in {".onnx", ".ort"}:
        raise argparse.ArgumentTypeError(
            f"Unsupported model extension: {path.suffix}. Expected .onnx or .ort"
        )
    return name, path


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Evaluate DocQuadNet models on a real-world test set.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Evaluation Modes:
  --mode raw      (default) Measure raw model quality.
                  Failures are detected but NOT repaired.
                  Use for: comparing training runs, checkpoint selection.

  --mode product  Measure product-level behavior.
                  Applies full post-processing pipeline (repairs, clamping).
                  Use for: release decisions, UX impact assessment.

Examples:
  # RAW mode: Compare model quality (default)
  python evaluate_docquad_models.py \
    --test_dir training/data/docquad_real_eval \
    --model uvdoc=training/runs/docquad_uvdoc_pretrain/checkpoints/best.onnx \
    --model mix=training/runs/docquad_mix_finetune/checkpoints/best.onnx \
    --out reports/eval_raw.json

  # PRODUCT mode: Measure real-world UX impact
  python evaluate_docquad_models.py \
    --mode product \
    --test_dir training/data/docquad_real_eval \
    --model candidate=path/to/model.ort \
    --out reports/eval_product.json

  # Compare both modes for the same model
  python evaluate_docquad_models.py --mode raw \\
    --test_dir data --model m=model.onnx --out eval_raw.json
  python evaluate_docquad_models.py --mode product \\
    --test_dir data --model m=model.onnx --out eval_product.json
""",
    )

    # Required arguments
    parser.add_argument(
        "--test_dir",
        type=Path,
        required=True,
        help="Directory containing images/ and labels/ subdirectories.",
    )
    parser.add_argument(
        "--model",
        type=parse_model_arg,
        action="append",
        required=True,
        dest="models",
        metavar="NAME=PATH",
        help="Named ONNX/ORT model to evaluate (repeatable). Format: name=path.onnx or name=path.ort",
    )
    parser.add_argument(
        "--out",
        type=Path,
        required=True,
        help="Output path for the JSON report.",
    )

    # Evaluation mode
    parser.add_argument(
        "--mode",
        type=str,
        choices=["raw", "product"],
        default="raw",
        help=(
            "Evaluation mode (default: raw). "
            "'raw': Measure raw model quality - failures detected but NOT repaired. "
            "'product': Measure product-level behavior with full post-processing pipeline."
        ),
    )

    # Execution / runtime
    parser.add_argument(
        "--device",
        choices=["cpu", "cuda"],
        default="cpu",
        help="ONNX Runtime execution provider (default: cpu).",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Evaluate only the first N samples (default: all).",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=0,
        help="Random seed for reproducibility (default: 0).",
    )

    # Metrics / thresholds
    parser.add_argument(
        "--iou_thresh",
        type=float,
        default=0.90,
        help="IoU threshold for 'match' classification (default: 0.90).",
    )
    parser.add_argument(
        "--max_corner_px",
        type=float,
        default=8.0,
        help="Maximum mean corner distance (px) to count as 'good' (default: 8.0).",
    )
    parser.add_argument(
        "--min_area_frac",
        type=float,
        default=0.05,
        help="Minimum quad area fraction relative to image area (default: 0.05).",
    )
    parser.add_argument(
        "--product-agreement-max-corner-dist",
        type=float,
        default=32.0,
        help=(
            "Experimental PRODUCT-mode threshold: maximum corner distance in 256-space before "
            "MASK is considered unreliable (default: 32.0, matches Android production)."
        ),
    )
    parser.add_argument(
        "--product-mask-score-margin",
        type=float,
        default=50.0,
        help=(
            "Experimental PRODUCT-mode threshold: MASK must beat CORNERS geometry penalty by "
            "this margin (default: 50.0, matches Android production)."
        ),
    )
    parser.add_argument(
        "--product-path-policy",
        choices=["production", "corners_only", "mask_if_valid"],
        default="production",
        help=(
            "Experimental PRODUCT-mode path policy. 'production' matches Android; "
            "'corners_only' never chooses MASK except hard invalid fallbacks are bypassed; "
            "'mask_if_valid' chooses MASK whenever its geometry is valid."
        ),
    )
    parser.add_argument(
        "--product-peak-mode",
        choices=["raw_argmax", "argmax", "refine_3x3", "refine_5x5_quadratic"],
        default="refine_3x3",
        help=(
            "Experimental PRODUCT-mode corner peak extraction. Default keeps the historical Python "
            "evaluator behavior; 'refine_5x5_quadratic' matches current Android DocQuadDetector."
        ),
    )

    # Failure conditions
    parser.add_argument(
        "--no-fail-on-oob",
        action="store_false",
        dest="fail_on_oob",
        help="Do not count out-of-bounds quads as FAIL.",
    )
    parser.add_argument(
        "--no-fail-on-geom",
        action="store_false",
        dest="fail_on_geom",
        help="Do not count self-intersecting or non-convex quads as FAIL.",
    )
    parser.add_argument(
        "--no-fail-on-degenerate",
        action="store_false",
        dest="fail_on_degenerate",
        help="Do not count degenerate quads as FAIL.",
    )

    parser.set_defaults(fail_on_oob=True, fail_on_geom=True, fail_on_degenerate=True)

    args = parser.parse_args()

    global _AGREEMENT_MAX_CORNER_DIST, _MASK_SCORE_MARGIN, _PRODUCT_PATH_POLICY, _PRODUCT_PEAK_MODE
    _AGREEMENT_MAX_CORNER_DIST = args.product_agreement_max_corner_dist
    _MASK_SCORE_MARGIN = args.product_mask_score_margin
    _PRODUCT_PATH_POLICY = args.product_path_policy
    _PRODUCT_PEAK_MODE = args.product_peak_mode

    # Parse evaluation mode
    eval_mode = EvalMode.RAW if args.mode == "raw" else EvalMode.PRODUCT

    # Set seed
    import random
    random.seed(args.seed)
    np.random.seed(args.seed)

    # Validate test directory
    if not args.test_dir.is_dir():
        raise SystemExit(f"FATAL: test_dir does not exist: {args.test_dir}")

    # Load test set
    print(f"Loading test set from: {args.test_dir}")
    print(f"Evaluation mode: {eval_mode} - {('RAW: Measuring raw model quality (no repairs)' if eval_mode == EvalMode.RAW else 'PRODUCT: Measuring product-level behavior (with post-processing)')}")
    samples = load_test_set(args.test_dir, limit=args.limit)
    print(f"Loaded {len(samples)} samples")

    if not samples:
        raise SystemExit("FATAL: No valid samples found in test set.")

    # Load models
    models: dict[str, ModelWrapper] = {}
    for name, path in args.models:
        print(f"Loading model '{name}' from: {path}")
        models[name] = ModelWrapper.load(name, path, device=args.device)

    # Evaluate each model
    model_results: dict[str, tuple[list[SampleResult], ModelMetrics]] = {}
    for name, model in models.items():
        print(f"Evaluating model '{name}'...")
        results, metrics = evaluate_model(
            model=model,
            samples=samples,
            iou_thresh=args.iou_thresh,
            max_corner_px=args.max_corner_px,
            min_area_frac=args.min_area_frac,
            fail_on_oob=args.fail_on_oob,
            fail_on_geom=args.fail_on_geom,
            fail_on_degenerate=args.fail_on_degenerate,
            eval_mode=eval_mode,
        )
        model_results[name] = (results, metrics)
        print(f"  FAIL rate: {metrics.fail_rate:.2%} ({metrics.fail_count}/{metrics.num_samples})")
        print(f"  IoU mean: {metrics.iou_mean:.4f}, median: {metrics.iou_median:.4f}")
        print(f"  Corner MAE mean: {metrics.corner_mae_mean:.2f}px, median: {metrics.corner_mae_median:.2f}px")
        print(f"  Corner MAE rel mean: {metrics.corner_mae_rel_mean:.4f} ({metrics.corner_mae_rel_mean*100:.2f}%), median: {metrics.corner_mae_rel_median:.4f} ({metrics.corner_mae_rel_median*100:.2f}%)")
        
        # In PRODUCT mode, show repair statistics
        if eval_mode == EvalMode.PRODUCT:
            repair_counts: dict[str, int] = {}
            for r in results:
                if r.repairs_applied:
                    for repair in r.repairs_applied:
                        repair_counts[repair] = repair_counts.get(repair, 0) + 1
            if repair_counts:
                print(f"  Repairs applied: {repair_counts}")

    # Generate report
    report = generate_report(
        test_dir=str(args.test_dir),
        num_samples=len(samples),
        device=args.device,
        iou_thresh=args.iou_thresh,
        max_corner_px=args.max_corner_px,
        eval_mode=eval_mode,
        model_results=model_results,
    )

    # Write report
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\nReport written to: {args.out}")


if __name__ == "__main__":
    main()

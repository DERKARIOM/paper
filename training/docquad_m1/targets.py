from __future__ import annotations

import numpy as np

OUT_H = 64
OUT_W = 64
OUT_SCALE = 4.0  # 256 -> 64 (Downsample ×4)


def _clamp_corners_256(corners_256: np.ndarray) -> np.ndarray:
    corners = np.asarray(corners_256, dtype=np.float64)
    if corners.shape != (4, 2):
        raise ValueError("corners_256 must have shape (4, 2)")
    # Spec: valid range [0, 255] inclusive → internal clamp
    return np.clip(corners, 0.0, 255.0)


def generate_corner_heatmaps_64(corners_256: np.ndarray, sigma: float) -> np.ndarray:
    """Generates corner heatmaps with shape (4, 64, 64) in TL,TR,BR,BL order.

    - `corners_256`: float32/float64, shape (4,2) in 256-space, clamped to [0,255]
    - `sigma`: 2..4 in 64×64 space
    """
    if not (sigma > 0.0):
        raise ValueError("sigma must be > 0")

    corners = _clamp_corners_256(corners_256)
    centers = corners / OUT_SCALE  # float, range [0, 63.75]

    # Consistent with mask rasterization: heatmap is evaluated at pixel centers.
    # Pixel (i,j) has its center at (i+0.5, j+0.5).
    xs = np.arange(OUT_W, dtype=np.float64) + 0.5
    ys = np.arange(OUT_H, dtype=np.float64) + 0.5
    X, Y = np.meshgrid(xs, ys)  # (H,W)

    inv2s2 = 1.0 / (2.0 * float(sigma) * float(sigma))
    out = np.empty((4, OUT_H, OUT_W), dtype=np.float32)
    for c in range(4):
        cx, cy = float(centers[c, 0]), float(centers[c, 1])
        dx = X - cx
        dy = Y - cy
        out[c] = np.exp(-(dx * dx + dy * dy) * inv2s2).astype(np.float32)
    return out


def generate_mask_target_64(corners_256: np.ndarray) -> np.ndarray:
    """Generates a binary mask target (64×64) from the quad (filled polygon).

    Specification (FIX):
    - Pixel center convention: Pixel (i,j) is inside if (i+0.5, j+0.5) lies within the polygon
    - Edge counts as inside
    - Output strictly 0/1 (no soft mask)
    """
    corners = _clamp_corners_256(corners_256)
    poly = (corners / OUT_SCALE).astype(np.float64)  # (4,2) in 64-space

    mask = np.zeros((OUT_H, OUT_W), dtype=np.uint8)
    for j in range(OUT_H):
        py = float(j) + 0.5
        for i in range(OUT_W):
            px = float(i) + 0.5
            if _point_in_polygon_inclusive(px, py, poly):
                mask[j, i] = 1
    return mask


def _point_in_polygon_inclusive(px: float, py: float, poly: np.ndarray) -> bool:
    # 1) Boundary counts as inside
    n = int(poly.shape[0])
    for i in range(n):
        a = poly[(i - 1) % n]
        b = poly[i]
        if _point_on_segment(px, py, float(a[0]), float(a[1]), float(b[0]), float(b[1])):
            return True

    # 2) Ray casting
    inside = False
    for i in range(n):
        xj, yj = poly[(i - 1) % n]
        xi, yi = poly[i]

        cond = (yi > py) != (yj > py)
        if cond:
            x_int = (xj - xi) * (py - yi) / (yj - yi) + xi
            if px < x_int:
                inside = not inside
    return inside


def _point_on_segment(
    px: float,
    py: float,
    ax: float,
    ay: float,
    bx: float,
    by: float,
) -> bool:
    # Deterministic segment check with small tolerance.
    eps = 1e-6
    abx = bx - ax
    aby = by - ay
    apx = px - ax
    apy = py - ay

    cross = abx * apy - aby * apx
    if abs(cross) > eps:
        return False

    dot = apx * abx + apy * aby
    if dot < -eps:
        return False

    ab2 = abx * abx + aby * aby
    return dot <= ab2 + eps

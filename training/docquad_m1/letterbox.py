from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class LetterboxTransform:
    """Letterbox transform (preserving aspect ratio) with forward+inverse mapping.

    Specification-relevant:
    - Target size is typically 256×256
    - Stores `scale`, `offset_x`, `offset_y`
    """

    src_w: int
    src_h: int
    dst_w: int
    dst_h: int
    scale: float
    offset_x: float
    offset_y: float

    @staticmethod
    def create(src_w: int, src_h: int, dst_w: int = 256, dst_h: int = 256) -> "LetterboxTransform":
        if src_w <= 0 or src_h <= 0 or dst_w <= 0 or dst_h <= 0:
            raise ValueError("Invalid dimensions")

        sx = float(dst_w) / float(src_w)
        sy = float(dst_h) / float(src_h)
        scale = min(sx, sy)

        scaled_w = float(src_w) * scale
        scaled_h = float(src_h) * scale

        offset_x = (float(dst_w) - scaled_w) / 2.0
        offset_y = (float(dst_h) - scaled_h) / 2.0

        return LetterboxTransform(
            src_w=int(src_w),
            src_h=int(src_h),
            dst_w=int(dst_w),
            dst_h=int(dst_h),
            scale=float(scale),
            offset_x=float(offset_x),
            offset_y=float(offset_y),
        )

    def forward(self, pts_src: np.ndarray) -> np.ndarray:
        """Maps points from source space to destination space.

        `pts_src`: Array with shape (..., 2).
        """
        pts = np.asarray(pts_src, dtype=np.float64)
        if pts.shape[-1] != 2:
            raise ValueError("pts_src must have shape (..., 2)")
        out = np.empty_like(pts, dtype=np.float64)
        out[..., 0] = pts[..., 0] * self.scale + self.offset_x
        out[..., 1] = pts[..., 1] * self.scale + self.offset_y
        return out

    def inverse(self, pts_dst: np.ndarray) -> np.ndarray:
        """Maps points from destination space back to source space.

        `pts_dst`: Array with shape (..., 2).
        """
        pts = np.asarray(pts_dst, dtype=np.float64)
        if pts.shape[-1] != 2:
            raise ValueError("pts_dst must have shape (..., 2)")
        out = np.empty_like(pts, dtype=np.float64)
        out[..., 0] = (pts[..., 0] - self.offset_x) / self.scale
        out[..., 1] = (pts[..., 1] - self.offset_y) / self.scale
        return out

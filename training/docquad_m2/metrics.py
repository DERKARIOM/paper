from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import torch

from training.docquad_m2.synth_dataset import SynthSampleMeta


@dataclass(frozen=True)
class CornerError:
    mean_l2_px: float


def decode_corners_argmax_64_to_256(corner_heatmaps: torch.Tensor) -> torch.Tensor:
    """Dekodiert Ecken via argmax (ohne subpixel) nach 256-Space.

    - Heatmap ist 64×64 auf Pixelzentren (i+0.5, j+0.5).
    - Mapping nach 256: *4.
    """
    if corner_heatmaps.ndim != 4 or corner_heatmaps.shape[1] != 4 or corner_heatmaps.shape[-2:] != (64, 64):
        raise ValueError("corner_heatmaps must have shape [B,4,64,64]")

    b = int(corner_heatmaps.shape[0])
    flat = corner_heatmaps.reshape(b, 4, 64 * 64)
    idx = torch.argmax(flat, dim=-1)  # [B,4]
    py = (idx // 64).to(dtype=torch.float32)
    px = (idx % 64).to(dtype=torch.float32)
    x64 = px + 0.5
    y64 = py + 0.5
    x256 = x64 * 4.0
    y256 = y64 * 4.0
    return torch.stack([x256, y256], dim=-1)  # [B,4,2]


def corner_error_after_inverse_transform(
    pred_corner_heatmaps: torch.Tensor,
    meta: list[SynthSampleMeta],
) -> CornerError:
    pred_256 = decode_corners_argmax_64_to_256(pred_corner_heatmaps).detach().cpu().numpy()  # [B,4,2]

    errs = []
    for i, m in enumerate(meta):
        pred_src = m.letterbox.inverse(pred_256[i])
        gt_src = m.corners_src.astype(np.float64)
        d = pred_src.astype(np.float64) - gt_src
        l2 = np.sqrt((d * d).sum(axis=-1))  # (4,)
        errs.append(float(l2.mean()))
    return CornerError(mean_l2_px=float(np.mean(errs)))

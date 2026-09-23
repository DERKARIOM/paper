from __future__ import annotations

from dataclasses import dataclass
from typing import Dict

import numpy as np
import torch
from torch.utils.data import Dataset

from training.docquad_m1.letterbox import LetterboxTransform
from training.docquad_m1.targets import generate_corner_heatmaps_64, generate_mask_target_64


@dataclass(frozen=True)
class SynthSampleMeta:
    # For metrics (corner error after inverse transform)
    src_w: int
    src_h: int
    corners_src: np.ndarray  # (4,2)
    letterbox: LetterboxTransform


class SyntheticDocQuadDataset(Dataset):
    """Small deterministic synthetic dataset for overfit sanity.

    No file I/O, no external image libs. Inputs are RGB 0..1.
    Targets are created with the M1 generators.
    """

    def __init__(self, n: int = 8, seed: int = 0, sigma: float = 2.0):
        if n <= 0:
            raise ValueError("n must be > 0")
        self._n = int(n)
        self._seed = int(seed)
        self._sigma = float(sigma)

        rng = np.random.default_rng(self._seed)
        self._samples: list[Dict[str, object]] = []
        for _ in range(self._n):
            # Vary source slightly, letterbox path remains identical to specification.
            src_w = int(rng.integers(320, 1100))
            src_h = int(rng.integers(320, 1100))
            lb = LetterboxTransform.create(src_w, src_h, 256, 256)

            # Simple, valid quad: axis-aligned rect with margin (no self-intersection risk).
            mx = float(rng.uniform(0.10, 0.25))
            my = float(rng.uniform(0.10, 0.25))
            nx = float(rng.uniform(0.10, 0.25))
            ny = float(rng.uniform(0.10, 0.25))
            x0 = mx * (src_w - 1)
            y0 = my * (src_h - 1)
            x1 = (1.0 - nx) * (src_w - 1)
            y1 = (1.0 - ny) * (src_h - 1)
            corners_src = np.array(
                [[x0, y0], [x1, y0], [x1, y1], [x0, y1]],
                dtype=np.float32,
            )
            corners_256 = lb.forward(corners_src).astype(np.float32)

            # Targets (64×64)
            hm64 = generate_corner_heatmaps_64(corners_256, sigma=self._sigma)  # (4,64,64)
            mask64 = generate_mask_target_64(corners_256)  # (64,64) uint8

            # Simple RGB input: background dark, document bright (from mask64 upscaled).
            mask256 = np.repeat(np.repeat(mask64, 4, axis=0), 4, axis=1).astype(np.float32)
            bg = float(rng.uniform(0.05, 0.20))
            fg = float(rng.uniform(0.80, 0.95))
            img = (bg + (fg - bg) * mask256).astype(np.float32)
            # small deterministic noise
            noise = rng.normal(0.0, 0.01, size=(256, 256)).astype(np.float32)
            img = np.clip(img + noise, 0.0, 1.0)
            rgb = np.stack([img, img, img], axis=0)  # (3,256,256)

            meta = SynthSampleMeta(src_w=src_w, src_h=src_h, corners_src=corners_src, letterbox=lb)
            self._samples.append(
                {
                    "input": rgb,
                    "corner_heatmaps": hm64,
                    "mask": mask64,
                    "meta": meta,
                }
            )

    def __len__(self) -> int:
        return self._n

    def __getitem__(self, idx: int) -> Dict[str, object]:
        s = self._samples[int(idx)]
        x = torch.from_numpy(s["input"]).to(dtype=torch.float32)
        corner = torch.from_numpy(s["corner_heatmaps"]).to(dtype=torch.float32)
        mask = torch.from_numpy(s["mask"]).to(dtype=torch.float32).unsqueeze(0)  # [1,64,64]
        return {
            "input": x,
            "corner_heatmaps": corner,
            "mask": mask,
            "meta": s["meta"],
        }


def collate_with_meta(batch: list[Dict[str, object]]) -> Dict[str, object]:
    """Collate function for DataLoader.

    Torch default collate cannot stack arbitrary Python objects (here: `SynthSampleMeta`).
    For training/checks we stack tensors and return `meta` as a list.
    """
    inputs = torch.stack([b["input"] for b in batch], dim=0)
    corners = torch.stack([b["corner_heatmaps"] for b in batch], dim=0)
    masks = torch.stack([b["mask"] for b in batch], dim=0)
    meta = [b["meta"] for b in batch]
    return {
        "input": inputs,
        "corner_heatmaps": corners,
        "mask": masks,
        "meta": meta,
    }

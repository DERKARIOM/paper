from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np


# Golden stats must explicitly version their definitions so that tests don't
# "accidentally" break due to implicit changes (e.g. `logits > 0` instead of `sigmoid > 0.5`)
# or silently get wrong references.
MASK_AREA_DEFINITION_V1 = "mask_prob_gt_0.5"


@dataclass(frozen=True)
class GoldenSamplePaths:
    expected_json: Path


def golden_dir() -> Path:
    return Path(__file__).resolve().parent / "golden"


def golden_paths() -> GoldenSamplePaths:
    d = golden_dir()
    return GoldenSamplePaths(
        expected_json=d / "expected_stats_v1.json",
    )


def _make_input_sample_v1() -> np.ndarray:
    """Creates a deterministic synthetic input sample.

    - dtype float32
    - Shape [1,3,256,256]
    - Value range 0..1
    """

    x = np.zeros((1, 3, 256, 256), dtype=np.float32)
    # Simple, compression-friendly pattern: gradients + block.
    gx = (np.arange(256, dtype=np.float32) / 255.0)[None, None, None, :]
    gy = (np.arange(256, dtype=np.float32) / 255.0)[None, None, :, None]
    x[:, 0] = gx  # R: horizontal
    x[:, 1] = gy  # G: vertical
    x[:, 2] = 0.25  # B: constant
    x[:, 0, 64:192, 64:192] = 1.0
    return x


def get_golden_inputs_v1() -> dict[str, np.ndarray]:
    """Fixed synthetic inputs (no file I/O required).

    Returns: Dict with keys like `sample0`.
    """

    return {"sample0": _make_input_sample_v1()}


def read_expected_stats_v1() -> dict[str, Any]:
    p = golden_paths().expected_json
    return json.loads(p.read_text(encoding="utf-8"))


def read_expected_stats_trained() -> dict[str, Any]:
    """Reads expected stats for a *trained* snapshot.

    This is intentionally NOT versioned as a schema (the schema is `v1`); it's a
    reference snapshot for a specific trained checkpoint used by opt-in tests.
    """

    p = golden_dir() / "expected_stats_trained.json"
    return json.loads(p.read_text(encoding="utf-8"))


def compute_mask_area_v1(mask_logits: np.ndarray) -> int:
    """Computes `mask_area` for golden samples v1.

    Definition (FIX for v1):
    - `mask_prob = sigmoid(mask_logits)`
    - `mask_bin = mask_prob > 0.5`
    - `mask_area = sum(mask_bin)`

    Background: When `mask_logits == 0`, `sigmoid(0) == 0.5` and due to strict
    `>` this results in an empty mask (`mask_area == 0`).
    """

    x = np.asarray(mask_logits, dtype=np.float64)
    mask_prob = 1.0 / (1.0 + np.exp(-x))
    mask_bin = mask_prob > 0.5
    return int(mask_bin.sum())

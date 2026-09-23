#!/usr/bin/env python3
"""
Float16 quantization of the DocQuad ONNX model and comparison with the original.

Usage:
    cd makeacopy
    venv/bin/python training/scripts/quantize_fp16_compare.py \
        --model app/src/main/assets/docquad/docquadnet256_trained_opset17.onnx \
        --dataset training/data/my_data_trainable \
        --out /tmp/docquadnet256_fp16.onnx

The script:
1. Converts the FP32 model to FP16
2. Compares both models on the my_data_trainable dataset
3. Prints statistics on size, output deviation and corner detection
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path

import cv2
import numpy as np
import onnx
import onnxruntime as ort
from onnxruntime.transformers import float16


def convert_fp16(model_path: Path, out_path: Path) -> Path:
    """Converts an FP32 ONNX model to FP16."""
    model = onnx.load(str(model_path))
    model_fp16 = float16.convert_float_to_float16(model, keep_io_types=True)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    onnx.save(model_fp16, str(out_path))
    return out_path


def load_session(model_path: Path) -> ort.InferenceSession:
    return ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])


def preprocess_image(img_path: Path) -> np.ndarray:
    """Loads an image and prepares it for the model (256x256, RGB, /255)."""
    img = cv2.imread(str(img_path))
    if img is None:
        raise ValueError(f"Could not load image: {img_path}")
    img = cv2.resize(img, (256, 256))
    img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    img = img.astype(np.float32) / 255.0
    img = np.transpose(img, (2, 0, 1))[np.newaxis, ...]  # (1, 3, 256, 256)
    return img


def refine_corners_64_to_256(corner_heatmaps: np.ndarray) -> list[tuple[float, float]]:
    """
    Subpixel refinement via 3x3 weighted centroid around the argmax peak.
    Port of DocQuadPostprocessor.refineCorners64ToCorners256_3x3.
    """
    corners256 = []
    for c in range(4):
        hm = corner_heatmaps[0, c]  # (64, 64)
        # Argmax
        idx = int(np.argmax(hm))
        best_y, best_x = divmod(idx, 64)
        # 3x3 refinement
        x0, x1 = max(0, best_x - 1), min(63, best_x + 1)
        y0, y1 = max(0, best_y - 1), min(63, best_y + 1)
        wsum_x, wsum_y, wsum = 0.0, 0.0, 0.0
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                w = float(hm[y, x])
                if w > 0:
                    wsum_x += w * x
                    wsum_y += w * y
                    wsum += w
        if wsum > 0:
            cx = wsum_x / wsum
            cy = wsum_y / wsum
        else:
            cx, cy = float(best_x), float(best_y)
        # 64-space -> 256-space
        corners256.append((cx * 4.0, cy * 4.0))
    return corners256


def load_labels(label_path: Path) -> tuple[int, int, list[tuple[float, float]]]:
    """Loads a label JSON and returns (width, height, corners_px)."""
    data = json.loads(label_path.read_text())
    w, h = data["width"], data["height"]
    corners = [(c[0], c[1]) for c in data["corners_px"]]
    return w, h, corners


def corner_error_px(pred_256: list[tuple[float, float]],
                    gt_corners: list[tuple[float, float]],
                    img_w: int, img_h: int) -> float:
    """Mean corner error in original pixels."""
    sx, sy = img_w / 256.0, img_h / 256.0
    errors = []
    for (px, py), (gx, gy) in zip(pred_256, gt_corners):
        dx = px * sx - gx
        dy = py * sy - gy
        errors.append(np.sqrt(dx * dx + dy * dy))
    return float(np.mean(errors))


def run_comparison(model_fp32: Path, model_fp16: Path, dataset: Path,
                   max_images: int | None = None) -> None:
    sess_fp32 = load_session(model_fp32)
    sess_fp16 = load_session(model_fp16)

    images_dir = dataset / "images"
    labels_dir = dataset / "labels"
    image_files = sorted(images_dir.glob("*.jpg"))
    if max_images:
        image_files = image_files[:max_images]

    print(f"\nVergleich auf {len(image_files)} Bildern aus {dataset}")
    print("=" * 70)

    # Statistics
    corner_diffs = []       # max abs diff per image (corner_heatmaps)
    mask_diffs = []          # max abs diff per image (mask_logits)
    corner_mean_diffs = []
    mask_mean_diffs = []
    errors_fp32 = []         # corner error FP32 in original pixels
    errors_fp16 = []         # corner error FP16 in original pixels
    time_fp32_total = 0.0
    time_fp16_total = 0.0

    for img_path in image_files:
        label_path = labels_dir / (img_path.stem + ".json")
        if not label_path.exists():
            continue

        x = preprocess_image(img_path)
        w, h, gt_corners = load_labels(label_path)

        # FP32 inference
        t0 = time.perf_counter()
        out_fp32 = sess_fp32.run(None, {"input": x})
        time_fp32_total += time.perf_counter() - t0

        # FP16 inference
        t0 = time.perf_counter()
        out_fp16 = sess_fp16.run(None, {"input": x})
        time_fp16_total += time.perf_counter() - t0

        corners_fp32, mask_fp32 = out_fp32
        corners_fp16, mask_fp16 = out_fp16

        # Output deviations
        cd = np.abs(corners_fp32.astype(np.float64) - corners_fp16.astype(np.float64))
        md = np.abs(mask_fp32.astype(np.float64) - mask_fp16.astype(np.float64))
        corner_diffs.append(float(cd.max()))
        mask_diffs.append(float(md.max()))
        corner_mean_diffs.append(float(cd.mean()))
        mask_mean_diffs.append(float(md.mean()))

        # Corner detection
        pred_fp32 = refine_corners_64_to_256(corners_fp32)
        pred_fp16 = refine_corners_64_to_256(corners_fp16)
        errors_fp32.append(corner_error_px(pred_fp32, gt_corners, w, h))
        errors_fp16.append(corner_error_px(pred_fp16, gt_corners, w, h))

    n = len(errors_fp32)
    print(f"\nAusgewertete Bilder: {n}")

    # Size comparison
    size_fp32 = os.path.getsize(model_fp32) / 1e6
    size_fp16 = os.path.getsize(model_fp16) / 1e6
    print(f"\n--- Modellgröße ---")
    print(f"  FP32:      {size_fp32:.1f} MB")
    print(f"  FP16:      {size_fp16:.1f} MB")
    print(f"  Reduktion: {(1 - size_fp16 / size_fp32) * 100:.0f}%")

    # Output deviations
    print(f"\n--- Output-Abweichungen (FP32 vs FP16) ---")
    print(f"  corner_heatmaps  max_diff:  {np.max(corner_diffs):.6f}  "
          f"mean_diff: {np.mean(corner_mean_diffs):.6f}")
    print(f"  mask_logits      max_diff:  {np.max(mask_diffs):.6f}  "
          f"mean_diff: {np.mean(mask_mean_diffs):.6f}")

    # Corner error
    e32 = np.array(errors_fp32)
    e16 = np.array(errors_fp16)
    print(f"\n--- Eckenfehler (Mean Corner Error in Originalpixeln) ---")
    print(f"  FP32:  mean={e32.mean():.2f}  median={np.median(e32):.2f}  "
          f"p95={np.percentile(e32, 95):.2f}  max={e32.max():.2f}")
    print(f"  FP16:  mean={e16.mean():.2f}  median={np.median(e16):.2f}  "
          f"p95={np.percentile(e16, 95):.2f}  max={e16.max():.2f}")
    print(f"  Δ mean: {e16.mean() - e32.mean():+.2f} px")

    # Identical corners?
    identical = sum(1 for a, b in zip(errors_fp32, errors_fp16) if abs(a - b) < 0.01)
    print(f"  Identische Ergebnisse (<0.01 px Diff): {identical}/{n} ({identical/n*100:.0f}%)")

    # Inference time
    print(f"\n--- Inferenzzeit (CPU, {n} Bilder) ---")
    print(f"  FP32:  {time_fp32_total:.2f}s  ({time_fp32_total/n*1000:.1f} ms/Bild)")
    print(f"  FP16:  {time_fp16_total:.2f}s  ({time_fp16_total/n*1000:.1f} ms/Bild)")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="FP16 quantization and comparison of the DocQuad ONNX model")
    p.add_argument("--model", required=True, help="Path to the FP32 ONNX model")
    p.add_argument("--dataset", required=True, help="Path to the my_data_trainable dataset")
    p.add_argument("--out", default="/tmp/docquadnet256_fp16.onnx", help="Output path for the FP16 model")
    p.add_argument("--max-images", type=int, default=None, help="Max number of images (None=all)")
    args = p.parse_args(argv)

    model_fp32 = Path(args.model)
    dataset = Path(args.dataset)
    out_fp16 = Path(args.out)

    if not model_fp32.exists():
        print(f"Error: Model not found: {model_fp32}", file=sys.stderr)
        return 1
    if not (dataset / "images").exists():
        print(f"Error: Dataset not found: {dataset}", file=sys.stderr)
        return 1

    print(f"Konvertiere {model_fp32} → FP16 ...")
    convert_fp16(model_fp32, out_fp16)
    print(f"FP16-Modell gespeichert: {out_fp16}")

    run_comparison(model_fp32, out_fp16, dataset, max_images=args.max_images)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

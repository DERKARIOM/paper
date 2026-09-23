#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import onnxruntime as ort


def find_models(src_dir: Path) -> list[Path]:
    models = []
    for p in sorted(src_dir.glob("*.onnx")):
        if p.name.endswith(".basic.onnx"):
            continue
        # alte Alias-Dateien optional überspringen
        if p.name == "rec.onnx":
            continue
        models.append(p)

    if not any(p.name == "det.onnx" for p in models):
        raise SystemExit(f"missing det.onnx in {src_dir}")

    others = [p for p in models if p.name != "det.onnx"]
    if not others:
        raise SystemExit(f"no additional ONNX models found in {src_dir}")

    return models


def optimize_basic(model: Path) -> Path:
    out = model.with_suffix(".basic.onnx")

    so = ort.SessionOptions()
    so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_BASIC
    so.optimized_model_filepath = str(out)

    ort.InferenceSession(
        str(model),
        sess_options=so,
        providers=["CPUExecutionProvider"],
    )

    print(f"basic-optimized -> {out}")
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "dir",
        default="paddle-spike/onnx",
        nargs="?",
        help="Directory containing det.onnx, recognition ONNX models, and optional layout.onnx",
    )
    args = parser.parse_args()

    src_dir = Path(args.dir).resolve()
    models = find_models(src_dir)

    print("Models:")
    for m in models:
        print(f"  - {m.name}")

    basic_models = []

    try:
        for model in models:
            basic_models.append((model, optimize_basic(model)))

        with tempfile.TemporaryDirectory(prefix="paddleocr_v5_ort_") as tmp:
            stage = Path(tmp)

            for original, basic in basic_models:
                staged = stage / original.name
                shutil.copy2(basic, staged)

            subprocess.check_call([
                sys.executable,
                "-m",
                "onnxruntime.tools.convert_onnx_models_to_ort",
                str(stage),
                "--optimization_style",
                "Fixed",
            ])

            for fn in sorted(stage.iterdir()):
                if fn.suffix == ".ort" or fn.name == "required_operators.config":
                    dest = src_dir / fn.name
                    shutil.copy2(fn, dest)
                    print(f"copied {fn.name} -> {dest}")

    finally:
        for _, basic in basic_models:
            if basic.exists():
                basic.unlink()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
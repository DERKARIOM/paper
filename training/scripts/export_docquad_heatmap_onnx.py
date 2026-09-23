from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
import torch

from training.docquad_m3.export_onnx import DocQuadNet256OnnxWrapper, create_docquadnet256_from_checkpoint
from training.docquad_m3.golden_samples import MASK_AREA_DEFINITION_V1, compute_mask_area_v1, get_golden_inputs_v1


def _export_to_onnx(*, checkpoint: Path, out: Path, opset: int) -> Path:
    model = create_docquadnet256_from_checkpoint(checkpoint, backbone=None, map_location="cpu")
    model.eval()

    wrapper = DocQuadNet256OnnxWrapper(model).eval()
    dummy = torch.zeros(1, 3, 256, 256, dtype=torch.float32)

    out.parent.mkdir(parents=True, exist_ok=True)

    export_kwargs = dict(
        input_names=["input"],
        output_names=["corner_heatmaps", "mask_logits"],
        export_params=True,
        do_constant_folding=True,
        opset_version=int(opset),
        dynamic_axes={
            "input": {0: "N"},
            "corner_heatmaps": {0: "N"},
            "mask_logits": {0: "N"},
        },
        training=torch.onnx.TrainingMode.EVAL,
    )

    with torch.no_grad():
        try:
            torch.onnx.export(wrapper, dummy, str(out), dynamo=False, **export_kwargs)
        except TypeError:
            torch.onnx.export(wrapper, dummy, str(out), **export_kwargs)
    return out


def _run_ort(onnx_path: Path, x_np: np.ndarray) -> dict[str, np.ndarray]:
    sess = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    out_names = ["corner_heatmaps", "mask_logits"]
    out_vals = sess.run(out_names, {"input": x_np})
    return {k: v for k, v in zip(out_names, out_vals)}


def _diff_stats(a: np.ndarray, b: np.ndarray) -> tuple[float, float]:
    d = np.abs(a.astype(np.float64) - b.astype(np.float64))
    return float(d.max()), float(d.mean())


def _mean_std(x: np.ndarray) -> tuple[float, float]:
    v = np.asarray(x, dtype=np.float64)
    return float(v.mean()), float(v.std())


def _write_expected_json(*, onnx_path: Path, out_json: Path) -> None:
    inputs = get_golden_inputs_v1()
    x_np = inputs["sample0"].astype(np.float32)

    ort_out = _run_ort(onnx_path, x_np)
    corner = ort_out["corner_heatmaps"]
    mask = ort_out["mask_logits"]

    corner_mean, corner_std = _mean_std(corner)
    mask_mean, mask_std = _mean_std(mask)
    mask_area = compute_mask_area_v1(mask)

    payload = {
        "version": 1,
        "mask_area_definition": MASK_AREA_DEFINITION_V1,
        "samples": {
            "sample0": {
                "mask_area": int(mask_area),
                "corner_heatmaps_mean": corner_mean,
                "corner_heatmaps_std": corner_std,
                "mask_logits_mean": mask_mean,
                "mask_logits_std": mask_std,
            }
        },
    }

    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(
        description=(
            "Export DocQuad heatmap checkpoint to ONNX (ORT-mobile compatible), "
            "with stable I/O names and optional PyTorch↔ORT parity + expected-stats generation."
        )
    )
    p.add_argument("--checkpoint", required=True, help="Path to best.pt")
    p.add_argument("--out", required=True, help="Output ONNX path")
    p.add_argument("--opset", type=int, default=17)
    p.add_argument(
        "--expected-json-out",
        default=None,
        help="Optional output path for expected stats JSON (Golden sample v1).",
    )
    p.add_argument(
        "--parity",
        action="store_true",
        help="Run a deterministic PyTorch↔onnxruntime parity check after export.",
    )
    args = p.parse_args(argv)

    ckpt = Path(args.checkpoint)
    out = Path(args.out)
    onnx_path = _export_to_onnx(checkpoint=ckpt, out=out, opset=int(args.opset))
    print(f"wrote: {onnx_path}")

    if args.parity:
        model = create_docquadnet256_from_checkpoint(ckpt, backbone=None, map_location="cpu")
        model.eval()

        rng = np.random.default_rng(123)
        x_np = rng.random((1, 3, 256, 256), dtype=np.float32)
        x_t = torch.from_numpy(x_np)
        with torch.no_grad():
            out_pt = model(x_t)
            pt_corners = out_pt.corner_heatmaps.detach().cpu().numpy()
            pt_mask = out_pt.mask_logits.detach().cpu().numpy()

        ort_out = _run_ort(onnx_path, x_np)
        max_c, mean_c = _diff_stats(pt_corners, ort_out["corner_heatmaps"])
        max_m, mean_m = _diff_stats(pt_mask, ort_out["mask_logits"])

        print("parity.corner_heatmaps.max_abs_diff:", max_c)
        print("parity.corner_heatmaps.mean_abs_diff:", mean_c)
        print("parity.mask_logits.max_abs_diff:", max_m)
        print("parity.mask_logits.mean_abs_diff:", mean_m)

    if args.expected_json_out is not None:
        out_json = Path(args.expected_json_out)
        _write_expected_json(onnx_path=onnx_path, out_json=out_json)
        print(f"wrote: {out_json}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

import os
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import pytest
import torch

from training.docquad_m3.export_onnx import (
    ExportConfig,
    create_docquadnet256,
    create_docquadnet256_from_checkpoint,
    export_model_to_onnx,
)
from training.docquad_m3.golden_samples import (
    MASK_AREA_DEFINITION_V1,
    compute_mask_area_v1,
    get_golden_inputs_v1,
    read_expected_stats_v1,
    read_expected_stats_trained,
)


def _run_ort(onnx_path, x_np: np.ndarray) -> dict[str, np.ndarray]:
    sess = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    # Request outputs explicitly by name (robust against output order in graph).
    out_names = ["corner_heatmaps", "mask_logits"]
    out_vals = sess.run(out_names, {"input": x_np})
    return {k: v for k, v in zip(out_names, out_vals)}


def _trained_mix_run2_best_ckpt() -> Path:
    return (
        Path(__file__).resolve().parents[1]
        / "runs"
        / "docquad_heatmap_mix_run2_es"
        / "checkpoints"
        / "best.pt"
    )


def _trained_tests_enabled() -> bool:
    """Trained model tests are opt-in.

    Background: Trained checkpoints/reference JSONs naturally rotate and should
    not make the standard test run (`pytest`) flaky.
    """

    return os.environ.get("RUN_TRAINED_TESTS", "").strip() not in {"", "0", "false", "False"}


def test_onnx_contains_expected_tensor_names_and_shapes(tmp_path):
    cfg = ExportConfig(backbone="large", fpn_channels=96)
    model = create_docquadnet256(config=cfg, seed=0, zero_init=False)
    onnx_path = tmp_path / "docquadnet256.onnx"
    export_model_to_onnx(model, onnx_path)

    # ONNX graph-level checks
    m = onnx.load(str(onnx_path))
    in_names = [i.name for i in m.graph.input]
    out_names = [o.name for o in m.graph.output]
    assert in_names == ["input"]
    assert set(out_names) == {"corner_heatmaps", "mask_logits"}

    # Opset pinning (M3 Fix): explicitly set to 17.
    opset_by_domain = {op.domain: int(op.version) for op in m.opset_import}
    assert opset_by_domain.get("", None) == 17 or opset_by_domain.get("ai.onnx", None) == 17, opset_by_domain

    # onnxruntime checks (names + concrete shapes)
    sess = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    assert [i.name for i in sess.get_inputs()] == ["input"]
    assert {o.name for o in sess.get_outputs()} == {"corner_heatmaps", "mask_logits"}
    assert sess.get_inputs()[0].shape == [1, 3, 256, 256]
    out_by_name = {o.name: o for o in sess.get_outputs()}
    assert out_by_name["corner_heatmaps"].shape == [1, 4, 64, 64]
    assert out_by_name["mask_logits"].shape == [1, 1, 64, 64]


def test_pytorch_vs_onnxruntime_output_parity(tmp_path):
    cfg = ExportConfig(backbone="large", fpn_channels=96)
    model = create_docquadnet256(config=cfg, seed=0, zero_init=False)
    onnx_path = tmp_path / "docquadnet256.onnx"
    export_model_to_onnx(model, onnx_path)

    rng = np.random.default_rng(123)
    x_np = rng.random((1, 3, 256, 256), dtype=np.float32)
    x_t = torch.from_numpy(x_np)

    with torch.no_grad():
        out = model(x_t)
        pt_mask = out.mask_logits.detach().cpu().numpy()
        pt_corners = out.corner_heatmaps.detach().cpu().numpy()

    ort_out = _run_ort(onnx_path, x_np)
    ort_mask = ort_out["mask_logits"]
    ort_corners = ort_out["corner_heatmaps"]

    # Tolerance-based: ORT should be numerically very close to PyTorch forward.
    def _diff_stats(a: np.ndarray, b: np.ndarray) -> tuple[float, float]:
        d = np.abs(a.astype(np.float64) - b.astype(np.float64))
        return float(d.max()), float(d.mean())

    max_mask, mean_mask = _diff_stats(pt_mask, ort_mask)
    max_corners, mean_corners = _diff_stats(pt_corners, ort_corners)

    # Relatively conservative, but stable on CPU.
    assert max_mask <= 2e-4 and mean_mask <= 2e-6, (max_mask, mean_mask)
    assert max_corners <= 2e-4 and mean_corners <= 2e-6, (max_corners, mean_corners)


def test_pytorch_vs_onnxruntime_output_parity_trained_checkpoint(tmp_path):
    if not _trained_tests_enabled():
        pytest.skip("trained tests disabled (set RUN_TRAINED_TESTS=1 to enable)")

    ckpt = _trained_mix_run2_best_ckpt()
    if not ckpt.exists():
        pytest.skip(f"trained checkpoint not found: {ckpt}")

    model = create_docquadnet256_from_checkpoint(ckpt, backbone="large")
    onnx_path = tmp_path / "docquadnet256_trained.onnx"
    export_model_to_onnx(model, onnx_path)

    rng = np.random.default_rng(123)
    x_np = rng.random((1, 3, 256, 256), dtype=np.float32)
    x_t = torch.from_numpy(x_np)

    with torch.no_grad():
        out = model(x_t)
        pt_mask = out.mask_logits.detach().cpu().numpy()
        pt_corners = out.corner_heatmaps.detach().cpu().numpy()

    ort_out = _run_ort(onnx_path, x_np)
    ort_mask = ort_out["mask_logits"]
    ort_corners = ort_out["corner_heatmaps"]

    def _diff_stats(a: np.ndarray, b: np.ndarray) -> tuple[float, float]:
        d = np.abs(a.astype(np.float64) - b.astype(np.float64))
        return float(d.max()), float(d.mean())

    max_mask, mean_mask = _diff_stats(pt_mask, ort_mask)
    max_corners, mean_corners = _diff_stats(pt_corners, ort_corners)

    # Trained model: still low tolerance, but slightly more conservative than the init model.
    assert max_mask <= 5e-4 and mean_mask <= 5e-6, (max_mask, mean_mask)
    assert max_corners <= 5e-4 and mean_corners <= 5e-6, (max_corners, mean_corners)


def test_pytorch_vs_onnxruntime_output_parity_trained_best_onnx_artifact():
    if not _trained_tests_enabled():
        pytest.skip("trained tests disabled (set RUN_TRAINED_TESTS=1 to enable)")

    ckpt = _trained_mix_run2_best_ckpt()
    if not ckpt.exists():
        pytest.skip(f"trained checkpoint not found: {ckpt}")

    onnx_path = ckpt.parent / "best.onnx"
    if not onnx_path.exists():
        pytest.skip(f"trained ONNX artifact not found: {onnx_path}")

    model = create_docquadnet256_from_checkpoint(ckpt, backbone="large")

    rng = np.random.default_rng(123)
    x_np = rng.random((1, 3, 256, 256), dtype=np.float32)
    x_t = torch.from_numpy(x_np)

    with torch.no_grad():
        out = model(x_t)
        pt_mask = out.mask_logits.detach().cpu().numpy()
        pt_corners = out.corner_heatmaps.detach().cpu().numpy()

    ort_out = _run_ort(onnx_path, x_np)
    ort_mask = ort_out["mask_logits"]
    ort_corners = ort_out["corner_heatmaps"]

    def _diff_stats(a: np.ndarray, b: np.ndarray) -> tuple[float, float]:
        d = np.abs(a.astype(np.float64) - b.astype(np.float64))
        return float(d.max()), float(d.mean())

    max_mask, mean_mask = _diff_stats(pt_mask, ort_mask)
    max_corners, mean_corners = _diff_stats(pt_corners, ort_corners)

    assert max_mask <= 5e-4 and mean_mask <= 5e-6, (max_mask, mean_mask)
    assert max_corners <= 5e-4 and mean_corners <= 5e-6, (max_corners, mean_corners)


def test_golden_sample_stats_match_for_zero_init_model(tmp_path):
    cfg = ExportConfig(backbone="large", fpn_channels=96)
    model = create_docquadnet256(config=cfg, seed=0, zero_init=True)
    onnx_path = tmp_path / "docquadnet256_zero.onnx"
    export_model_to_onnx(model, onnx_path)

    expected = read_expected_stats_v1()
    inputs = get_golden_inputs_v1()
    x_np = inputs["sample0"].astype(np.float32)
    assert x_np.shape == (1, 3, 256, 256)
    assert float(x_np.min()) >= 0.0
    assert float(x_np.max()) <= 1.0

    ort_out = _run_ort(onnx_path, x_np)
    mask_logits = ort_out["mask_logits"]
    corner_logits = ort_out["corner_heatmaps"]
    assert mask_logits.shape == (1, 1, 64, 64)
    assert corner_logits.shape == (1, 4, 64, 64)

    corner_argmax_idx = [int(np.argmax(corner_logits[0, c])) for c in range(4)]
    mask_area = compute_mask_area_v1(mask_logits)

    exp0 = expected["samples"]["sample0"]
    assert expected["mask_area_definition"] == MASK_AREA_DEFINITION_V1
    assert corner_argmax_idx == exp0["corner_argmax_idx"]
    assert mask_area == int(exp0["mask_area"])


def test_golden_sample_stats_match_for_trained_checkpoint_model(tmp_path):
    if not _trained_tests_enabled():
        pytest.skip("trained tests disabled (set RUN_TRAINED_TESTS=1 to enable)")

    ckpt = _trained_mix_run2_best_ckpt()
    if not ckpt.exists():
        pytest.skip(f"trained checkpoint not found: {ckpt}")

    model = create_docquadnet256_from_checkpoint(ckpt, backbone="large")
    onnx_path = ckpt.parent / "best.onnx"
    if not onnx_path.exists():
        onnx_path = tmp_path / "docquadnet256_trained.onnx"
        export_model_to_onnx(model, onnx_path)

    expected = read_expected_stats_trained()
    inputs = get_golden_inputs_v1()
    x_np = inputs["sample0"].astype(np.float32)

    ort_out = _run_ort(onnx_path, x_np)
    corner_logits = ort_out["corner_heatmaps"]
    mask_logits = ort_out["mask_logits"]

    def _mean_std(x: np.ndarray) -> tuple[float, float]:
        v = np.asarray(x, dtype=np.float64)
        return float(v.mean()), float(v.std())

    corner_mean, corner_std = _mean_std(corner_logits)
    mask_mean, mask_std = _mean_std(mask_logits)
    mask_area = compute_mask_area_v1(mask_logits)

    exp0 = expected["samples"]["sample0"]
    assert expected["mask_area_definition"] == MASK_AREA_DEFINITION_V1
    assert mask_area == int(exp0["mask_area"])

    # Comparison tolerant (float stats), but deterministic enough for CPU/ORT.
    assert abs(corner_mean - float(exp0["corner_heatmaps_mean"])) <= 1e-4
    assert abs(corner_std - float(exp0["corner_heatmaps_std"])) <= 1e-4
    assert abs(mask_mean - float(exp0["mask_logits_mean"])) <= 1e-4
    assert abs(mask_std - float(exp0["mask_logits_std"])) <= 1e-4

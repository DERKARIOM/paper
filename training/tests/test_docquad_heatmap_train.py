from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

import numpy as np
import pytest
import torch
from PIL import Image

from training.docquad_m2.model import DocQuadNet256


def _write_label(path: Path, *, image_name: str, w: int, h: int, corners_px: list[list[float]]) -> None:
    path.write_text(
        json.dumps(
            {
                "image": image_name,
                "width": int(w),
                "height": int(h),
                "corners_px": corners_px,
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )


def _make_rect_corners(*, x0: float, y0: float, x1: float, y1: float) -> list[list[float]]:
    # TL, TR, BR, BL
    return [[x0, y0], [x1, y0], [x1, y1], [x0, y1]]


def test_forward_shapes_cpu():
    model = DocQuadNet256(backbone="large", fpn_channels=96)
    x = torch.zeros(2, 3, 256, 256, dtype=torch.float32)
    out = model(x)
    assert out.corner_heatmaps.shape == (2, 4, 64, 64)
    assert out.mask_logits.shape == (2, 1, 64, 64)


def test_heatmap_train_script_runs_one_epoch_cpu(tmp_path: Path):
    base = _make_mini_dataset(tmp_path)
    out_dir = tmp_path / "run"
    _run_train_script(
        base_dir=base,
        out_dir=out_dir,
        epochs=1,
        batch=2,
        extra_args=[],
    )

    assert (out_dir / "metrics.jsonl").is_file()
    assert (out_dir / "vis" / "epoch_01").is_dir()
    assert (out_dir / "checkpoints" / "last.pt").is_file()


def _make_mini_dataset(tmp_path: Path) -> Path:
    # Mini dataset in the required layout.
    base = tmp_path / "ds"
    images = base / "images"
    labels = base / "labels"
    images.mkdir(parents=True)
    labels.mkdir(parents=True)

    # 4 samples: 2 train, 2 val
    # Vary image sizes so that letterbox paths (letterbox/pillarbox) are used.
    samples = [
        ("s0.jpg", 400, 200, _make_rect_corners(x0=80.0, y0=40.0, x1=320.0, y1=160.0)),
        ("s1.jpg", 200, 400, _make_rect_corners(x0=30.0, y0=90.0, x1=170.0, y1=310.0)),
        ("s2.jpg", 320, 240, _make_rect_corners(x0=40.0, y0=30.0, x1=280.0, y1=210.0)),
        ("s3.jpg", 240, 320, _make_rect_corners(x0=50.0, y0=60.0, x1=190.0, y1=260.0)),
    ]

    for name, w, h, corners in samples:
        # deterministic, synthetic RGB pattern
        arr = np.zeros((h, w, 3), dtype=np.uint8)
        arr[..., 0] = np.linspace(0, 255, w, dtype=np.uint8)[None, :]
        arr[..., 1] = np.linspace(0, 255, h, dtype=np.uint8)[:, None]
        arr[..., 2] = 64
        Image.fromarray(arr, mode="RGB").save(images / name)
        _write_label(labels / f"{Path(name).stem}.json", image_name=name, w=w, h=h, corners_px=corners)

    (base / "split_train.txt").write_text("s0.jpg\ns1.jpg\n", encoding="utf-8")
    (base / "split_val.txt").write_text("s2.jpg\ns3.jpg\n", encoding="utf-8")
    return base


def _run_train_script(
    *,
    base_dir: Path,
    out_dir: Path,
    epochs: int,
    batch: int,
    extra_args: list[str],
    capture: bool = False,
) -> subprocess.CompletedProcess[str]:
    cmd = [
        sys.executable,
        "training/scripts/train_docquad_heatmap.py",
        "--base_dir",
        str(base_dir),
        "--epochs",
        str(int(epochs)),
        "--batch",
        str(int(batch)),
        "--device",
        "cpu",
        "--out_dir",
        str(out_dir),
        "--sigma",
        "2.0",
    ]
    cmd += list(extra_args)
    if capture:
        return subprocess.run(cmd, check=True, capture_output=True, text=True)
    subprocess.run(cmd, check=True)
    return subprocess.CompletedProcess(cmd, 0, "", "")


def _load_ckpt(path: Path) -> dict:
    obj = torch.load(str(path), map_location="cpu")
    assert isinstance(obj, dict)
    return obj


def _assert_model_state_equal(a: dict, b: dict) -> None:
    ak = sorted(a.keys())
    bk = sorted(b.keys())
    assert ak == bk
    for k in ak:
        ta = a[k]
        tb = b[k]
        assert isinstance(ta, torch.Tensor)
        assert isinstance(tb, torch.Tensor)
        assert torch.equal(ta, tb), k


def _assert_opt_state_equal(a: dict, b: dict) -> None:
    assert set(a.keys()) == set(b.keys())
    assert a["param_groups"] == b["param_groups"]

    sa = a.get("state", {})
    sb = b.get("state", {})
    assert set(sa.keys()) == set(sb.keys())

    for pid in sa.keys():
        da = sa[pid]
        db = sb[pid]
        assert set(da.keys()) == set(db.keys())
        for kk in da.keys():
            va = da[kk]
            vb = db[kk]
            if isinstance(va, torch.Tensor) or isinstance(vb, torch.Tensor):
                assert isinstance(va, torch.Tensor)
                assert isinstance(vb, torch.Tensor)
                assert torch.equal(va, vb), (pid, kk)
            else:
                assert va == vb


def _read_metrics_epochs(path: Path) -> list[int]:
    epochs: list[int] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        r = json.loads(line)
        epochs.append(int(r["epoch"]))
    return epochs


def test_resume_training_loads_model_and_optimizer_state(tmp_path: Path):
    base = _make_mini_dataset(tmp_path)

    out_cont = tmp_path / "cont"
    _run_train_script(base_dir=base, out_dir=out_cont, epochs=2, batch=2, extra_args=[])

    out_split = tmp_path / "split"
    _run_train_script(base_dir=base, out_dir=out_split, epochs=1, batch=2, extra_args=[])
    _run_train_script(
        base_dir=base,
        out_dir=out_split,
        epochs=2,
        batch=2,
        extra_args=["--resume", str(out_split / "checkpoints")],
    )

    ckpt_cont = _load_ckpt(out_cont / "checkpoints" / "last.pt")
    ckpt_split = _load_ckpt(out_split / "checkpoints" / "last.pt")
    assert int(ckpt_cont["epoch"]) == 2
    assert int(ckpt_split["epoch"]) == 2

    _assert_model_state_equal(ckpt_cont["model_state"], ckpt_split["model_state"])
    _assert_opt_state_equal(ckpt_cont["opt_state"], ckpt_split["opt_state"])

    epochs = _read_metrics_epochs(out_split / "metrics.jsonl")
    assert epochs == [1, 2]


def _read_metrics_lrs(path: Path) -> list[float]:
    lrs: list[float] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        r = json.loads(line)
        lrs.append(float(r["optimizer"]["lr"]))
    return lrs


def test_resume_training_cli_lr_overrides_optimizer_state_lr(tmp_path: Path):
    base = _make_mini_dataset(tmp_path)

    out_dir = tmp_path / "run"
    # Epoch 1 with default LR (1e-3)
    _run_train_script(base_dir=base, out_dir=out_dir, epochs=1, batch=2, extra_args=[])

    # Epoch 2 via resume, but with smaller LR.
    proc = _run_train_script(
        base_dir=base,
        out_dir=out_dir,
        epochs=2,
        batch=2,
        extra_args=["--resume", str(out_dir / "checkpoints"), "--lr", "3e-5"],
        capture=True,
    )
    assert "resume: enforced lr=" in (proc.stdout + proc.stderr)

    lrs = _read_metrics_lrs(out_dir / "metrics.jsonl")
    assert len(lrs) == 2
    assert abs(lrs[1] - 3e-5) < 1e-12

    # Additionally: in the checkpoint, the LR in param_groups must match the CLI value.
    ckpt = _load_ckpt(out_dir / "checkpoints" / "last.pt")
    opt_state = ckpt["opt_state"]
    assert abs(float(opt_state["param_groups"][0]["lr"]) - 3e-5) < 1e-12


def test_resume_without_optimizer_state_reinitializes_optimizer(tmp_path: Path):
    base = _make_mini_dataset(tmp_path)
    out_dir = tmp_path / "noopt"

    _run_train_script(base_dir=base, out_dir=out_dir, epochs=1, batch=2, extra_args=[])
    last_path = out_dir / "checkpoints" / "last.pt"
    obj = _load_ckpt(last_path)
    obj.pop("opt_state", None)
    resume_path = out_dir / "checkpoints" / "last_noopt.pt"
    torch.save(obj, resume_path)

    proc = _run_train_script(
        base_dir=base,
        out_dir=out_dir,
        epochs=2,
        batch=2,
        extra_args=["--resume", str(resume_path)],
        capture=True,
    )
    assert "checkpoint has no optimizer state" in (proc.stdout + proc.stderr)

    ckpt2 = _load_ckpt(out_dir / "checkpoints" / "last.pt")
    assert int(ckpt2["epoch"]) == 2


def test_early_stopping_stops_on_val_corner_mae(tmp_path: Path):
    base = _make_mini_dataset(tmp_path)
    out_dir = tmp_path / "early"

    _run_train_script(
        base_dir=base,
        out_dir=out_dir,
        epochs=10,
        batch=2,
        extra_args=[
            "--early_stop_patience",
            "1",
            "--early_stop_min_delta",
            "1000000000.0",
        ],
    )

    ckpt = _load_ckpt(out_dir / "checkpoints" / "last.pt")
    assert int(ckpt["epoch"]) == 2
    assert _read_metrics_epochs(out_dir / "metrics.jsonl") == [1, 2]


def test_resume_accepts_optimizer_state_key_and_logs_used_key(tmp_path: Path):
    base = _make_mini_dataset(tmp_path)
    out_dir = tmp_path / "optkey"

    _run_train_script(base_dir=base, out_dir=out_dir, epochs=1, batch=2, extra_args=[])
    last_path = out_dir / "checkpoints" / "last.pt"
    obj = _load_ckpt(last_path)

    # Simulate historical/external key name.
    obj["optimizer_state"] = obj.pop("opt_state")
    # Unknown key should NOT be guessed, but cleanly logged/ignored.
    obj["optim_state"] = {"note": "should_be_ignored"}

    resume_path = out_dir / "checkpoints" / "last_optimizer_state.pt"
    torch.save(obj, resume_path)

    proc = _run_train_script(
        base_dir=base,
        out_dir=out_dir,
        epochs=2,
        batch=2,
        extra_args=["--resume", str(resume_path)],
        capture=True,
    )
    out = proc.stdout + proc.stderr
    assert "loaded optimizer state (key=optimizer_state)" in out
    assert "ignoring unknown optimizer-related keys" in out and "optim_state" in out


def test_non_resume_logs_metrics_overwrite_when_file_exists(tmp_path: Path):
    base = _make_mini_dataset(tmp_path)
    out_dir = tmp_path / "overwrite"
    out_dir.mkdir(parents=True)

    metrics = out_dir / "metrics.jsonl"
    metrics.write_text('{"epoch": 999}\n', encoding="utf-8")

    proc = _run_train_script(
        base_dir=base,
        out_dir=out_dir,
        epochs=1,
        batch=2,
        extra_args=[],
        capture=True,
    )
    out = proc.stdout + proc.stderr
    assert "metrics: overwrite" in out
    assert _read_metrics_epochs(metrics) == [1]

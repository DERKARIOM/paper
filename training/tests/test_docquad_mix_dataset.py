from __future__ import annotations

import json
import random
import subprocess
import sys
from pathlib import Path

import pytest
from PIL import Image


def _write_image(path: Path, *, w: int, h: int) -> None:
    img = Image.new("RGB", (int(w), int(h)), color=(10, 20, 30))
    img.save(path)


def _write_label(path: Path, *, image_name: str, w: int, h: int) -> None:
    corners_px = [[0.0, 0.0], [float(w - 1), 0.0], [float(w - 1), float(h - 1)], [0.0, float(h - 1)]]
    obj = {
        "image": image_name,
        "width": int(w),
        "height": int(h),
        "corners_px": corners_px,
    }
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _make_mini_trainable(root: Path, *, prefix: str, ext: str) -> Path:
    ds = root / prefix
    (ds / "images").mkdir(parents=True)
    (ds / "labels").mkdir(parents=True)

    for i in range(2):
        img_name = f"{prefix}{i}{ext}"
        w, h = 16 + i, 12 + i
        _write_image(ds / "images" / img_name, w=w, h=h)
        _write_label(ds / "labels" / f"{prefix}{i}.json", image_name=img_name, w=w, h=h)
    return ds


def _run_merge(*, a_dir: Path, b_dir: Path, out_dir: Path) -> subprocess.CompletedProcess[str]:
    cmd = [
        sys.executable,
        "training/scripts/make_mix_trainable_from_two_trainables.py",
        "--a_dir",
        str(a_dir),
        "--b_dir",
        str(b_dir),
        "--out_dir",
        str(out_dir),
    ]
    return subprocess.run(cmd, capture_output=True, text=True)


def _read_lines(path: Path) -> list[str]:
    if not path.exists():
        return []
    out: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s:
            continue
        out.append(s)
    return out


def test_merge_creates_prefixed_images_labels_and_deterministic_splits(tmp_path: Path):
    a = _make_mini_trainable(tmp_path, prefix="a", ext=".jpg")
    b = _make_mini_trainable(tmp_path, prefix="b", ext=".png")
    out_dir = tmp_path / "out"

    proc = _run_merge(a_dir=a, b_dir=b, out_dir=out_dir)
    assert proc.returncode == 0, proc.stdout + proc.stderr

    out_images = out_dir / "images"
    out_labels = out_dir / "labels"
    assert out_images.is_dir()
    assert out_labels.is_dir()

    exp_images = [
        "a_a0.jpg",
        "a_a1.jpg",
        "b_b0.png",
        "b_b1.png",
    ]
    exp_labels = [
        "a_a0.json",
        "a_a1.json",
        "b_b0.json",
        "b_b1.json",
    ]
    assert sorted([p.name for p in out_images.iterdir() if p.is_file()]) == sorted(exp_images)
    assert sorted([p.name for p in out_labels.glob("*.json")]) == sorted(exp_labels)

    # Label "image" must match the prefixed image filename, and stem must match.
    for lbl_name in exp_labels:
        lp = out_labels / lbl_name
        obj = json.loads(lp.read_text(encoding="utf-8"))
        assert obj["image"].endswith(Path(lbl_name).stem + Path(obj["image"]).suffix)
        assert Path(obj["image"]).stem == Path(lbl_name).stem
        assert (out_images / obj["image"]).is_file()

    # Deterministic splits (default val_ratio=0.10; with 4 samples => round(0.4)=0 => min 1 => 1 val)
    split_val = _read_lines(out_dir / "split_val.txt")
    split_train = _read_lines(out_dir / "split_train.txt")

    names = sorted(exp_images)
    random.Random(0).shuffle(names)
    assert split_val == names[:1]
    assert split_train == names[1:]


def test_merge_aborts_if_out_dir_not_empty(tmp_path: Path):
    a = _make_mini_trainable(tmp_path, prefix="a", ext=".jpg")
    b = _make_mini_trainable(tmp_path, prefix="b", ext=".png")
    out_dir = tmp_path / "out"
    out_dir.mkdir()
    (out_dir / "some_file.txt").write_text("x", encoding="utf-8")

    proc = _run_merge(a_dir=a, b_dir=b, out_dir=out_dir)
    assert proc.returncode != 0
    assert "nicht leer" in (proc.stdout + proc.stderr)


def test_merge_aborts_on_strict_pairing_mismatch(tmp_path: Path):
    a = _make_mini_trainable(tmp_path, prefix="a", ext=".jpg")
    b = _make_mini_trainable(tmp_path, prefix="b", ext=".png")
    # Extra label without image => strict pairing violation
    extra = a / "labels" / "extra.json"
    _write_label(extra, image_name="extra.jpg", w=10, h=10)

    out_dir = tmp_path / "out"
    proc = _run_merge(a_dir=a, b_dir=b, out_dir=out_dir)
    assert proc.returncode != 0
    assert "strict pairing" in (proc.stdout + proc.stderr)

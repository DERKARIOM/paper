import json
import subprocess
import sys
from pathlib import Path

from PIL import Image


def test_smoke_training_script_runs_on_tiny_temp_dataset(tmp_path: Path):
    # Mini dataset (2 samples) so that the smoke train entry point is at least executable.
    base = tmp_path / "ds"
    (base / "images").mkdir(parents=True)
    (base / "labels").mkdir(parents=True)

    # Image 1: 100x50, simple RGB
    img1 = Image.new("RGB", (100, 50), (10, 20, 30))
    img1_name = "a.jpg"
    img1_path = base / "images" / img1_name
    img1.save(img1_path)
    label1 = {
        "image": img1_name,
        "width": 100,
        "height": 50,
        "corners_px": [[10.0, 5.0], [90.0, 5.0], [90.0, 45.0], [10.0, 45.0]],
    }
    (base / "labels" / "a.json").write_text(json.dumps(label1), encoding="utf-8")

    # Image 2: 80x120
    img2 = Image.new("RGB", (80, 120), (40, 50, 60))
    img2_name = "b.jpg"
    img2_path = base / "images" / img2_name
    img2.save(img2_path)
    label2 = {
        "image": img2_name,
        "width": 80,
        "height": 120,
        "corners_px": [[5.0, 10.0], [75.0, 12.0], [74.0, 110.0], [6.0, 112.0]],
    }
    (base / "labels" / "b.json").write_text(json.dumps(label2), encoding="utf-8")

    (base / "split_train.txt").write_text(f"{img1_name}\n", encoding="utf-8")
    (base / "split_val.txt").write_text(f"{img2_name}\n", encoding="utf-8")

    out_dir = tmp_path / "out"
    script = Path("training/scripts/train_docquad_smoke.py")
    assert script.exists()

    res = subprocess.run(
        [
            sys.executable,
            str(script),
            "--base_dir",
            str(base),
            "--epochs",
            "1",
            "--batch",
            "1",
            "--device",
            "cpu",
            "--out_dir",
            str(out_dir),
        ],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    # Sanity: Script writes log + visualization.
    assert (out_dir / "metrics.jsonl").exists(), res.stdout
    assert (out_dir / "smoke_vis" / "epoch_01").exists(), res.stdout

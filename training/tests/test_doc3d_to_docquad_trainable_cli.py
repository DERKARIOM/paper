import subprocess
import sys
from pathlib import Path


def _script_path() -> Path:
    # repo-root is the working dir in our test runs
    p = Path(__file__).resolve().parents[2] / "training" / "scripts" / "doc3d_to_docquad_trainable.py"
    assert p.is_file(), f"missing script: {p}"
    return p


def test_help_works_without_processing() -> None:
    p = _script_path()
    r = subprocess.run([sys.executable, str(p), "--help"], capture_output=True, text=True)
    assert r.returncode == 0
    out = (r.stdout or "") + (r.stderr or "")
    # basic smoke: critical flags should be present
    assert "--mask_mode" in out
    assert "--prefer_openexr" in out


def test_missing_img_root_fails_fast_with_clear_error() -> None:
    p = _script_path()
    r = subprocess.run(
        [
            sys.executable,
            str(p),
            "--img_root",
            "__definitely_missing_img_root__",
            "--uv_root",
            "__definitely_missing_uv_root__",
            "--out_dir",
            "__tmp_out__",
        ],
        capture_output=True,
        text=True,
    )
    assert r.returncode != 0
    assert "img_root not found" in (r.stderr or "") or "img_root not found" in (r.stdout or "")

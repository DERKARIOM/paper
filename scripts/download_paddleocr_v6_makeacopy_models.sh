#!/usr/bin/env bash
# download_paddleocr_v6_makeacopy_models.sh
# Downloads PP-OCRv6 ONNX detection + recognition models from Hugging Face.
#
# Output:
#   paddle-v6-spike/models/
#   paddle-v6-spike/models/manifest.json
#   paddle-v6-spike/models/SHA256SUMS
#   paddle-v6-spike/models/SUMMARY.txt

set -euo pipefail

BASE_DIR="${1:-paddle-v6-spike/models}"
VENV_DIR="${2:-venv-paddle-v6}"

REPOS=(
  "PaddlePaddle/PP-OCRv6_tiny_det_onnx"
  "PaddlePaddle/PP-OCRv6_tiny_rec_onnx"
  "PaddlePaddle/PP-OCRv6_small_det_onnx"
  "PaddlePaddle/PP-OCRv6_small_rec_onnx"
  "PaddlePaddle/PP-OCRv6_medium_det_onnx"
  "PaddlePaddle/PP-OCRv6_medium_rec_onnx"
)

echo "Output dir: $BASE_DIR"
echo "Venv dir:   $VENV_DIR"
echo ""

mkdir -p "$BASE_DIR"

if [ ! -d "$VENV_DIR" ]; then
  python3 -m venv "$VENV_DIR"
fi

# shellcheck disable=SC1090
source "$VENV_DIR/bin/activate"

python -m pip install --upgrade pip
python -m pip install \
  "huggingface_hub>=0.23,<1.0"

TMP_PY="$(mktemp)"

REPOS_JSON="$(printf '%s\n' "${REPOS[@]}" | python -c 'import sys,json; print(json.dumps([l.strip() for l in sys.stdin if l.strip()]))')"

cat > "$TMP_PY" <<'PY'
from pathlib import Path
from huggingface_hub import snapshot_download
import hashlib
import json
import os
import shutil
import sys

repos = json.loads(os.environ["REPOS_JSON"])
out = Path(os.environ["BASE_DIR"]).expanduser().resolve()
out.mkdir(parents=True, exist_ok=True)

manifest = []

def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

for repo_id in repos:
    model_name = repo_id.split("/", 1)[1]
    dst = out / model_name

    print(f"\n=== Downloading {repo_id} ===", flush=True)

    if dst.exists():
        shutil.rmtree(dst)

    snapshot_path = snapshot_download(
        repo_id=repo_id,
        revision="main",
        local_dir=dst,
        ignore_patterns=[
            ".git/*",
            "*.msgpack",
            "*.h5",
            "*.safetensors",
            "*.pdparams",
            "*.pdmodel",
        ],
    )

    snapshot_path = Path(snapshot_path)

    files = []
    total_size = 0

    for p in sorted(dst.rglob("*")):
        if not p.is_file():
            continue

        rel = p.relative_to(out)
        size = p.stat().st_size
        total_size += size

        files.append({
            "path": str(rel),
            "size": size,
            "sha256": sha256(p),
        })

    onnx_files = [f for f in files if f["path"].endswith(".onnx")]

    manifest.append({
        "repo_id": repo_id,
        "model": model_name,
        "revision": "main",
        "copied_to": str(dst),
        "snapshot_path": str(snapshot_path),
        "total_size": total_size,
        "onnx_files": [f["path"] for f in onnx_files],
        "files": files,
    })

    if not onnx_files:
        print(f"WARNING: no .onnx file found in {repo_id}", file=sys.stderr)

manifest_path = out / "manifest.json"
manifest_path.write_text(
    json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
    encoding="utf-8",
)

sha_path = out / "SHA256SUMS"
with sha_path.open("w", encoding="utf-8") as f:
    for item in manifest:
        for file in item["files"]:
            f.write(f'{file["sha256"]}  {file["path"]}\n')

summary_path = out / "SUMMARY.txt"
with summary_path.open("w", encoding="utf-8") as f:
    for item in manifest:
        mib = item["total_size"] / 1024 / 1024
        f.write(f'{item["model"]}: {item["total_size"]} bytes ({mib:.2f} MiB)\n')
        for onnx in item["onnx_files"]:
            f.write(f'  ONNX: {onnx}\n')

print("\nDone.")
print(f"Manifest: {manifest_path}")
print(f"SHA256:   {sha_path}")
print(f"Summary:  {summary_path}")
PY

BASE_DIR="$BASE_DIR" REPOS_JSON="$REPOS_JSON" python "$TMP_PY"
rm -f "$TMP_PY"

echo ""
echo "Downloaded model directories:"
find "$BASE_DIR" -maxdepth 1 -type d -not -path "$BASE_DIR" -print | sort

echo ""
echo "Model size summary:"
cat "$BASE_DIR/SUMMARY.txt"

echo ""
echo "Total size:"
du -sh "$BASE_DIR"
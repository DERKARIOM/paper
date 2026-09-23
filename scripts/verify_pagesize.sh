#!/usr/bin/env bash
set -euo pipefail

# Verify that all native libraries in app/src/main/jniLibs have 16 KB max page size
# Usage: scripts/verify_pagesize.sh [path-to-jniLibs]

JNILIBS_DIR="${1:-$(cd "$(dirname "$0")/.." && pwd)/app/src/main/jniLibs}"

if [ ! -d "$JNILIBS_DIR" ]; then
  echo "ERROR: jniLibs directory not found: $JNILIBS_DIR" >&2
  exit 2
fi

# Prefer llvm-readobj from NDK if available
pick_readobj() {
  if [ -n "${ANDROID_NDK_HOME:-}" ]; then
    for host in linux-x86_64 darwin-x86_64 darwin-aarch64 darwin-arm64; do
      cand="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$host/bin/llvm-readobj"
      if [ -x "$cand" ]; then echo "$cand"; return 0; fi
    done
  fi
  command -v llvm-readobj 2>/dev/null || true
}

READOBJ_BIN="$(pick_readobj)"
READELF_BIN="$(command -v readelf || true)"

if [ -z "$READOBJ_BIN" ] && [ -z "$READELF_BIN" ]; then
  echo "ERROR: neither llvm-readobj nor readelf is available in PATH. Install binutils or use NDK llvm tools." >&2
  exit 2
fi

echo "Scanning .so files under: $JNILIBS_DIR"

FAIL=0
count=0
while IFS= read -r -d '' so; do
  count=$((count+1))
  PAGESIZE=""

  if [ -n "$READOBJ_BIN" ]; then
    # Extract only PT_LOAD segment alignments
    PAGESIZE=$("$READOBJ_BIN" --program-headers "$so" 2>/dev/null \
      | awk '/Type:/ {t=$2} /Alignment:/ {a=$2; if(t=="PT_LOAD") print a}' \
      | sort -u | tail -n1)
  fi

  if [ -z "$PAGESIZE" ] && [ -n "$READELF_BIN" ]; then
    # Extract only Align for LOAD segments
    PAGESIZE=$("$READELF_BIN" -l "$so" 2>/dev/null \
      | awk '/LOAD/ && match($0, /Align *([0-9A-Fx]+)/) {print substr($0, RSTART+6, RLENGTH-6)}' \
      | sort -u | tail -n1)
  fi

  # Normalize
  if [[ "$PAGESIZE" =~ ^0x ]]; then
    PAGESIZE_DEC=$((PAGESIZE))
  else
    PAGESIZE_DEC=${PAGESIZE:-0}
  fi

  printf "%-60s  PT_LOAD Align=%s\n" "$(basename "$so")" "${PAGESIZE:-unknown}"

  if [ "$PAGESIZE_DEC" -lt 16384 ]; then
    echo "  -> FAIL: PT_LOAD alignment < 16384" >&2
    FAIL=1
  fi

done < <(find "$JNILIBS_DIR" -type f -name '*.so' -print0 | sort -z)

if [ "$count" -eq 0 ]; then
  echo "WARN: no .so files found under $JNILIBS_DIR"
fi

if [ "$FAIL" -eq 0 ]; then
  echo "✅ All native libraries have PT_LOAD alignment >= 16384."
else
  echo "❌ Some libraries do not meet 16 KB page alignment. Rebuild with NDK >= 26 and -Wl,-z,max-page-size=16384." >&2
fi

exit "$FAIL"

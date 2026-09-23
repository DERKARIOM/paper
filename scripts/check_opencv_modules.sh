#!/bin/bash
# Usage: ./check_opencv_modules.sh [PFAD_ZUM_CODE] (optional)

CODE_DIR="${1:-.}"

echo "🔎 Scanning OpenCV module usage in: $CODE_DIR"
echo

# Liste bekannter OpenCV-Module
MODULES=(
  core imgproc imgcodecs video videoio
  calib3d features2d objdetect dnn gapi
  ml highgui photo stitching
)

FOUND=()

for module in "${MODULES[@]}"; do
  matches=$(grep -r -i -E "opencv.*\b$module\b" "$CODE_DIR" 2>/dev/null)
  if [[ -n "$matches" ]]; then
    echo "✅ Found OpenCV module used: $module"
    FOUND+=("$module")
  fi
done

if [[ ${#FOUND[@]} -eq 0 ]]; then
  echo "✅ No OpenCV modules found in codebase."
else
  echo
  echo "📦 Consider enabling only these in your CMake:"
  for m in "${FOUND[@]}"; do
    echo "  -D BUILD_opencv_$m=ON \\"
  done
  echo "Disable others to reduce APK size:"
  for m in "${MODULES[@]}"; do
    if [[ ! " ${FOUND[*]} " =~ " $m " ]]; then
      echo "  -D BUILD_opencv_$m=OFF \\"
    fi
  done
fi


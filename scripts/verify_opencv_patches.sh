#!/usr/bin/env bash
set -Eeuo pipefail

BASE="${1:-/tmp/opencv-src}"   # ggf. Pfad zu deinen gepatchten OpenCV-Sources übergeben
ok(){ printf "✅ %s\n" "$1"; }
ko(){ printf "❌ %s\n" "$1"; exit 1; }

echo "Checking OpenCV patches under: $BASE"

# Patch 1: ocv_output_status() deterministisch
UTIL="$BASE/cmake/OpenCVUtils.cmake"
[ -f "$UTIL" ] || ko "OpenCVUtils.cmake fehlt unter $UTIL"
grep -q 'function(ocv_output_status' "$UTIL" || ko "ocv_output_status() nicht gefunden"
grep -q 'OPENCV_BUILD_INFO_STR' "$UTIL" || ko "OPENCV_BUILD_INFO_STR wird nicht gesetzt"
grep -q '(reproducible build)' "$UTIL" && ok "Patch1: ocv_output_status() -> reproduzierbares Build-Info gesetzt" || ok "Patch1: Build-Info gesetzt (Text ohne '(reproducible build)')"

# Patch 2: internes Strip im JNI-CMake entfernt
JNI="$BASE/modules/java/jni/CMakeLists.txt"
[ -f "$JNI" ] || ko "JNI CMakeLists.txt fehlt unter $JNI"
if grep -q 'POST_BUILD' "$JNI"; then
  ko "Patch2: POST_BUILD-Strip von libopencv_java4.so ist NOCH vorhanden"
else
  ok "Patch2: POST_BUILD-Strip entfernt"
fi

# Patch 3: Gradle-AAR Target nicht per default bauen (ALL entfernt)
AAR="$BASE/modules/java/android_sdk/CMakeLists.txt"
[ -f "$AAR" ] || ko "android_sdk CMakeLists.txt fehlt unter $AAR"
if grep -Eq 'add_custom_target\(.+_android\s+ALL' "$AAR"; then
  ko "Patch3: 'ALL' hängt noch an add_custom_target(... _android)"
else
  ok "Patch3: 'ALL' bei add_custom_target(... _android) entfernt"
fi

# Sortierung A1/B2: alle file(GLOB ...) Ergebnisse werden sortiert
if grep -q 'list(SORT _result)' "$BASE/modules/java/CMakeLists.txt"; then
  ok "Sort: list(SORT _result) in modules/java/CMakeLists.txt"
else
  ko "Sort: list(SORT _result) fehlt in modules/java/CMakeLists.txt"
fi
if grep -q 'list(SORT _result)' "$JNI"; then
  ok "Sort: list(SORT _result) in modules/java/jni/CMakeLists.txt"
else
  ko "Sort: list(SORT _result) fehlt in modules/java/jni/CMakeLists.txt"
fi

# Sortierung B1: Module-Liste stabilisieren
if grep -q 'set(__mods ${OPENCV_MODULES_BUILD})' "$JNI" && \
   grep -q 'list(SORT __mods)' "$JNI" && \
   grep -q 'foreach(m ${__mods})' "$JNI" && \
   ! grep -q 'foreach(m ${OPENCV_MODULES_BUILD})' "$JNI"
then
  ok "Sort: OPENCV_MODULES_BUILD wird über __mods sortiert"
else
  ko "Sort: foreach(m \${OPENCV_MODULES_BUILD}) ist noch aktiv ODER __mods-Sort fehlt"
fi

# Sortierung A2 (kritisch): vor ocv_add_library(...) alle Listen sortieren – im JNI-CMake!
if grep -q 'foreach(v handwritten_h_sources' "$JNI"; then
  ok "Sort: Vor ocv_add_library(...) werden Quellen im JNI-CMake sortiert"
else
  echo "⚠️  Hinweis: keine A2-Sortier-Injektion im JNI-CMake gefunden."
  echo "    Prüfe, ob du versehentlich in modules/java/CMakeLists.txt injiziert hast:"
  if grep -q 'foreach(v handwritten_h_sources' "$BASE/modules/java/CMakeLists.txt"; then
    ko "A2 wurde im FALSCHEN File (modules/java/CMakeLists.txt) injiziert – bitte ins JNI-CMake verschieben"
  else
    ko "A2-Sortierblock fehlt komplett – bitte im JNI-CMake vor ocv_add_library(...) einfügen"
  fi
fi

ok "Alle Checks bestanden."

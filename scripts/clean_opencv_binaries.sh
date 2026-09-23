#!/bin/bash
# This script removes pre-compiled OpenCV binaries and native source files
# from the repository to ensure F-Droid compatibility and minimal Java-only wrapper.

set -e

echo "Cleaning OpenCV Java wrapper module for F-Droid compliance..."

# Remove native binaries
echo "Removing pre-compiled native libraries..."
find opencv -type f \( -name "*.so" -o -name "*.a" -o -name "*.dll" -o -name "*.dylib" -o -name "*.lib" \) -delete

# Remove build directories
echo "Removing build artifacts..."
rm -rf opencv/build opencv/.cxx opencv/native/bin

# Remove Makefiles and CMake files
echo "Removing build configuration files..."
find opencv -type f \( -name "*.cmake" -o -name "*.mk" -o -name "*.make" \) -delete

# Remove native source code files
echo "Removing native C++ source files..."
find opencv -type f \( -name "*.cpp" -o -name "*.hpp" -o -name "*.c" -o -name "*.h" \) -delete

# Recreate expected empty directory structure (to avoid Gradle issues)
echo "Creating empty JNI-related directories..."
for dir in \
    opencv/native/libs \
    opencv/native/3rdparty/libs \
    opencv/native/staticlibs \
    opencv/src/main/jniLibs \
    app/libs; do
    for arch in arm64-v8a armeabi-v7a x86 x86_64; do
        mkdir -p "$dir/$arch"
        touch "$dir/$arch/.gitkeep"
    done
done

# Preserve app/libs/.gitkeep if no arch is used
mkdir -p app/libs
touch app/libs/.gitkeep

echo "✅ OpenCV module cleaned. Only Java sources are retained. Ready for F-Droid."

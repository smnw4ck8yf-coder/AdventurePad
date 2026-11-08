#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 /absolute/dependency-root" >&2
    exit 2
fi
if [[ -z "${ANDROID_NDK_ROOT:-}" ]]; then
    echo "ANDROID_NDK_ROOT must point to Android NDK 23.2.8568313" >&2
    exit 2
fi

deps_root=$1
toolchain="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake"
oboe_revision=a81bb9f87d4105b84b682685d3bfbb5beca371d1
libpng_revision=cd952f49f95bb27154ae77dbb103032d95f6e580
flac_revision=1507800de4b70e21be71f38caa0d9079d0bc6e45

mkdir -p "$deps_root/src"

fetch_revision() {
    local repository=$1
    local revision=$2
    local destination=$3
    if [[ ! -d "$destination/.git" ]]; then
        git init "$destination"
        git -C "$destination" remote add origin "$repository"
    fi
    git -C "$destination" fetch --depth=1 origin "$revision"
    git -C "$destination" checkout --detach FETCH_HEAD
    test "$(git -C "$destination" rev-parse HEAD)" = "$revision"
}

fetch_revision https://github.com/google/oboe.git "$oboe_revision" "$deps_root/src/oboe"
fetch_revision https://github.com/pnggroup/libpng.git "$libpng_revision" "$deps_root/src/libpng"
fetch_revision https://github.com/xiph/flac.git "$flac_revision" "$deps_root/src/flac"

cmake -S "$deps_root/src/oboe" -B "$deps_root/oboe-build-arm64" \
    -DCMAKE_TOOLCHAIN_FILE="$toolchain" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-21 \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF \
    -DOBOE_BUILD_SAMPLES=OFF \
    -DOBOE_BUILD_TESTS=OFF
cmake --build "$deps_root/oboe-build-arm64" --parallel

cmake -S "$deps_root/src/libpng" -B "$deps_root/libpng-build-arm64" \
    -DCMAKE_TOOLCHAIN_FILE="$toolchain" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-21 \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$deps_root/libpng-prefix-arm64" \
    -DPNG_SHARED=OFF \
    -DPNG_STATIC=ON \
    -DPNG_TESTS=OFF \
    -DPNG_TOOLS=OFF
cmake --build "$deps_root/libpng-build-arm64" --parallel
cmake --install "$deps_root/libpng-build-arm64"

cmake -S "$deps_root/src/flac" -B "$deps_root/flac-build-arm64" \
    -DCMAKE_TOOLCHAIN_FILE="$toolchain" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-21 \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$deps_root/flac-prefix-arm64" \
    -DBUILD_SHARED_LIBS=OFF \
    -DBUILD_CXXLIBS=OFF \
    -DBUILD_PROGRAMS=OFF \
    -DBUILD_EXAMPLES=OFF \
    -DBUILD_TESTING=OFF \
    -DBUILD_DOCS=OFF \
    -DINSTALL_MANPAGES=OFF \
    -DWITH_OGG=OFF
cmake --build "$deps_root/flac-build-arm64" --parallel
cmake --install "$deps_root/flac-build-arm64"

printf '%s\n' \
    "oboe=$oboe_revision" \
    "libpng=$libpng_revision" \
    "flac=$flac_revision" \
    "ndk=23.2.8568313" > "$deps_root/DEPENDENCIES.lock"

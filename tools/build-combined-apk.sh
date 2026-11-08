#!/usr/bin/env bash
set -euo pipefail

variant=${1:-debug}
scummvm_root_input=${2:-"$(dirname "$0")/../../ScummVM-AdventurePad"}
scummvm_root=$(cd "$scummvm_root_input" && pwd)
repo_root=$(cd "$(dirname "$0")/.." && pwd)

case "$variant" in
    debug) gradle_task=assembleDebug ;;
    release) gradle_task=assembleRelease ;;
    *) echo "variant must be debug or release" >&2; exit 2 ;;
esac

cd "$repo_root"
./gradlew \
    -Padventurepad.bundledEngine=true \
    -Pscummvm.dir="$scummvm_root" \
    ":app:$gradle_task"

#!/usr/bin/env bash
set -euo pipefail

# The project has one native app flavor: standard. Removed the obsolete Termux tasks.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE=debug
TASK=assembleStandardDebug
if [[ "${1:-}" == "--release" || "${1:-}" == "-r" ]]; then
    MODE=release
    TASK=assembleStandardRelease
    : "${SAIF_RELEASE_STORE_FILE:?Provide a private release keystore path}"
    : "${SAIF_RELEASE_STORE_PASSWORD:?Provide the keystore password through the environment}"
    : "${SAIF_RELEASE_KEY_ALIAS:?Provide the release key alias}"
    : "${SAIF_RELEASE_KEY_PASSWORD:?Provide the release key password through the environment}"
fi
# Prevent overlapping packaging runs, without killing anyone else's build.
LOCK_DIR="$ROOT_DIR/.saif-build-lock"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    echo "A packaging run is active (or a stale .saif-build-lock remains)." >&2
    exit 1
fi
trap 'rmdir "$LOCK_DIR"' EXIT
bash "$ROOT_DIR/gradlew" -p "$ROOT_DIR" ":app:$TASK"
OUTPUT_DIR="$ROOT_DIR/.build-outputs/$MODE"
mkdir -p "$OUTPUT_DIR"
find "$ROOT_DIR/app/build/outputs/apk/standard/$MODE" -name '*.apk' -exec cp {} "$OUTPUT_DIR/" \;
echo "APK output: $OUTPUT_DIR"

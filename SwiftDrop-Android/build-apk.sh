#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle 8.13 is not installed. Please use GitHub Actions."
  exit 1
fi
if [ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]; then
  echo "Set ANDROID_HOME or ANDROID_SDK_ROOT to Android SDK (platform 36)."
  exit 1
fi
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"

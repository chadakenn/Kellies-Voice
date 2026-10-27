#!/usr/bin/env bash
set -euo pipefail

apk="${1:-app/build/outputs/apk/release/app-release.apk}"
if [[ ! -s "$apk" ]]; then
  echo "::error::APK is missing or empty: $apk"
  exit 1
fi
unzip -tq "$apk" >/dev/null
entries="$(unzip -Z1 "$apk")"
for required in \
  "lib/arm64-v8a/libmicrowakeword.so" \
  "lib/armeabi-v7a/libmicrowakeword.so" \
  "assets/wakeword/okay_nabu.tflite" \
  "assets/wakeword/okay_nabu.json"; do
  if ! grep -Fxq "$required" <<< "$entries"; then
    echo "::error::Missing required wake-word APK entry: $required"
    exit 1
  fi
  echo "Verified wake-word APK entry: $required"
done
echo "Wake-word JNI and model packaging verified."

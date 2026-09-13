#!/usr/bin/env bash
set -euo pipefail
android_dir="$(cd "$(dirname "$0")/.." && pwd)"
classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
sources="$android_dir/app/src/main/java/com/alexbatalov/fallout2ce"
javac --release 8 -encoding UTF-8 -d "$classes" \
  "$sources/IniDocument.java" "$sources/SettingsValidation.java" \
  "$sources/GameSession.java" "$android_dir/tests/SettingsTests.java"
java -cp "$classes" com.alexbatalov.fallout2ce.SettingsTests

#!/usr/bin/env bash
set -euo pipefail
android_dir="$(cd "$(dirname "$0")/.." && pwd)"
output="$(mktemp -d)"
trap 'rm -rf "$output"' EXIT
javac --release 8 -encoding UTF-8 -d "$output" \
  "$android_dir/app/src/main/java/com/alexbatalov/fallout2ce/CommandBindings.java" \
  "$android_dir/tests/CommandBindingsTests.java"
java -cp "$output" com.alexbatalov.fallout2ce.CommandBindingsTests
c++ -std=c++17 -Wall -Wextra -Werror "$android_dir/tests/game_clock_test.cc" -o "$output/game-clock-test"
"$output/game-clock-test"

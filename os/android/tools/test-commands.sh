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

c++ -std=c++17 -Wall -Wextra -Werror \
  "$android_dir/tests/fake_perks_test.cc" "$android_dir/../../src/sfall_fake_perks.cc" \
  -o "$output/fake-perks-test"
"$output/fake-perks-test"
c++ -std=c++17 -Wall -Wextra -Werror \
  "$android_dir/tests/virtual_files_test.cc" "$android_dir/../../src/sfall_filesystem.cc" \
  -o "$output/virtual-files-test"
"$output/virtual-files-test"

c++ -std=c++17 -Wall -Wextra -Werror \
  "$android_dir/tests/global_vars_test.cc" "$android_dir/../../src/sfall_global_vars.cc" \
  -o "$output/global-vars-test"
"$output/global-vars-test"

#!/usr/bin/env python3
"""Check HUD keycodes against the engine and the SDL sources used by the build."""
from pathlib import Path
import argparse
import re
root = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser()
parser.add_argument("--sdl-keyboard", type=Path)
args = parser.parse_args()
sources = [args.sdl_keyboard] if args.sdl_keyboard else list((root/"os/android/app/.cxx").rglob("SDL_androidkeyboard.c"))
if not sources:
    raise SystemExit("No fetched SDL Android keyboard source found")
dik = re.findall(r"SDL_SCANCODE_\w+", (root/"src/sfall_kb_helpers.cc").read_text().split("kDiks[DIK_MAP_COUNT] = {",1)[1].split("};",1)[0])
java = (root/"os/android/app/src/main/java/com/alexbatalov/fallout2ce/CommandBindings.java").read_text()
actual = [int(n) for n in re.findall(r"-?\d+", java.split("ANDROID_KEYS = {",1)[1].split("};",1)[0])]
for source in sources:
    android = re.findall(r"SDL_SCANCODE_\w+", source.read_text().split("Android_Keycodes[] = {",1)[1].split("};",1)[0])
    expected = [android.index(v) if v != "SDL_SCANCODE_UNKNOWN" and v in android else -1 for v in dik]
    assert len(expected) == 256 and actual == expected, source
print("All 256 HUD key mappings match the engine and fetched SDL sources.")

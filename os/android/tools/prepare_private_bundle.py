#!/usr/bin/env python3
"""Prepare private game assets outside the source repository; never uploads them."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

REPO = Path(__file__).resolve().parents[3]

def sha256(path):
    result = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            result.update(chunk)
    return result.hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("game_directory", type=Path)
    parser.add_argument("output_directory", type=Path)
    parser.add_argument("--native-apk", type=Path, required=True,
                        help="Previously verified APK whose unchanged native libraries will be reused.")
    args = parser.parse_args()
    game, output = args.game_directory.resolve(), args.output_directory.resolve()
    if output == REPO or REPO in output.parents or output == game or game in output.parents:
        parser.error("Use an output directory outside the source repository and game directory.")
    for name in ("master.dat", "critter.dat", "ce.dat", "fallout2.cfg"):
        if not (game / name).is_file():
            parser.error("Missing required game file: " + name)
    assets = output / "assets/bundled-game"
    files = []
    for source in sorted(game.rglob("*")):
        if not source.is_file():
            continue
        path = source.relative_to(game)
        lower = [part.lower() for part in path.parts]
        if any(part in ("savegame", "backup", "backups") or part.startswith(".") for part in lower):
            continue
        if source.suffix.lower() in (".exe", ".dll", ".so", ".dylib", ".log", ".bak"):
            continue
        if game not in source.resolve().parents:
            parser.error("File points outside the game directory: " + str(path))
        if any(part != part.lower() for part in path.parts):
            parser.error("Normalize game filenames to lowercase first: " + str(path))
        target = assets / "files" / path
        target.parent.mkdir(parents=True, exist_ok=True)
        digest = sha256(source)
        if not target.exists() or sha256(target) != digest:
            shutil.copy2(source, target)
        files.append({"path": path.as_posix(), "size": target.stat().st_size, "sha256": digest})
    manifest = {"format": 1, "private_build": True, "total_bytes": sum(f["size"] for f in files), "files": files}
    (assets / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    with zipfile.ZipFile(args.native_apk) as apk:
        count = 0
        for info in apk.infolist():
            path = Path(info.filename)
            if len(path.parts) == 3 and path.parts[0] == "lib" and path.suffix == ".so":
                target = output / "native" / path.relative_to("lib")
                target.parent.mkdir(parents=True, exist_ok=True)
                with apk.open(info) as source, target.open("wb") as destination:
                    shutil.copyfileobj(source, destination)
                count += 1
        if not count:
            parser.error("The APK contains no native libraries.")
    (output / "BUNDLE_INFO.json").write_text(json.dumps({
        "private_game_data": True, "file_count": len(files), "game_bytes": manifest["total_bytes"],
        "source_apk_sha256": sha256(args.native_apk), "native_libraries": count,
        "manifest_sha256": sha256(assets / "manifest.json"),
    }, indent=2) + "\n")
    print(f"Prepared {len(files)} game files, {manifest['total_bytes']} bytes, {count} native libraries.")
    print("Assets:", output / "assets")

if __name__ == "__main__":
    main()

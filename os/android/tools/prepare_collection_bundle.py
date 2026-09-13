#!/usr/bin/env python3
"""Build a private, deduplicated collection from prepared, lowercase game folders."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

REPO = Path(__file__).resolve().parents[3]
PROFILES = ("rpu", "sonora", "nevada")
EXCLUDED = {"savegame", "backup", "backups", "original", "readme"}
BINARY_SUFFIXES = {".exe", ".dll", ".so", ".dylib", ".log", ".bak", ".zip"}

def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            value.update(chunk)
    return value.hexdigest()

def copy_file(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        # APFS clones avoid another multi-gigabyte working copy on macOS.
        cloned = subprocess.run(["cp", "-c", str(source), str(target)],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if cloned.returncode:
            shutil.copyfile(source, target)

def build(games, output, versions, previous=None):
    output = output.resolve()
    if output == REPO or REPO in output.parents:
        raise ValueError("Private game assets must stay outside the source repository.")
    if any(output == root or root in output.parents for root in games.values()):
        raise ValueError("Output must be separate from all input game directories.")
    if output.exists():
        raise ValueError("Use a new output directory; do not mix previous pool contents.")
    output.mkdir(parents=True)
    pool = output / "assets/bundled-pool"
    pooled = {}
    reports = {}
    for profile in PROFILES:
        root = games[profile].resolve()
        for required in ("master.dat", "critter.dat", "ce.dat", "fallout2.cfg"):
            if not (root / required).is_file():
                raise ValueError(f"{profile}: missing {required}")
        before = {}
        if previous:
            old = previous / "assets/bundled-games" / profile / "manifest.json"
            if not old.exists() and profile == "rpu":
                old = previous / "assets/bundled-game/manifest.json"
            if old.exists():
                before = {f["path"]: f for f in json.loads(old.read_text())["files"]}
        files = []
        for source in sorted(root.rglob("*")):
            if not source.is_file():
                continue
            rel = source.relative_to(root)
            if any(part.startswith(".") or part.lower() in EXCLUDED for part in rel.parts):
                continue
            if source.suffix.lower() in BINARY_SUFFIXES:
                continue
            if source.is_symlink() or root not in source.resolve().parents:
                raise ValueError("Unsafe input: " + str(source))
            name = rel.as_posix()
            if name != name.lower() or "\t" in name or "\n" in name:
                raise ValueError("Normalize game filenames first: " + name)
            sha = digest(source)
            size = source.stat().st_size
            if sha not in pooled:
                copy_file(source, pool / sha)
                if digest(pool / sha) != sha:
                    raise ValueError("Pool copy failed validation: " + name)
                pooled[sha] = size
            entry = {"path": name, "size": size, "sha256": sha,
                     "seed": source.suffix.lower() in {".ini", ".cfg"}}
            if name in before:
                old = before[name]
                entry["previous_sha256"] = sorted(set(
                    old.get("previous_sha256", []) + [old["sha256"]]) - {sha})
            files.append(entry)
        manifest = {"format": 2, "private_build": True, "profile": profile,
                    "version": versions[profile], "files": files,
                    "total_bytes": sum(f["size"] for f in files)}
        path = output / "assets/bundled-games" / profile / "manifest.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(manifest, indent=2) + "\n")
        reports[profile] = {"version": versions[profile], "files": len(files),
                            "bytes": manifest["total_bytes"],
                            "manifest_sha256": digest(path)}
    report = {"private_game_data": True, "profiles": reports,
              "pool_files": len(pooled), "pool_bytes": sum(pooled.values())}
    (output / "BUNDLE_INFO.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for profile in PROFILES:
        parser.add_argument("--" + profile, type=Path, required=True)
        parser.add_argument("--" + profile + "-version", required=True)
    parser.add_argument("--previous-bundle", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build({p: getattr(args, p).resolve() for p in PROFILES}, args.output,
          {p: getattr(args, p + "_version") for p in PROFILES},
          args.previous_bundle)

if __name__ == "__main__":
    main()

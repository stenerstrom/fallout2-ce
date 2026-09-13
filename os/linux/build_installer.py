#!/usr/bin/env python3
"""Package locally supplied, verified game data; never upload this output to CI."""
import argparse
import gzip
import io
import json
from pathlib import Path
import tarfile
from collection import digest

HERE = Path(__file__).resolve().parent

def build(engine, assets, output, version, commit):
    repo = HERE.parents[1]
    if repo == output.resolve() or repo in output.resolve().parents:
        raise ValueError("Private installers must be stored outside the repository.")
    files = {name: HERE / name for name in ("collection.py", "launcher.py", "icon.svg", "README.md")}
    files["engine/fallout2-ce"] = engine / "fallout2-ce"
    files["engine/ce.dat"] = engine / "ce.dat"
    files["engine/BUILD_INFO.json"] = engine / "BUILD_INFO.json"
    files["engine-settings.json"] = repo / "os/android/app/src/main/assets/engine-settings.json"
    info = json.loads(files["engine/BUILD_INFO.json"].read_text())
    if info["engine_commit"] != commit:
        raise ValueError("Engine/source commit mismatch")
    for name, checksum in info["files"].items():
        if digest(engine / name) != checksum:
            raise ValueError("CI engine checksum mismatch: " + name)
    ce_hash = digest(files["engine/ce.dat"])
    for profile in ("rpu", "sonora", "nevada"):
        relative = "bundled-games/" + profile + "/manifest.json"
        manifest = json.loads((assets / relative).read_text())
        files["assets/" + relative] = assets / relative
        for entry in manifest["files"]:
            if entry["path"] == "ce.dat" and entry["sha256"] != ce_hash:
                raise ValueError("Game data and engine ce.dat do not match")
            if any(part.lower() in ("savegame", "backups", "backup") for part in entry["path"].split("/")) or "gl_test_" in entry["path"]:
                raise ValueError("Private test/save files cannot be bundled")
            files["assets/bundled-pool/" + entry["sha256"]] = assets / "bundled-pool" / entry["sha256"]
    checksums = {}
    for name, path in sorted(files.items()):
        checksums[name] = digest(path)
        if name.startswith("assets/bundled-pool/") and checksums[name] != path.name:
            raise ValueError("Corrupt game content: " + name)
    package = json.dumps({"version": version, "architecture": "x86_64", "engine_commit": commit,
                          "private_game_data": True, "files": checksums}, indent=2).encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    bootstrap = (HERE / "bootstrap.py").read_text()
    header = ("#!/bin/sh\nexec python3 - \"$0\" \"$@\" <<'WASTELAND_PY'\n" + bootstrap +
              "\nWASTELAND_PY\n__WASTELAND_PAYLOAD__\n").encode()
    with output.open("xb") as raw:
        raw.write(header)
        with gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=3, mtime=0) as compressed:
            with tarfile.open(fileobj=compressed, mode="w|") as tar:
                for name, path in sorted(files.items()):
                    entry = tarfile.TarInfo(name)
                    entry.size = path.stat().st_size
                    entry.mode = 0o755 if name in ("launcher.py", "engine/fallout2-ce") else 0o644
                    with path.open("rb") as stream:
                        tar.addfile(entry, stream)
                entry = tarfile.TarInfo("package.json")
                entry.size = len(package)
                entry.mode = 0o644
                tar.addfile(entry, io.BytesIO(package))
    output.chmod(0o755)
    report = {"installer": output.name, "sha256": digest(output), "bytes": output.stat().st_size,
              "files": len(files), "engine_commit": commit, "version": version}
    output.with_suffix(".json").write_text(json.dumps(report, indent=2) + "\n")
    output.with_suffix(".sha256").write_text(report["sha256"] + "  " + output.name + "\n")
    print(json.dumps(report, indent=2), flush=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--engine", type=Path, required=True)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--version", default="1.4.0-alpha.4-linux.1")
    parser.add_argument("--commit", required=True)
    a = parser.parse_args()
    build(a.engine, a.assets, a.output, a.version, a.commit)

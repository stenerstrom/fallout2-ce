#!/usr/bin/env python3
"""Local Wasteland Collection storage and launch operations (no GUI dependency)."""
import contextlib
import fcntl
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile
import time
import zipfile

PROFILES = {
    "rpu": ("Fallout 2", "Restoration Project", "Restored places, quests and companions."),
    "sonora": ("Sonora", "A new frontier", "The desert borderlands, including Dayglow."),
    "nevada": ("Nevada", "Before the legend", "Leave Vault 8 and discover an earlier wasteland."),
}
MARGIN = 64 * 1024 * 1024

def digest(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def safe_path(root, name):
    """Refuse traversal, symlink components and cross-profile paths."""
    if not isinstance(name, str) or not name or "\\" in name or any(ord(c) < 32 for c in name):
        raise ValueError("Invalid relative path")
    parts = name.split("/")
    if any(p in ("", ".", "..") for p in parts) or PurePosixPath(name).is_absolute():
        raise ValueError("Invalid relative path: " + name)
    path = Path(root)
    for part in parts:
        path = path / part
        if path.is_symlink():
            raise ValueError("Symbolic links are not supported in game storage: " + str(path))
    if Path(root).resolve() not in path.resolve().parents:
        raise ValueError("Path escapes game storage")
    return path

def atomic_write(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.is_symlink():
        raise ValueError("Cannot replace a symbolic link")
    fd, name = tempfile.mkstemp(prefix="." + path.name + ".", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)

def ini_get(text, section, key, default=""):
    current = ""
    result = default
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("[") and "]" in stripped:
            current = stripped[1:stripped.index("]")].strip().lower()
        elif current == section.lower() and "=" in stripped and not stripped.startswith((";", "#")):
            name, value = stripped.split("=", 1)
            if name.strip().lower() == key.lower():
                result = value.split(";", 1)[0].strip()
    return result

def ini_patch(text, changes):
    newline = "\r\n" if "\r\n" in text else "\n"
    lines = text.splitlines()
    for section, key, value in changes:
        value = str(value)
        if any(c in value + section + key for c in "\r\n\0") or len(value) > 2048:
            raise ValueError("Invalid configuration value")
        current = ""
        found = False
        insertion = None
        for i, line in enumerate(lines):
            stripped = line.strip()
            if stripped.startswith("[") and "]" in stripped:
                if current == section.lower():
                    insertion = i
                current = stripped[1:stripped.index("]")].strip().lower()
                if current == section.lower():
                    insertion = len(lines)
            elif current == section.lower() and "=" in stripped and not stripped.startswith((";", "#")):
                name, old = stripped.split("=", 1)
                if name.strip().lower() == key.lower():
                    comment = " ;" + old.split(";", 1)[1] if ";" in old else ""
                    lines[i] = line[:len(line) - len(line.lstrip())] + name.strip() + "=" + value + comment
                    found = True
        if not found:
            if insertion is None:
                lines.extend(["", "[" + section + "]", key + "=" + value])
            else:
                lines.insert(insertion, key + "=" + value)
    return newline.join(lines) + newline

def validate_value(field, value):
    value = str(value)
    if field["type"] == "bool":
        if value not in ("0", "1"):
            raise ValueError(field["label"] + ": choose On or Off.")
    elif field["type"] in ("integer", "float", "double"):
        try:
            number = int(value) if field["type"] == "integer" else float(value)
        except ValueError:
            raise ValueError(field["label"] + ": enter a number.")
        if not math.isfinite(number) or number < field.get("min", -math.inf) or number > field.get("max", math.inf):
            raise ValueError(field["label"] + ": value is out of range.")
    elif "\n" in value or "\r" in value or "\0" in value:
        raise ValueError("Use one line for each setting.")
    return value

class BusyError(RuntimeError):
    pass

class Library:
    def __init__(self, bundle, home=None):
        self.bundle = Path(bundle).resolve()
        self.home = Path(home or os.environ.get("WASTELAND_DATA_DIR") or
                         Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local/share")) / "wasteland-collection").absolute()
        self.home.mkdir(parents=True, exist_ok=True)
        self.home = self.home.resolve()
        self.package = json.loads((self.bundle / "package.json").read_text())

    @contextlib.contextmanager
    def session(self):
        lock = safe_path(self.home, ".session.lock")
        with lock.open("a") as stream:
            try:
                fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise BusyError("A game or another collection operation is already running.")
            try:
                yield
            finally:
                fcntl.flock(stream, fcntl.LOCK_UN)

    def profile_dir(self, profile):
        if profile not in PROFILES:
            raise ValueError("Unknown game")
        return safe_path(self.home, "profiles/" + profile)

    def manifest(self, profile):
        self.profile_dir(profile)
        path = self.bundle / "assets/bundled-games" / profile / "manifest.json"
        manifest = json.loads(path.read_text())
        if manifest["profile"] != profile or manifest["format"] != 2:
            raise ValueError("Invalid game manifest")
        seen = set()
        for entry in manifest["files"]:
            name = entry["path"]
            safe_path(self.profile_dir(profile), name)
            if name in seen or name != name.lower() or not re.fullmatch(r"[a-f0-9]{64}", entry["sha256"]):
                raise ValueError("Invalid or duplicate game file")
            if not isinstance(entry["size"], int) or not 0 <= entry["size"] <= 8 * 1024**3:
                raise ValueError("Invalid game file size")
            seen.add(name)
        return manifest, digest(path)

    def ready(self, profile):
        record = self.profile_dir(profile) / ".content.json"
        try:
            return json.loads(record.read_text())["manifest"] == self.manifest(profile)[1]
        except (OSError, ValueError, KeyError):
            return False

    def prepare(self, profile, progress=lambda done, total: None):
        """Caller holds session lock. Verify every changed file before activation."""
        root = self.profile_dir(profile)
        root.mkdir(parents=True, exist_ok=True)
        manifest, manifest_hash = self.manifest(profile)
        record = safe_path(root, ".content.json")
        previous = json.loads(record.read_text()) if record.exists() else {}
        if previous.get("manifest") == manifest_hash:
            return root
        before = {e["path"]: e for e in previous.get("files", [])}
        work = safe_path(root, ".content-update/" + manifest_hash)
        work.mkdir(parents=True, exist_ok=True)
        changed = []
        needed = 0
        for entry in manifest["files"]:
            target = safe_path(root, entry["path"])
            if target.exists():
                if not target.is_file():
                    raise ValueError("A game file is a directory: " + entry["path"])
                if entry.get("seed"):
                    continue
                actual = digest(target)
                if actual == entry["sha256"]:
                    continue
                allowed = entry.get("previous_sha256", []) + ([before[entry["path"]]["sha256"]] if entry["path"] in before else [])
                if actual not in allowed:
                    raise ValueError("This game file was changed outside the launcher: " + entry["path"])
            stage = safe_path(work, entry["path"])
            valid = stage.is_file() and stage.stat().st_size == entry["size"] and digest(stage) == entry["sha256"]
            if not valid:
                needed += entry["size"]
            changed.append((entry, target, stage, valid))
        if shutil.disk_usage(root).free < needed + MARGIN:
            raise OSError("More free space is needed to prepare this game.")
        total = sum(entry["size"] for entry, *_ in changed)
        done = 0
        for entry, target, stage, valid in changed:
            if not valid:
                source = self.bundle / "assets/bundled-pool" / entry["sha256"]
                stage.parent.mkdir(parents=True, exist_ok=True)
                partial = safe_path(work, entry["path"] + ".partial")
                h = hashlib.sha256()
                with source.open("rb") as inp, partial.open("wb") as out:
                    for block in iter(lambda: inp.read(1024 * 1024), b""):
                        out.write(block)
                        h.update(block)
                        progress(done + out.tell(), max(total, 1))
                    out.flush()
                    os.fsync(out.fileno())
                if partial.stat().st_size != entry["size"] or h.hexdigest() != entry["sha256"]:
                    raise ValueError("Game content failed verification: " + entry["path"])
                os.replace(partial, stage)
            done += entry["size"]
            progress(done, max(total, 1))
        # Resume activation by matching new and prior file hashes on the next run.
        for entry, target, stage, valid in changed:
            target.parent.mkdir(parents=True, exist_ok=True)
            if entry["path"] == "fallout2.cfg" and not target.exists():
                data = stage.read_bytes().decode("latin-1")
                changes = [("screen", "resolution_x", "1024"), ("screen", "resolution_y", "768"),
                           ("screen", "windowed", "1"), ("screen", "scale", "1"), ("screen", "mouse_lock", "0")]
                atomic_write(stage, ini_patch(data, changes).encode("latin-1"))
            os.replace(stage, target)
        atomic_write(record, json.dumps({"manifest": manifest_hash, "files": manifest["files"]}).encode())
        shutil.rmtree(work)
        return root

    def settings(self, profile, filename="fallout2.cfg"):
        path = safe_path(self.profile_dir(profile), filename)
        return path.read_bytes().decode("latin-1") if path.exists() else ""

    def save_settings(self, profile, changes, filename="fallout2.cfg"):
        if filename not in ("fallout2.cfg", "ddraw.ini", "mods/upu.ini"):
            raise ValueError("Unsupported settings file")
        with self.session():
            self.prepare(profile)
            path = safe_path(self.profile_dir(profile), filename)
            old = path.read_bytes() if path.exists() else b""
            updated = ini_patch(old.decode("latin-1"), changes)
            if filename == "fallout2.cfg":
                x, y, scale = (int(ini_get(updated, "screen", key, default)) for key, default in
                               (("resolution_x", "1024"), ("resolution_y", "768"), ("scale", "1")))
                if scale < 1 or scale > 4 or x // scale < 640 or y // scale < 480:
                    raise ValueError("Choose a resolution that leaves at least 640 × 480 pixels after scaling.")
            if old:
                atomic_write(safe_path(self.profile_dir(profile), filename + ".launcher-backup"), old)
            atomic_write(path, updated.encode("latin-1"))

    def play(self, profile, progress=lambda done, total: None):
        with self.session():
            root = self.prepare(profile, progress)
            log_dir = safe_path(self.home, "logs")
            log_dir.mkdir(exist_ok=True)
            with (log_dir / (profile + ".log")).open("ab") as output:
                result = subprocess.run([str(self.bundle / "engine/fallout2-ce")], cwd=root,
                                        stdout=output, stderr=subprocess.STDOUT)
            if result.returncode:
                raise RuntimeError("The game exited with an error. See " + str(log_dir / (profile + ".log")))

    def save_directory(self, profile):
        root = self.profile_dir(profile)
        patches = ini_get(self.settings(profile), "system", "master_patches", "data").replace("\\\\", "/")
        relative = (patches.rstrip("/") + "/" if patches not in ("", ".") else "") + "SAVEGAME"
        safe_path(root, relative)
        current = root
        for part in relative.split("/"):
            matches = [p for p in current.iterdir() if p.name.lower() == part.lower()] if current.is_dir() else []
            if len(matches) > 1:
                raise ValueError("Ambiguous save directory capitalization")
            current = matches[0] if matches else current / part
            if current.is_symlink():
                raise ValueError("A save path is a symbolic link")
        return current

    def export_saves(self, profile):
        with self.session():
            source = self.save_directory(profile)
            if not source.is_dir():
                raise ValueError("This game has no saved games yet.")
            out = safe_path(self.home, "backups")
            out.mkdir(exist_ok=True)
            target = out / (profile + "-" + time.strftime("%Y%m%d-%H%M%S") + "-" + str(time.time_ns()) + ".zip")
            with zipfile.ZipFile(target, "x", zipfile.ZIP_DEFLATED) as archive:
                for item in sorted(source.rglob("*")):
                    if item.is_symlink():
                        raise ValueError("A save path is a symbolic link")
                    if item.is_file():
                        archive.write(item, "SAVEGAME/" + item.relative_to(source).as_posix())
            return target

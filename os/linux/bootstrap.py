"""Embedded in the self-extracting installer; Python standard library only."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile

MARKER = b"__WASTELAND_PAYLOAD__\n"

def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def atomic(path, text):
    temp = path.with_name(path.name + ".new")
    if temp.is_symlink():
        raise ValueError("Unexpected symbolic link")
    temp.write_text(text)
    os.replace(temp, path)

def desktop_quote(value):
    value = str(value).replace("%", "%%")
    escaped = "".join(chr(92) + ch if ch in (chr(92), chr(34), chr(96), "$") else ch for ch in value)
    return '"' + escaped.replace(chr(92), chr(92) * 2) + '"'

def install(archive_path, destination, *, extract_only=False, desktop=True, launch=True, dependencies=True):
    destination = Path(destination).absolute()
    destination.mkdir(parents=True, exist_ok=True)
    destination = destination.resolve()
    if not extract_only:
        if platform.system() != "Linux" or platform.machine() not in ("x86_64", "AMD64"):
            raise RuntimeError("This installer is for Intel/AMD x86_64 Linux.")
        if dependencies:
            check = subprocess.run([sys.executable, "-c",
                "import gi; gi.require_version('Gtk','4.0'); from gi.repository import Gtk; import ctypes; ctypes.CDLL('libSDL2-2.0.so.0')"],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if check.returncode:
                command = ["sudo", "pacman", "-S", "--needed", "python", "python-gobject", "gtk4", "sdl2-compat", "zlib"]
                print("Required desktop/game packages are missing.\n" + " ".join(command), flush=True)
                if not shutil.which("pacman") or input("Install these packages now? [y/N] ").strip().lower() != "y":
                    raise RuntimeError("Install the listed dependencies, then run this installer again.")
                subprocess.run(command, check=True)
    lock_path = destination / ".session.lock"
    if lock_path.is_symlink():
        raise ValueError("Unexpected session lock")
    with lock_path.open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError("Close the running game or launcher operation before updating.")
        staging = Path(tempfile.mkdtemp(prefix=".install-", dir=destination))
        try:
            with open(archive_path, "rb") as raw:
                while True:
                    line = raw.readline()
                    if not line:
                        raise ValueError("Installer payload is missing")
                    if line == MARKER:
                        break
                hashes = {}
                with tarfile.open(fileobj=raw, mode="r|gz") as tar:
                    for entry in tar:
                        parts = entry.name.split("/")
                        if any(p in ("", ".", "..") for p in parts) or PurePosixPath(entry.name).is_absolute() or "\\" in entry.name:
                            raise ValueError("Invalid installer path")
                        if not entry.isfile() or entry.name in hashes:
                            raise ValueError("Unexpected installer member")
                        target = staging.joinpath(*parts)
                        target.parent.mkdir(parents=True, exist_ok=True)
                        h = hashlib.sha256()
                        with tar.extractfile(entry) as inp, target.open("xb") as out:
                            for block in iter(lambda: inp.read(1024 * 1024), b""):
                                out.write(block)
                                h.update(block)
                        target.chmod(0o755 if entry.mode & 0o111 else 0o644)
                        hashes[entry.name] = h.hexdigest()
            package = json.loads((staging / "package.json").read_text())
            expected = package["files"]
            if set(hashes) != set(expected) | {"package.json"} or any(hashes[name] != sha for name, sha in expected.items()):
                raise ValueError("Installer content failed SHA-256 verification")
            if package["architecture"] != "x86_64":
                raise ValueError("Unexpected package architecture")
            release_id = package["version"] + "-" + hashes["package.json"][:12]
            if any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_" for c in release_id):
                raise ValueError("Invalid release name")
            releases = destination / "releases"
            if releases.is_symlink():
                raise ValueError("Unexpected releases link")
            releases.mkdir(exist_ok=True)
            release = releases / release_id
            if release.exists() or release.is_symlink():
                if release.is_symlink() or any(not (release / name).is_file() or (release / name).is_symlink() or
                    digest(release / name) != sha for name, sha in hashes.items()):
                    raise ValueError("Existing release differs; it was not overwritten")
                shutil.rmtree(staging)
            else:
                os.rename(staging, release)
            current = destination / "current"
            new_link = destination / ".current-new"
            if new_link.exists() or new_link.is_symlink():
                new_link.unlink()
            new_link.symlink_to(Path("releases") / release_id)
            os.replace(new_link, current)
            if desktop and not extract_only:
                data_home = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local/share"))
                menu = data_home / "applications"
                menu.mkdir(parents=True, exist_ok=True)
                entry = "\n".join(["[Desktop Entry]", "Type=Application", "Version=1.0",
                    "Name=Wasteland Collection", "Comment=Fallout 2 · Sonora · Nevada",
                    "Exec=" + " ".join([desktop_quote(sys.executable), desktop_quote(current / "launcher.py"), "--data-dir", desktop_quote(destination)]),
                    "Icon=" + str(current / "icon.svg"), "Terminal=false", "Categories=Game;RolePlaying;",
                    "StartupNotify=true", "StartupWMClass=io.github.stenerstrom.WastelandCollection", ""])
                atomic(menu / "wasteland-collection.desktop", entry)
                if shutil.which("update-desktop-database"):
                    subprocess.run(["update-desktop-database", str(menu)], check=False)
            print("Installed Wasteland Collection in " + str(destination), flush=True)
            if not extract_only:
                print("Open Wasteland Collection from the application menu (Super + Space on Omarchy).", flush=True)
        except BaseException:
            shutil.rmtree(staging, ignore_errors=True)
            raise
    if launch and not extract_only:
        subprocess.Popen([sys.executable, str(destination / "current/launcher.py"), "--data-dir", str(destination)],
                         start_new_session=True)
    return release

def main():
    parser = argparse.ArgumentParser(description="Install Wasteland Collection for Omarchy / Arch Linux.")
    parser.add_argument("installer")
    parser.add_argument("--destination", type=Path, default=Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local/share")) / "wasteland-collection")
    parser.add_argument("--extract-only", action="store_true")
    parser.add_argument("--no-launch", action="store_true")
    args = parser.parse_args()
    try:
        install(args.installer, args.destination, extract_only=args.extract_only, launch=not args.no_launch)
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError, tarfile.TarError) as error:
        print("Installation stopped: " + str(error), file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()

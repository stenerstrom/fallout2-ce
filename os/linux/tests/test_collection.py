import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from collection import Library, BusyError, MARGIN, atomic_write, ini_get, ini_patch, safe_path, validate_value
from bootstrap import install

def fixture(root):
    bundle = root / "bundle"
    bundle.mkdir()
    (bundle / "package.json").write_text(json.dumps({"version":"test"}))
    (bundle / "engine").mkdir()
    engine = bundle / "engine/fallout2-ce"
    engine.write_text("#!/bin/sh\nprintf '%s' \"$PWD\" > launched-here.txt\n")
    engine.chmod(0o755)
    schema = Path(__file__).resolve().parents[2] / "android/app/src/main/assets/engine-settings.json"
    shutil.copyfile(schema, bundle / "engine-settings.json")
    for profile in ("rpu", "sonora", "nevada"):
        put_manifest(bundle, profile, {"master.dat": b"original", "fallout2.cfg": b"; preserved\n[screen]\nresolution_x=800\nresolution_y=600\nscale=1\nwindowed=0\n"})
    return bundle

def put_manifest(bundle, profile, files):
    entries = []
    for name, data in files.items():
        sha = hashlib.sha256(data).hexdigest()
        pool = bundle / "assets/bundled-pool" / sha
        pool.parent.mkdir(parents=True, exist_ok=True)
        pool.write_bytes(data)
        entries.append({"path":name,"size":len(data),"sha256":sha,"seed":name.endswith((".ini",".cfg"))})
    path = bundle / "assets/bundled-games" / profile / "manifest.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"format":2,"profile":profile,"files":entries}))
    return entries

class CollectionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.bundle = fixture(self.root)
        self.lib = Library(self.bundle, self.root / "data")

    def tearDown(self):
        self.temp.cleanup()

    def test_prepare_profiles_and_pc_defaults(self):
        with self.lib.session():
            first = self.lib.prepare("rpu")
            second = self.lib.prepare("sonora")
        self.assertNotEqual(first, second)
        self.assertTrue(self.lib.ready("rpu"))
        text = self.lib.settings("rpu")
        self.assertEqual(ini_get(text,"screen","resolution_x"),"1024")
        self.assertEqual(ini_get(text,"screen","windowed"),"1")
        self.assertIn("; preserved",text)

    def test_resume_uses_verified_stage_with_only_margin_free(self):
        put_manifest(self.bundle,"rpu",{"master.dat":b"ready"})
        manifest_hash = self.lib.manifest("rpu")[1]
        stage = self.lib.profile_dir("rpu") / ".content-update" / manifest_hash / "master.dat"
        stage.parent.mkdir(parents=True)
        stage.write_bytes(b"ready")
        space = shutil.disk_usage(self.root)._replace(free=MARGIN)
        with patch("collection.shutil.disk_usage",return_value=space):
            with self.lib.session():
                self.lib.prepare("rpu")
        self.assertEqual((self.lib.profile_dir("rpu")/"master.dat").read_bytes(),b"ready")

    def test_corruption_never_activates(self):
        entries = put_manifest(self.bundle,"rpu",{"master.dat":b"wanted"})
        (self.bundle/"assets/bundled-pool"/entries[0]["sha256"]).write_bytes(b"broken")
        with self.assertRaises(ValueError), self.lib.session():
            self.lib.prepare("rpu")
        self.assertFalse((self.lib.profile_dir("rpu")/"master.dat").exists())
        self.assertFalse(self.lib.ready("rpu"))

    def test_interrupted_activation_resumes_and_keeps_settings_saves(self):
        with self.lib.session():
            root = self.lib.prepare("rpu")
        (root/"fallout2.cfg").write_text("[screen]\nresolution_x=1920\nresolution_y=1080\nscale=1\n")
        save = root/"data/savegame/slot01/save.dat"
        save.parent.mkdir(parents=True)
        save.write_bytes(b"keep save")
        put_manifest(self.bundle,"rpu",{"master.dat":b"updated","extra.dat":b"new","fallout2.cfg":b"seed"})
        real = os.replace
        def fail(source,target):
            if str(target).endswith("extra.dat"):
                raise OSError("interrupted")
            return real(source,target)
        with patch("collection.os.replace",side_effect=fail), self.assertRaises(OSError), self.lib.session():
            self.lib.prepare("rpu")
        self.assertFalse(self.lib.ready("rpu"))
        with self.lib.session():
            self.lib.prepare("rpu")
        self.assertTrue(self.lib.ready("rpu"))
        self.assertEqual(save.read_bytes(),b"keep save")
        self.assertIn("1920",self.lib.settings("rpu"))

    def test_modified_assets_are_preserved(self):
        with self.lib.session():
            root = self.lib.prepare("rpu")
        (root/"master.dat").write_bytes(b"custom")
        put_manifest(self.bundle,"rpu",{"master.dat":b"updated"})
        with self.assertRaises(ValueError),self.lib.session():
            self.lib.prepare("rpu")
        self.assertEqual((root/"master.dat").read_bytes(),b"custom")

    def test_lock_blocks_another_instance(self):
        other = Library(self.bundle,self.lib.home)
        with self.lib.session():
            with self.assertRaises(BusyError),other.session():
                pass
        with other.session():
            pass

    def test_game_cwd_and_profile_isolation(self):
        self.lib.play("nevada")
        root = self.lib.profile_dir("nevada")
        self.assertEqual((root/"launched-here.txt").read_text(),str(root))
        self.assertFalse(self.lib.profile_dir("rpu").exists())

    def test_atomic_write_failure_keeps_original(self):
        path = self.root/"config.ini"
        path.write_bytes(b"original")
        with patch("collection.os.replace",side_effect=OSError("disk full")),self.assertRaises(OSError):
            atomic_write(path,b"replacement")
        self.assertEqual(path.read_bytes(),b"original")
        self.assertEqual(list(self.root.glob(".config.ini.*")),[])

    def test_settings_validation_and_comments(self):
        text = "; comment\r\n[SCREEN]\r\nscale=1 ; keep\r\n[other]\r\nx=2\r\n"
        patched = ini_patch(text,[("screen","scale","2"),("screen","resolution_x","1280")])
        self.assertIn("; keep",patched)
        self.assertIn("\r\n",patched)
        self.assertEqual(ini_get(patched,"screen","resolution_x"),"1280")
        with self.assertRaises(ValueError):
            self.lib.save_settings("rpu",[("screen","scale","4")])
        self.lib.save_settings("rpu",[("screen","resolution_x","1280"),("screen","resolution_y","960"),("screen","scale","2")])
        self.assertEqual(ini_get(self.lib.settings("rpu"),"screen","scale"),"2")
        with self.assertRaises(ValueError):
            validate_value({"type":"float","label":"Speed"},"nan")

    def test_paths_and_symlinks(self):
        for path in ("../outside","/absolute","a//b","a/./b","a\\b","x\nb"):
            with self.assertRaises(ValueError):
                safe_path(self.root,path)
        (self.root/"redirect").symlink_to(self.root.parent)
        with self.assertRaises(ValueError):
            safe_path(self.root,"redirect/outside")
        with self.assertRaises(ValueError):
            self.lib.profile_dir("../outside")

    def test_save_export_isolated(self):
        for profile in ("rpu","sonora"):
            path = self.lib.profile_dir(profile)/"data/savegame/slot01/save.dat"
            path.parent.mkdir(parents=True)
            path.write_bytes(profile.encode())
        result = self.lib.export_saves("rpu")
        import zipfile
        with zipfile.ZipFile(result) as archive:
            self.assertEqual(archive.read("SAVEGAME/slot01/save.dat"),b"rpu")

def small_installer(path, data=b"engine", malicious=None):
    raw_files = {"engine/fallout2-ce":data,"launcher.py":b"print('fixture')\n","icon.svg":b"<svg/>"}
    package = {"version":"test-1","architecture":"x86_64","files":{n:hashlib.sha256(v).hexdigest() for n,v in raw_files.items()}}
    raw_files["package.json"] = json.dumps(package).encode()
    if malicious:
        raw_files[malicious] = b"bad"
    with path.open("wb") as raw:
        raw.write(b"#!/bin/sh\n__WASTELAND_PAYLOAD__\n")
        with tarfile.open(fileobj=raw,mode="w|gz") as tar:
            for name,data in raw_files.items():
                entry=tarfile.TarInfo(name);entry.size=len(data)
                tar.addfile(entry,io.BytesIO(data))

class InstallerTests(unittest.TestCase):
    def test_install_upgrade_keeps_profiles_and_validates_archive(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);archive=root/"package.run";dest=root/"data"
            small_installer(archive)
            first=install(archive,dest,extract_only=True,launch=False)
            save=dest/"profiles/rpu/data/savegame/slot01/save.dat";save.parent.mkdir(parents=True);save.write_bytes(b"keep")
            self.assertEqual((dest/"current").resolve(),first)
            install(archive,dest,extract_only=True,launch=False)
            small_installer(archive,b"new engine")
            second=install(archive,dest,extract_only=True,launch=False)
            self.assertNotEqual(first,second)
            self.assertEqual(save.read_bytes(),b"keep")
            small_installer(archive,malicious="../outside")
            with self.assertRaises(ValueError):
                install(archive,dest,extract_only=True,launch=False)
            self.assertEqual((dest/"current").resolve(),second)
            self.assertFalse((root/"outside").exists())

    def test_truncated_install_preserves_current(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);archive=root/"package.run";dest=root/"data"
            small_installer(archive)
            install(archive,dest,extract_only=True,launch=False)
            current=(dest/"current").resolve()
            archive.write_bytes(archive.read_bytes()[:100])
            with self.assertRaises((tarfile.TarError,EOFError,ValueError)):
                install(archive,dest,extract_only=True,launch=False)
            self.assertEqual((dest/"current").resolve(),current)

if __name__ == "__main__":
    unittest.main()

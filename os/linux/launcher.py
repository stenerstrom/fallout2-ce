#!/usr/bin/env python3
"""English GTK 4 game library for Omarchy/Arch and other Linux desktops."""
import argparse
import json
from pathlib import Path
import sys
import tempfile
import threading
import time
import gi
gi.require_version("Gtk", "4.0")
from gi.repository import Gdk, Gio, GLib, Gtk
from collection import Library, PROFILES, ini_get, validate_value

CSS = b"""
window { background: #141917; color: #ece8dc; }
headerbar { background: #141917; border-bottom: 1px solid #313a34; }
label.eyebrow { color: #aca78f; font-size: 11px; letter-spacing: 3px; }
label.title { font-size: 36px; font-weight: 700; }
label.subtitle { color: #c7b783; font-size: 17px; }
label.muted { color: #a6aea5; }
label.card-title { font-size: 23px; font-weight: 700; }
label.number { font-size: 56px; font-weight: 800; color: #d1c195; }
button { border-radius: 8px; padding: 10px 18px; }
button.card { background: #202820; border: 1px solid #3b453b; padding: 20px; }
button.card.selected { background: #30382a; border: 2px solid #c7b783; }
button.sonora { background: #302820; }
button.nevada { background: #1c2c2b; }
button.primary { background: #c7b783; color: #182018; font-weight: 700; padding: 13px 30px; }
button.primary:disabled { background: #616454; color: #abb0a2; }
box.detail { background: #1c221e; border: 1px solid #313a34; border-radius: 14px; padding: 24px; }
entry, dropdown { min-height: 30px; }
progressbar trough { background: #303b32; }
progressbar progress { background: #c7b783; }
"""

def label(text, css=None, wrap=False):
    widget = Gtk.Label(label=text, xalign=0, wrap=wrap)
    if css:
        widget.add_css_class(css)
    return widget

def box(spacing=12, vertical=True):
    return Gtk.Box(orientation=Gtk.Orientation.VERTICAL if vertical else Gtk.Orientation.HORIZONTAL, spacing=spacing)

def margins(widget, size):
    for name in ("top", "bottom", "start", "end"):
        getattr(widget, "set_margin_" + name)(size)

def button(text, callback, css=None):
    widget = Gtk.Button(label=text)
    widget.connect("clicked", lambda *_: callback())
    if css:
        widget.add_css_class(css)
    return widget

def rpu_sections():
    def choices(key, title, options, values=None):
        return {"key": key, "label": title, "type": "integer", "default": "0",
                "choices": options, "values": values or [str(i) for i in range(len(options))]}
    return [
        {"section": "Misc", "file": "ddraw.ini", "title": "RPU · Appearance & engine", "fields": [
            choices("EnableHeroAppearanceMod", "Hero Appearance", ["Off", "On"]),
            choices("UseFileSystemOverride", "Virtual file support (walking / Goris)", ["Off", "On"]),
            choices("DamageFormula", "Ammo damage rules", ["Default", "Glovz", "YAAM"], ["0", "1", "5"])]},
        {"section": "RPU", "file": "ddraw.ini", "title": "RPU · Artwork", "fields": [
            choices("AlternativeExplosions", "Explosion artwork", ["Original", "Alternative"])]},
        {"section": "main", "file": "mods/upu.ini", "title": "RPU · Gameplay", "fields": [
            choices("critters_walk_faster", "Faster walking animations", ["Off", "On"]),
            choices("goris_derobing_speed", "Goris robe animation", ["Off", "16 FPS", "25 FPS", "50 FPS", "75 FPS", "100 FPS"],
                    ["0", "16", "25", "50", "75", "100"]),
            choices("wipe_inventory", "Merchant restocking", ["Keep sold items", "Refresh inventory"])]},
    ]

class CollectionApp(Gtk.Application):
    def __init__(self, library, smoke=False):
        super().__init__(application_id="io.github.stenerstrom.WastelandCollection",
                         flags=Gio.ApplicationFlags.NON_UNIQUE if smoke else Gio.ApplicationFlags.FLAGS_NONE)
        self.library = library
        self.profile = "rpu"
        self.busy = False
        self.smoke = smoke
        self.connect("activate", self.activate)

    def activate(self, *_):
        if hasattr(self, "window"):
            self.window.present()
            return
        Gtk.Settings.get_default().set_property("gtk-application-prefer-dark-theme", True)
        provider = Gtk.CssProvider()
        provider.load_from_data(CSS)
        Gtk.StyleContext.add_provider_for_display(Gdk.Display.get_default(), provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)
        self.window = Gtk.ApplicationWindow(application=self, title="Wasteland Collection")
        self.window.set_default_size(1080, 730)
        self.window.connect("close-request", lambda *_: self.busy)
        header = Gtk.HeaderBar()
        header.set_title_widget(label("WASTELAND COLLECTION", "eyebrow"))
        self.window.set_titlebar(header)
        body = box(20)
        margins(body, 30)
        self.window.set_child(body)
        body.append(label("CHOOSE YOUR STORY", "eyebrow"))
        self.cards = {}
        row = box(16, False)
        row.set_homogeneous(True)
        for profile, (title, subtitle, description) in PROFILES.items():
            content = box(6)
            content.append(label({"rpu": "13", "sonora": "S", "nevada": "08"}[profile], "number"))
            content.append(label(title, "card-title"))
            content.append(label(subtitle, "muted"))
            item = Gtk.Button()
            item.set_child(content)
            item.set_hexpand(True)
            item.add_css_class("card")
            item.add_css_class(profile)
            item.connect("clicked", lambda _, p=profile: self.select(p))
            self.cards[profile] = item
            row.append(item)
        body.append(row)
        detail = box(14)
        detail.add_css_class("detail")
        detail.set_vexpand(True)
        self.heading = label("", "title")
        self.subtitle = label("", "subtitle")
        self.description = label("", "muted", True)
        for item in (self.heading, self.subtitle, self.description):
            detail.append(item)
        spacer = Gtk.Box()
        spacer.set_vexpand(True)
        detail.append(spacer)
        self.status = label("", "muted", True)
        self.progress = Gtk.ProgressBar()
        self.progress.set_visible(False)
        detail.append(self.status)
        detail.append(self.progress)
        actions = box(12, False)
        self.play_button = button("Play", self.play, "primary")
        self.settings_button = button("Settings", self.open_settings)
        self.folder_button = button("Game folder", self.open_folder)
        self.backup_button = button("Back up saves", self.backup)
        for item in (self.play_button, self.settings_button, self.folder_button, self.backup_button):
            actions.append(item)
        detail.append(actions)
        body.append(detail)
        footer = box(12, False)
        version = self.library.package.get("version", "Development")
        version_label = label(version + "  ·  Linux x86_64", "muted")
        version_label.set_hexpand(True)
        footer.append(version_label)
        footer.append(button("Help", self.help))
        body.append(footer)
        self.select(self.profile)
        self.window.present()
        if self.smoke:
            GLib.timeout_add(1000, self.smoke_check)

    def select(self, profile):
        if self.busy:
            return
        self.profile = profile
        for p, card in self.cards.items():
            card.remove_css_class("selected")
            if p == profile:
                card.add_css_class("selected")
        title, subtitle, description = PROFILES[profile]
        self.heading.set_text(title)
        self.subtitle.set_text(subtitle)
        self.description.set_text(description)
        ready = self.library.ready(profile)
        self.play_button.set_label("Play " + title if ready else "Prepare & play")
        self.status.set_text("Ready to play. Settings and saves belong to this game." if ready else
                             "First launch prepares and verifies this game. Your other games remain separate.")

    def message(self, text):
        dialog = Gtk.MessageDialog(transient_for=self.window, modal=True,
                                   buttons=Gtk.ButtonsType.OK, text=text)
        dialog.connect("response", lambda d, *_: d.destroy())
        dialog.present()

    def set_busy(self, busy):
        self.busy = busy
        for item in list(self.cards.values()) + [self.play_button, self.settings_button, self.folder_button, self.backup_button]:
            item.set_sensitive(not busy)
        self.progress.set_visible(busy)
        if not busy:
            self.select(self.profile)

    def task(self, work, success=None, status="Preparing game files…"):
        if self.busy:
            return
        self.set_busy(True)
        self.status.set_text(status)
        self.progress.set_fraction(0)
        def run():
            result = None
            error = None
            try:
                result = work()
            except Exception as exc:
                error = str(exc)
            GLib.idle_add(finish, result, error)
        def finish(result, error):
            self.set_busy(False)
            if error:
                self.message(error)
            elif success:
                success(result)
            return False
        threading.Thread(target=run, daemon=False).start()

    def prepare_task(self, success):
        profile = self.profile
        def prepare():
            with self.library.session():
                return self.library.prepare(profile, self.update_progress)
        self.task(prepare, success)

    def update_progress(self, done, total):
        now = time.monotonic()
        if done != total and now - getattr(self, "_last_progress", 0) < 0.15:
            return
        self._last_progress = now
        def update():
            self.progress.set_fraction(min(done / max(total, 1), 1))
            self.status.set_text("Files verified. Starting game…" if done == total else
                                 f"Preparing files · {done / 1024**2:.0f} / {total / 1024**2:.0f} MB")
            return False
        GLib.idle_add(update)

    def play(self):
        profile = self.profile
        self.task(lambda: self.library.play(profile, self.update_progress),
                  status="Starting " + PROFILES[profile][0] + "… Close the game to return to the library.")

    def open_folder(self):
        self.prepare_task(lambda path: Gio.AppInfo.launch_default_for_uri(path.as_uri(), None))

    def backup(self):
        profile = self.profile
        self.task(lambda: self.library.export_saves(profile),
                  lambda path: self.message("Backup saved:\n" + str(path)), status="Backing up saved games…")

    def help(self):
        self.message("Choose a game, then Play. Settings apply to that game on its next launch.\n\n"
                     "RPU includes the Hero Appearance panel in character creation. In-game keyboard commands work normally.\n\n"
                     "Game folder opens the selected game's files, including data/savegame. Back up saves creates a separate ZIP.\n\n"
                     "This is a test build. Complete RPU, Sonora and Nevada campaigns have not been certified.")

    def open_settings(self):
        self.prepare_task(lambda _: self.settings_window())

    def settings_window(self):
        profile = self.profile
        sections = json.loads((self.library.bundle / "engine-settings.json").read_text())
        if profile == "rpu":
            sections += rpu_sections()
        window = Gtk.Window(title=PROFILES[profile][0] + " · Settings", transient_for=self.window, modal=True)
        window.set_default_size(830, 660)
        outer = box()
        margins(outer, 20)
        window.set_child(outer)
        outer.append(label("Changes apply the next time you launch this game.", "muted", True))
        names = Gtk.StringList.new([s["title"] for s in sections])
        selector = Gtk.DropDown(model=names)
        outer.append(selector)
        scroll = Gtk.ScrolledWindow()
        scroll.set_vexpand(True)
        outer.append(scroll)
        controls = []
        current = [None]
        def render(*_):
            section = sections[selector.get_selected()]
            current[0] = section
            text = self.library.settings(profile, section.get("file", "fallout2.cfg"))
            fields = box(18)
            margins(fields, 8)
            controls.clear()
            for field in section["fields"]:
                row = box(5)
                row.append(label(field["label"]))
                value = ini_get(text, section["section"], field["key"], field.get("default", ""))
                choices = field.get("choices") or (["Off", "On"] if field["type"] == "bool" else None)
                if choices:
                    values = field.get("values", [str(i) for i in range(len(choices))])
                    labels = list(choices)
                    values = list(values)
                    if value not in values:
                        labels.append("Custom: " + value)
                        values.append(value)
                    widget = Gtk.DropDown(model=Gtk.StringList.new(labels))
                    widget.set_selected(values.index(value))
                    getter = lambda w=widget, v=values: v[w.get_selected()]
                else:
                    widget = Gtk.Entry(text=str(value))
                    widget.set_max_length(2048)
                    getter = widget.get_text
                row.append(widget)
                description = field.get("description", "")
                if field["key"] == "windowed":
                    description = "Native fullscreen, a desktop window, or borderless fullscreen."
                if description:
                    row.append(label(description, "muted", True))
                fields.append(row)
                controls.append((field, getter))
            scroll.set_child(fields)
        selector.connect("notify::selected", render)
        render()
        actions = box(10, False)
        def save():
            try:
                section = current[0]
                changes = []
                for field, getter in controls:
                    value = validate_value(field, getter())
                    changes.append((section["section"], field["key"], value))
                    if field["key"] == "DamageFormula" and section.get("file") == "ddraw.ini":
                        changes.append(("RPU", "DamageFormula", value))
                self.library.save_settings(profile, changes, section.get("file", "fallout2.cfg"))
                self.status.set_text("Settings saved for " + PROFILES[profile][0] + ".")
                window.destroy()
            except Exception as error:
                dialog = Gtk.MessageDialog(transient_for=window, modal=True, buttons=Gtk.ButtonsType.OK, text=str(error))
                dialog.connect("response", lambda d, *_: d.destroy())
                dialog.present()
        actions.append(button("Save changes", save, "primary"))
        actions.append(button("Cancel", window.destroy))
        outer.append(actions)
        window.present()
        return window

    def smoke_check(self):
        # The headless CI test exercises every card and all setting sections.
        try:
            for profile in PROFILES:
                self.select(profile)
                assert self.heading.get_text() == PROFILES[profile][0]
            self.select("rpu")
            window = self.settings_window()
            outer = window.get_child()
            selector = outer.get_first_child().get_next_sibling()
            for index in range(selector.get_model().get_n_items()):
                selector.set_selected(index)
            window.destroy()
            print("GTK smoke passed: three profiles and all settings sections.", flush=True)
            self.smoke_passed = True
        finally:
            self.quit()
        return False

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundle", type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument("--data-dir", type=Path)
    parser.add_argument("--smoke-test", action="store_true")
    args = parser.parse_args()
    app = CollectionApp(Library(args.bundle, args.data_dir), args.smoke_test)
    code = app.run([])
    if args.smoke_test and not getattr(app, "smoke_passed", False):
        code = 1
    return code

if __name__ == "__main__":
    sys.exit(main())

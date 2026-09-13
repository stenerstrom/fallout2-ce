#!/usr/bin/env python3
"""Generate all engine setting controls from the registry and C++ defaults."""
from pathlib import Path
import argparse, json, re

ROOT = Path(__file__).resolve().parents[3]
ASSET = ROOT / "os/android/app/src/main/assets/engine-settings.json"
SYMBOLS = {
    "ENGLISH": "english", "WindowMode::Fullscreen": 0,
    "GAME_DIFFICULTY_NORMAL": 1, "COMBAT_DIFFICULTY_NORMAL": 1,
    "VIOLENCE_LEVEL_MAXIMUM_BLOOD": 3, "TARGET_HIGHLIGHT_TARGETING_ONLY": 2,
    "PERK_CARRY_OVER_MODE_ON": 1,
}
LABELS = {
    "resolution_x": "Width", "resolution_y": "Height", "scale": "Scaling",
    "windowed": "Window mode", "mouse_lock": "Lock mouse cursor",
    "master_volume": "Master volume", "music_volume": "Music volume",
    "sndfx_volume": "Sound effects volume", "speech_volume": "Speech volume",
    "initialize": "Enable sound system", "sounds": "Sound effects", "music": "Music",
    "speech": "Speech", "gapless_music": "Keep music playing between maps",
    "music_path1": "Music path", "music_path2": "Alternate music path",
    "cache_size": "Sound cache", "debug_sfxc": "Debug sound cache",
    "game_difficulty": "Game difficulty", "combat_difficulty": "Combat difficulty",
    "violence_level": "Violence level", "target_highlight": "Target highlighting",
    "item_highlight": "Highlight items", "combat_looks": "Target descriptions in combat",
    "combat_messages": "Detailed combat messages", "combat_taunts": "Combat taunts",
    "language_filter": "Language filter", "running": "Run by default",
    "subtitles": "Subtitles", "combat_speed": "Combat speed",
    "player_speedup": "Also speed up player combat", "text_base_delay": "Text delay",
    "text_line_delay": "Delay per text line", "brightness": "Brightness",
    "mouse_sensitivity": "Mouse sensitivity", "running_burning_guy": "Burning characters run",
    "main_menu_scale_mode": "Main menu scaling", "in_game_menu_help": "In-game menu help",
    "iface_bar_mode": "Interface overlaps the map", "perks_progress_bar": "Perk progress bar",
    "iface_bar_width": "Interface width", "iface_bar_side_art": "Interface side art",
    "iface_bar_sides_ori": "Align side art to screen edges", "alternate_ammo_meter": "Ammo meter",
    "splash_screen_size": "Splash screen size", "death_screen_size": "Death screen size",
    "end_slide_size": "Ending slide size", "movie_aspect_fit": "Preserve movie aspect ratio",
    "edg_support": "Use map edge data", "ignore_scroll_limit": "Ignore camera scroll limits",
    "ignore_map_edges": "Allow viewing beyond map edges", "quick_toolbar_visible": "Quick toolbar (iOS only)",
    "anim_speed": "Menu animation speed", "skip_opening_movies": "Skip intro",
    "display_karma_changes": "Show karma changes", "display_bonus_damage": "Show bonus damage",
    "numbers_in_dialogue": "Number dialogue responses", "dialog_border": "Detailed dialogue border",
    "auto_quick_save": "Number of quicksave slots", "enable_high_resolution_stencil": "High-resolution edge mask",
    "extend_ap_bar": "Extended action point bar", "expand_barter_window": "Larger barter window",
    "inventory_columns": "Inventory columns", "loot_weight_indicator": "Weight indicator",
    "loot_container_size_indicator_threshold": "Container indicator threshold (%)",
    "perk_carryover": "Keep unused perk choices", "use_walk_distance": "Walk short distances",
    "auto_open_doors": "Open doors automatically", "party_trade_from_menu": "Trade through the companion menu",
    "party_loot_and_barter": "Companions in looting and barter", "fast_ammo_load": "Fast ammo loading",
    "executable": "Program mode", "master_dat": "Main archive", "master_patches": "Data folder",
    "f2_res_dat": "High-resolution graphics archive", "critter_dat": "Critter archive", "critter_patches": "Critter data folder",
    "language": "Language", "scroll_lock": "Lock scrolling", "interrupt_walk": "Allow interrupted movement",
    "art_cache_size": "Graphics cache (MB)", "color_cycling": "Animated palette colors",
    "cycle_speed_factor": "Palette animation speed", "hashing": "File indexing",
    "splash": "Splash screen", "free_space": "Minimum free storage space", "screenshots_format": "Screenshot format",
    "mode": "Logging mode", "show_fps": "Show frame rate", "show_tile_num": "Show map tile numbers",
    "show_script_messages": "Show script messages", "show_load_info": "Show loading information",
    "output_map_data_info": "Log map data", "window_width": "Debug window width",
    "window_height": "Debug window height", "console_output_path": "Console log file", "debug": "Sound debugging",
}
TITLES = {"screen":"Display", "ui":"Interface", "preferences":"Gameplay and controls", "sound":"Sound",
          "gameplay":"Perks", "qol":"Quality of life", "system":"System and game files", "debug":"Debugging"}
CHOICES = {
    "screen.windowed": ["Fullscreen", "Windowed", "Borderless window"],
    "preferences.game_difficulty": ["Easy", "Normal", "Hard"],
    "preferences.combat_difficulty": ["Easy", "Normal", "Hard"],
    "preferences.violence_level": ["None", "Minimal", "Normal", "Maximum"],
    "preferences.target_highlight": ["Off", "On", "Targeting only"],
    "ui.main_menu_scale_mode": ["Original size", "Fit background", "Fit background and buttons"],
    "ui.skip_opening_movies": ["Show all", "Skip movies", "Skip movies and splash screen"],
    "ui.alternate_ammo_meter": ["Original", "Show bursts", "Also show small magazines"],
    "ui.loot_weight_indicator": ["Off", "Simple", "Detailed", "Container size"],
    "gameplay.perk_carryover": ["Off", "On", "sfall mode"],
}
EXTRA_BOUNDS = {
    "preferences.game_difficulty": (0,2), "preferences.combat_difficulty": (0,2),
    "preferences.violence_level": (0,3), "preferences.target_highlight": (0,2),
    "preferences.combat_speed": (0,50), "preferences.text_base_delay": (1,6),
    "preferences.text_line_delay": (0,2), "preferences.brightness": (1,1.17999267578125),
    "preferences.mouse_sensitivity": (0.25,2.5), "gameplay.perk_carryover": (0,2),
    "screen.windowed": (0,2),
}
for key in ("master_volume","music_volume","sndfx_volume","speech_volume"):
    EXTRA_BOUNDS["sound."+key]=(0,32767)
NOTES = {
    "screen.scale":"Higher scaling makes text and buttons larger and shows less of the map. At least 640 × 480 pixels must remain after scaling.",
    "screen.windowed":"Android normally fills the entire screen. These engine modes also affect how the pointer is handled.",
    "ui.quick_toolbar_visible":"This engine option is used only on iOS and has no effect in the Android build.",
    "preferences.mouse_sensitivity":"Applies to relative mouse movement. Direct touch is not affected.",
    "preferences.text_base_delay":"Gameplay and sound preferences may be restored from an older save. They can also be changed in the game's Preferences menu.",
    "system.language":"Game files for the selected language must be installed.",
}

def generate():
    structs={}
    header=(ROOT/"src/settings.h").read_text()
    for name,body in re.findall(r"struct (\w+)Settings\s*\{(.*?)\n\};",header,re.S):
        values={}
        for kind,key,initial in re.findall(r"^\s*(std::string|bool|int|double|WindowMode|PerkCarryOverMode)\s+(\w+)\s*(?:=\s*([^;]+))?;",body,re.M):
            initial=initial.strip()
            if not initial: default=""
            elif initial in SYMBOLS: default=SYMBOLS[initial]
            elif initial in ("true","false"): default=1 if initial=="true" else 0
            elif initial.startswith('"'): default=json.loads(initial)
            else: default=float(initial) if "." in initial else int(initial)
            values[key]={"type": {"std::string":"text","bool":"bool","double":"decimal"}.get(kind,"integer"),
                         "default":str(default)}
        structs[name.lower()]=values
    source=(ROOT/"src/settings.cc").read_text()
    groups={}
    for section,body in re.findall(r"#define SECT (\w+)\n(.*?)#undef SECT",source,re.S):
        if section=="mapper": continue # No Android mapper executable.
        fields=[]
        for macro,key,rest in re.findall(r"^\s*(SETTING(?:_P|_PATH)?)\((\w+)(.*?)\);",body,re.M):
            field=dict(structs[section][key+"_path" if macro=="SETTING_PATH" else key])
            field.update(key=key,label=LABELS[key])
            identifier=section+"."+key
            bounds=re.search(r"clamp\(([\d.]+),\s*([\d.]+)\)",rest)
            if bounds: field.update(min=float(bounds[1]),max=float(bounds[2]))
            if identifier in EXTRA_BOUNDS:
                field.update(min=EXTRA_BOUNDS[identifier][0],max=EXTRA_BOUNDS[identifier][1])
            if identifier in CHOICES: field["choices"]=CHOICES[identifier]
            if identifier in NOTES: field["description"]=NOTES[identifier]
            fields.append(field)
        groups[section]={"section":section,"title":TITLES[section],"fields":fields}
    return [groups[s] for s in TITLES]

if __name__=="__main__":
    args=argparse.ArgumentParser()
    args.add_argument("--check",action="store_true")
    options=args.parse_args()
    output=json.dumps(generate(),ensure_ascii=False,indent=2)+"\n"
    if options.check:
        if not ASSET.exists() or ASSET.read_text()!=output:
            raise SystemExit("Android settings schema is stale; run generate_settings_schema.py")
    else:
        ASSET.parent.mkdir(parents=True,exist_ok=True)
        ASSET.write_text(output)
    print("Engine settings:",sum(len(s["fields"]) for s in json.loads(output)))

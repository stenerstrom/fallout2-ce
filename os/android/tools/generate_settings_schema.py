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
    "resolution_x": "Bredd", "resolution_y": "Höjd", "scale": "Skalning",
    "windowed": "Fönsterläge", "mouse_lock": "Lås muspekaren",
    "master_volume": "Huvudvolym", "music_volume": "Musikvolym",
    "sndfx_volume": "Ljudeffekter – volym", "speech_volume": "Talvolym",
    "initialize": "Aktivera ljudsystemet", "sounds": "Ljudeffekter", "music": "Musik",
    "speech": "Tal", "gapless_music": "Fortsätt musiken vid kartbyte",
    "music_path1": "Sökväg till musik", "music_path2": "Alternativ musiksökväg",
    "cache_size": "Ljudcache", "debug_sfxc": "Felsökning av ljudcache",
    "game_difficulty": "Svårighetsgrad", "combat_difficulty": "Stridens svårighetsgrad",
    "violence_level": "Våldsnivå", "target_highlight": "Markera stridsmål",
    "item_highlight": "Markera föremål", "combat_looks": "Stridsmeddelanden om målet",
    "combat_messages": "Detaljerade stridsmeddelanden", "combat_taunts": "Stridsrop",
    "language_filter": "Språkfilter", "running": "Spring som standard",
    "subtitles": "Undertexter", "combat_speed": "Stridshastighet",
    "player_speedup": "Öka även spelarens stridshastighet", "text_base_delay": "Textfördröjning",
    "text_line_delay": "Fördröjning per textrad", "brightness": "Ljusstyrka",
    "mouse_sensitivity": "Muskänslighet", "running_burning_guy": "Brinnande figurer springer",
    "main_menu_scale_mode": "Skala huvudmenyn", "in_game_menu_help": "Hjälp i spelmenyn",
    "iface_bar_mode": "Gränssnittet överlappar kartan", "perks_progress_bar": "Förloppsrad för förmåner",
    "iface_bar_width": "Gränssnittets bredd", "iface_bar_side_art": "Sidografik vid gränssnittet",
    "iface_bar_sides_ori": "Sidografik från skärmens kanter", "alternate_ammo_meter": "Ammunitionsmätare",
    "splash_screen_size": "Startbildens storlek", "death_screen_size": "Dödsbildens storlek",
    "end_slide_size": "Slutbildernas storlek", "movie_aspect_fit": "Anpassa filmer med rätt proportioner",
    "edg_support": "Använd kartornas kantdata", "ignore_scroll_limit": "Friare kameraförflyttning",
    "ignore_map_edges": "Tillåt visning utanför kartkanter", "quick_toolbar_visible": "Snabbverktygsrad (endast iOS)",
    "anim_speed": "Menyanimationernas hastighet", "skip_opening_movies": "Hoppa över intro",
    "display_karma_changes": "Visa karmaändringar", "display_bonus_damage": "Visa bonusskada",
    "numbers_in_dialogue": "Numrera dialogsvar", "dialog_border": "Detaljerad dialogram",
    "auto_quick_save": "Antal snabbsparplatser", "enable_high_resolution_stencil": "Kantmask för hög upplösning",
    "extend_ap_bar": "Utökad handlingspoängsrad", "expand_barter_window": "Större byteshandelsfönster",
    "inventory_columns": "Kolumner i inventariet", "loot_weight_indicator": "Viktindikator",
    "loot_container_size_indicator_threshold": "Gräns för behållarindikator (%)",
    "perk_carryover": "Spara oanvända förmånsval", "use_walk_distance": "Gå kortare sträckor",
    "auto_open_doors": "Öppna dörrar automatiskt", "party_trade_from_menu": "Byt föremål via följeslagarmenyn",
    "party_loot_and_barter": "Följeslagare i byte och plundring", "fast_ammo_load": "Snabb ammunitionsladdning",
    "executable": "Programläge", "master_dat": "Huvudarkiv", "master_patches": "Datamapp",
    "f2_res_dat": "Arkiv för högupplöst grafik", "critter_dat": "Figurarkiv", "critter_patches": "Figurernas datamapp",
    "language": "Språk", "scroll_lock": "Lås skrollning", "interrupt_walk": "Tillåt avbruten förflyttning",
    "art_cache_size": "Grafikcache (MB)", "color_cycling": "Animerade palettfärger",
    "cycle_speed_factor": "Palettanimationens hastighet", "hashing": "Filindexering",
    "splash": "Startbild", "free_space": "Minsta lediga diskutrymme", "screenshots_format": "Skärmbildsformat",
    "mode": "Loggläge", "show_fps": "Visa bildfrekvens", "show_tile_num": "Visa kartrutornas nummer",
    "show_script_messages": "Visa skriptmeddelanden", "show_load_info": "Visa laddningsinformation",
    "output_map_data_info": "Logga kartdata", "window_width": "Felsökningsfönstrets bredd",
    "window_height": "Felsökningsfönstrets höjd", "console_output_path": "Fil för konsollogg", "debug": "Ljudfelsökning",
}
TITLES = {"screen":"Bild", "ui":"Gränssnitt", "preferences":"Spel och styrning", "sound":"Ljud",
          "gameplay":"Förmåner", "qol":"Bekvämlighetsval", "system":"System och spelfiler", "debug":"Felsökning"}
CHOICES = {
    "screen.windowed": ["Helskärm", "Fönster", "Kantlöst fönster"],
    "preferences.game_difficulty": ["Lätt", "Normal", "Svår"],
    "preferences.combat_difficulty": ["Lätt", "Normal", "Svår"],
    "preferences.violence_level": ["Ingen", "Minimal", "Normal", "Maximal"],
    "preferences.target_highlight": ["Av", "På", "Endast vid sikte"],
    "ui.main_menu_scale_mode": ["Originalstorlek", "Anpassa bakgrunden", "Anpassa bakgrund och knappar"],
    "ui.skip_opening_movies": ["Visa allt", "Hoppa över filmer", "Hoppa över filmer och startbild"],
    "ui.alternate_ammo_meter": ["Original", "Visa skottsalvor", "Visa även små magasin"],
    "ui.loot_weight_indicator": ["Av", "Enkel", "Detaljerad", "Behållarstorlek"],
    "gameplay.perk_carryover": ["Av", "På", "sfall-läge"],
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
    "screen.scale":"Högre skalning gör text och knappar större och visar mindre av kartan. Minst 640 × 480 måste återstå efter skalning.",
    "screen.windowed":"Android fyller normalt hela skärmen. Dessa motorlägen påverkar även hur pekaren hanteras.",
    "ui.quick_toolbar_visible":"Det här motorvalet används endast på iOS och har ingen effekt i Android-bygget.",
    "preferences.mouse_sensitivity":"Gäller relativ musrörelse. Direktpekning påverkas inte.",
    "preferences.text_base_delay":"Inställningar för spel och ljud kan återställas från en äldre sparning. De kan även ändras i spelets Preferences-meny.",
    "system.language":"Det valda språkets spelfiler måste vara installerade.",
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

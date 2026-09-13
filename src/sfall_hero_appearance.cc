#include "sfall_hero_appearance.h"

#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include "art.h"
#include "critter.h"
#include "debug.h"
#include "object.h"
#include "sfall_config.h"
#include "sfall_global_vars.h"
#include "stat.h"
#include "tile.h"
#include "svga.h"
#include "combat.h"
#include "font_manager.h"
#include "game.h"
#include "game_mouse.h"
#include "interface.h"
#include "map.h"
#include "window_manager.h"
#include "window_manager_private.h"

namespace fallout {
namespace {
struct AppearanceArchive {
    std::string folder;
    DBase* archive = nullptr;
    ~AppearanceArchive() { if (archive) dbaseClose(archive); }
};
std::vector<std::unique_ptr<AppearanceArchive>> archives;
bool initialized = false;
std::atomic<bool> selectionRequested { false };
int currentRace = 0;
int currentStyle = 0;
int currentGender = -1;

int gender()
{
    return gDude && critterGetStat(gDude, STAT_GENDER) == GENDER_FEMALE ? GENDER_FEMALE : GENDER_MALE;
}

std::unique_ptr<AppearanceArchive> openArchive(int race, int style, int sex)
{
    if (race < 0 || race > 99 || style < 0 || style > 99) return nullptr;
    char path[COMPAT_MAX_PATH];
    snprintf(path, sizeof(path), "appearance/h%cr%02ds%02d", sex == GENDER_FEMALE ? 'f' : 'm', race, style);
    auto result = std::make_unique<AppearanceArchive>();
    if (compat_is_dir(path)) result->folder = path;
    std::string datPath = std::string(path) + ".dat";
    result->archive = dbaseOpen(datPath.c_str());
    if (!result->archive && result->folder.empty()) return nullptr;
    return result;
}

void store()
{
    sfall_gl_vars_store("HAp_Race", currentRace);
    sfall_gl_vars_store("HApStyle", currentStyle);
}

void redraw()
{
    if (!gDude) return;
    gDude->fid = heroAppearanceFid(gDude->fid);
    Rect rect;
    objectGetRect(gDude, &rect);
    tileWindowRefreshRect(&rect, gDude->elevation);
}

bool select(int race, int style, bool draw)
{
    if (!heroAppearanceEnabled() || race < 0 || race > 99 || style < 0 || style > 99) return false;
    int sex = gender();
    std::vector<std::unique_ptr<AppearanceArchive>> pending;
    auto selected = openArchive(race, style, sex);
    // Original graphics remain a valid default even without an appearance pack.
    if (!selected && (race != 0 || style != 0)) return false;
    if (selected) pending.push_back(std::move(selected));
    if (style != 0) {
        auto base = openArchive(race, 0, sex);
        if (base) pending.push_back(std::move(base));
    }
    artCacheFlush();
    archives = std::move(pending);
    currentRace = race;
    currentStyle = style;
    currentGender = sex;
    store();
    if (draw) redraw();
    return true;
}

File* openFrom(AppearanceArchive& source, const char* path, const char* mode)
{
    if (!source.folder.empty()) {
        std::string full = source.folder + "/" + path;
        if (FILE* plain = compat_fopen(full.c_str(), mode)) {
            auto file = static_cast<File*>(std::malloc(sizeof(File)));
            if (!file) { fclose(plain); return nullptr; }
            file->type = XFILE_TYPE_FILE;
            file->file = plain;
            return file;
        }
    }
    if (source.archive) {
        if (DFile* entry = dfileOpen(source.archive, path, mode)) {
            auto file = static_cast<File*>(std::malloc(sizeof(File)));
            if (!file) { dfileClose(entry); return nullptr; }
            file->type = XFILE_TYPE_DFILE;
            file->dfile = entry;
            return file;
        }
    }
    return nullptr;
}
} // namespace

void heroAppearanceInitialize()
{
    initialized = true;
    heroAppearanceLoad();
}

bool heroAppearanceEnabled()
{
    int enabled = 0;
    if (!initialized || !gSfallConfigInitialized) return false;
    configGetInt(&gSfallConfig, SFALL_CONFIG_MISC_KEY, "EnableHeroAppearanceMod", &enabled);
    int count = artCritterListSize();
    return enabled != 0 && count > 0 && count <= 2048;
}

bool heroAppearanceIsFrame(int frame)
{
    int count = artCritterListSize();
    return heroAppearanceEnabled() && frame >= count && frame < count * 2;
}

int heroAppearanceBaseFrame(int frame)
{
    return heroAppearanceIsFrame(frame) ? frame - artCritterListSize() : frame;
}

int heroAppearanceFid(int fid)
{
    if (fid < 0 || objectTypeFromFid(fid) != OBJ_TYPE_CRITTER || !heroAppearanceEnabled()) return fid;
    int frame = frameIdFromFid(fid);
    return frame < artCritterListSize() ? fid + artCritterListSize() : fid;
}

int heroAppearanceBaseFid(int fid)
{
    return fid >= 0 && objectTypeFromFid(fid) == OBJ_TYPE_CRITTER && heroAppearanceIsFrame(frameIdFromFid(fid))
        ? fid - artCritterListSize() : fid;
}

int heroAppearanceRace() { return currentRace; }
int heroAppearanceStyle() { return currentStyle; }

void heroAppearanceReset()
{
    selectionRequested = false;
    archives.clear();
    currentRace = 0;
    currentStyle = 0;
    currentGender = -1;
    artCacheFlush();
}

void heroAppearanceLoad()
{
    if (!heroAppearanceEnabled()) return;
    int race = 0, style = 0;
    sfall_gl_vars_fetch("HAp_Race", race);
    sfall_gl_vars_fetch("HApStyle", style);
    if (!select(race, style, false)) {
        debugPrint("Hero Appearance: unavailable saved appearance %d/%d, using default.\n", race, style);
        select(0, 0, false);
    }
    redraw();
}

void heroAppearanceSyncGender()
{
    if (!heroAppearanceEnabled() || currentGender == gender()) return;
    if (!select(currentRace, currentStyle, false)) select(0, 0, false);
}

bool heroAppearanceSetRace(int race)
{
    heroAppearanceSyncGender();
    if (heroAppearanceEnabled() && race == currentRace) return true;
    return select(race, 0, true);
}

bool heroAppearanceSetStyle(int style)
{
    heroAppearanceSyncGender();
    if (heroAppearanceEnabled() && style == currentStyle) return true;
    return select(currentRace, style, true);
}

static std::string choiceLabel(AppearanceArchive& source, bool style, int index)
{
    const char* path = style ? "text\\english\\game\\appstyle.msg" : "text\\english\\game\\apprace.msg";
    std::string label = index == 0 ? "Default" : std::string(style ? "Style " : "Body variant ") + std::to_string(index);
    if (File* file = openFrom(source, path, "rb")) {
        char text[4097] {};
        size_t length = fileRead(text, 1, sizeof(text) - 1, file);
        fileClose(file);
        std::string content(text, length);
        auto start = content.find("{100}{}{");
        if (start != std::string::npos) {
            start += 8;
            auto end = content.find('}', start);
            if (end != std::string::npos && end > start && end - start <= 80)
                label = content.substr(start, end - start);
        }
    }
    return label;
}

void heroAppearanceSelect(int mode)
{
    static bool showing = false;
    if (showing || !heroAppearanceEnabled()) return;
    showing = true;
    bool isoWasEnabled = isoDisable();
    int oldCursor = gameMouseGetCursor();
    gameMouseSetCursor(MOUSE_CURSOR_ARROW);
    int oldFont = fontGetCurrent();
    fontSetCurrent(101);
    int x = (screenGetWidth() - 400) / 2;
    int y = (screenGetHeight() - 270) / 2;
    if (mode < 0) {
        const char* groups[] = { "Body variant", "Hairstyle" };
        mode = _win_list_select("Hero Appearance", groups, 2, nullptr, x, y, COLOR_LIGHT_YELLOW | DRAW_TEXT_FLAG_SHADOWED);
    }
    if (mode >= 0) {
        bool style = mode != 0;
        std::vector<int> values;
        std::vector<std::string> labels;
        for (int index = 0; index <= 99; index++) {
            auto pack = openArchive(style ? currentRace : index, style ? index : 0, gender());
            if (pack) {
                values.push_back(index);
                labels.push_back(choiceLabel(*pack, style, index));
            } else if (index == 0) {
                values.push_back(0);
                labels.emplace_back("Default");
            }
        }
        std::vector<const char*> items;
        int selected = 0;
        for (size_t index = 0; index < values.size(); index++) {
            if (values[index] == (style ? currentStyle : currentRace)) selected = index;
            items.push_back(labels[index].c_str());
        }
        int result = _win_list_select_at(style ? "Choose hairstyle" : "Choose body variant",
            items.data(), items.size(), nullptr, x, y, COLOR_LIGHT_YELLOW | DRAW_TEXT_FLAG_SHADOWED, selected);
        if (result >= 0 && result < static_cast<int>(values.size())) {
            if (style) heroAppearanceSetStyle(values[result]);
            else heroAppearanceSetRace(values[result]);
        }
    }
    fontSetCurrent(oldFont);
    gameMouseSetCursor(oldCursor);
    if (isoWasEnabled) isoEnable();
    showing = false;
}

void heroAppearanceRequestSelection()
{
    selectionRequested = true;
}

void heroAppearanceProcessRequest()
{
    if (!selectionRequested.exchange(false)) return;
    if (gGameLoaded && !isInCombat() && interfaceBarEnabled()) heroAppearanceSelect(-1);
}

File* heroAppearanceOpen(const char* path, const char* mode)
{
    // Underscored critter filenames are reserved for the player's virtual FID
    // range. NPC art and the normal mod load order are never remounted.
    constexpr char prefix[] = "art\\critters\\_";
    if (!heroAppearanceEnabled() || compat_strnicmp(path, prefix, sizeof(prefix) - 1) != 0)
        return nullptr;
    heroAppearanceSyncGender();
    for (auto& source : archives) {
        if (File* file = openFrom(*source, path, mode)) return file;
    }
    // Missing weapon, armor, or animation uses the ordinary CE/RPU asset.
    std::string fallback = path;
    fallback.erase(sizeof(prefix) - 2, 1);
    return fileOpen(fallback.c_str(), mode);
}

} // namespace fallout

#if defined(__ANDROID__)
#include <jni.h>
extern "C" JNIEXPORT void JNICALL
Java_com_alexbatalov_fallout2ce_GameCommandHud_nativeOpenAppearance(JNIEnv*, jclass)
{
    fallout::heroAppearanceRequestSelection();
}
#endif

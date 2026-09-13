#include "sfall_global_vars.h"

#include <cstdint>
#include <cstring>
#include <unordered_map>

namespace fallout {

struct SfallGlobalVarsState {
    std::unordered_map<uint64_t, int> vars;
};

#pragma pack(push)
#pragma pack(8)

// Matches Sfall's `GlobalVar` to maintain binary compatibility.
struct GlobalVarEntry {
    uint64_t key;
    int32_t value;
    int32_t unused;
};

#pragma pack(pop)

static bool sfall_gl_vars_store(uint64_t key, int value);
static bool sfall_gl_vars_fetch(uint64_t key, int& value);

static SfallGlobalVarsState* sfall_gl_vars_state = nullptr;

bool sfall_gl_vars_init()
{
    sfall_gl_vars_state = new (std::nothrow) SfallGlobalVarsState();
    if (sfall_gl_vars_state == nullptr) {
        return false;
    }

    return true;
}

void sfall_gl_vars_reset()
{
    sfall_gl_vars_state->vars.clear();
}

void sfall_gl_vars_exit()
{
    if (sfall_gl_vars_state != nullptr) {
        delete sfall_gl_vars_state;
        sfall_gl_vars_state = nullptr;
    }
}

bool sfall_gl_vars_save(File* stream)
{
    int count = static_cast<int>(sfall_gl_vars_state->vars.size());
    if (fileWrite(&count, sizeof(count), 1, stream) != 1) {
        return false;
    }

    GlobalVarEntry entry {};
    for (auto& pair : sfall_gl_vars_state->vars) {
        entry.key = pair.first;
        entry.value = pair.second;

        if (fileWrite(&entry, sizeof(entry), 1, stream) != 1) {
            return false;
        }
    }

    return true;
}

bool sfall_gl_vars_load(File* stream)
{
    if (!sfall_gl_vars_state) return false;
    int count;
    if (fileRead(&count, sizeof(count), 1, stream) != 1 || count < 0 || count > 1000000)
        return false;
    long remaining = fileGetSize(stream) - fileTell(stream);
    if (remaining < 0 || static_cast<size_t>(count) > static_cast<size_t>(remaining) / sizeof(GlobalVarEntry))
        return false;

    std::unordered_map<uint64_t, int> pending;
    pending.reserve(count);
    GlobalVarEntry entry;
    for (int index = 0; index < count; index++) {
        if (fileRead(&entry, sizeof(entry), 1, stream) != 1) return false;
        pending[entry.key] = entry.value;
    }
    sfall_gl_vars_state->vars.swap(pending);
    return true;
}

bool sfall_gl_vars_store(const char* key, int value)
{
    if (key == nullptr || strlen(key) != 8) {
        return false;
    }

    uint64_t numericKey;
    memcpy(&numericKey, key, sizeof(numericKey));
    return sfall_gl_vars_store(numericKey, value);
}

bool sfall_gl_vars_store(int key, int value)
{
    return sfall_gl_vars_store(static_cast<uint64_t>(key), value);
}

bool sfall_gl_vars_fetch(const char* key, int& value)
{
    if (key == nullptr || strlen(key) != 8) {
        return false;
    }

    uint64_t numericKey;
    memcpy(&numericKey, key, sizeof(numericKey));
    return sfall_gl_vars_fetch(numericKey, value);
}

bool sfall_gl_vars_fetch(int key, int& value)
{
    return sfall_gl_vars_fetch(static_cast<uint64_t>(key), value);
}

static bool sfall_gl_vars_store(uint64_t key, int value)
{
    if (!sfall_gl_vars_state) return false;
    auto it = sfall_gl_vars_state->vars.find(key);
    if (it == sfall_gl_vars_state->vars.end()) {
        sfall_gl_vars_state->vars.emplace(key, value);
    } else {
        if (value == 0) {
            sfall_gl_vars_state->vars.erase(it);
        } else {
            it->second = value;
        }
    }

    return true;
}

static bool sfall_gl_vars_fetch(uint64_t key, int& value)
{
    if (!sfall_gl_vars_state) return false;
    auto it = sfall_gl_vars_state->vars.find(key);
    if (it == sfall_gl_vars_state->vars.end()) {
        return false;
    }

    value = it->second;

    return true;
}

} // namespace fallout

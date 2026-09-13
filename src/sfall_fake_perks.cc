#include "sfall_fake_perks.h"

#include <algorithm>
#include <cstring>
#include <set>
#include <utility>

namespace fallout {

FakePerkState gFakePerks;

static constexpr size_t kRecordSize = 1096;
static constexpr uint32_t kMaxEntries = 4096;

static uint32_t readLE(const unsigned char* p)
{
    return uint32_t(p[0]) | (uint32_t(p[1]) << 8)
        | (uint32_t(p[2]) << 16) | (uint32_t(p[3]) << 24);
}

static void writeLE(unsigned char* p, uint32_t value)
{
    for (int i = 0; i < 4; i++) p[i] = static_cast<unsigned char>(value >> (i * 8));
}

const std::vector<FakePerk>& FakePerkState::entries(FakePerkKind kind) const
{
    return lists[static_cast<size_t>(kind)];
}

void FakePerkState::set(FakePerkKind kind, const std::string& name, int level, int image,
    const std::string& description, int32_t owner)
{
    if (level < 0 || name.empty()) return;
    const std::string key = name.substr(0, 63);
    auto& list = lists[static_cast<size_t>(kind)];
    auto found = std::find_if(list.begin(), list.end(), [&](const FakePerk& perk) {
        return perk.owner == owner && perk.name == key;
    });
    if (level == 0) {
        if (found != list.end()) list.erase(found);
        return;
    }
    if (found == list.end()) {
        if (list.size() >= kMaxEntries) return;
        list.emplace_back();
        found = list.end() - 1;
        found->name = key;
        found->owner = owner;
    }
    found->level = std::min(level, kind == FakePerkKind::Perk ? 100 : 1);
    found->image = image;
    found->description = description.substr(0, 511);
}

int FakePerkState::rank(FakePerkKind kind, const std::string& name, int32_t owner) const
{
    if (name.empty()) return 0;
    const std::string key = name.substr(0, 63);
    for (const auto& perk : entries(kind)) {
        if (perk.owner == owner && perk.name == key) return perk.level;
    }
    return 0;
}

int FakePerkState::rankById(int id) const
{
    if (id < 119) return 0;
    for (const auto& perk : entries(FakePerkKind::Perk)) {
        if (perk.owner == 0 && perk.id == id) return perk.level;
    }
    return 0;
}

bool FakePerkState::hasOwned(FakePerkKind kind, int32_t owner) const
{
    for (const auto& perk : entries(kind)) {
        if (perk.owner == owner && perk.level > 0) return true;
    }
    return false;
}

void FakePerkState::reset()
{
    for (auto& list : lists) list.clear();
}

// Explicit byte layout matches sfall's packed FakePerk records on every ABI:
// level, image, name[64], description[512], reserved[506], owner, extra perk id.
// Do not write a C++ struct (pointer size, padding and endianness differ).
bool FakePerkState::save(const Writer& write) const
{
    for (const auto& list : lists) {
        unsigned char count[4];
        writeLE(count, static_cast<uint32_t>(list.size()));
        if (!write(count, sizeof(count))) return false;
        for (const auto& perk : list) {
            std::array<unsigned char, kRecordSize> bytes {};
            writeLE(bytes.data(), perk.level);
            writeLE(bytes.data() + 4, perk.image);
            memcpy(bytes.data() + 8, perk.name.data(), std::min(size_t(63), perk.name.size()));
            memcpy(bytes.data() + 72, perk.description.data(), std::min(size_t(511), perk.description.size()));
            memcpy(bytes.data() + 584, perk.reserved.data(), perk.reserved.size());
            writeLE(bytes.data() + 1090, perk.owner);
            bytes[1094] = static_cast<unsigned char>(perk.id);
            bytes[1095] = static_cast<unsigned char>(static_cast<uint16_t>(perk.id) >> 8);
            if (!write(bytes.data(), bytes.size())) return false;
        }
    }
    return true;
}

bool FakePerkState::load(const Reader& read)
{
    FakePerkState pending;
    for (size_t kind = 0; kind < pending.lists.size(); kind++) {
        unsigned char size[4];
        if (!read(size, sizeof(size))) return false;
        uint32_t count = readLE(size);
        if (count > kMaxEntries) return false;
        auto& list = pending.lists[kind];
        std::set<std::pair<int32_t, std::string>> names;
        for (uint32_t i = 0; i < count; i++) {
            std::array<unsigned char, kRecordSize> bytes;
            if (!read(bytes.data(), bytes.size())) return false;
            if (memchr(bytes.data() + 8, 0, 64) == nullptr
                || memchr(bytes.data() + 72, 0, 512) == nullptr) return false;
            FakePerk perk;
            perk.level = static_cast<int32_t>(readLE(bytes.data()));
            perk.image = static_cast<int32_t>(readLE(bytes.data() + 4));
            perk.name = reinterpret_cast<const char*>(bytes.data() + 8);
            perk.description = reinterpret_cast<const char*>(bytes.data() + 72);
            memcpy(perk.reserved.data(), bytes.data() + 584, perk.reserved.size());
            perk.owner = static_cast<int32_t>(readLE(bytes.data() + 1090));
            perk.id = static_cast<int16_t>(uint16_t(bytes[1094]) | uint16_t(bytes[1095]) << 8);
            if (perk.level < 0 || perk.level > (kind == 1 ? 100 : 1)
                || !names.emplace(perk.owner, perk.name).second) return false;
            list.push_back(std::move(perk));
        }
    }
    lists.swap(pending.lists);
    return true;
}

} // namespace fallout

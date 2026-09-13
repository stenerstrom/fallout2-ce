#include "../../../src/sfall_fake_perks.h"

#include <cassert>
#include <cstring>
#include <iostream>
#include <vector>

using namespace fallout;

static std::vector<unsigned char> encode(const FakePerkState& state)
{
    std::vector<unsigned char> bytes;
    assert(state.save([&](const void* data, size_t n) {
        const auto* p = static_cast<const unsigned char*>(data);
        bytes.insert(bytes.end(), p, p + n);
        return true;
    }));
    return bytes;
}

static bool decode(FakePerkState& state, const std::vector<unsigned char>& bytes, size_t* consumed = nullptr)
{
    size_t position = 0;
    bool ok = state.load([&](void* data, size_t n) {
        if (n > bytes.size() - position) return false;
        memcpy(data, bytes.data() + position, n);
        position += n;
        return true;
    });
    if (consumed != nullptr) *consumed = position;
    return ok;
}

int main()
{
    FakePerkState state;
    assert(encode(state) == std::vector<unsigned char>(12)); // existing CE zero sections
    state.set(FakePerkKind::Perk, "Slot Jinxer", 1, 154, "The EPA reward.");
    assert(state.rank(FakePerkKind::Perk, "Slot Jinxer") == 1);
    state.set(FakePerkKind::Perk, "Slot Jinxer", 2, 155, "Updated description.");
    assert(state.entries(FakePerkKind::Perk).size() == 1);
    assert(state.entries(FakePerkKind::Perk)[0].description == "Updated description.");
    state.set(FakePerkKind::Perk, "Slot Jinxer", -1, 0, "ignored");
    assert(state.rank(FakePerkKind::Perk, "Slot Jinxer") == 2);
    state.set(FakePerkKind::Perk, "Slot Jinxer", 999, 154, "Capped");
    assert(state.rank(FakePerkKind::Perk, "Slot Jinxer") == 100);
    state.set(FakePerkKind::Trait, "Custom trait", 3, 10, "Trait");
    assert(state.rank(FakePerkKind::Trait, "Custom trait") == 1);
    state.set(FakePerkKind::Perk, "Slot Jinxer", 4, 155, "NPC", 12345);
    assert(state.rank(FakePerkKind::Perk, "Slot Jinxer", 12345) == 4);
    assert(state.rank(FakePerkKind::Perk, "Slot Jinxer") == 100);
    assert(state.rank(FakePerkKind::Perk, "slot jinxer") == 0);
    std::string temporary(200, 'N'), description(800, 'D');
    state.set(FakePerkKind::Perk, temporary, 1, -999, description);
    temporary.clear(); description.clear();
    assert(state.entries(FakePerkKind::Perk).back().name.size() == 63);
    assert(state.entries(FakePerkKind::Perk).back().description.size() == 511);
    assert(state.rank(FakePerkKind::Perk, std::string(200, 'N')) == 1);

    auto saved = encode(state);
    // Independent checks of the on-disk sfall ABI: little-endian count and fields.
    assert(saved.size() == 12 + 4 * 1096);
    assert(saved[0] == 1 && saved[1] == 0 && saved[2] == 0 && saved[3] == 0);
    assert(saved[4] == 1 && saved[8] == 10);
    assert(memcmp(saved.data() + 12, "Custom trait", 12) == 0);
    assert(saved[1100] == 3); // fake perks list count follows exactly one trait
    assert(saved[1104] == 100 && saved[1108] == 154);
    assert(saved[1104 + 1094] == 255 && saved[1104 + 1095] == 255); // no extra perk ID

    FakePerkState loaded;
    assert(decode(loaded, saved));
    assert(encode(loaded) == saved);
    assert(loaded.rank(FakePerkKind::Perk, "Slot Jinxer") == 100);
    loaded.reset();
    assert(!loaded.hasOwned(FakePerkKind::Perk));
    assert(decode(loaded, saved));
    assert(decode(loaded, std::vector<unsigned char>(12)));
    assert(!loaded.hasOwned(FakePerkKind::Trait)); // loading a different/old save clears state

    // Every truncated prefix fails without replacing the caller's active state.
    for (size_t n = 0; n < saved.size(); n++) {
        loaded.set(FakePerkKind::Perk, "Keep", 1, 1, "Untouched");
        assert(!decode(loaded, std::vector<unsigned char>(saved.begin(), saved.begin() + n)));
        assert(loaded.rank(FakePerkKind::Perk, "Keep") == 1);
    }
    auto corrupt = saved;
    corrupt[0] = corrupt[1] = corrupt[2] = corrupt[3] = 255;
    assert(!decode(loaded, corrupt)); // bounded allocation for untrusted save counts
    corrupt = saved;
    memset(corrupt.data() + 12, 'A', 64);
    assert(!decode(loaded, corrupt)); // unterminated name
    corrupt = saved;
    memset(corrupt.data() + 76, 'A', 512);
    assert(!decode(loaded, corrupt)); // unterminated description

    // Preserve unrelated sections and NPC ownership in imported sfall records.
    FakePerkState extras;
    extras.set(FakePerkKind::Selectable, "Selectable", 1, 154, "Preserved", 77);
    auto extraBytes = encode(extras);
    extraBytes[12 + 584] = 0x5a; // reserved bytes are not discarded
    assert(decode(extras, extraBytes));
    assert(encode(extras) == extraBytes);
    auto withTail = saved;
    withTail.push_back(0xca); withTail.push_back(0xfe);
    size_t used = 0;
    assert(decode(loaded, withTail, &used) && used == saved.size());
    assert(withTail[used] == 0xca); // arrays following the perk lists stay aligned

    for (int failAt = 0; failAt < 7; failAt++) {
        int calls = 0;
        assert(!state.save([&](const void*, size_t) { return calls++ != failAt; }));
    }
    state.set(FakePerkKind::Perk, "Slot Jinxer", 0, 0, "");
    assert(state.rank(FakePerkKind::Perk, "Slot Jinxer") == 0);
    assert(state.rank(FakePerkKind::Perk, "Slot Jinxer", 12345) == 4);

    std::cout << "Fake perk tests passed: mutation, ownership, old saves, portable records, "
                 "all truncated prefixes, corruption, write errors and trailing sections.\n";
}

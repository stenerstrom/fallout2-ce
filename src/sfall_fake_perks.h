#ifndef SFALL_FAKE_PERKS_H
#define SFALL_FAKE_PERKS_H

#include <array>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace fallout {

enum class FakePerkKind { Trait, Perk, Selectable };

struct FakePerk {
    int level = 0;
    int image = 0;
    std::string name;
    std::string description;
    std::array<unsigned char, 506> reserved {};
    int32_t owner = 0;
    int16_t id = -1;
};

// Owns script strings; no pointers into a script's temporary string heap.
class FakePerkState {
public:
    using Reader = std::function<bool(void*, size_t)>;
    using Writer = std::function<bool(const void*, size_t)>;

    void set(FakePerkKind kind, const std::string& name, int level, int image,
        const std::string& description, int32_t owner = 0);
    int rank(FakePerkKind kind, const std::string& name, int32_t owner = 0) const;
    int rankById(int id) const;
    bool hasOwned(FakePerkKind kind, int32_t owner = 0) const;
    const std::vector<FakePerk>& entries(FakePerkKind kind) const;
    void reset();
    bool save(const Writer& write) const;
    // Commits only after all three lists have been read and validated.
    bool load(const Reader& read);

private:
    std::array<std::vector<FakePerk>, 3> lists;
};

extern FakePerkState gFakePerks;

} // namespace fallout
#endif

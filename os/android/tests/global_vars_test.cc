#include "../../../src/sfall_global_vars.h"

#include <cassert>
#include <cstring>
#include <iostream>
#include <memory>
#include <vector>

namespace fallout {
static std::vector<unsigned char> output;
size_t fileRead(void* bytes, size_t size, size_t count, File* file) { return file->memory->read(bytes, size, count); }
size_t fileWrite(const void* bytes, size_t size, size_t count, File*) {
    auto begin = static_cast<const unsigned char*>(bytes); output.insert(output.end(), begin, begin + size * count); return count;
}
int fileGetSize(File* file) { return file->memory->size(); }
long fileTell(File* file) { return file->memory->tell(); }
}
using namespace fallout;
static bool load(const std::vector<unsigned char>& bytes) {
    MemoryFile memory(std::make_shared<std::vector<unsigned char>>(bytes));
    File file {}; file.type = XFILE_TYPE_MEMORY; file.memory = &memory;
    return sfall_gl_vars_load(&file);
}
int main() {
    int value = 0;
    assert(!sfall_gl_vars_fetch("HApStyle", value));
    assert(!sfall_gl_vars_store("HApStyle", 1));
    assert(sfall_gl_vars_init());
    char unaligned[16] {};
    std::memcpy(unaligned + 1, "HApStyle", 8);
    assert(sfall_gl_vars_store(unaligned + 1, 2));
    assert(sfall_gl_vars_fetch("HApStyle", value) && value == 2);
    assert(!sfall_gl_vars_store(nullptr, 1));
    assert(!sfall_gl_vars_store("short", 1));
    assert(sfall_gl_vars_save(nullptr));
    auto saved = output;
    assert(saved.size() == 20 && saved[0] == 1);
    for (size_t length = 0; length < saved.size(); length++) {
        assert(!load({saved.begin(), saved.begin() + length}));
        assert(sfall_gl_vars_fetch("HApStyle", value) && value == 2);
    }
    auto invalid = saved;
    invalid[0] = invalid[1] = invalid[2] = invalid[3] = 0xff;
    assert(!load(invalid));
    invalid[0] = 0xff; invalid[1] = 0xff; invalid[2] = 0x7f; invalid[3] = 0;
    assert(!load(invalid));
    assert(sfall_gl_vars_store("OLDSTATE", 77));
    assert(load(saved));
    assert(!sfall_gl_vars_fetch("OLDSTATE", value));
    assert(sfall_gl_vars_fetch("HApStyle", value) && value == 2);
    assert(load({0,0,0,0}));
    assert(!sfall_gl_vars_fetch("HApStyle", value));
    sfall_gl_vars_exit();
    assert(!sfall_gl_vars_fetch("HApStyle", value));
    std::cout << "Global variable tests passed: unaligned keys, truncated records, invalid counts and atomic replacement.\n";
}

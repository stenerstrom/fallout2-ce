#include "../../../src/sfall_filesystem.h"
#include "../../../src/db.h"
#include "../../../src/sfall_config.h"

#include <cassert>
#include <climits>
#include <cstring>
#include <iostream>
#include <map>
#include <string>

// Only archive, cache and configuration dependencies are substituted. All VFS operations
// and the memory stream used by xfile are the production implementations.
namespace fallout {
static std::map<std::string, std::vector<unsigned char>> archive;
static int cacheFlushes = 0;
bool gSfallConfigInitialized = true;
Config gSfallConfig;
static int overrideSetting = 1;
bool configGetInt(Config*, const char* section, const char* key, int* value)
{
    assert(std::strcmp(section, "Misc") == 0);
    assert(std::strcmp(key, "UseFileSystemOverride") == 0);
    *value = overrideSetting;
    return true;
}
int artCacheFlush() { return ++cacheFlushes; }
File* fileOpen(const char* path, const char*)
{
    auto data = sfallFileSystemData(path, true);
    if (!data) {
        auto found = archive.find(path);
        if (found == archive.end()) return nullptr;
        data = std::make_shared<std::vector<unsigned char>>(found->second);
    }
    auto file = new File {};
    file->type = XFILE_TYPE_MEMORY;
    file->memory = new MemoryFile(std::move(data));
    return file;
}
int fileGetSize(File* stream) { return stream->memory->size(); }
size_t fileRead(void* output, size_t size, size_t count, File* stream)
{
    return stream->memory->read(output, size, count);
}
int fileClose(File* stream) { delete stream->memory; delete stream; return 0; }
}

using namespace fallout;

int main()
{
    assert(sfallFileSystemCreate("", 4) == -1);
    assert(sfallFileSystemCreate(nullptr, 4) == -1);
    assert(sfallFileSystemCreate("negative", -1) == -1);
    assert(sfallFileSystemCreate("oversize", INT_MAX) == -1);
    assert(sfallFileSystemCopy("missing", "missing") == -1);
    assert(sfallFileSystemFind("missing") == -1);
    int id = sfallFileSystemCreate("Art/Test.frm", 16);
    assert(id == 0 && sfallFileSystemFind("ART\\TEST.FRM") == id);
    assert(sfallFileSystemCreate("Art/Test.frm", 9) == id);
    assert(sfallFileSystemSize(id) == 16);
    sfallFileSystemWriteInteger(id, 0xff, 1);
    sfallFileSystemWriteInteger(id, 0xff80, 2);
    sfallFileSystemWriteInteger(id, 0xdeadbeef, 4);
    assert(sfallFileSystemPosition(id) == 7);
    sfallFileSystemSeek(id, 0);
    assert(sfallFileSystemReadInteger(id, 1) == 255);
    assert(sfallFileSystemReadInteger(id, 2) == -128);
    assert(static_cast<uint32_t>(sfallFileSystemReadInteger(id, 4)) == 0xdeadbeef);
    auto snapshot = sfallFileSystemData("art/test.frm");
    assert((*snapshot)[1] == 0xff && (*snapshot)[2] == 0x80);
    sfallFileSystemSeek(id, 15);
    sfallFileSystemWriteInteger(id, 1234, 2);
    assert(sfallFileSystemPosition(id) == 15);
    assert(sfallFileSystemReadInteger(id, 2) == 0 && sfallFileSystemPosition(id) == 15);
    sfallFileSystemSeek(id, -1); sfallFileSystemSeek(id, INT_MAX);
    assert(sfallFileSystemPosition(id) == 15);
    sfallFileSystemSeek(id, 16);
    assert(sfallFileSystemReadInteger(id, 1) == 0);
    sfallFileSystemSeek(id, 7);
    sfallFileSystemWriteString(id, "abc", true);
    sfallFileSystemWriteString(id, "xy", false);
    assert(sfallFileSystemPosition(id) == 13);
    auto changed = sfallFileSystemData("art/test.frm");
    assert((*snapshot)[7] == 0 && (*changed)[7] == 'a');
    assert((*changed)[10] == 0 && (*changed)[11] == 'x');
    assert(sfallFileSystemReadInteger(-1, 4) == 0);
    sfallFileSystemWriteInteger(INT_MAX, 3, 4);
    assert(sfallFileSystemSize(-1) == 0 && sfallFileSystemPosition(INT_MAX) == 0);

    // The exact FRM operation performed by walking and Goris scripts:
    // copy a packed asset, read its frame count at 8, replace FPS at 4.
    archive["art\\critters\\walk.frm"] = {0, 0, 0, 4, 0, 8, 0, 0, 0, 16, 0xaa, 0xbb};
    int frm = sfallFileSystemCopy("art\\critters\\walk.frm", "art\\critters\\walk.frm");
    assert(frm >= 0);
    sfallFileSystemSeek(frm, 8);
    assert(sfallFileSystemReadInteger(frm, 2) == 16);
    sfallFileSystemSeek(frm, 4);
    sfallFileSystemWriteInteger(frm, 16, 2);
    File* reader = fileOpen("art\\critters\\walk.frm", "rb");
    unsigned char bytes[12] {};
    assert(fileRead(bytes, 1, sizeof(bytes), reader) == 12);
    assert(bytes[5] == 16 && bytes[9] == 16 && bytes[11] == 0xbb);
    assert(archive["art\\critters\\walk.frm"][5] == 8);
    assert(sfallFileSystemCopy("art\\critters\\walk.frm", "missing") == frm);
    assert(sfallFileSystemPosition(frm) == 6); // engine reads have their own cursor
    sfallFileSystemSeek(frm, 4);
    sfallFileSystemWriteInteger(frm, 50, 2);
    reader->memory->seek(4, SEEK_SET);
    assert(reader->memory->get() == 0 && reader->memory->get() == 16);
    // Disabling engine overrides keeps script files and existing readers alive.
    overrideSetting = 0;
    assert(!sfallFileSystemData("art/test.frm", true));
    assert(sfallFileSystemData("art/test.frm"));
    File* original = fileOpen("art\\critters\\walk.frm", "rb");
    assert(fileRead(bytes, 1, sizeof(bytes), original) == 12 && bytes[5] == 8);
    fileClose(original);
    assert(sfallFileSystemFind("art/test.frm") == id);
    reader->memory->seek(4, SEEK_SET);
    assert(reader->memory->get() == 0 && reader->memory->get() == 16);
    overrideSetting = 1;
    assert(sfallFileSystemData("art/test.frm", true));
    int copied = sfallFileSystemCopy("copied", "art\\critters\\walk.frm");
    assert(sfallFileSystemData("copied")->at(5) == 50);
    sfallFileSystemDelete(frm);
    assert(sfallFileSystemFind("art\\critters\\walk.frm") == -1);
    reader->memory->seek(4, SEEK_SET);
    assert(reader->memory->get() == 0 && reader->memory->get() == 16);
    fileClose(reader);
    int sfx = sfallFileSystemCreate("sound/sfx/test.acm", 8);
    assert(sfx >= 0 && !sfallFileSystemData("sound\\sfx\\test.acm", true));
    assert(sfallFileSystemData("sound\\sfx\\test.acm"));
    sfallFileSystemReset();
    assert(sfallFileSystemFind("copied") == -1 && !sfallFileSystemData("copied"));
    assert(sfallFileSystemSize(copied) == 0 && cacheFlushes > 5);
    frm = sfallFileSystemCopy("art\\critters\\walk.frm", "art\\critters\\walk.frm");
    sfallFileSystemSeek(frm, 4);
    assert(sfallFileSystemReadInteger(frm, 2) == 8); // fresh archive on each game load

    auto text = std::make_shared<std::vector<unsigned char>>(std::initializer_list<unsigned char>{'a','b','\n','c'});
    MemoryFile stream(text);
    char line[4];
    assert(stream.gets(line, 4) == line && std::strcmp(line, "ab\n") == 0);
    assert(stream.gets(line, 4) == line && std::strcmp(line, "c") == 0 && stream.eof());
    assert(stream.gets(line, 4) == nullptr);
    assert(stream.seek(-1, SEEK_SET) == -1);
    assert(stream.seek(0, 99) == -1);
    assert(stream.seek(0, SEEK_SET) == 0 && !stream.eof());
    assert(stream.read(line, 2, 3) == 2 && stream.eof());
    assert(stream.seek(1, SEEK_END) == 0 && stream.tell() == 5);
    assert(stream.get() == EOF);
    stream.seek(0, SEEK_SET);
    assert(stream.read(line, SIZE_MAX, 2) == 0 && stream.eof()); // bounded, no multiplication overflow
    MemoryFile empty(std::make_shared<std::vector<unsigned char>>());
    assert(empty.get() == EOF && empty.size() == 0);
    std::cout << "Virtual file tests passed: archive overrides, FRM edits, byte order, bounds, snapshots, reset and streams.\n";
}

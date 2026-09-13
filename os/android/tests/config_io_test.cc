#include "../../../src/config.h"
#include "../../../src/db.h"
#include "../../../src/memory.h"
#include "../../../src/platform_compat.h"

#include <cassert>
#include <cerrno>
#include <cstdarg>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include <unistd.h>

namespace {
enum class Failure { None, BufferedWrite, ImmediateWrite, Close };
Failure failure = Failure::None;
int closes = 0;
int failClose(void*) { ++closes; if (failure == Failure::Close) { errno = ENOSPC; return -1; } return 0; }
#ifdef __APPLE__
int failWrite(void*, const char*, int size)
#else
ssize_t failWrite(void*, const char*, size_t size)
#endif
{
    if (failure == Failure::Close) return size;
    errno = ENOSPC;
    return -1;
}
FILE* outputStream(const char* path, const char* mode)
{
    if (failure == Failure::None || mode[0] != 'w') return fopen(path, mode);
#ifdef __APPLE__
    FILE* file = funopen(nullptr, nullptr, failWrite, nullptr, failClose);
#else
    cookie_io_functions_t io {};
    io.write = failWrite; io.close = failClose;
    FILE* file = fopencookie(nullptr, "w", io);
#endif
    assert(file);
    if (failure == Failure::ImmediateWrite) setvbuf(file, nullptr, _IONBF, 0);
    return file;
}
}

// Actual config parsing/writing and dictionary storage, with only platform/file
// dependencies substituted to inject failures without filling the user's disk.
namespace fallout {
void* internal_malloc(size_t size) { return malloc(size); }
void* internal_realloc(void* ptr, size_t size) { return realloc(ptr, size); }
void internal_free(void* ptr) { free(ptr); }
char* internal_strdup(const char* text) { return strdup(text); }
int compat_stricmp(const char* a, const char* b) { return strcasecmp(a, b); }
char* compat_itoa(int value, char* output, int radix) { assert(radix == 10); auto text = std::to_string(value); memcpy(output, text.c_str(), text.size() + 1); return output; }
FILE* compat_fopen(const char* path, const char* mode) { return outputStream(path, mode); }
char* compat_fgets(char* text, int size, FILE* stream) { return fgets(text, size, stream); }
int compat_remove(const char* path) { return remove(path); }
int compat_rename(const char* from, const char* to) { return rename(from, to); }
File* fileOpen(const char* path, const char* mode) {
    FILE* stream = outputStream(path, mode); if (!stream) return nullptr;
    auto file = new File {}; file->file = stream; return file;
}
int fileClose(File* file) { int rc = fclose(file->file); delete file; return rc; }
char* fileReadString(char* text, size_t size, File* file) { return fgets(text, size, file->file); }
int filePrintFormatted(File* file, const char* format, ...) {
    va_list args; va_start(args, format); int rc = vfprintf(file->file, format, args); va_end(args); return rc;
}
}
int main() {
    namespace fs = std::filesystem;
    using namespace fallout;
    char scratch[] = "/tmp/ce-config-io-XXXXXX";
    assert(mkdtemp(scratch));
    const fs::path root(scratch);
    ScopedConfig config;
    assert(config && configSetInt(config.get(), "format", "version", 2));
    assert(configSetString(config.get(), "perks", "owed_levels", "3,6"));
    for (bool database : {false, true}) {
        const auto path = (root / (database ? "db.sav" : "ce.sav")).string();
        assert(configWrite(config.get(), path.c_str(), database));
        for (Failure injected : {Failure::BufferedWrite, Failure::ImmediateWrite, Failure::Close}) {
            failure = injected; closes = 0;
            assert(!configWrite(config.get(), path.c_str(), database));
            assert(closes == 1);
        }
        failure = Failure::None;
    }
    const auto path = (root / "fallout2.cfg").string();
    const std::string original = "; keep this\n[display]\nwidth=800\n";
    for (Failure injected : {Failure::BufferedWrite, Failure::ImmediateWrite, Failure::Close}) {
        { std::ofstream file(path); file << original; }
        // A prior recoverable backup must also survive a failed temporary write.
        { std::ofstream file(path + ".bak"); file << "previous"; }
        failure = injected;
        assert(!configWriteEx(config.get(), path.c_str(), CONFIG_RETAIN_ALL));
        failure = Failure::None;
        std::ifstream file(path); std::string actual((std::istreambuf_iterator<char>(file)), {});
        assert(actual == original);
        std::ifstream backup(path + ".bak"); std::string kept((std::istreambuf_iterator<char>(backup)), {});
        assert(kept == "previous");
    }
    assert(configWriteEx(config.get(), path.c_str(), CONFIG_RETAIN_ALL));
    ScopedConfig loaded(path.c_str(), false); int version = 0;
    assert(loaded && configGetInt(loaded.get(), "format", "version", &version) && version == 2);
    fs::remove_all(root);
    std::cout << "Config I/O tests passed: buffered writes, immediate errors, close failures and original/backup preservation.\n";
}

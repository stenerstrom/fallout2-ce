#include "sfall_filesystem.h"

#include <algorithm>
#include <cstring>
#include <string>

#include "art.h"
#include "db.h"

namespace fallout {

namespace {
constexpr size_t kMaxFileSize = 10 * 1024 * 1024; // sfall FScreate limit
constexpr size_t kMaxTotalSize = 256 * 1024 * 1024;
constexpr size_t kMaxFiles = 4096;
struct VirtualFile {
    std::string path;
    std::shared_ptr<std::vector<unsigned char>> data;
    size_t position = 0;
};
std::vector<VirtualFile> files;

std::string normalizePath(const char* path)
{
    std::string result = path != nullptr ? path : "";
    for (char& ch : result) {
        if (ch == '/') ch = '\\';
        else if (ch >= 'A' && ch <= 'Z') ch += 'a' - 'A';
    }
    return result;
}

VirtualFile* get(int id)
{
    return id >= 0 && static_cast<size_t>(id) < files.size() && files[id].data
        ? &files[id] : nullptr;
}

size_t totalSize()
{
    size_t size = 0;
    for (const auto& file : files) if (file.data) size += file.data->size();
    return size;
}

int insert(const char* path, std::shared_ptr<std::vector<unsigned char>> data)
{
    VirtualFile file { normalizePath(path), std::move(data), 0 };
    for (size_t index = 0; index < files.size(); index++) {
        if (!files[index].data) {
            files[index] = std::move(file);
            artCacheFlush();
            return static_cast<int>(index);
        }
    }
    if (files.size() >= kMaxFiles) return -1;
    files.push_back(std::move(file));
    artCacheFlush();
    return static_cast<int>(files.size() - 1);
}

bool validPath(const char* path)
{
    return path != nullptr && *path != '\0' && std::strlen(path) < 128;
}

void write(int id, const unsigned char* bytes, size_t size)
{
    auto file = get(id);
    if (!file || size > file->data->size() - file->position) return;
    if (size == 0) return;
    if (file->data.use_count() > 1) file->data = std::make_shared<std::vector<unsigned char>>(*file->data);
    std::memcpy(file->data->data() + file->position, bytes, size);
    file->position += size;
    artCacheFlush();
}
} // namespace

int sfallFileSystemFind(const char* path)
{
    if (!validPath(path)) return -1;
    auto normalized = normalizePath(path);
    for (size_t index = 0; index < files.size(); index++) {
        if (files[index].data && files[index].path == normalized) return static_cast<int>(index);
    }
    return -1;
}

int sfallFileSystemCreate(const char* path, int size)
{
    if (!validPath(path) || size < 0 || static_cast<size_t>(size) > kMaxFileSize
        || totalSize() + size > kMaxTotalSize) return -1;
    // Keep names unique, matching FScopy and the existing CE alias behavior.
    int existing = sfallFileSystemFind(path);
    if (existing != -1) return existing;
    return insert(path, std::make_shared<std::vector<unsigned char>>(size, 0));
}

int sfallFileSystemCopy(const char* path, const char* source)
{
    if (!validPath(path) || !validPath(source)) return -1;
    int existing = sfallFileSystemFind(path);
    if (existing != -1) return existing;
    File* stream = fileOpen(source, "rb");
    if (!stream) return -1;
    long size = fileGetSize(stream);
    // FScopy also needs larger assets (for example talking heads).
    if (size < 0 || static_cast<size_t>(size) > kMaxTotalSize - totalSize()) {
        fileClose(stream);
        return -1;
    }
    auto data = std::make_shared<std::vector<unsigned char>>(size);
    bool success = size == 0 || fileRead(data->data(), 1, size, stream) == static_cast<size_t>(size);
    fileClose(stream);
    return success ? insert(path, std::move(data)) : -1;
}

void sfallFileSystemDelete(int id)
{
    if (get(id)) {
        files[id] = {};
        artCacheFlush();
    }
}

void sfallFileSystemReset()
{
    if (!files.empty()) {
        files.clear();
        artCacheFlush();
    }
}

int sfallFileSystemSize(int id)
{
    auto file = get(id);
    return file ? static_cast<int>(file->data->size()) : 0;
}

int sfallFileSystemPosition(int id)
{
    auto file = get(id);
    return file ? static_cast<int>(file->position) : 0;
}

void sfallFileSystemSeek(int id, int position)
{
    auto file = get(id);
    if (file && position >= 0 && static_cast<size_t>(position) <= file->data->size())
        file->position = position;
}

int sfallFileSystemReadInteger(int id, int width)
{
    auto file = get(id);
    if (!file || (width != 1 && width != 2 && width != 4)
        || static_cast<size_t>(width) > file->data->size() - file->position) return 0;
    uint32_t value = 0;
    for (int index = 0; index < width; index++)
        value = (value << 8) | (*file->data)[file->position++];
    // sfall byte is unsigned; short/int are signed, all in big-endian order.
    if (width == 2 && value & 0x8000) return static_cast<int>(value) - 0x10000;
    int32_t signedValue;
    std::memcpy(&signedValue, &value, sizeof(value));
    return signedValue;
}

void sfallFileSystemWriteInteger(int id, uint32_t value, int width)
{
    if (width != 1 && width != 2 && width != 4) return;
    unsigned char bytes[4];
    for (int index = width - 1; index >= 0; index--) {
        bytes[index] = value & 0xff;
        value >>= 8;
    }
    write(id, bytes, width);
}

void sfallFileSystemWriteString(int id, const char* value, bool terminate)
{
    if (value) write(id, reinterpret_cast<const unsigned char*>(value),
        std::strlen(value) + (terminate ? 1 : 0));
}

std::shared_ptr<const std::vector<unsigned char>> sfallFileSystemData(const char* path, bool engineLookup)
{
    if (engineLookup && normalizePath(path).rfind("sound\\sfx\\", 0) == 0) return nullptr;
    auto file = get(sfallFileSystemFind(path));
    return file ? file->data : nullptr;
}

} // namespace fallout

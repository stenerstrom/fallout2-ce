#ifndef SFALL_FILESYSTEM_H
#define SFALL_FILESYSTEM_H

#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

namespace fallout {

int sfallFileSystemCreate(const char* path, int size);
int sfallFileSystemCopy(const char* path, const char* source);
int sfallFileSystemFind(const char* path);
void sfallFileSystemDelete(int id);
void sfallFileSystemReset();
int sfallFileSystemSize(int id);
int sfallFileSystemPosition(int id);
void sfallFileSystemSeek(int id, int position);
int sfallFileSystemReadInteger(int id, int width);
void sfallFileSystemWriteInteger(int id, uint32_t value, int width);
void sfallFileSystemWriteString(int id, const char* value, bool terminate);
std::shared_ptr<const std::vector<unsigned char>> sfallFileSystemData(const char* path, bool engineLookup = false);

} // namespace fallout

#endif

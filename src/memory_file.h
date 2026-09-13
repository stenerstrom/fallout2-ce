#ifndef MEMORY_FILE_H
#define MEMORY_FILE_H

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>
#include <memory>
#include <vector>

namespace fallout {

// Each engine handle owns a snapshot and an independent cursor. Script changes
// use copy-on-write, so deleting/replacing an override cannot invalidate readers.
class MemoryFile {
public:
    explicit MemoryFile(std::shared_ptr<const std::vector<unsigned char>> data)
        : data_(std::move(data)) {}
    size_t read(void* output, size_t size, size_t count)
    {
        if (size == 0 || count == 0) return 0;
        size_t available = position_ < data_->size() ? data_->size() - position_ : 0;
        size_t bytes = count > available / size ? available : size * count;
        if (bytes != 0) std::memcpy(output, data_->data() + position_, bytes);
        position_ += bytes;
        if (bytes / size < count) eof_ = true;
        return bytes / size;
    }
    int get()
    {
        unsigned char ch;
        return read(&ch, 1, 1) == 1 ? ch : EOF;
    }
    char* gets(char* output, int size)
    {
        if (size <= 0) return nullptr;
        int used = 0;
        while (used < size - 1) {
            int ch = get();
            if (ch == EOF) break;
            output[used++] = static_cast<char>(ch);
            if (ch == '\n') break;
        }
        output[used] = '\0';
        return used || size == 1 ? output : nullptr;
    }
    int seek(long offset, int origin)
    {
        int64_t base = origin == SEEK_SET ? 0 : origin == SEEK_CUR ? static_cast<int64_t>(position_)
            : origin == SEEK_END ? static_cast<int64_t>(data_->size()) : -1;
        if (base < 0 || (offset > 0 && base > INT64_MAX - offset)) return -1;
        int64_t next = base + offset;
        if (next < 0 || next > std::numeric_limits<long>::max()) return -1;
        position_ = static_cast<size_t>(next);
        eof_ = false;
        return 0;
    }
    long tell() const { return static_cast<long>(position_); }
    long size() const { return static_cast<long>(data_->size()); }
    int eof() const { return eof_; }
private:
    std::shared_ptr<const std::vector<unsigned char>> data_;
    size_t position_ = 0;
    bool eof_ = false;
};

} // namespace fallout

#endif

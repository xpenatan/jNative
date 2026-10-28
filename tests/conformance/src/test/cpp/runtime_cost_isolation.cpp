#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

// Storage controls only: no Java metadata, covariance checks, GC roots or polls.
#ifdef ATOMIC_PAYLOAD
template<class T> using Slot = std::atomic<T>;
template<class T> T read(const Slot<T>& value) { return value.load(std::memory_order_relaxed); }
template<class T> void write(Slot<T>& slot, T value) { slot.store(value, std::memory_order_relaxed); }
#else
template<class T> using Slot = T;
template<class T> T read(const Slot<T>& value) { return value; }
template<class T> void write(Slot<T>& slot, T value) { slot = value; }
#endif
static std::atomic<void*> published;

static void buffers(const std::string& operation, int size, int rounds, bool report) {
    auto source = std::make_unique<Slot<float>[]>(size);
    std::vector<float> target(size);
    for(int i = 0; i < size; i++) write(source[i], float(i & 255) * 0.25f);
    const auto start = std::chrono::steady_clock::now();
    for(int r = 0; r < rounds; r++) {
        const int index = r % size;
        write(source[index], read(source[index]) + 0.25f);
#ifndef ATOMIC_PAYLOAD
        if(operation == "buffer-bulk") std::memmove(target.data(), source.get(), size * sizeof(float));
        else
#endif
        for(int i = 0; i < size; i++) target[i] = read(source[i]);
        published.store(target.data());
    }
    const auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now() - start).count();
    std::int64_t checksum = 0;
    for(int i = 0; i < size; i++) {
        if(target[i] != read(source[i])) throw std::runtime_error("buffer value");
        checksum += std::int64_t(target[i] * 4) * (1 + i % 7);
    }
    published.store(nullptr);
    if(report) std::cout << "BENCH," << operation << ',' << size << ',' << rounds << ',' << elapsed << ',' << checksum << '\n';
}

static void references(const std::string& operation, int size, int rounds, bool report) {
    std::vector<int> values(size);
    auto source = std::make_unique<Slot<void*>[]>(size);
    auto target = std::make_unique<Slot<void*>[]>(size);
    for(int i = 0; i < size; i++) {
        write(source[i], static_cast<void*>(&values[i]));
        write(target[i], static_cast<void*>(nullptr));
    }
    void* marker = read(source[0]);
    std::int64_t checksum = 0;
    const auto start = std::chrono::steady_clock::now();
    for(int r = 0; r < rounds; r++) {
        int offset = r % size;
        for(int i = 0; i < size; i++) {
            int index = i + offset;
            if(index >= size) index -= size;
            void* value = read(source[index]);
            write(target[i], value);
            if(value == marker) checksum++;
        }
        published.store(target.get());
    }
    const auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now() - start).count();
    for(int i = 0; i < size; i++)
        if(read(target[i]) != read(source[(i + (rounds - 1) % size) % size])) throw std::runtime_error("reference value");
    if(checksum != rounds) throw std::runtime_error("reference count");
    published.store(nullptr);
    if(report) std::cout << "BENCH," << operation << ',' << size << ',' << rounds << ',' << elapsed << ',' << checksum << '\n';
}

static void sample(const std::string& operation, int size, int rounds, bool report) {
    if(operation == "buffer-bulk" || operation == "buffer-scalar") buffers(operation, size, rounds, report);
    else if(operation == "store-rows" || operation == "store-cells" || operation == "store-objects")
        references(operation, size, rounds, report);
    else throw std::invalid_argument(operation);
}

int main(int argc, char** argv) {
    if(argc != 5) return 2;
    const int size = std::atoi(argv[2]), rounds = std::atoi(argv[3]), samples = std::atoi(argv[4]);
    if(size < 1 || rounds < 1 || samples < 1) return 2;
    for(int i = 0; i < 3; i++) sample(argv[1], size, rounds, false);
    for(int i = 0; i < samples; i++) sample(argv[1], size, rounds, true);
}

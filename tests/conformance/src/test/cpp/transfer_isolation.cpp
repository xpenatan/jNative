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

// Plain payload reference; Java metadata/GC are deliberately absent.
static std::atomic<void*> published;
static std::uint32_t bits(float value) {
    std::uint32_t result;
    std::memcpy(&result, &value, 4);
    return result;
}
static float number(std::uint32_t value) {
    float result;
    std::memcpy(&result, &value, 4);
    return result;
}
static std::uint32_t reverse(std::uint32_t value) {
    return (value >> 24) | ((value >> 8) & 0xff00u) | ((value << 8) & 0xff0000u) | (value << 24);
}
static void sample(const std::string& operation, int size, int rounds, bool report) {
    if(operation != "array-copy" && operation != "buffer-copy" && operation != "buffer-swap")
        throw std::invalid_argument(operation);
    const bool swap = operation == "buffer-swap";
    std::vector<std::uint32_t> source(size), target(size);
    for(int i = 0; i < size; ++i) source[i] = bits(float(i & 255) * 0.25f);
    const auto start = std::chrono::steady_clock::now();
    for(int r = 0; r < rounds; ++r) {
        int index = r % size;
        source[index] = bits(number(source[index]) + 0.25f);
        if(swap) {
            for(int i = 0; i < size; ++i) target[i] = reverse(source[i]);
        } else std::memmove(target.data(), source.data(), std::size_t(size) * 4);
        published.store(target.data());
    }
    const auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now() - start).count();
    std::int64_t checksum = 0;
    for(int i = 0; i < size; ++i) {
        std::uint32_t actual = swap ? reverse(target[i]) : target[i];
        if(actual != source[i]) throw std::runtime_error("payload mismatch");
        checksum += std::int64_t(number(actual) * 4) * (1 + i % 7);
    }
    published.store(nullptr);
    if(report) std::cout << "BENCH," << operation << ',' << size << ',' << rounds << ',' << elapsed << ',' << checksum << '\n';
}
int main(int argc, char** argv) {
    if(argc != 5) return 2;
    int size = std::atoi(argv[2]), rounds = std::atoi(argv[3]), samples = std::atoi(argv[4]);
    if(size < 1 || rounds < 1 || samples < 1) return 2;
    for(int i = 0; i < 3; ++i) sample(argv[1], size, rounds, false);
    for(int i = 0; i < samples; ++i) sample(argv[1], size, rounds, true);
}

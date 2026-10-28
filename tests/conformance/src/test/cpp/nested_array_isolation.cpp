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

// Native payload counterpart; Java metadata, checks and GC polling are absent.
static std::atomic<void*> published;

template<int Width>
static void rows_fixed(const std::vector<std::unique_ptr<float[]>>& source,
                       float* target, int rounds) {
    for (int r = 0; r < rounds; ++r) {
        for (std::size_t i = 0; i < source.size(); ++i) {
            source[i][0] += 1;
            std::memmove(target + i * 24, source[i].get(), Width * sizeof(float));
        }
        published.store(target);
    }
}

static void rows_variable(const std::vector<std::unique_ptr<float[]>>& source,
                          float* target, int rounds, int width) {
    for (int r = 0; r < rounds; ++r) {
        for (std::size_t i = 0; i < source.size(); ++i) {
            source[i][0] += 1;
            std::memmove(target + i * 24, source[i].get(), width * sizeof(float));
        }
        published.store(target);
    }
}

static void flat(float* source, float* target, int size, int rounds) {
    for (int r = 0; r < rounds; ++r) {
        for (int i = 0; i < size * 24; i += 24) {
            source[i] += 1;
            std::memmove(target + i, source + i, 24 * sizeof(float));
        }
        published.store(target);
    }
}

static void sample(const std::string& operation, int size, int rounds, bool report) {
    std::vector<std::unique_ptr<float[]>> rows(size);
    std::vector<float> source(size * 24), target(size * 24);
    for (int i = 0; i < size; ++i) {
        rows[i] = std::make_unique<float[]>(24);
        for (int j = 0; j < 24; ++j) rows[i][j] = source[i * 24 + j] = float((i * 17 + j) & 255);
    }
    const auto start = std::chrono::steady_clock::now();
    if (operation == "rows-8") rows_fixed<8>(rows, target.data(), rounds);
    else if (operation == "rows-24") rows_fixed<24>(rows, target.data(), rounds);
    else if (operation == "rows-variable") rows_variable(rows, target.data(), rounds, 24);
    else if (operation == "flat-24") flat(source.data(), target.data(), size, rounds);
    else throw std::invalid_argument(operation);
    const auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now() - start).count();
    std::int64_t checksum = 0;
    for (std::size_t i = 0; i < target.size(); ++i) checksum += std::int64_t(target[i]) * (1 + i % 7);
    published.store(nullptr);
    if (report) std::cout << "BENCH," << operation << ',' << size << ',' << rounds << ','
                          << elapsed << ',' << checksum << '\n';
}

int main(int argc, char** argv) {
    if (argc != 5) return 2;
    const int size = std::atoi(argv[2]), rounds = std::atoi(argv[3]), samples = std::atoi(argv[4]);
    if (size < 1 || rounds < 1 || samples < 1) return 2;
    for (int i = 0; i < 3; ++i) sample(argv[1], size, rounds, false);
    for (int i = 0; i < samples; ++i) sample(argv[1], size, rounds, true);
}

#include <atomic>
#include <chrono>
#include <cstdint>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

namespace {
std::atomic<void*> escaped{nullptr};
volatile std::int64_t sink;
struct Box { int value; };
struct Hash { std::size_t operator()(const Box* box) const { return box->value; } };
struct Equal { bool operator()(const Box* a, const Box* b) const { return a->value == b->value; } };
struct Map {
    std::unordered_map<Box*, Box*, Hash, Equal> entries;
    bool owns_boxes;
    explicit Map(bool owns) : owns_boxes(owns) { entries.max_load_factor(.75f); }
    ~Map() {
        if (owns_boxes) for (const auto& entry : entries) {
            if (entry.first->value >= 128) delete entry.first;
            if (entry.second->value >= 128) delete entry.second;
        }
    }
};

void sample(const std::string& operation, int size, int rounds, bool report) {
    const bool boxed = operation.compare(0, 6, "boxed-") == 0;
    const bool presized = operation.find("presized") != std::string::npos;
    if (operation != "boxed-grow" && operation != "boxed-presized"
            && operation != "preboxed-grow" && operation != "preboxed-presized")
        throw std::invalid_argument(operation);
    std::vector<Box> keys(size), values(size);
    Box cache[128];
    for (int i = 0; i < 128; i++) cache[i].value = i;
    for (int i = 0; i < size; i++) { keys[i].value = i; values[i].value = i + 1; }
    std::unique_ptr<Map> last;
    std::int64_t sum = 0;
    const auto start = std::chrono::steady_clock::now();
    for (int r = 0; r < rounds; r++) {
        auto map = std::make_unique<Map>(boxed);
        if (presized) map->entries.reserve(size);
        else map->entries.rehash(16);
        for (int i = 0; i < size; i++) {
            Box* key = boxed ? (i < 128 ? &cache[i] : new Box{i}) : &keys[i];
            Box* value = boxed ? (i + 1 < 128 ? &cache[i + 1] : new Box{i + 1}) : &values[i];
            map->entries.emplace(key, value);
        }
        escaped.store(map.get());
        sum += map->entries.size();
        last = std::move(map);
    }
    const auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now() - start).count();
    for (int i = 0; i < size; i++) {
        auto found = last->entries.find(&keys[i]);
        if (found == last->entries.end() || found->second->value != i + 1)
            throw std::runtime_error("Lost entry");
        sum += found->second->value;
    }
    sink = sum;
    escaped.store(nullptr);
    if (report) std::cout << "BENCH," << operation << ',' << size << ',' << rounds << ','
            << elapsed << ',' << sum << '\n';
}
}

int main(int argc, char** argv) {
    const std::string operation = argv[1];
    int size = std::stoi(argv[2]), rounds = std::stoi(argv[3]);
    int samples = argc > 4 ? std::stoi(argv[4]) : 5;
    for (int i = 0; i < 3; i++) sample(operation, size, rounds, false);
    for (int i = 0; i < samples; i++) sample(operation, size, rounds, true);
}

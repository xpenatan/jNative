#include <atomic>
#include <chrono>
#include <cstdint>
#include <iostream>
#include <list>
#include <memory>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

namespace {
volatile std::int64_t sink;
struct Box { int value; };
struct Hash { std::size_t operator()(const Box* box) const { return box->value; } };
struct Equal { bool operator()(const Box* a, const Box* b) const { return a->value == b->value; } };

void sample(const std::string& operation, int size, int rounds, bool report) {
    const bool linked = operation == "linked-sparse";
    if (!linked && operation != "hash-dense" && operation != "hash-sparse")
        throw std::invalid_argument(operation);
    std::unordered_map<Box*, Box*, Hash, Equal> map;
    std::list<std::pair<Box*, Box*>> ordered;
    std::vector<std::unique_ptr<Box>> boxes;
    Box cache[128];
    for (int i = 0; i < 128; i++) cache[i].value = i;
    auto box = [&](int value) {
        if (value < 128) return &cache[value];
        boxes.push_back(std::make_unique<Box>(Box{value}));
        return boxes.back().get();
    };
    map.max_load_factor(.75f);
    map.rehash(size * (operation == "hash-dense" ? 2 : 64));
    for (int i = 0; i < size; i++) {
        Box* key = box(i * 31);
        Box* value = box(i + 1);
        map.emplace(key, value);
        if (linked) ordered.emplace_back(key, value);
    }
    std::int64_t sum = 0;
    const auto start = std::chrono::steady_clock::now();
    for (int r = 0; r < rounds; r++) {
        // Prevent reuse of a traversal result across rounds, without a CPU fence.
        std::atomic_signal_fence(std::memory_order_seq_cst);
        if (linked) for (const auto& entry : ordered) sum += entry.first->value + entry.second->value;
        else for (const auto& entry : map) sum += entry.first->value + entry.second->value;
    }
    const auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now() - start).count();
    const auto expected = (static_cast<std::int64_t>(size) * (size - 1) * 16 + size) * rounds;
    if (sum != expected || map.size() != static_cast<std::size_t>(size))
        throw std::runtime_error("Iteration mismatch");
    for (int i = 0; i < size; i++) {
        Box key{i * 31};
        if (map.at(&key)->value != i + 1) throw std::runtime_error("Lost entry");
    }
    sink = sum;
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

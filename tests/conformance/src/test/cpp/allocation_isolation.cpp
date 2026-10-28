#include <atomic>
#include <chrono>
#include <cstdint>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

// Equivalent observable allocations and lifetimes, using normal C++ ownership.
// Replaced allocations are reclaimed inside the timer; final cleanup is outside.
// Java GC scheduling, headers, type checks and exception contracts differ.
namespace {
struct Cell { int value; Cell* next = nullptr; explicit Cell(int value) : value(value) {} };
std::atomic<void*> escaped{nullptr};
volatile std::int64_t sink;

void sample(const std::string& operation, int size, int rounds, bool report) {
    std::vector<std::unique_ptr<Cell>> ring(operation == "shell-ring" ? size : 0), graph;
    if (operation == "retained-graph") {
        graph.reserve(size);
        for (int i = 0; i < size; ++i) {
            auto cell = std::make_unique<Cell>(i);
            cell->next = graph.empty() ? nullptr : graph.back().get();
            graph.push_back(std::move(cell));
        }
    }
    int marker = 0;
    std::unique_ptr<Cell> last;
    std::unique_ptr<void*[]> references;
    std::unique_ptr<Cell*[]> typed;
    std::unique_ptr<int[]> numbers;
    std::int64_t sum = 0;
    auto start = std::chrono::steady_clock::now();
    if (operation == "shell-drop" || operation == "retained-graph") {
        for (int r = 0; r < rounds; ++r) {
            auto cell = std::make_unique<Cell>(r);
            escaped.store(cell.get());
            sum += cell->value;
            last = std::move(cell);
        }
    } else if (operation == "shell-ring") {
        int slot = 0;
        for (int r = 0; r < rounds; ++r) {
            auto cell = std::make_unique<Cell>(r);
            auto* value = cell.get();
            ring[slot] = std::move(cell);
            if (++slot == size) slot = 0;
            escaped.store(value);
            sum += value->value;
        }
    } else if (operation == "reference-array") {
        for (int r = 0; r < rounds; ++r) {
            auto array = std::make_unique<void*[]>(size);
            sum += array[0] == nullptr ? 1 : 0;
            array[size - 1] = &marker;
            escaped.store(array.get());
            sum += size + (array[size - 1] == &marker ? 1 : 0);
            references = std::move(array);
        }
    } else if (operation == "typed-array") {
        for (int r = 0; r < rounds; ++r) {
            auto array = std::make_unique<Cell*[]>(size);
            sum += array[0] == nullptr ? 1 : 0;
            escaped.store(array.get());
            sum += size;
            typed = std::move(array);
        }
    } else if (operation == "primitive-array") {
        for (int r = 0; r < rounds; ++r) {
            auto array = std::make_unique<int[]>(size);
            sum += array[0];
            array[size - 1] = r;
            escaped.store(array.get());
            sum += size + array[size - 1];
            numbers = std::move(array);
        }
    } else throw std::invalid_argument(operation);
    auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count();
    if (operation == "shell-ring") {
        for (int i = 0; i < size && i < rounds; ++i) {
            int expected = i + (rounds - 1 - i) / size * size;
            if (ring[i]->value != expected) throw std::runtime_error("Lost retained cell");
            sum += ring[i]->value;
        }
    }
    if (operation == "retained-graph") {
        auto* cell = graph.back().get();
        for (int i = size - 1; i >= 0; --i) {
            if (!cell || cell->value != i) throw std::runtime_error("Lost retained graph");
            sum += cell->value;
            cell = cell->next;
        }
        if (cell) throw std::runtime_error("Unexpected graph tail");
    }
    sink = sum;
    escaped.store(nullptr);
    if (report) std::cout << "BENCH," << operation << ',' << size << ',' << rounds << ',' << elapsed << ',' << sum << '\n';
}
}
int main(int argc, char** argv) {
    try {
        if (argc != 5) throw std::invalid_argument("Expected operation size rounds samples");
        std::string operation = argv[1];
        int size = std::stoi(argv[2]), rounds = std::stoi(argv[3]), samples = std::stoi(argv[4]);
        if (size <= 0 || rounds <= 0 || samples <= 0) throw std::invalid_argument("Positive sizes required");
        for (int i = 0; i < 3; ++i) sample(operation, size, rounds, false);
        for (int i = 0; i < samples; ++i) sample(operation, size, rounds, true);
    } catch (const std::exception& error) { std::cerr << error.what() << '\n'; return 1; }
}

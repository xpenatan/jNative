#include "jn_runtime.hpp"
#include <chrono>
#include <cstdlib>
#include <iostream>
#include <string>
#include <vector>

namespace {
using namespace jnative;
struct Cell final : Object { std::uint64_t value; explicit Cell(std::uint64_t value) : value(value) {} };
volatile std::uint64_t sink = 0;
#if defined(_MSC_VER)
#define PROBE_NOINLINE __declspec(noinline)
#else
#define PROBE_NOINLINE __attribute__((noinline))
#endif
// Reading the published chain prevents the optimizer from deleting registration.
PROBE_NOINLINE std::uint64_t read_root() {
    return static_cast<Cell*>(current_thread()->root_frames->values[0])->value;
}

void sample(const std::string& operation, std::size_t count, int sample_index) {
    auto& heap = Heap::instance();
    heap.collect();
    heap.collection_threshold(operation == "allocate-gc" ? 4096 : std::numeric_limits<std::size_t>::max());
    // Queue capacity and timer/output storage are not part of the allocation probe.
    current_thread()->allocation_queue.reserve(65536);
    std::uint64_t elapsed = 0, checksum = 0;
    const auto collections = heap.statistics().second;
    if (operation == "array-calloc" || operation == "array-new") {
        using Field = OrdinaryField<Object*>;
        Field* arrays[8]{};
        const auto start = std::chrono::steady_clock::now();
        for (std::size_t i = 0; i < 8; ++i) {
            if (operation == "array-calloc") {
                void* memory = std::calloc(count, sizeof(Field));
                if (!memory) throw std::bad_alloc();
                arrays[i] = ::new (memory) Field[count];
            } else arrays[i] = new Field[count];
            if (arrays[i][0].get() || arrays[i][count - 1].get())
                throw std::runtime_error("Array initialization failed");
            checksum += count / 8 + (i < count % 8 ? 1 : 0);
        }
        elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count();
        for (auto array : arrays) {
            if (operation == "array-calloc") {
                for (std::size_t i = 0; i < count; ++i) array[i].~Field();
                std::free(array);
            } else delete[] array;
        }
    } else if (operation == "roots" || operation == "poll") {
        LocalRoot<Cell> cell(allocate<Cell>(1));
        auto start = std::chrono::steady_clock::now();
        if (operation == "roots") {
            for (std::size_t i = 0; i < count; ++i) {
                RootFrame<3> frame;
                FrameRoot<Cell> value(frame.slot(0), cell.get());
                checksum += read_root();
            }
        } else {
            for (std::size_t i = 0; i < count; ++i) {
                heap.before_allocation();
                checksum += cell->value;
            }
        }
        elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count();
    } else {
        for (std::size_t offset = 0; offset < count; offset += 65536) {
            const auto size = std::min<std::size_t>(65536, count - offset);
            std::vector<Cell*> raw(size);
            if (operation == "adopt")
                for (std::size_t i = 0; i < size; ++i) raw[i] = new Cell(1);
            auto start = std::chrono::steady_clock::now();
            if (operation == "pool") {
                for (std::size_t i = 0; i < size; ++i) { raw[i] = new Cell(1); checksum += raw[i]->value; }
            } else if (operation == "adopt") {
                for (std::size_t i = 0; i < size; ++i) { heap.adopt(raw[i]); checksum += raw[i]->value; }
            } else {
                for (std::size_t i = 0; i < size; ++i) checksum += allocate<Cell>(1)->value;
            }
            elapsed += std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count();
            // Cleanup is outside the timer. allocate-gc additionally includes automatic collections.
            if (operation == "pool") { for (auto object : raw) delete object; }
            else {
                if (operation != "allocate-gc" && heap.statistics().first != size)
                    throw std::runtime_error("Lost allocation publication");
                heap.collect();
                if (heap.statistics().first != 0) throw std::runtime_error("Unreachable objects survived collection");
            }
        }
    }
    sink = checksum;
    if (checksum != count) throw std::runtime_error("Incorrect isolated work count");
    if (sample_index >= 0)
        std::cout << operation << ',' << count << ',' << sample_index << ',' << elapsed << ',' << checksum
                  << ',' << heap.statistics().second - collections << '\n';
}
}

int main(int argc, char** argv) {
    try {
        if (argc != 3) throw std::invalid_argument("Expected operation and count");
        std::string operation = argv[1];
        if (operation != "pool" && operation != "adopt" && operation != "allocate"
                && operation != "allocate-gc" && operation != "roots" && operation != "poll"
                && operation != "array-calloc" && operation != "array-new")
            throw std::invalid_argument("Unknown isolated operation");
        auto count = std::stoull(argv[2]);
        if (!count || count > 100000000) throw std::invalid_argument("Invalid work count");
        ThreadAttachment attachment;
        for (int i = -3; i < 5; ++i) sample(operation, count, i);
        Heap::instance().collect();
        return 0;
    } catch (const std::exception& error) { std::cerr << error.what() << '\n'; return 1; }
}

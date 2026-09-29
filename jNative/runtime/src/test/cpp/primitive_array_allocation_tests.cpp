#include "jn_runtime.hpp"
#include <cstdlib>
#include <iostream>

// Fault injection is confined to this executable; the runtime has no test hooks.
namespace {
struct AllocationProbe {
    static AllocationProbe* active;
    std::size_t attempts = 0;
    std::size_t failures_at;
    std::size_t allocations = 0;
    std::size_t deallocations = 0;
    void* blocks[16]{};
    explicit AllocationProbe(std::size_t fail = 0) : failures_at(fail) { active = this; }
    ~AllocationProbe() { active = nullptr; }
    void allocated(void* value) {
        if (allocations >= 16) std::terminate();
        blocks[allocations++] = value;
    }
    void deallocated(void* value) {
        if (!value) return;
        for (std::size_t i = 0; i < allocations; ++i) {
            if (blocks[i] == value) { blocks[i] = nullptr; ++deallocations; return; }
        }
    }
};
AllocationProbe* AllocationProbe::active = nullptr;
void* watched = nullptr;
bool watched_deleted = false;
void require(bool value, const char* message) { if (!value) throw std::runtime_error(message); }
}
void* operator new(std::size_t bytes) {
    auto* probe = AllocationProbe::active;
    if (probe && ++probe->attempts == probe->failures_at) throw std::bad_alloc();
    void* value = std::malloc(bytes ? bytes : 1);
    if (!value) throw std::bad_alloc();
    if (probe) probe->allocated(value);
    return value;
}
void operator delete(void* value) noexcept {
    if (AllocationProbe::active) AllocationProbe::active->deallocated(value);
    if (value && value == watched) watched_deleted = true;
    std::free(value);
}
void* operator new[](std::size_t bytes) { return ::operator new(bytes); }
void operator delete[](void* value) noexcept { ::operator delete(value); }
void operator delete(void* value, std::size_t) noexcept { ::operator delete(value); }
void operator delete[](void* value, std::size_t) noexcept { ::operator delete(value); }

namespace {
template<class T> void single_allocation(const char* descriptor) {
    using namespace jnative;
    auto& heap = Heap::instance();
    auto& queue = current_thread()->allocation_queue;
    queue.reserve(16); // Keep heap bookkeeping out of the object-allocation probe.
    for (std::int32_t length : {0, 1, 33, 257}) {
        Object* value;
        bool one_block;
        {
            AllocationProbe probe;
            value = new_array(descriptor, length);
            bool has_shell = false;
            for (std::size_t i = 0; i < probe.allocations; ++i) has_shell |= probe.blocks[i] == value;
            one_block = probe.attempts == 1 && probe.allocations == 1 && has_shell;
        }
        require(one_block, "Primitive shell and payload used more than one allocation");
        watched = value;
        watched_deleted = false;
        heap.collect(); // Delete through Object*, exercising virtual destruction/deallocation.
        watched = nullptr;
        require(watched_deleted && heap.statistics().first == 0, "GC did not release the complete primitive allocation");
    }
    // A fixed primitive descriptor needs no storage, including MSVC Debug.
    // Fail the single shell/payload allocation before managed publication.
    {
        bool caught = false, cleaned = false;
        {
            AllocationProbe probe(1);
            try { PrimitiveArray<T>::create(33, descriptor); }
            catch (const std::bad_alloc&) { caught = true; }
            cleaned = probe.attempts == 1 && probe.allocations == 0 && probe.deallocations == 0;
        }
        require(caught && cleaned && heap.statistics().first == 0, "Primitive construction failure leaked its shell");
    }
    // Drop only the empty allocation queue's capacity so adopt must grow it.
    require(queue.empty() && current_thread()->queued_allocations == 0, "Fault probe needs an empty heap queue");
    std::vector<Object*>().swap(queue);
    bool caught = false, cleaned = false;
    {
        AllocationProbe probe(2);
        try { PrimitiveArray<T>::create(33, descriptor); }
        catch (const std::bad_alloc&) { caught = true; }
        cleaned = probe.attempts == 2 && probe.allocations == 1 && probe.deallocations == 1;
    }
    require(caught && cleaned && queue.empty() && heap.statistics().first == 0,
        "Heap adoption failure leaked or published a primitive array");
}
}
int main() {
    try {
        jnative::ThreadAttachment attachment;
        jnative::Heap::instance().collection_threshold(1000000);
        single_allocation<std::uint8_t>("[Z");
        single_allocation<std::int8_t>("[B");
        single_allocation<std::uint16_t>("[C");
        single_allocation<std::int16_t>("[S");
        single_allocation<std::int32_t>("[I");
        single_allocation<std::int64_t>("[J");
        single_allocation<float>("[F");
        single_allocation<double>("[D");
        std::cout << "primitive allocation contracts passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}

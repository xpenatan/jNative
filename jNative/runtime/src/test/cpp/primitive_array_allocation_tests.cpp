#include "jn_runtime.hpp"
#include <cstdlib>
#include <iostream>
#include <thread>

// Fault injection is confined to this executable; the runtime has no test hooks.
namespace {
struct AllocationProbe {
    static AllocationProbe* active;
    std::size_t attempts = 0;
    std::size_t failures_at;
    std::size_t allocations = 0;
    std::size_t deallocations = 0;
    void* blocks[16]{};
    std::size_t sizes[16]{};
    explicit AllocationProbe(std::size_t fail = 0) : failures_at(fail) { active = this; }
    ~AllocationProbe() { active = nullptr; }
    void allocated(void* value, std::size_t size) {
        if (allocations >= 16) std::terminate();
        sizes[allocations] = size;
        blocks[allocations++] = value;
    }
    bool contains(void* value, std::size_t size) const {
        const auto address = reinterpret_cast<std::uintptr_t>(value);
        for (std::size_t i = 0; i < allocations; ++i) {
            const auto begin = reinterpret_cast<std::uintptr_t>(blocks[i]);
            if (address >= begin && address - begin <= sizes[i] && size <= sizes[i] - (address - begin)) return true;
        }
        return false;
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
    if (probe) probe->allocated(value, bytes);
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
template<class T> T element_value(const T& value) { return value; }
template<class T> T element_value(const jnative::OrdinaryField<T>& value) { return value.get(); }
template<class T, bool Confined = false> void single_allocation(const char* descriptor) {
    using namespace jnative;
    using ArrayType = typename std::conditional<Confined, ConfinedPrimitiveArray<T>, PrimitiveArray<T>>::type;
    auto& heap = Heap::instance();
    auto& queue = current_thread()->allocation_queue;
    queue.reserve(16); // Keep heap bookkeeping out of the object-allocation probe.
    for (std::int32_t length : {0, 1, 33, 257, 8193}) {
        current_thread()->arena->trim(0);
        Object* value;
        bool one_block;
        {
            AllocationProbe probe;
            value = ArrayType::create(length, descriptor);
            one_block = probe.attempts == 1 && probe.allocations == 1
                && probe.contains(value, ArrayType::allocation_size(length));
            watched = probe.blocks[0];
        }
        require(one_block, "Primitive shell and payload used more than one allocation");
        require(managed_allocation_size(value) >= ArrayType::allocation_size(length),
                "Primitive payload missing from managed byte accounting");
        auto payload = static_cast<ArrayType*>(value)->elements.get();
        require(reinterpret_cast<std::uintptr_t>(payload) % alignof(T) == 0, "Primitive payload is misaligned");
        for (std::int32_t i = 0; i < length; ++i) {
            require(element_value(payload[i]) == T{}, "Primitive payload was not zero initialized");
        }
        watched_deleted = false;
        heap.collect(); // Delete through Object*, exercising virtual destruction/deallocation.
        current_thread()->arena->trim(0);
        watched = nullptr;
        require(watched_deleted && heap.statistics().first == 0, "GC did not release the complete primitive allocation");
    }
    // A fixed primitive descriptor needs no storage, including MSVC Debug.
    // Fail the single shell/payload allocation before managed publication.
    {
        current_thread()->arena->trim(0);
        bool caught = false, cleaned = false;
        {
            AllocationProbe probe(1);
            try { ArrayType::create(33, descriptor); }
            catch (const std::bad_alloc&) { caught = true; }
            cleaned = probe.attempts == 1 && probe.allocations == 0 && probe.deallocations == 0;
        }
        require(caught && cleaned && heap.statistics().first == 0, "Primitive construction failure leaked its shell");
    }
    // Small pooled arrays need no secondary ownership allocation. Larger
    // pooled and system arrays still exercise queue failure after construction.
    require(queue.empty() && current_thread()->queued_allocations == 0, "Fault probe needs an empty heap queue");
    std::vector<Object*>().swap(queue);
    current_thread()->arena->trim(0);
    {
        AllocationProbe probe(2);
        ArrayType::create(33, descriptor);
        require(probe.attempts == 1 && queue.empty() && heap.statistics().first == 1,
                "Pooled adoption allocated a secondary ownership queue");
    }
    heap.collect();
    current_thread()->arena->trim(0);
    const auto large_count = static_cast<std::int32_t>(4194304 / sizeof(typename PrimitiveArray<T>::Field) + 1);
    for (std::int32_t count : {std::int32_t(1024), large_count}) {
        bool caught = false, cleaned = false;
        {
            AllocationProbe probe(2);
            try { ArrayType::create(count, descriptor); }
            catch (const std::bad_alloc&) { caught = true; }
            current_thread()->arena->trim(0);
            cleaned = probe.attempts == 2 && probe.allocations == 1 && probe.deallocations == 1;
        }
        require(caught && cleaned && queue.empty() && heap.statistics().first == 0,
            "Heap adoption failure leaked or published a primitive array");
    }
    bool overflow = false;
    try { ArrayType::allocation_size(static_cast<std::size_t>(INT32_MAX) + 1); }
    catch (const std::bad_array_new_length&) { overflow = true; }
    require(overflow, "Primitive count overflow was accepted");
}
void reference_allocation() {
    using namespace jnative;
    auto& heap = Heap::instance();
    auto& queue = current_thread()->allocation_queue;
    // Initialize immutable type metadata before injecting allocation failures.
    new_array("[Ljava/lang/Object;", 0);
    heap.collect();
    queue.reserve(16);
    for (const char* descriptor : {"[Ljava/lang/Object;", "[Ljava/lang/String;", "[[Ljava/lang/String;", "[Ltest/LongUnregisteredDescriptor;"}) {
        for (std::int32_t size : {0, 1, 33, 257, 8193}) {
            current_thread()->arena->trim(0);
            ReferenceArray* array;
            bool one_block;
            {
                AllocationProbe probe;
                array = ReferenceArray::create(size, descriptor);
                one_block = probe.attempts == 1 && probe.allocations == 1
                    && probe.contains(array, ReferenceArray::allocation_size(size, std::strlen(descriptor)));
                watched = probe.blocks[0];
            }
            require(one_block, "Reference shell, payload and descriptor used more than one allocation");
            require(array->elements.get() == array->data(), "Reference payload address changed");
            require(reinterpret_cast<unsigned char*>(array->data()) == reinterpret_cast<unsigned char*>(array)
                    + ReferenceArray::payload_offset(), "Reference payload is not contiguous");
            require(std::strcmp(array->type_name(), descriptor) == 0, "Reference descriptor changed");
            require(reinterpret_cast<std::uintptr_t>(array->data()) % alignof(ReferenceArray::Field) == 0,
                    "Reference payload is misaligned");
            for (std::int32_t i = 0; i < size; ++i) require(array->elements[i].get() == nullptr, "Reference payload is not zeroed");
            watched_deleted = false;
            heap.collect();
            current_thread()->arena->trim(0);
            watched = nullptr;
            require(watched_deleted && heap.statistics().first == 0, "Reference array collection leaked its block");
        }
    }
    const auto maximum = std::numeric_limits<std::size_t>::max();
    for (auto name_size : {maximum, maximum - ReferenceArray::payload_offset(), maximum - ReferenceArray::payload_offset() - 1}) {
        bool caught = false;
        try { ReferenceArray::allocation_size(1, name_size); }
        catch (const std::bad_array_new_length&) { caught = true; }
        require(caught, "Reference descriptor size overflow was accepted");
    }
    bool count_caught = false;
    try { ReferenceArray::allocation_size(static_cast<std::size_t>(INT32_MAX) + 1, 1); }
    catch (const std::bad_array_new_length&) { count_caught = true; }
    require(count_caught, "Reference count overflow was accepted");
    for (std::int32_t count : {33, 1024, 524288}) {
        // Fail a small block or queued ownership for a larger pooled/system block.
        const std::size_t failure = count == 33 ? 1 : 2;
        std::vector<Object*>().swap(queue);
        current_thread()->arena->trim(0);
        bool caught = false, cleaned;
        {
            AllocationProbe probe(failure);
            try { ReferenceArray::create(count, "[Ljava/lang/Object;"); }
            catch (const std::bad_alloc&) { caught = true; }
            current_thread()->arena->trim(0);
            cleaned = probe.attempts == failure && probe.allocations == probe.deallocations;
        }
        require(caught && cleaned && queue.empty() && heap.statistics().first == 0,
                "Reference array allocation/adoption failure leaked or published its block");
    }
}
void allocator_contracts() {
    using namespace jnative;
    auto arena = current_thread()->arena;
    // Exercise every size, including all class boundaries and the large path.
    for (std::size_t size = 1; size <= 4200; ++size) {
        auto first = managed_allocate(size);
        require(reinterpret_cast<std::uintptr_t>(first) % alignof(std::max_align_t) == 0, "Managed allocation alignment changed");
#if defined(__STDCPP_DEFAULT_NEW_ALIGNMENT__)
        require(reinterpret_cast<std::uintptr_t>(first) % __STDCPP_DEFAULT_NEW_ALIGNMENT__ == 0,
                "Default native allocation alignment changed");
#elif defined(_MSC_VER) && defined(_WIN64)
        require(reinterpret_cast<std::uintptr_t>(first) % 16 == 0, "Default native allocation alignment changed");
#endif
        std::memset(first, 0xa5, size);
        require(managed_allocation_size(first) >= size, "Managed size accounting lost payload bytes");
        managed_deallocate(first);
    }
    arena->trim(0);
    require(arena->reserved_bytes() == 0, "Empty allocation pages were retained");
    // Medium size-class edges and the system-allocation fallback retain their
    // size metadata, alignment and neighbors through reuse.
    for (std::size_t size : {1016, 1017, 2040, 2041, 4088, 4089, 8184, 8185,
            16376, 16377, 32760, 32761, 65528, 65529, 131073}) {
        void* blocks[40];
        for (int pass = 0; pass < 2; ++pass) {
            for (int i = 0; i < 40; ++i) {
                blocks[i] = managed_allocate(size);
                require(managed_allocation_size(blocks[i]) >= size, "Medium allocation size was truncated");
                require(reinterpret_cast<std::uintptr_t>(blocks[i]) % alignof(std::max_align_t) == 0,
                        "Medium allocation is misaligned");
                std::memset(blocks[i], i + pass + 1, size);
            }
            for (int i = 0; i < 40; ++i) {
                auto bytes = static_cast<unsigned char*>(blocks[i]);
                for (std::size_t j = 0; j < size; ++j)
                    require(bytes[j] == i + pass + 1, "Medium allocations overlap");
            }
            for (int i = 0; i < 40; ++i) managed_deallocate(blocks[(i * 17) % 40]);
        }
        arena->trim(0);
        require(arena->reserved_bytes() == 0, "Medium pages leaked after trimming");
    }
    // Single-object large pages reuse storage without another system allocation.
    for (std::size_t size : {65529, 131073, 1048577, 4194296}) {
        auto block = managed_allocate(size);
        require(managed_allocation_size(block) >= size, "Large page lost payload accounting");
        std::memset(block, 0x5a, size);
        managed_deallocate(block);
        {
            AllocationProbe probe(1);
            block = managed_allocate(size);
            std::memset(block, 0xa5, size);
            managed_deallocate(block);
            require(probe.attempts == 0, "Large page reuse called the system allocator");
        }
        arena->trim(0);
        require(arena->reserved_bytes() == 0, "Large page survived explicit trimming");
    }
    auto beyond_pool = managed_allocate(4194304);
    require(arena->reserved_bytes() == 0 && managed_allocation_size(beyond_pool) >= 4194304,
            "Oversized allocation did not use accounted system storage");
    std::memset(beyond_pool, 0xa5, 4194304);
    managed_deallocate(beyond_pool);
    // Keep neighbors live across page boundaries, then recycle them in a
    // different order. Payload writes must never overwrite another slot's
    // compact header, free-list link, or live bytes.
    void* neighbors[1100];
    for (std::size_t size : {0, 1, 8, 9, 16, 17, 24, 25, 31, 32, 40, 56, 64, 72, 120, 128, 256, 504, 512, 513, 4096, 4097}) {
        for (int pass = 0; pass < 2; ++pass) {
            for (std::size_t i = 0; i < 1100; ++i) {
                neighbors[i] = managed_allocate(size);
                std::memset(neighbors[i], static_cast<unsigned char>(i * 37 + size), size);
            }
            for (std::size_t i = 0; i < 1100; ++i) {
                auto bytes = static_cast<unsigned char*>(neighbors[i]);
                for (std::size_t j = 0; j < size; ++j)
                    require(bytes[j] == static_cast<unsigned char>(i * 37 + size), "Allocation slots overlap or corrupt live neighbors");
            }
            for (std::size_t i = 0; i < 1100; ++i) managed_deallocate(neighbors[(i * 37) % 1100]);
        }
        arena->trim(0);
        require(arena->reserved_bytes() == 0, "Recycled slots leaked a page");
    }
    void* slots[2000];
    for (auto& slot : slots) slot = managed_allocate(64);
    const auto peak = arena->reserved_bytes();
    require(peak > 65536, "Page growth test did not exercise reclamation");
    for (auto slot : slots) managed_deallocate(slot);
    arena->trim(65536);
    require(arena->reserved_bytes() == 65536, "Warm-page cache exceeded its byte budget");
    {
        AllocationProbe probe(1);
        // A warmed allocation must not call the system allocator at all.
        auto slot = managed_allocate(64);
        managed_deallocate(slot);
        require(probe.attempts == 0, "Warm allocation still called the system allocator");
    }
    arena->trim(0);
    // Remote deletion races owner allocation and trimming. Live counts must
    // protect pages containing blocks which are still on the remote queue.
    for (auto& slot : slots) slot = managed_allocate(64);
    std::thread remote[4];
    for (int worker = 0; worker < 4; ++worker)
        remote[worker] = std::thread([&, worker] {
            for (int i = worker; i < 2000; i += 4) managed_deallocate(slots[i]);
        });
    for (int i = 0; i < 2000; ++i) {
        auto slot = managed_allocate(64);
        managed_deallocate(slot);
        arena->trim(0);
    }
    for (auto& worker : remote) worker.join();
    arena->trim(0);
    require(arena->reserved_bytes() == 0, "Remote frees leaked pages");
    struct ConstructorFailure : Object {
        ConstructorFailure() { throw 123; }
    };
    bool caught = false;
    try { new ConstructorFailure(); } catch (int) { caught = true; }
    arena->trim(0);
    require(caught && arena->reserved_bytes() == 0, "Throwing constructor leaked a pooled block");
    // Dirty reused payloads must still get Java default initialization.
    for (int pass = 0; pass < 2; ++pass) {
        auto values = static_cast<PrimitiveArray<std::int32_t>*>(new_array("[I", 33));
        for (int i = 0; i < 33; ++i) {
            require(values->elements[i].get() == 0, "Reused primitive payload was not zero initialized");
            values->elements[i].set(-1);
        }
        auto refs = ReferenceArray::create(33, "[Ljava/lang/Object;");
        for (int i = 0; i < 33; ++i) {
            require(refs->elements[i].get() == nullptr, "Reused reference payload was not zero initialized");
            refs->elements[i].set(values);
        }
        Heap::instance().collect();
    }
    arena->trim(0);
    require(arena->reserved_bytes() == 0, "Collected arrays retained live slots");
    bool overflow = false;
    try { managed_allocate(std::numeric_limits<std::size_t>::max()); }
    catch (const std::bad_alloc&) { overflow = true; }
    require(overflow, "Large allocation header overflow was accepted");
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
        single_allocation<std::uint16_t, true>("[C");
        single_allocation<std::int16_t, true>("[S");
        single_allocation<std::int32_t, true>("[I");
        single_allocation<std::int64_t, true>("[J");
        single_allocation<float, true>("[F");
        single_allocation<double, true>("[D");
        reference_allocation();
        allocator_contracts();
        std::cout << "array allocation contracts passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}

#include "jn_runtime.hpp"
#include <iostream>
#include <string>
#include <thread>

namespace {
using namespace jnative;
void require(bool condition, const char* message) { if (!condition) throw std::runtime_error(message); }
struct Cell final : Object { int value = 41; };
struct Failed final : Object { Failed() { throw std::runtime_error("constructor failure"); } };
struct FailingTrace final : Object {
    Object* child;
    bool fail = true;
    explicit FailingTrace(Object* child) : child(child) {}
    void trace(Tracer& tracer) override {
        tracer.visit(child);
        if (fail) throw std::bad_alloc();
    }
};
struct Cleanup final : Object {
    std::atomic<int>& destroyed;
    std::unique_ptr<int> native_resource{new int(73)};
    explicit Cleanup(std::atomic<int>& destroyed) : destroyed(destroyed) {}
    ~Cleanup() override { destroyed.fetch_add(1, std::memory_order_relaxed); }
};
int custom_frees = 0;
struct ExtraStorage final : Object {
    static void* operator new(std::size_t bytes) {
        return managed_allocate(bytes + 2048);
    }
    static void operator delete(void* value) noexcept { ++custom_frees; managed_deallocate(value); }
    explicit ExtraStorage(bool fail = false) {
        if (fail) throw std::runtime_error("custom allocation constructor failure");
    }
};
struct InheritedExtraStorage final : Object {
    using Object::operator new;
    static void* operator new(std::size_t bytes) { return managed_allocate(bytes + 2048); }
    static void operator delete(void* value) noexcept { ++custom_frees; managed_deallocate(value); }
};
struct CustomDelete final : Object {
    static void operator delete(void* value, ManagedArena*) noexcept { managed_deallocate(value); }
    static void operator delete(void* value) noexcept { ++custom_frees; managed_deallocate(value); }
};
struct SizedDelete final : Object {
    static void operator delete(void* value, ManagedArena*) noexcept { managed_deallocate(value); }
    static void operator delete(void* value, std::size_t) noexcept { ++custom_frees; managed_deallocate(value); }
};

void shells(Heap& heap) {
    heap.collection_threshold(1);
    LocalRoot<Cell> first(allocate<Cell>());
    auto collections = heap.statistics().second;
    {
        LocalRoot<Cell> second(allocate<Cell>());
        require(heap.statistics().first == 2 && heap.statistics().second == collections + 1,
                "Allocation ignored the attached heap or exact collection threshold");
        heap.collect();
        require(first->value == 41 && second->value == 41, "Collection lost a rooted shell");
    }
    heap.collect();
    require(heap.statistics().first == 1 && first->value == 41, "Shell roots were not respected");
}

void arrays(Heap& heap) {
    heap.collection_threshold(1);
    LocalRoot<Cell> value(allocate<Cell>());
    LocalRoot<PrimitiveArray<std::int32_t>> numbers(PrimitiveArray<std::int32_t>::create(17, "[I"));
    LocalRoot<ReferenceArray> references(ReferenceArray::create(17, "[Ljava/lang/Object;", 0));
    for (int i = 0; i < 17; ++i) {
        require(numbers->elements[i].get() == 0 && references->elements[i].get() == nullptr,
                "Array allocation did not preserve zero initialization");
    }
    numbers->elements[16].set(73);
    references->elements[16].set(value.get());
    value.set(nullptr);
    heap.collect();
    require(heap.statistics().first == 3 && numbers->elements[16].get() == 73
            && static_cast<Cell*>(references->elements[16].get())->value == 41,
            "Array allocation used a different heap or lost a managed reference");
}

void failures(Heap& heap) {
    bool rejected = false;
    { Heap other;
      try { other.collect(); } catch (const std::logic_error&) { rejected = true; } }
    require(rejected, "Collection accepted a heap without the calling mutator");
    rejected = false;
    { NativeRegion native;
      try { allocate<Cell>(); } catch (const std::logic_error&) { rejected = true; } }
    require(rejected && heap.statistics().first == 0, "Native-region allocation was published");
    rejected = false;
    try { allocate<Failed>(); } catch (const std::runtime_error&) { rejected = true; }
    require(rejected && heap.statistics().first == 0, "Failed construction was published");
    {
        LocalRoot<ExtraStorage> custom(allocate<ExtraStorage>());
        require(managed_allocation_size(custom.get()) >= sizeof(ExtraStorage) + 2048,
                "Small-object factory bypassed a custom native allocator");
        require(current_thread()->queued_bytes.load() == managed_allocation_size(custom.get()),
                "Small-object factory miscounted a custom native allocation");
    }
    heap.collect();
    rejected = false;
    try { allocate<ExtraStorage>(true); } catch (const std::runtime_error&) { rejected = true; }
    require(rejected && heap.statistics().first == 0 && custom_frees == 2,
            "Failed custom construction leaked storage or published an object");
    {
        LocalRoot<InheritedExtraStorage> custom(allocate<InheritedExtraStorage>());
        require(managed_allocation_size(custom.get()) >= sizeof(InheritedExtraStorage) + 2048,
                "Inherited placement new bypassed ordinary custom allocation");
    }
    heap.collect();
    allocate<CustomDelete>();
    allocate<SizedDelete>();
    heap.collect();
    require(custom_frees == 5, "Arena sweep bypassed a custom native deallocator");
    LocalRoot<Cell> value(allocate<Cell>());
    heap.collect();
    require(value->value == 41, "Allocation did not recover after failure");
}

void pressure(Heap& heap) {
    for (std::size_t threshold : {1, 7, 63, 64, 65, 129, 4097}) {
        heap.collect();
        heap.collection_threshold(threshold);
        const auto collections = heap.statistics().second;
        for (std::size_t i = 1; i <= threshold * 3 + 1; ++i) {
            allocate<Cell>();
            require(heap.statistics().second == collections + (i - 1) / threshold,
                    "Batched pressure changed the single-mutator collection interval");
        }
    }
    heap.collect();
    heap.collection_threshold(129);
    std::thread worker([&] {
        ThreadContext context;
        heap.attach(&context);
        for (int i = 0; i < 71; ++i) allocate<Cell>();
        heap.detach(&context);
    });
    { NativeRegion waiting; worker.join(); }
    require(heap.statistics().first == 71, "Thread detach lost queued objects");
    const auto collections = heap.statistics().second;
    for (int i = 0; i < 58; ++i) allocate<Cell>();
    require(heap.statistics().second == collections, "Detached pressure collected too early");
    allocate<Cell>();
    require(heap.statistics().second == collections + 1 && heap.statistics().first == 1,
            "Thread detach lost the partial allocation batch");
}

void concurrent_pressure(Heap& heap) {
    heap.collection_threshold(129);
    std::atomic<int> ready{0};
    std::atomic<bool> resume{false}, intact{true};
    auto waiting_mutator = [&] {
        ThreadContext context;
        heap.attach(&context);
        {
            LocalRoot<Cell> last;
            for (int i = 0; i < 63; ++i) last.set(allocate<Cell>());
            {
                NativeRegion waiting;
                ready.fetch_add(1);
                while (!resume.load()) std::this_thread::yield();
            }
            if (last->value != 41) intact.store(false);
        }
        heap.detach(&context);
    };
    std::thread first(waiting_mutator), second(waiting_mutator);
    while (ready.load() != 2) safepoint();
    const auto collections = heap.statistics().second;
    for (int i = 0; i < 129; ++i) allocate<Cell>();
    bool correct = heap.statistics().first == 255 && heap.statistics().second == collections;
    allocate<Cell>();
    correct = correct && heap.statistics().first == 3 && heap.statistics().second == collections + 1;
    resume.store(true);
    { NativeRegion waiting; first.join(); second.join(); }
    require(correct && intact.load(), "Concurrent partial batches lost roots or exceeded the pressure bound");
}

void automatic_pressure(Heap& heap) {
    // A survivor budget not divisible by 64 exercises the partial final batch.
    LocalRoot<ReferenceArray> retained(ReferenceArray::create(1030, "[Ljava/lang/Object;", 0));
    for (int i = 0; i < retained->length; ++i) retained->elements[i].set(allocate<Cell>());
    const std::size_t budget = 4 * 1031;
    for (int pass = 0; pass < 2; ++pass) {
        heap.collect();
        const auto collections = heap.statistics().second;
        for (std::size_t i = 0; i < budget; ++i) allocate<Cell>();
        require(heap.statistics().second == collections, "Automatic pressure collected before its budget");
        for (int i = 0; i < 64; ++i) allocate<Cell>();
        require(heap.statistics().second == collections + 1,
                "Automatic object pressure was lost or exceeded one partial batch");
        require(static_cast<Cell*>(retained->elements[1029].get())->value == 41,
                "Automatic collection lost a retained graph");
    }
    heap.collect();
    const auto collections = heap.statistics().second;
    for (int i = 0; i < 4064; ++i) allocate<Cell>();
    std::thread worker([&] {
        ThreadContext context;
        heap.attach(&context);
        for (int i = 0; i < 100; ++i) allocate<Cell>();
        heap.detach(&context);
    });
    { NativeRegion waiting; worker.join(); }
    // Either the worker or this next allocation may collect. A partially
    // reported worker must not erase pressure or lose the main thread's graph.
    allocate<Cell>();
    const auto statistics = heap.statistics();
    require(statistics.second == collections + 1 && statistics.first >= 1031 && statistics.first <= 1095
            && static_cast<Cell*>(retained->elements[1029].get())->value == 41,
            "Thread detach lost automatic collection pressure or retained objects");
}

void byte_pressure(Heap& heap) {
    // A few large arrays must cause collection before the object-count limit.
    // Keep an older array and a reference-only child live through repeated sweeps.
    LocalRoot<Cell> value(allocate<Cell>());
    LocalRoot<ReferenceArray> retained(ReferenceArray::create(65536, "[Ljava/lang/Object;", 0));
    retained->elements[65535].set(value.get());
    value.set(nullptr);
    const auto collections = heap.statistics().second;
    for (int pass = 0; pass < 40; ++pass) {
        LocalRoot<ReferenceArray> array(ReferenceArray::create(65536, "[Ljava/lang/Object;", 0));
        for (int i = 0; i < 65536; ++i) {
            require(array->elements[i].get() == nullptr, "Reused reference storage was not initialized");
            array->elements[i].set(retained.get());
        }
    }
    require(heap.statistics().second > collections && heap.statistics().first < 20,
            "Large discarded arrays bypassed byte-based collection pressure");
    heap.collect();
    require(heap.statistics().first == 2
            && static_cast<Cell*>(retained->elements[65535].get())->value == 41,
            "Byte-based collection lost an older array or its child");
    // An explicit interval remains an object-count control even for large arrays.
    heap.collection_threshold(4097);
    const auto explicit_collections = heap.statistics().second;
    for (int i = 0; i < 12; ++i) PrimitiveArray<std::int64_t>::create(65536, "[J");
    require(heap.statistics().second == explicit_collections, "Byte pressure overrode an explicit GC interval");
}

void byte_detach(Heap& heap) {
    std::uint64_t retained = 0;
    std::thread worker([&] {
        ThreadContext context;
        heap.attach(&context);
        for (int i = 0; i < 7; ++i) {
            LocalRoot<ReferenceArray> array(ReferenceArray::create(32768, "[Ljava/lang/Object;", 0));
            if (i == 0) retained = heap.retain(array.get());
        }
        heap.detach(&context);
    });
    { NativeRegion waiting; worker.join(); }
    require(heap.statistics().first == 7 && heap.statistics().second == 0,
            "Detached thread lost its large allocations");
    for (int i = 0; i < 3; ++i) PrimitiveArray<std::int64_t>::create(32768, "[J");
    const bool correct = heap.statistics().second > 0
            && static_cast<ReferenceArray*>(heap.resolve(retained))->length == 32768;
    heap.release(retained);
    require(correct,
            "Thread detach lost byte pressure or its exported array");
}

void byte_survivors(Heap& heap) {
    LocalRoot<PrimitiveArray<std::uint8_t>> retained(PrimitiveArray<std::uint8_t>::create(8 * 1024 * 1024, "[B"));
    retained->elements[0].set(19);
    allocate<Cell>(); // Collect, then pace the next collection against live bytes.
    const auto collections = heap.statistics().second;
    require(collections == 1, "Large surviving array did not trigger byte pressure");
    for (int i = 0; i < 20; ++i) PrimitiveArray<std::int32_t>::create(32768, "[I");
    require(heap.statistics().second == collections && retained->elements[0].get() == 19,
            "Live bytes caused excessive collections or lost the surviving array");
}

void cache_budget(Heap& heap) {
    std::atomic<int> ready{0};
    std::atomic<bool> resume{false};
    ManagedArena* arenas[2]{};
    auto work = [&](int index) {
        ThreadContext context;
        heap.attach(&context);
        for (int i = 0; i < 160; ++i) ReferenceArray::create(4096, "[Ljava/lang/Object;", 0);
        arenas[index] = context.arena;
        {
            NativeRegion waiting;
            ready.fetch_add(1);
            while (!resume.load()) std::this_thread::yield();
        }
        heap.detach(&context);
    };
    std::thread first(work, 0), second(work, 1);
    while (ready.load() != 2) safepoint();
    heap.collect();
    const auto reserved = arenas[0]->reserved_bytes() + arenas[1]->reserved_bytes()
            + current_thread()->arena->reserved_bytes();
    const bool correct = heap.statistics().first == 0 && reserved <= 8 * 1024 * 1024;
    resume.store(true);
    { NativeRegion waiting; first.join(); second.join(); }
    require(correct, "Empty-page cache multiplied its budget by the thread count");
    require(arenas[0]->reserved_bytes() == 0 && arenas[1]->reserved_bytes() == 0,
            "Detached medium-array arenas retained empty pages");
}
void arena_ownership(Heap& heap) {
    heap.collection_threshold(100000);
    // Allocation alone does not transfer native ownership to the collector.
    auto raw = new Cell();
    allocate<Cell>();
    heap.collect();
    require(raw->value == 41 && heap.statistics().first == 0,
            "Arena sweep collected an unadopted native object");
    heap.adopt(raw);
    {
        LocalRoot<Cell> retained(raw);
        for (int i = 0; i < 3000; ++i) allocate<Cell>();
        heap.collect();
        require(retained->value == 41 && heap.statistics().first == 1,
                "Arena slots lost explicit adoption or a retained object");
        require(current_thread()->allocation_queue.empty(), "Pooled ownership still used a pointer queue");
    }
    heap.collect();
    auto replacement = new Cell();
    heap.collect();
    require(replacement->value == 41 && heap.statistics().first == 0,
            "A reused native block retained a stale GC ownership tag");
    delete replacement;
    {
        LocalRoot<Cell> child(allocate<Cell>());
        LocalRoot<FailingTrace> parent(allocate<FailingTrace>(child.get()));
        child.set(nullptr);
        bool rejected = false;
        try { heap.collect(); } catch (const std::bad_alloc&) { rejected = true; }
        require(rejected && !parent->marked && !parent->child->marked,
                "Failed marking left arena-owned objects marked");
        parent->fail = false;
        heap.collect();
        require(heap.statistics().first == 2 && static_cast<Cell*>(parent->child)->value == 41,
                "Collection after failed marking lost an arena-owned edge");
    }
    heap.collect();
    // Enumeration must retain virtual/native cleanup, both during a collection
    // and when an arena with adopted objects outlives its allocating thread.
    std::atomic<int> destroyed{0};
    {
        LocalRoot<Cleanup> retained(allocate<Cleanup>(destroyed));
        for (int i = 0; i < 1000; ++i) allocate<Cleanup>(destroyed);
        heap.collect();
        require(destroyed.load() == 1000 && *retained->native_resource == 73,
                "Arena sweep skipped derived cleanup or destroyed a rooted object");
    }
    heap.collect();
    require(destroyed.load() == 1001, "Arena sweep destroyed a native resource twice");
    std::thread worker([&] {
        Heap temporary;
        ThreadContext context;
        temporary.attach(&context);
        allocate<Cleanup>(destroyed);
        temporary.detach(&context);
    });
    { NativeRegion waiting; worker.join(); }
    require(destroyed.load() == 1002, "Heap teardown skipped an adopted arena object");
}
}

int main(int argc, char** argv) {
    try {
        if (argc != 2) throw std::invalid_argument("Expected one isolated contract");
        bool rejected = false;
        try { allocate<Cell>(); } catch (const std::logic_error&) { rejected = true; }
        require(rejected, "Unattached allocation was accepted");
        // A distinct heap detects accidental use of the process singleton.
        Heap heap;
        ThreadContext thread;
        heap.attach(&thread);
        const std::string contract = argv[1];
        if (contract == "shells") shells(heap);
        else if (contract == "arrays") arrays(heap);
        else if (contract == "failures") failures(heap);
        else if (contract == "pressure") pressure(heap);
        else if (contract == "concurrent-pressure") concurrent_pressure(heap);
        else if (contract == "automatic-pressure") automatic_pressure(heap);
        else if (contract == "byte-pressure") byte_pressure(heap);
        else if (contract == "byte-detach") byte_detach(heap);
        else if (contract == "byte-survivors") byte_survivors(heap);
        else if (contract == "cache-budget") cache_budget(heap);
        else if (contract == "arena-ownership") arena_ownership(heap);
        else throw std::invalid_argument("Unknown isolated contract");
        heap.collect();
        require(heap.statistics().first == 0, "Isolated contract leaked managed objects");
        heap.detach(&thread);
        std::cout << contract << "-ok\n";
        return 0;
    } catch (const std::exception& error) { std::cerr << error.what() << '\n'; return 1; }
}

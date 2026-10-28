#pragma once
#include "jn_common.hpp"
#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <new>

namespace jnative {
class Heap;

// Managed addresses never move. Arenas belong to the heap, so their pages can
// outlive the thread which allocated them. Only the owner or a stopped-world
// collector mutates page lists; foreign deletion goes through a remote queue.
class ManagedArena {
    friend void* managed_allocate(std::size_t bytes);
    friend void* managed_allocate(std::size_t bytes, ManagedArena* arena);
    friend std::size_t managed_allocation_size(const void* value) noexcept;
    // Match the platform's ordinary-new alignment, including MSVC's 16-byte
    // guarantee despite its 8-byte max_align_t.
#if defined(__STDCPP_DEFAULT_NEW_ALIGNMENT__)
    static constexpr std::size_t alignment = __STDCPP_DEFAULT_NEW_ALIGNMENT__;
#elif defined(_MSC_VER) && defined(_WIN64)
    static constexpr std::size_t alignment = 16;
#else
    static constexpr std::size_t alignment = alignof(std::max_align_t);
#endif
    static constexpr std::size_t align_size(std::size_t bytes) {
        return (bytes + alignment - 1) / alignment * alignment;
    }
    struct Page;
    struct Block {
        std::uintptr_t page_flags;
        explicit Block(Page* page) : page_flags(page ? reinterpret_cast<std::uintptr_t>(page) : 0) {}
        Page* page() const noexcept {
            const auto address = page_flags & ~std::uintptr_t(3);
            return address ? reinterpret_cast<Page*>(address) : nullptr;
        }
        bool owned() const noexcept { return (page_flags & 1) != 0; }
        bool standard_delete() const noexcept { return (page_flags & 2) != 0; }
        // A free-list link occupies dead payload, never a live object header.
        struct FreeLink { Block* next; };
        Block*& next() noexcept { return static_cast<FreeLink*>(static_cast<void*>(this + 1))->next; }
        void link(Block* value) noexcept { ::new (static_cast<void*>(this + 1)) FreeLink{value}; }
    };
    struct alignas(alignment) Page {
        ManagedArena* arena;
        Page* previous;
        Page* next;
        Page* available_previous;
        Page* available_next;
        Block* free;
        std::size_t bin;
        std::size_t stride;
        std::size_t used;
        std::size_t capacity;
        std::size_t live;
        std::size_t bytes;
    };
    static constexpr std::size_t small_limit = 512;
    static constexpr std::size_t small_bins = small_limit / 16 + 1;
    // Reuse individual large blocks up to the default allocation working set.
    static constexpr std::size_t pooled_limit = 4 * 1024 * 1024 - sizeof(void*);
    static constexpr std::size_t bin_count = small_bins + 13;
    Page* available_[bin_count]{};
    Page* pages_ = nullptr;
    std::atomic<Block*> remote_{nullptr};
    std::atomic<std::size_t> reserved_{0};
    Heap* owner_;
    void add_available(Page* page) noexcept;
    void remove_available(Page* page) noexcept;
    void release_local(Block* block) noexcept;
    void drain_remote() noexcept;
    JNATIVE_NOINLINE Page* acquire_page(std::size_t bin, std::size_t stride);
    JNATIVE_FORCEINLINE void* allocate(std::size_t bytes) {
        auto bin = (std::max<std::size_t>(bytes, sizeof(Block*)) + sizeof(Block) - 1) / 16;
        auto stride = align_size((bin + 1) * 16);
        if (bytes > small_limit) {
            bin = small_bins;
            stride = 1024;
            while (stride < bytes + sizeof(Block)) { stride *= 2; ++bin; }
        }
        Page* page = available_[bin];
        if (!page) page = acquire_page(bin, stride);
        Block* block;
        if (page->free) {
            block = page->free;
            page->free = block->next();
        } else {
            auto storage = reinterpret_cast<unsigned char*>(page) + sizeof(Page)
                    + (alignment - sizeof(Block)) + page->used++ * stride;
            block = ::new (storage) Block{page};
        }
        if (++page->live == page->capacity) remove_available(page);
        return block + 1;
    }
public:
    static constexpr bool is_small_size(std::size_t bytes) { return bytes <= small_limit; }
    static constexpr std::size_t small_allocation_size(std::size_t bytes) {
        return align_size(((bytes < sizeof(Block*) ? sizeof(Block*) : bytes) + sizeof(Block) - 1) / 16 * 16 + 16);
    }
    // Protected by the heap lock, not consulted by the allocation fast path.
    bool attached = false;
    explicit ManagedArena(Heap* owner) : owner_(owner) {}
    ~ManagedArena();
    ManagedArena(const ManagedArena&) = delete;
    ManagedArena& operator=(const ManagedArena&) = delete;
    static void deallocate(void* value) noexcept;
    // Dense small slots own adopted objects directly. Sparse larger classes
    // retain pointer queues: scanning their widely spaced headers costs more
    // than queue insertion. Raw native allocations remain unowned.
    static JNATIVE_FORCEINLINE std::size_t adopt(void* value, Heap* heap) noexcept {
        auto block = static_cast<Block*>(value) - 1;
        auto page = block->page();
        if (!page || page->bin >= small_bins || page->arena->owner_ != heap) return 0;
        block->page_flags |= 1;
        return page->stride;
    }
    // The ordinary small-object factory already knows the allocation's arena
    // and size class. Construction has succeeded; ownership can now be set
    // without recovering the page, arena and heap from the allocation header.
    static JNATIVE_FORCEINLINE void adopt_pooled(void* value, bool standard_delete) noexcept {
        (static_cast<Block*>(value) - 1)->page_flags |= standard_delete ? 3 : 1;
    }
    std::pair<std::size_t, std::size_t> sweep(bool destroy_all = false) noexcept;
    void clear_marks() noexcept;
    void trim(std::size_t keep_bytes) noexcept;
    std::size_t reserved_bytes() const noexcept { return reserved_.load(std::memory_order_relaxed); }
};

void* managed_allocate(std::size_t bytes);
// The caller has already validated managed execution. Do not rediscover TLS
// while constructing a shell; the same arena stays attached across a safepoint.
JNATIVE_FORCEINLINE void* managed_allocate(std::size_t bytes, ManagedArena* arena) {
    return arena && bytes <= ManagedArena::pooled_limit ? arena->allocate(bytes) : managed_allocate(bytes);
}
// Variable-size arrays share bin selection and page handling out of line.
// Constant-size shell factories keep the specialized path above.
JNATIVE_NOINLINE void* managed_allocate_dynamic(std::size_t bytes, ManagedArena* arena);
void managed_deallocate(void* value) noexcept;
// Actual managed storage including allocator headers and size-class rounding.
std::size_t managed_allocation_size(const void* value) noexcept;

}

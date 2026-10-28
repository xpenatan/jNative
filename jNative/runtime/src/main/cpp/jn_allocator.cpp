#include "jn_allocator.hpp"
#include "jn_runtime.hpp"

namespace jnative {
namespace {
constexpr std::size_t page_bytes = 16384;
// MSVC's ordinary new supports 16-byte alignment even though max_align_t is
// only 8-byte aligned. Preserve that guarantee for native object subclasses.
#if defined(__STDCPP_DEFAULT_NEW_ALIGNMENT__)
constexpr std::size_t alignment = __STDCPP_DEFAULT_NEW_ALIGNMENT__;
#elif defined(_MSC_VER) && defined(_WIN64)
constexpr std::size_t alignment = 16;
#else
constexpr std::size_t alignment = alignof(std::max_align_t);
#endif
constexpr std::size_t align_size(std::size_t bytes) {
    return (bytes + alignment - 1) / alignment * alignment;
}
constexpr std::size_t large_prefix = align_size(sizeof(std::size_t) + sizeof(void*));
}

// The aligned prefix holds a system allocation's size without enlarging
// pooled-object headers (on 64-bit desktop targets it uses existing padding).
struct LargeAllocationSize { std::size_t bytes; };

void ManagedArena::add_available(Page* page) noexcept {
    page->available_previous = nullptr;
    page->available_next = available_[page->bin];
    if (page->available_next) page->available_next->available_previous = page;
    available_[page->bin] = page;
}
void ManagedArena::remove_available(Page* page) noexcept {
    if (page->available_previous) page->available_previous->available_next = page->available_next;
    else available_[page->bin] = page->available_next;
    if (page->available_next) page->available_next->available_previous = page->available_previous;
}
ManagedArena::Page* ManagedArena::acquire_page(std::size_t bin, std::size_t stride) {
    drain_remote();
    Page* page = available_[bin];
    if (!page) {
        const auto storage_bytes = stride <= 65536 ? std::max(page_bytes, stride * 8)
                : sizeof(Page) + (alignment - sizeof(Block)) + stride;
        page = ::new (platform::allocate(storage_bytes)) Page{};
        page->arena = this;
        page->bin = bin;
        page->stride = stride;
        page->bytes = storage_bytes;
        page->capacity = (storage_bytes - sizeof(Page) - (alignment - sizeof(Block))) / stride;
        page->next = pages_;
        if (pages_) pages_->previous = page;
        pages_ = page;
        reserved_.fetch_add(storage_bytes, std::memory_order_relaxed);
        add_available(page);
    }
    return page;
}
void ManagedArena::release_local(Block* block) noexcept {
    Page* page = block->page();
    if (page->live-- == page->capacity) add_available(page);
    block->link(page->free);
    page->free = block;
}
void ManagedArena::drain_remote() noexcept {
    auto block = remote_.exchange(nullptr, std::memory_order_acquire);
    while (block) {
        auto next = block->next();
        release_local(block);
        block = next;
    }
}
void ManagedArena::deallocate(void* value) noexcept {
    if (!value) return;
    auto block = static_cast<Block*>(value) - 1;
    auto page = block->page();
    if (!page) {
        platform::deallocate(static_cast<unsigned char*>(value) - large_prefix);
        return;
    }
    // Foreign raw frees never write this word: an unowned block may be on the
    // remote queue while a collector enumerates arena slots.
    if (block->owned()) block->page_flags &= ~std::uintptr_t(3);
    auto arena = page->arena;
    auto thread = current_thread();
    if (thread && (thread->sweeping || (thread->arena == arena
            && thread->state.load(std::memory_order_relaxed) == ThreadState::running_managed))) {
        arena->release_local(block);
    } else {
        // Live counts include pending remote frees, preventing premature page
        // reclamation. Producers never dereference the old stack head.
        block->link(nullptr);
        auto head = arena->remote_.load(std::memory_order_relaxed);
        do { block->next() = head; }
        while (!arena->remote_.compare_exchange_weak(head, block,
                std::memory_order_release, std::memory_order_relaxed));
    }
}
std::pair<std::size_t, std::size_t> ManagedArena::sweep(bool destroy_all) noexcept {
    std::size_t count = 0, bytes = 0;
    for (auto page = pages_; page; page = page->next) {
        // Larger allocations are owned by the heap's pointer queues; none of
        // their slots can carry arena ownership, so do not scan their payloads.
        if (page->bin >= small_bins || !page->live) continue;
        auto storage = reinterpret_cast<unsigned char*>(page) + sizeof(Page) + (alignment - sizeof(Block));
        for (std::size_t i = 0; i < page->used; ++i, storage += page->stride) {
            auto block = reinterpret_cast<Block*>(storage);
            if (!block->owned()) continue;
            auto object = static_cast<Object*>(static_cast<void*>(block + 1));
            if (object->marked && !destroy_all) {
                object->marked = false;
                ++count;
                bytes += page->stride;
            } else if (block->standard_delete()) {
                // Virtual destruction still releases derived/native resources.
                // The factory proved that deallocation is Object's ordinary
                // arena release, so no TLS lookup or remote-free dispatch is
                // needed while this collector owns the stopped arena.
                object->~Object();
                block->page_flags &= ~std::uintptr_t(3);
                release_local(block);
            } else delete object;
        }
    }
    return {count, bytes};
}
void ManagedArena::clear_marks() noexcept {
    for (auto page = pages_; page; page = page->next) {
        if (page->bin >= small_bins || !page->live) continue;
        auto storage = reinterpret_cast<unsigned char*>(page) + sizeof(Page) + (alignment - sizeof(Block));
        for (std::size_t i = 0; i < page->used; ++i, storage += page->stride) {
            auto block = reinterpret_cast<Block*>(storage);
            if (block->owned()) static_cast<Object*>(static_cast<void*>(block + 1))->marked = false;
        }
    }
}
void ManagedArena::trim(std::size_t keep_bytes) noexcept {
    drain_remote();
    auto page = pages_;
    while (page) {
        auto next = page->next;
        if (!page->live) {
            if (keep_bytes >= page->bytes) {
                keep_bytes -= page->bytes;
            } else {
                remove_available(page);
                if (page->previous) page->previous->next = next;
                else pages_ = next;
                if (next) next->previous = page->previous;
                const auto storage_bytes = page->bytes;
                page->~Page();
                platform::deallocate(page);
                reserved_.fetch_sub(storage_bytes, std::memory_order_relaxed);
            }
        }
        page = next;
    }
}
ManagedArena::~ManagedArena() {
    // Normal heap teardown destroys objects first. Windows immediate process
    // exit can also run CRT destructors after killing daemon threads, without
    // unwinding their attachments or publishing an in-flight allocation. Arena
    // teardown therefore releases all storage; trim() alone requires no live
    // slots and is used for reclamation while the runtime remains usable.
    while (pages_) {
        auto next = pages_->next;
        pages_->~Page();
        platform::deallocate(pages_);
        pages_ = next;
    }
}
void* managed_allocate(std::size_t bytes) {
    if (bytes <= ManagedArena::pooled_limit) {
        auto thread = current_thread();
        if (thread && thread->arena
                && thread->state.load(std::memory_order_relaxed) == ThreadState::running_managed)
            return thread->arena->allocate(bytes);
    }
    // Preserve a single shell/payload allocation for large arrays and raw
    // runtime objects created without a managed attachment.
    if (bytes > std::numeric_limits<std::size_t>::max() - large_prefix)
        throw std::bad_alloc();
    auto storage = static_cast<unsigned char*>(platform::allocate(large_prefix + bytes));
    static_assert(sizeof(LargeAllocationSize) + sizeof(ManagedArena::Block) <= large_prefix,
            "Large allocation size must fit in the alignment prefix");
    ::new (storage) LargeAllocationSize{large_prefix + bytes};
    auto block = ::new (storage + large_prefix - sizeof(ManagedArena::Block)) ManagedArena::Block{nullptr};
    return block + 1;
}
void* managed_allocate_dynamic(std::size_t bytes, ManagedArena* arena) {
    return managed_allocate(bytes, arena);
}
void managed_deallocate(void* value) noexcept { ManagedArena::deallocate(value); }
std::size_t managed_allocation_size(const void* value) noexcept {
    auto block = static_cast<const ManagedArena::Block*>(value) - 1;
    if (block->page()) return block->page()->stride;
    auto header = static_cast<const void*>(static_cast<const unsigned char*>(value) - large_prefix);
    return static_cast<const LargeAllocationSize*>(header)->bytes;
}

}

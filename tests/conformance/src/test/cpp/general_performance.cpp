#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

// Direct C++ reference for the Java fixture's observable workloads.
// This does not model Java collection, GC, bounds checks or exception contracts.
namespace {
volatile std::int64_t sink;
std::atomic<void*> escaped{nullptr};
struct Box { int value; };
struct BoxHash { std::size_t operator()(const Box* value) const { return static_cast<std::size_t>(value->value); } };
struct BoxEqual { bool operator()(const Box* a, const Box* b) const { return a->value == b->value; } };
struct BoxMap {
    std::unordered_map<Box*, Box*, BoxHash, BoxEqual> entries;
    ~BoxMap() {
        for (const auto& entry : entries) {
            if (entry.first->value >= 128) delete entry.first;
            if (entry.second->value >= 128) delete entry.second;
        }
    }
};
std::int32_t signed32(std::uint32_t value) {
    std::int32_t result;
    std::memcpy(&result, &value, sizeof(result));
    return result;
}
std::u16string text(int size, bool repeated) {
    std::u16string result(static_cast<std::size_t>(size), 0);
    for (int i = 0; i < size; ++i) result[i] = repeated ? u'a' : static_cast<char16_t>(u'a' + (i * 13 + i / 7) % 23);
    return result;
}
struct Operation {
    virtual int apply(int value) const = 0;
    virtual ~Operation() = default;
};
struct Add final : Operation { int apply(int value) const override { return value + 17; } };
struct Multiply final : Operation { int apply(int value) const override { return value * 3; } };

#if defined(JNATIVE_LIST_SMALL) || defined(JNATIVE_LIST_STACK)
#if defined(JNATIVE_LIST_SMALL)
// Native small-list representation: inline pointers, then geometric
// heap growth. realloc may expand in place; every append still stores a value.
#ifndef JNATIVE_LIST_INLINE_CAPACITY
#define JNATIVE_LIST_INLINE_CAPACITY 8
#endif
class SmallPointerList {
    static constexpr std::size_t inline_capacity = JNATIVE_LIST_INLINE_CAPACITY;
    static_assert(inline_capacity > 0);
    const std::u16string* local_[inline_capacity];
    const std::u16string** data_ = local_;
    std::size_t size_ = 0;
    std::size_t capacity_ = inline_capacity;
public:
    SmallPointerList() = default;
    SmallPointerList(const SmallPointerList&) = delete;
    SmallPointerList& operator=(const SmallPointerList&) = delete;
    ~SmallPointerList() { if (data_ != local_) std::free(data_); }
    SmallPointerList& operator=(SmallPointerList&& source) noexcept {
        if (this == &source) return *this;
        if (data_ != local_) std::free(data_);
        size_ = source.size_;
        capacity_ = source.capacity_;
        if (source.data_ == source.local_) {
            data_ = local_;
            std::memcpy(data_, source.data_, size_ * sizeof(*data_));
        } else {
            data_ = source.data_;
        }
        source.data_ = source.local_;
        source.size_ = 0;
        source.capacity_ = inline_capacity;
        return *this;
    }
    void push_back(const std::u16string* value) {
        if (size_ == capacity_) {
            const auto next = capacity_ * 2;
            const bool local = data_ == local_;
            auto storage = static_cast<const std::u16string**>(
                    local ? std::malloc(next * sizeof(*data_))
                          : std::realloc(data_, next * sizeof(*data_)));
            if (!storage) throw std::bad_alloc();
            if (local) std::memcpy(storage, local_, size_ * sizeof(*data_));
            data_ = storage;
            capacity_ = next;
        }
        data_[size_++] = value;
    }
    std::size_t size() const { return size_; }
    const std::u16string* operator[](std::size_t index) const { return data_[index]; }
};
using TunedList = SmallPointerList;
#else
using TunedList = std::vector<const std::u16string*>;
#endif

void prepare_list(TunedList& list, int size) {
#if defined(JNATIVE_LIST_STACK)
    // Match ArrayList's initial capacity, without reserving the final size.
    if (size) list.reserve(10);
#endif
}

void verify_list_storage() {
    const std::u16string values[] = {u"first", u"second", u"third"};
    TunedList retained;
    for (int size : {0, 1, 7, 8, 9, 10, 11, 15, 16, 17, 31, 32, 33, 63, 64, 65,
                     255, 256, 257, 4096, 65537, 0, 1, 9}) {
        TunedList next;
        prepare_list(next, size);
        for (int i = 0; i < size; ++i) next.push_back(&values[i % 3]);
        for (int i = 0; i < size; ++i)
            if (next[i] != &values[i % 3]) throw std::runtime_error("list append contents");
        retained = std::move(next);
        if (retained.size() != static_cast<std::size_t>(size)) throw std::runtime_error("list move size");
        for (int i = 0; i < size; ++i)
            if (retained[i] != &values[i % 3]) throw std::runtime_error("list move contents");
        // The moved-from list must be reusable without aliasing retained storage.
        next.push_back(&values[2]);
        if (next.size() != 1 || next[0] != &values[2]) throw std::runtime_error("list move reuse");
        for (int i = 0; i < size; ++i)
            if (retained[i] != &values[i % 3]) throw std::runtime_error("list move aliasing");
    }
    std::cout << "LIST-VERIFIED\n";
}
#endif

void sample(const std::string& operation, int size, int rounds, bool report) {
    const bool search = operation.compare(0, 7, "search-") == 0;
    const bool hash = operation == "hash-cold";
    const auto haystack = search ? text(size, operation == "search-prefix") + u'z' : u"";
    const auto needle = operation == "search-prefix" ? text(std::max(1, size / 8), true) + u'z' : u"absent";
    std::vector<std::u16string> texts;
    if (hash) {
        texts.reserve(rounds);
        auto value = text(size, false);
        for (int r = 0; r < rounds; ++r) {
            if (size) value[size - 1] = static_cast<char16_t>(r);
            texts.push_back(value);
        }
    }
    int marker = 0;
    const std::u16string marker_text = u"marker", list_value = u"value";
    Box cache[128];
    for (int i = 0; i < 128; ++i) cache[i].value = i;
    std::unique_ptr<std::vector<void*>> references;
    std::unique_ptr<std::vector<const std::u16string*>> strings, list;
    std::unique_ptr<BoxMap> map;
#if defined(JNATIVE_LIST_SMALL) || defined(JNATIVE_LIST_STACK)
    TunedList tuned_list;
#endif
    const auto start = std::chrono::steady_clock::now();
    std::int64_t sum = 0;
    if (operation == "reference-array") {
        for (int r = 0; r < rounds; ++r) {
            auto array = std::make_unique<std::vector<void*>>(size, nullptr);
            if (size) (*array)[size - 1] = &marker;
            escaped.store(array.get());
            sum += array->size();
            if (size && (*array)[size - 1] == &marker) ++sum;
            references = std::move(array);
        }
    } else if (operation == "typed-array") {
        for (int r = 0; r < rounds; ++r) {
            auto array = std::make_unique<std::vector<const std::u16string*>>(size, nullptr);
            if (size) (*array)[size - 1] = &marker_text;
            escaped.store(array.get());
            sum += array->size();
            if (size) sum += (*array)[size - 1]->size();
            strings = std::move(array);
        }
    } else if (operation == "list-grow") {
#if defined(JNATIVE_LIST_SMALL) || defined(JNATIVE_LIST_STACK)
        for (int r = 0; r < rounds; ++r) {
            TunedList value;
            prepare_list(value, size);
            for (int i = 0; i < size; ++i) value.push_back(&list_value);
            tuned_list = std::move(value);
            escaped.store(&tuned_list);
            sum += tuned_list.size();
        }
#else
        for (int r = 0; r < rounds; ++r) {
            auto value = std::make_unique<std::vector<const std::u16string*>>();
            for (int i = 0; i < size; ++i) value->push_back(&list_value);
            escaped.store(value.get());
            sum += value->size();
            list = std::move(value);
        }
#endif
    } else if (operation == "map-grow") {
        for (int r = 0; r < rounds; ++r) {
            auto value = std::make_unique<BoxMap>();
            for (int i = 0; i < size; ++i) {
                auto* key = i < 128 ? &cache[i] : new Box{i};
                auto* entry = i + 1 < 128 ? &cache[i + 1] : new Box{i + 1};
                value->entries.emplace(key, entry);
            }
            escaped.store(value.get());
            sum += value->entries.size();
            map = std::move(value);
        }
    } else if (search) {
        for (int r = 0; r < rounds; ++r) {
            const auto position = haystack.find(needle, r & 1);
            sum += position == std::u16string::npos ? -1 : static_cast<std::int64_t>(position);
        }
    } else if (hash) {
        for (const auto& value : texts) {
            std::uint32_t code = 0;
            for (char16_t unit : value) code = code * UINT32_C(31) + static_cast<std::uint32_t>(unit);
            sum += signed32(code);
        }
    } else if (operation == "arithmetic") {
        for (int r = 0; r < rounds; ++r) {
            auto value = static_cast<std::uint32_t>(r);
            for (int i = 0; i < size; ++i) value = value * UINT32_C(1664525) + UINT32_C(1013904223);
            sum += signed32(value);
        }
    } else if (operation == "dispatch") {
        Add add;
        Multiply multiply;
        const Operation* operations[] = {&add, &multiply};
        for (int r = 0; r < rounds; ++r)
            for (int i = 0; i < size; ++i) sum += operations[i & 1]->apply(i);
    } else throw std::invalid_argument(operation);
    const auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count();
    sink = sum;
    escaped.store(nullptr);
#if defined(JNATIVE_LIST_SMALL) || defined(JNATIVE_LIST_STACK)
    if (operation == "list-grow") {
        for (std::size_t i = 0; i < tuned_list.size(); ++i)
            if (tuned_list[i] != &list_value) throw std::runtime_error("measured list contents");
    }
#endif
    if (report) std::cout << "BENCH," << operation << ',' << size << ',' << rounds << ',' << elapsed << ',' << sum << '\n';
}
}
int main(int argc, char** argv) {
#if defined(JNATIVE_LIST_SMALL) || defined(JNATIVE_LIST_STACK)
    if (argc == 2 && std::string(argv[1]) == "--verify-list") {
        verify_list_storage();
        return 0;
    }
#endif
    if (argc != 5) return 2;
    const std::string operation = argv[1];
    const int size = std::stoi(argv[2]), rounds = std::stoi(argv[3]), samples = std::stoi(argv[4]);
    for (int i = 0; i < 3; ++i) sample(operation, size, rounds, false);
    for (int i = 0; i < samples; ++i) sample(operation, size, rounds, true);
}

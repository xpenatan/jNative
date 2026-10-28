#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
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
        for (int r = 0; r < rounds; ++r) {
            auto value = std::make_unique<std::vector<const std::u16string*>>();
            for (int i = 0; i < size; ++i) value->push_back(&list_value);
            escaped.store(value.get());
            sum += value->size();
            list = std::move(value);
        }
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
    if (report) std::cout << "BENCH," << operation << ',' << size << ',' << rounds << ',' << elapsed << ',' << sum << '\n';
}
}
int main(int argc, char** argv) {
    if (argc != 5) return 2;
    const std::string operation = argv[1];
    const int size = std::stoi(argv[2]), rounds = std::stoi(argv[3]), samples = std::stoi(argv[4]);
    for (int i = 0; i < 3; ++i) sample(operation, size, rounds, false);
    for (int i = 0; i < samples; ++i) sample(operation, size, rounds, true);
}

#include <chrono>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

// Matching operations and checksums, with separately allocated keys/values.
// std::unordered_map has its own bucket policy; this is a C++ reference, not
// a Java GC, bounds-check or exception implementation.
namespace {
volatile std::int64_t sink;
std::int32_t signed32(std::uint32_t value) {
    std::int32_t result;
    std::memcpy(&result, &value, sizeof(result));
    return result;
}
enum class Kind { object, key, string };
struct Object {
    const Kind kind;
    explicit Object(Kind kind = Kind::object) : kind(kind) {}
    virtual ~Object() = default;
    std::int32_t identityHash() const {
        return signed32(static_cast<std::uint32_t>(std::hash<const void*>{}(this)));
    }
    virtual std::int32_t hashCode() const { return identityHash(); }
    virtual bool equals(const Object* other) const { return this == other; }
};
struct Key final : Object {
    const int id;
    const std::int32_t hash;
    Key(int id, std::int32_t hash) : Object(Kind::key), id(id), hash(hash) {}
    std::int32_t hashCode() const override { return hash; }
    bool equals(const Object* other) const override {
        return other && other->kind == Kind::key && static_cast<const Key*>(other)->id == id;
    }
};
struct String final : Object {
    const std::string value;
    const std::int32_t hash;
    static std::int32_t javaHash(const std::string& value) {
        std::uint32_t code = 0;
        for (unsigned char unit : value) code = code * 31u + unit;
        return signed32(code);
    }
    explicit String(std::string value) : Object(Kind::string), value(std::move(value)), hash(javaHash(this->value)) {}
    std::int32_t hashCode() const override { return hash; }
    bool equals(const Object* other) const override {
        return other && other->kind == Kind::string && static_cast<const String*>(other)->value == value;
    }
};
struct Value { const int number; explicit Value(int number) : number(number) {} };
struct Hash {
    std::size_t operator()(const Object* object) const { return static_cast<std::uint32_t>(object->hashCode()); }
};
struct Equal {
    bool operator()(const Object* first, const Object* second) const { return first == second || first->equals(second); }
};
struct State {
    std::vector<std::unique_ptr<Object>> owned;
    std::vector<std::unique_ptr<Value>> values;
    std::vector<Object*> keys, queries;
    std::vector<Key*> typed;
    std::vector<std::int32_t> expectedHashes;
    std::unordered_map<Object*, Value*, Hash, Equal> map;

    Object* keep(std::unique_ptr<Object> object) {
        Object* result = object.get();
        owned.push_back(std::move(object));
        return result;
    }
    State(const std::string& operation, int size) : keys(size), queries(size), typed(size), expectedHashes(size) {
        bool lookup = operation.compare(0, 4, "get-") == 0;
        bool objects = operation == "hash-object" || operation == "hash-identity" || operation == "get-object";
        bool strings = operation == "hash-string" || operation == "get-string";
        int chain = operation.compare(0, 15, "get-collision8-") == 0 ? 8 : 4;
        bool equal = operation.size() >= 6 && operation.compare(operation.size() - 6, 6, "-equal") == 0;
        bool missing = operation.size() >= 5 && operation.compare(operation.size() - 5, 5, "-miss") == 0;
        if (lookup) map.reserve(static_cast<std::size_t>(size) * 2);
        for (int i = 0; i < size; ++i) {
            if (objects) keys[i] = keep(std::make_unique<Object>());
            else if (strings) keys[i] = keep(std::make_unique<String>("probe-key-" + std::to_string(i)));
            else {
                auto hash = lookup ? i / chain : signed32(static_cast<std::uint32_t>(i) * UINT32_C(0x9e3779b9));
                keys[i] = keep(std::make_unique<Key>(i, hash));
                typed[i] = static_cast<Key*>(keys[i]);
            }
            expectedHashes[i] = keys[i]->hashCode();
            queries[i] = equal || missing ? keep(std::make_unique<Key>(missing ? i + size : i, i / chain)) : keys[i];
            if (lookup) {
                values.push_back(std::make_unique<Value>(i + 1));
                map.emplace(keys[i], values.back().get());
            }
        }
    }
};
std::int64_t hashErased(const std::vector<Object*>& keys, int rounds) {
    std::int64_t sum = 0;
    for (int r = 0; r < rounds; ++r)
        for (Object* key : keys) sum += key->hashCode();
    return sum;
}
std::int64_t hashTyped(const std::vector<Key*>& keys, int rounds) {
    std::int64_t sum = 0;
    for (int r = 0; r < rounds; ++r)
        for (Key* key : keys) sum += key->hashCode();
    return sum;
}
std::int64_t hashObject(const State& state, int rounds, bool identity) {
    std::int64_t sum = 0;
    for (int r = 0; r < rounds; ++r)
        for (std::size_t i = 0; i < state.keys.size(); ++i) {
            auto hash = identity ? state.keys[i]->identityHash() : state.keys[i]->hashCode();
            sum += hash == state.expectedHashes[i] ? static_cast<std::int64_t>(i + 1) : -1;
        }
    return sum;
}
std::int64_t lookup(const State& state, int rounds) {
    std::int64_t sum = 0;
    for (int r = 0; r < rounds; ++r)
        for (Object* key : state.queries) {
            auto entry = state.map.find(key);
            if (entry != state.map.end()) sum += entry->second->number;
        }
    return sum;
}
std::int64_t run(const std::string& operation, const State& state, int rounds) {
    if (operation == "hash-erased" || operation == "hash-string") return hashErased(state.keys, rounds);
    if (operation == "hash-typed") return hashTyped(state.typed, rounds);
    if (operation == "hash-object") return hashObject(state, rounds, false);
    if (operation == "hash-identity") return hashObject(state, rounds, true);
    if (operation == "get-object" || operation == "get-string"
            || operation == "get-collision4-same" || operation == "get-collision4-equal" || operation == "get-collision4-miss"
            || operation == "get-collision8-same" || operation == "get-collision8-equal" || operation == "get-collision8-miss")
        return lookup(state, rounds);
    throw std::invalid_argument("Unknown probe");
}
void sample(const std::string& operation, int size, int rounds, bool report) {
    State state(operation, size);
    const auto start = std::chrono::steady_clock::now();
    const auto sum = run(operation, state, rounds);
    const auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count();
    sink = sum;
    if (report) std::cout << "BENCH," << operation << ',' << size << ',' << rounds << ',' << elapsed << ',' << sum << '\n';
}
}
int main(int argc, char** argv) {
    if (argc != 5) return 2;
    const std::string operation = argv[1];
    int size = std::stoi(argv[2]), rounds = std::stoi(argv[3]), samples = std::stoi(argv[4]);
    if (size < 1 || rounds < 1 || samples < 1) return 2;
    for (int i = 0; i < 3; ++i) sample(operation, size, rounds, false);
    for (int i = 0; i < samples; ++i) sample(operation, size, rounds, true);
}

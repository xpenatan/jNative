#include <chrono>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

// Workload counterpart of LookupIsolation.java, with separate key/value
// allocations. Bucket policy and Java GC/exception semantics are not emulated.
namespace {
volatile std::int64_t sink;
struct Object {
    enum class Kind { key };
    const Kind kind;
    explicit Object(Kind kind) : kind(kind) {}
    virtual ~Object() = default;
    virtual std::int32_t hashCode() const = 0;
    virtual bool equals(const Object* other) const = 0;
};
struct Key final : Object {
    const int id;
    explicit Key(int id) : Object(Kind::key), id(id) {}
    std::int32_t hashCode() const override {
        const auto bits = static_cast<std::uint32_t>(id) * UINT32_C(0x9e3779b9);
        std::int32_t result;
        std::memcpy(&result, &bits, sizeof(result));
        return result;
    }
    bool equals(const Object* other) const override {
        return other && other->kind == Kind::key && static_cast<const Key*>(other)->id == id;
    }
};
struct Value { const int number; explicit Value(int number) : number(number) {} };
struct Hash {
    std::size_t operator()(const Object* key) const { return static_cast<std::uint32_t>(key->hashCode()); }
};
struct Equal {
    bool operator()(const Object* first, const Object* second) const { return first == second || first->equals(second); }
};
struct State {
    std::vector<std::unique_ptr<Key>> ownedKeys;
    std::vector<std::unique_ptr<Value>> ownedValues;
    std::vector<Object*> keys, equal, missing;
    std::vector<Value*> values;
    std::unordered_map<Object*, Value*, Hash, Equal> map;
    Object* key(int id) {
        ownedKeys.push_back(std::make_unique<Key>(id));
        return ownedKeys.back().get();
    }
    explicit State(int size) : keys(size), equal(size), missing(size), values(size) {
        map.reserve(static_cast<std::size_t>(size) * 2);
        for (int i = 0; i < size; ++i) {
            keys[i] = key(i); equal[i] = key(i); missing[i] = key(i + size);
            ownedValues.push_back(std::make_unique<Value>(i + 1));
            values[i] = ownedValues.back().get();
            map.emplace(keys[i], values[i]);
        }
    }
};
std::int64_t hash(const std::vector<Object*>& keys, int rounds) {
    std::int64_t sum = 0;
    for (int r = 0; r < rounds; ++r)
        for (Object* key : keys) sum += key->hashCode();
    return sum;
}
std::int64_t get(const State& state, const std::vector<Object*>& keys, int rounds) {
    std::int64_t sum = 0;
    for (int r = 0; r < rounds; ++r)
        for (Object* key : keys) {
            auto entry = state.map.find(key);
            if (entry != state.map.end()) sum += entry->second->number;
        }
    return sum;
}
std::int64_t replace(State& state, int rounds) {
    std::int64_t sum = 0;
    for (int r = 0; r < rounds; ++r)
        for (std::size_t i = 0; i < state.keys.size(); ++i) {
            auto entry = state.map.find(state.keys[i]);
            Value* previous = entry->second;
            entry->second = state.values[i];
            sum += previous->number;
        }
    return sum;
}
std::int64_t scan(const std::vector<Value*>& values, int rounds) {
    std::int64_t sum = 0;
    for (int r = 0; r < rounds; ++r)
        for (std::size_t i = 0; i < values.size(); ++i) {
            if ((i & 1) == 0) sum += values[i]->number;
            else sum -= values[i]->number;
        }
    return sum;
}
std::int64_t run(const std::string& operation, State& state, int rounds) {
    if (operation == "hash") return hash(state.keys, rounds);
    if (operation == "get-same") return get(state, state.keys, rounds);
    if (operation == "get-equal") return get(state, state.equal, rounds);
    if (operation == "get-miss") return get(state, state.missing, rounds);
    if (operation == "put-replace") return replace(state, rounds);
    if (operation == "scan") return scan(state.values, rounds);
    throw std::invalid_argument("Unknown probe");
}
void sample(const std::string& operation, int size, int rounds, bool report) {
    State state(size);
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

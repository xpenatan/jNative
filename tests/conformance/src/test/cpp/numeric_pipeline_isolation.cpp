#include <array>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
std::atomic<void*> published;
std::array<float, 16384> table;
float first(float angle) { return table[static_cast<int>(angle * (16384.f / 360.f)) & 16383]; }
float second(float angle) { return table[static_cast<int>((angle + 90.f) * (16384.f / 360.f)) & 16383]; }

struct Item {
    float angle = 0, scale = 1, x = 0, y = 0, first = 0, second = 0;
    std::unique_ptr<float[]> values = std::make_unique<float[]>(24);
    void update(float amount) {
        angle += 0.25f;
        if(angle >= 360.f) angle -= 360.f;
        scale = amount;
    }
    void lookup() { first = ::first(angle); second = ::second(angle); }
    void pack() {
        float left = -16.f * scale, right = 16.f * scale;
        float bottom = -8.f * scale, top = 8.f * scale;
        values[0] = first * left - second * bottom + x;
        values[1] = second * left + first * bottom + y;
        values[6] = first * left - second * top + x;
        values[7] = second * left + first * top + y;
        values[12] = first * right - second * top + x;
        values[13] = second * right + first * top + y;
        values[18] = first * right - second * bottom + x;
        values[19] = second * right + first * bottom + y;
    }
};

using Items = std::vector<std::unique_ptr<Item>>;
struct Holder { Items* items; };
void repeatedLocal(Items& items, int rounds) {
    for(int r = 0; r < rounds; r++) {
        float scale = 0.5f + (r & 31) / 64.f;
        for(std::size_t i = 0; i < items.size(); i++) {
            items[i]->update(scale); items[i]->pack();
        }
        published.store(items.data());
    }
}
void repeatedField(Holder& holder, int rounds) {
    for(int r = 0; r < rounds; r++) {
        float scale = 0.5f + (r & 31) / 64.f;
        for(std::size_t i = 0; i < holder.items->size(); i++) {
            (*holder.items)[i]->update(scale); (*holder.items)[i]->pack();
        }
        published.store(&holder);
    }
}
void fields(Items& items, int rounds) {
    for(int r = 0; r < rounds; r++) {
        float scale = 0.5f + (r & 31) / 64.f;
        for(auto& item : items) item->update(scale);
        published.store(items.data());
    }
}
void lookup(Items& items, int rounds) {
    for(int r = 0; r < rounds; r++) {
        for(auto& item : items) { item->angle += 0.25f; item->lookup(); }
        published.store(items.data());
    }
}
void pack(Items& items, int rounds) {
    for(int r = 0; r < rounds; r++) {
        float scale = 0.5f + (r & 31) / 64.f;
        for(auto& item : items) { item->scale = scale; item->pack(); }
        published.store(items.data());
    }
}
void pipeline(Items& items, std::vector<float>& output, int rounds) {
    for(int r = 0; r < rounds; r++) {
        float scale = 0.5f + (r & 31) / 64.f;
        for(std::size_t i = 0; i < items.size(); i++) {
            auto& item = items[i];
            item->update(scale); item->lookup(); item->pack();
            std::memcpy(output.data() + i * 24, item->values.get(), 24 * sizeof(float));
        }
        published.store(output.data());
    }
}
std::uint64_t mix(std::uint64_t hash, float value) {
    std::int32_t bits;
    std::memcpy(&bits, &value, sizeof bits);
    return hash * 31 + static_cast<std::uint64_t>(static_cast<std::int64_t>(bits));
}
void sample(const std::string& operation, int size, int rounds, bool report) {
    Items items;
    std::vector<float> output(size * 24);
    for(int i = 0; i < size; i++) {
        auto item = std::make_unique<Item>();
        item->angle = (i & 255) * 0.5f;
        item->x = (i & 31) * 0.25f;
        item->y = (i & 15) * -0.5f;
        item->lookup();
        items.push_back(std::move(item));
    }
    Holder holder{&items};
    auto start = std::chrono::steady_clock::now();
    if(operation == "fields") fields(items, rounds);
    else if(operation == "lookup") lookup(items, rounds);
    else if(operation == "pack") pack(items, rounds);
    else if(operation == "pipeline") pipeline(items, output, rounds);
    else if(operation == "repeat-local") repeatedLocal(items, rounds);
    else if(operation == "repeat-field") repeatedField(holder, rounds);
    else throw std::invalid_argument(operation);
    auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count();
    std::uint64_t hash = 1;
    for(auto& item : items) {
        hash = mix(hash, item->angle); hash = mix(hash, item->scale);
        hash = mix(hash, item->x); hash = mix(hash, item->y);
        hash = mix(hash, item->first); hash = mix(hash, item->second);
        for(int i = 0; i < 24; i++) hash = mix(hash, item->values[i]);
    }
    for(float value : output) hash = mix(hash, value);
    published.store(nullptr);
    std::int64_t signedHash;
    std::memcpy(&signedHash, &hash, sizeof hash);
    if(report) std::cout << "BENCH," << operation << ',' << size << ',' << rounds << ',' << elapsed << ',' << signedHash << '\n';
}
}
int main(int argc, char** argv) {
    if(argc != 5) return 2;
    std::string operation = argv[1];
    int size = std::stoi(argv[2]), rounds = std::stoi(argv[3]), samples = std::stoi(argv[4]);
    if(size < 1 || rounds < 1 || samples < 1) return 2;
    for(int i = 0; i < 16384; i++) table[i] = ((i & 255) - 128) / 128.f;
    for(int i = 0; i < 3; i++) sample(operation, size, rounds, false);
    for(int i = 0; i < samples; i++) sample(operation, size, rounds, true);
}

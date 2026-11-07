#pragma once
#include <cstdint>
#include <cstring>

namespace jnative {
template<class T> class OrdinaryField;
namespace detail {
template<class T> T transfer_load(const T& element) { return element; }
template<class T> T transfer_load(const OrdinaryField<T>& element) { return element.get(); }
template<class T> void transfer_store(T& element, T value) { element = value; }
template<class T> void transfer_store(OrdinaryField<T>& element, T value) { element.set(value); }

// Callers validate ranges, representation and lifetime. Each atomic element
// access remains ordered. Byte storage may be unaligned. No atomic storage
// representation is ever read or written through a plain payload pointer.
template<class T, bool Write, bool Reverse, class Element>
inline void transfer_element(Element& element, std::uint8_t* buffer) {
    T value;
    if (Write) value = transfer_load(element);
    auto representation = reinterpret_cast<unsigned char*>(&value);
    if (Reverse) {
        for (std::size_t byte = 0; byte < sizeof(T); ++byte) {
            if (Write) buffer[byte] = representation[sizeof(T) - 1 - byte];
            else representation[sizeof(T) - 1 - byte] = buffer[byte];
        }
    } else {
        // memcpy supports unaligned slices and preserves floating-point bits.
        if (Write) std::memcpy(buffer, &value, sizeof(T));
        else std::memcpy(&value, buffer, sizeof(T));
    }
    if (!Write) transfer_store(element, value);
}

template<class T, bool Write, bool Reverse, class Element>
void transfer_elements(Element* array, std::uint8_t* buffer, std::int32_t count) {
    // Keep atomic accesses and their element order while reducing loop overhead.
    // Never copy the storage representation of an atomic-backed Java array.
    // Native-order writes need no byte shuffling and benefit from a larger block.
    if (Write && !Reverse) {
        while (count >= 16) {
            transfer_element<T, Write, Reverse>(array[0], buffer);
            transfer_element<T, Write, Reverse>(array[1], buffer + sizeof(T));
            transfer_element<T, Write, Reverse>(array[2], buffer + 2 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[3], buffer + 3 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[4], buffer + 4 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[5], buffer + 5 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[6], buffer + 6 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[7], buffer + 7 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[8], buffer + 8 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[9], buffer + 9 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[10], buffer + 10 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[11], buffer + 11 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[12], buffer + 12 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[13], buffer + 13 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[14], buffer + 14 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[15], buffer + 15 * sizeof(T));
            array += 16;
            buffer += 16 * sizeof(T);
            count -= 16;
        }
    }
    while (count >= 4) {
        transfer_element<T, Write, Reverse>(array[0], buffer);
        transfer_element<T, Write, Reverse>(array[1], buffer + sizeof(T));
        transfer_element<T, Write, Reverse>(array[2], buffer + 2 * sizeof(T));
        transfer_element<T, Write, Reverse>(array[3], buffer + 3 * sizeof(T));
        array += 4;
        buffer += 4 * sizeof(T);
        count -= 4;
    }
    for (std::int32_t i = 0; i < count; ++i) {
        transfer_element<T, Write, Reverse>(array[i], buffer + i * sizeof(T));
    }
}


}
}

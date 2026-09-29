# Probe the selected target compiler with its current toolchain, sysroot, flags,
# language mode and configuration. These compile/link checks never run target code.
include(CheckCXXSourceCompiles)
set(JNATIVE_ATOMIC_LIBRARIES "" CACHE STRING "Target libraries needed for atomic operations, for example atomic")
set(_jn_saved_required_libraries "${CMAKE_REQUIRED_LIBRARIES}")
list(APPEND CMAKE_REQUIRED_LIBRARIES ${JNATIVE_ATOMIC_LIBRARIES})
if(CMAKE_BUILD_TYPE)
    set(CMAKE_TRY_COMPILE_CONFIGURATION "${CMAKE_BUILD_TYPE}")
endif()
set(JNATIVE_FEATURES "AUTO" CACHE STRING "AUTO selects available helpers; PORTABLE keeps common implementations")
set_property(CACHE JNATIVE_FEATURES PROPERTY STRINGS AUTO PORTABLE)
if(NOT JNATIVE_FEATURES MATCHES "^(AUTO|PORTABLE)$")
    message(FATAL_ERROR "JNATIVE_FEATURES must be AUTO or PORTABLE")
endif()
# Reconfigure is deliberately authoritative; a changed SDK or flags cannot reuse
# a stale feature result from the CMake cache.
unset(_jn_common CACHE)
unset(_jn_unique CACHE)
unset(_jn_charconv CACHE)
check_cxx_source_compiles([=[
#include <atomic>
#include <cstdint>
#include <memory>
#include <limits>
struct Base { virtual ~Base() {} };
struct Derived : Base {};
int main() {
    static_assert(sizeof(std::int32_t)==4 && sizeof(std::int64_t)==8,"integer widths");
    static_assert(sizeof(void*)<=8,"pointer width");
    static_assert(sizeof(float)==4 && sizeof(double)==8,"floating widths");
    static_assert(std::numeric_limits<float>::is_iec559 && std::numeric_limits<double>::is_iec559,"IEEE arithmetic");
    std::atomic<std::uint64_t> value(0); value.fetch_add(1);
    std::unique_ptr<Base> object(new Derived());
    try { throw 1; } catch (int) { return dynamic_cast<Derived*>(object.get()) ? 0 : 1; }
}
]=] _jn_common)
if(NOT _jn_common)
    message(FATAL_ERROR "The selected C++ mode/toolchain lacks required runtime facilities (fixed-width integers, IEEE arithmetic, atomics, ownership, RTTI or exceptions). If atomics require a target support library, set JNATIVE_ATOMIC_LIBRARIES. Adjust your toolchain settings; jNative does not change the language standard.")
endif()
set(_jn_use_unique 0)
set(_jn_use_charconv 0)
set(_jn_link_probes 1)
if(CMAKE_TRY_COMPILE_TARGET_TYPE STREQUAL "STATIC_LIBRARY")
    set(_jn_link_probes 0)
endif()
if(JNATIVE_FEATURES STREQUAL "AUTO")
    check_cxx_source_compiles("#include <memory>\nint main(){auto value=std::make_unique<int>(42);return *value!=42;}" _jn_unique)
    # A cross SDK may permit only compile probes. Header availability alone
    # does not confirm the out-of-line floating conversion implementation.
    if(_jn_link_probes)
        check_cxx_source_compiles("#include <charconv>\nint main(){char text[128]; auto a=std::to_chars(text,text+128,1.25f);auto b=std::to_chars(text,text+128,1.25,std::chars_format::scientific,1);return a.ec!=std::errc()||b.ec!=std::errc();}" _jn_charconv)
    endif()
    if(_jn_unique)
        set(_jn_use_unique 1)
    endif()
    if(_jn_charconv)
        set(_jn_use_charconv 1)
    endif()
endif()
add_library(jnative_features INTERFACE)
target_link_libraries(jnative_features INTERFACE ${JNATIVE_ATOMIC_LIBRARIES})
target_compile_definitions(jnative_features INTERFACE
    JNATIVE_USE_MAKE_UNIQUE=${_jn_use_unique} JNATIVE_USE_CHARCONV=${_jn_use_charconv})
set(CMAKE_REQUIRED_LIBRARIES "${_jn_saved_required_libraries}")
file(GENERATE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/jnative-capabilities-$<CONFIG>.properties" CONTENT
"compiler=${CMAKE_CXX_COMPILER_ID} ${CMAKE_CXX_COMPILER_VERSION}
target=${CMAKE_SYSTEM_NAME}/${CMAKE_SYSTEM_PROCESSOR}
pointer.bytes=${CMAKE_SIZEOF_VOID_P}
probes.link=${_jn_link_probes}
standard.requested=${CMAKE_CXX_STANDARD}
mode=${JNATIVE_FEATURES}
atomic.libraries=${JNATIVE_ATOMIC_LIBRARIES}
make_unique=${_jn_use_unique}
charconv=${_jn_use_charconv}
configuration=$<CONFIG>
")
message(STATUS "jNative implementations: make_unique=${_jn_use_unique}, charconv=${_jn_use_charconv}; standard remains developer-controlled")

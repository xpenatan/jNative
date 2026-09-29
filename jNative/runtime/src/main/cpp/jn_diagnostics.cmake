# Included by generated and exported projects.
set(JNATIVE_SYMBOLS "${JNATIVE_DEFAULT_SYMBOLS}" CACHE STRING "AUTO, EMBEDDED, SEPARATE or NONE")
set_property(CACHE JNATIVE_SYMBOLS PROPERTY STRINGS AUTO EMBEDDED SEPARATE NONE)
set(JNATIVE_NATIVE_TRACES "${JNATIVE_DEFAULT_NATIVE_TRACES}" CACHE BOOL "Capture native exception addresses")
set(JNATIVE_JAVA_TRACES "${JNATIVE_DEFAULT_JAVA_TRACES}" CACHE BOOL "Capture generated Java frames")
set(JNATIVE_CRASH_REPORTS "${JNATIVE_DEFAULT_CRASH_REPORTS}" CACHE BOOL "Save local crash reports")
option(JNATIVE_STRONG_UNWIND "Retain frame pointers and disable sibling-call optimization" OFF)
set(JNATIVE_ARCHIVE_STORE "" CACHE PATH "Permanent private release archive")
set(JNATIVE_ARCHIVE_INPUTS "" CACHE STRING "Additional project-relative source/header/config files to retain")
set(JNATIVE_FINALIZE_COMMAND "" CACHE STRING "Optional signing command, invoked with each application/helper image before hashing")
if(NOT JNATIVE_SYMBOLS MATCHES "^(AUTO|EMBEDDED|SEPARATE|NONE)$")
    message(FATAL_ERROR "Unknown JNATIVE_SYMBOLS")
endif()
set(_jn_runtime "${CMAKE_CURRENT_LIST_DIR}")
if(JNATIVE_NATIVE_TRACES)
    jnative_require_service(native-traces JNATIVE_NATIVE_TRACES)
endif()
if(JNATIVE_CRASH_REPORTS)
    jnative_require_service(crash-reports JNATIVE_CRASH_REPORTS)
endif()
if(NOT JNATIVE_NATIVE_TRACES AND NOT JNATIVE_CRASH_REPORTS AND JNATIVE_SYMBOLS STREQUAL "NONE")
    target_sources(jnative_runtime PRIVATE "${_jn_runtime}/jn_diagnostics_none.cpp")
    target_compile_definitions(jnative_runtime PUBLIC JNATIVE_NATIVE_TRACES=0 JNATIVE_CRASH_REPORTS=0
        JNATIVE_JAVA_TRACES=$<BOOL:${JNATIVE_JAVA_TRACES}>)
    file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/jnative-diagnostics.properties" "provider=disabled\n")
    return()
endif()
if(NOT CMAKE_SIZEOF_VOID_P EQUAL 8 OR NOT CMAKE_SYSTEM_NAME MATCHES "^(Windows|Linux)$" OR NOT CMAKE_SYSTEM_PROCESSOR MATCHES "^(AMD64|amd64|x86_64)$")
    message(FATAL_ERROR "Native diagnostics currently support Windows/Linux 64-bit targets")
endif()
set(CMAKE_OBJECT_PATH_MAX 240)
set(CMAKE_CXX_USE_RESPONSE_FILE_FOR_INCLUDES ON)
set(CMAKE_CXX_USE_RESPONSE_FILE_FOR_OBJECTS ON)
set(CMAKE_C_USE_RESPONSE_FILE_FOR_INCLUDES ON)
set(CMAKE_C_USE_RESPONSE_FILE_FOR_OBJECTS ON)
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/jnative-diagnostics.properties" "provider=desktop\n")
if(JNATIVE_OUTPUT_KIND STREQUAL "EXECUTABLE" AND (JNATIVE_NATIVE_TRACES OR JNATIVE_CRASH_REPORTS))
    add_executable(jnative_diagnostics "${_jn_runtime}/jn_diagnostic_tool.cpp")
    target_link_libraries(jnative_diagnostics PRIVATE jnative_platform)
set_target_properties(jnative_diagnostics PROPERTIES OUTPUT_NAME jnative-diagnostics
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/helper"
    RUNTIME_OUTPUT_DIRECTORY_DEBUG "${CMAKE_CURRENT_BINARY_DIR}/helper"
    RUNTIME_OUTPUT_DIRECTORY_RELEASE "${CMAKE_CURRENT_BINARY_DIR}/helper")
if(WIN32)
    target_link_libraries(jnative_diagnostics PRIVATE dbghelp psapi)
    if(MINGW)
        target_link_options(jnative_diagnostics PRIVATE -municode -static-libgcc -static-libstdc++)
    endif()
endif()
endif()
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    target_link_libraries(jnative_runtime PUBLIC ${CMAKE_DL_LIBS})
endif()
target_sources(jnative_runtime PRIVATE "${_jn_runtime}/jn_diagnostics.cpp")
target_include_directories(jnative_runtime PRIVATE "${CMAKE_CURRENT_BINARY_DIR}")
target_compile_definitions(jnative_runtime PUBLIC
    JNATIVE_NATIVE_TRACES=$<BOOL:${JNATIVE_NATIVE_TRACES}>
    JNATIVE_JAVA_TRACES=$<BOOL:${JNATIVE_JAVA_TRACES}>
    JNATIVE_CRASH_REPORTS=$<BOOL:${JNATIVE_CRASH_REPORTS}>
    JNATIVE_HAVE_BUILD_ID=1)
add_custom_target(jnative_identity
    COMMAND "${CMAKE_COMMAND}" "-DOUTPUT=${CMAKE_CURRENT_BINARY_DIR}/jnative_identity.hpp"
        "-DPROJECT=${CMAKE_CURRENT_SOURCE_DIR}" "-DSOURCES=${JNATIVE_SOURCE_DIRECTORY}"
        "-DEXTRA=${JNATIVE_ARCHIVE_INPUTS}" -P "${_jn_runtime}/jn_identity.cmake"
    BYPRODUCTS "${CMAKE_CURRENT_BINARY_DIR}/jnative_identity.hpp"
    VERBATIM)
add_dependencies(jnative_app jnative_identity)
add_dependencies(jnative_runtime jnative_identity)
set(_jn_helper "")
if(TARGET jnative_diagnostics)
    add_dependencies(jnative_diagnostics jnative_identity)
    add_dependencies(jnative_app jnative_diagnostics)
    set(_jn_helper "$<TARGET_FILE:jnative_diagnostics>")
endif()
if(NOT JNATIVE_OUTPUT_KIND STREQUAL "EXECUTABLE" AND (JNATIVE_CRASH_REPORTS OR NOT JNATIVE_SYMBOLS STREQUAL "NONE"))
    message(FATAL_ERROR "Fatal handlers and symbol archives belong to the final host executable. Embedded builds support native exception capture; disable JNATIVE_CRASH_REPORTS and use JNATIVE_SYMBOLS=NONE here, and retain the host's final symbols.")
endif()
if(MSVC)
    message(FATAL_ERROR "Diagnostics require GCC/Clang DWARF tools; MSVC/PDB support is not validated yet")
else()
    # GCC's LTO compiler runs during linking. Keep debug information there too,
    # before packaging separate symbols; this does not disable optimization.
    if(NOT JNATIVE_SYMBOLS STREQUAL "NONE")
        target_link_options(jnative_app PRIVATE -g
            "-fdebug-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=."
            "-fdebug-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=."
            "-fdebug-prefix-map=${JNATIVE_SOURCE_DIRECTORY}=./src")
    endif()
    foreach(_target IN LISTS _jn_compiled_targets)
        if(JNATIVE_SYMBOLS STREQUAL "NONE")
            target_compile_options(${_target} PRIVATE -g0)
        else()
            target_compile_options(${_target} PRIVATE -g "-fdebug-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=." "-fdebug-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=." "-fdebug-prefix-map=${JNATIVE_SOURCE_DIRECTORY}=./src")
        endif()
        target_compile_options(${_target} PRIVATE -fasynchronous-unwind-tables)
        if(JNATIVE_STRONG_UNWIND)
            target_compile_options(${_target} PRIVATE -fno-omit-frame-pointer -fno-optimize-sibling-calls)
        endif()
    endforeach()
    if(TARGET jnative_diagnostics AND NOT JNATIVE_SYMBOLS STREQUAL "NONE")
        target_compile_options(jnative_diagnostics PRIVATE -g "-fdebug-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=." "-fdebug-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=.")
    endif()
endif()
if(NOT JNATIVE_OUTPUT_KIND STREQUAL "EXECUTABLE")
    return()
endif()

file(GENERATE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/jn_archive_$<CONFIG>.cmake" CONTENT
"set(JN_PROJECT [==[${CMAKE_CURRENT_SOURCE_DIR}]==])
set(JN_BINARY [==[${CMAKE_CURRENT_BINARY_DIR}]==])
set(JN_SOURCES [==[${JNATIVE_SOURCE_DIRECTORY}]==])
set(JN_EXE [==[$<TARGET_FILE:jnative_app>]==])
set(JN_HELPER [==[${_jn_helper}]==])
set(JN_CONFIG [==[$<CONFIG>]==])
set(JN_SYMBOLS [==[${JNATIVE_SYMBOLS}]==])
set(JN_OBJCOPY [==[${CMAKE_OBJCOPY}]==])
set(JN_STRIP [==[${CMAKE_STRIP}]==])
set(JN_OBJDUMP [==[${CMAKE_OBJDUMP}]==])
set(JNATIVE_RUNTIME_LIBRARY_DIRECTORIES [==[${JNATIVE_RUNTIME_LIBRARY_DIRECTORIES}]==])
set(JN_COMPILER [==[${CMAKE_CXX_COMPILER_ID} ${CMAKE_CXX_COMPILER_VERSION}]==])
set(JN_FLAGS [==[${CMAKE_CXX_FLAGS} $<$<CONFIG:Debug>:${CMAKE_CXX_FLAGS_DEBUG}>$<$<CONFIG:Release>:${CMAKE_CXX_FLAGS_RELEASE}> $<JOIN:$<TARGET_PROPERTY:jnative_classes,COMPILE_OPTIONS>, >]==])
set(JN_SYSTEM [==[${CMAKE_SYSTEM_NAME}]==])
set(JN_DEFINITIONS [==[$<JOIN:$<TARGET_PROPERTY:jnative_runtime,COMPILE_DEFINITIONS>, >]==])
set(JN_LINK_FLAGS [==[${CMAKE_EXE_LINKER_FLAGS} $<JOIN:$<TARGET_PROPERTY:jnative_app,LINK_OPTIONS>, >]==])
set(JN_CMAKE [==[${CMAKE_VERSION}]==])
set(JN_FINALIZE [==[${JNATIVE_FINALIZE_COMMAND}]==])
include([==[${_jn_runtime}/jn_archive.cmake]==])
")
add_custom_command(TARGET jnative_app POST_BUILD
    COMMAND "${CMAKE_COMMAND}" -P "${CMAKE_CURRENT_BINARY_DIR}/jn_archive_$<CONFIG>.cmake" VERBATIM)
add_custom_target(jnative_archive
    COMMAND "${CMAKE_COMMAND}" "-DPROJECT=${CMAKE_CURRENT_SOURCE_DIR}" "-DSTORE=${JNATIVE_ARCHIVE_STORE}" "-DCONFIG=$<CONFIG>"
        -P "${_jn_runtime}/jn_store.cmake"
    DEPENDS jnative_app VERBATIM)

add_custom_target(jnative_release
    COMMAND "${CMAKE_COMMAND}" "-DPROJECT=${CMAKE_CURRENT_SOURCE_DIR}" "-DSTORE=${JNATIVE_ARCHIVE_STORE}" "-DCONFIG=$<CONFIG>" -DREQUIRE_RELEASE=ON
        -P "${_jn_runtime}/jn_store.cmake"
    DEPENDS jnative_app VERBATIM)

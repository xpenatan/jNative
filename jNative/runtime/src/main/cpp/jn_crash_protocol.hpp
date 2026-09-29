#pragma once
#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
namespace jnative { namespace diagnostics {
struct CrashRequest {
    DWORD process=0,thread=0;
    CONTEXT context{};
    EXCEPTION_RECORD exception{};
    wchar_t directory[1024]{};
    char build[64]{};
    bool include_dump=true;
    volatile LONG ready=0, saved=0, stop=0;
};
} }
#endif

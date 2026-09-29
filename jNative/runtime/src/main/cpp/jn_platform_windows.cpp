#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <process.h>
#include <conio.h>
#include <system_error>
#include "jn_platform_desktop.hpp"
#include "jn_platform_files.hpp"

namespace jnative { namespace platform {
struct Mutex::Impl { SRWLOCK lock; Impl() { InitializeSRWLock(&lock); } };
Mutex::Mutex() {
    static_assert(sizeof(Impl) <= sizeof(storage_) && alignof(Impl) <= alignof(std::max_align_t), "Platform mutex storage");
    new (storage_) Impl();
}
Mutex::~Mutex() { impl()->~Impl(); }
void Mutex::lock() { AcquireSRWLockExclusive(&impl()->lock); }
void Mutex::unlock() { ReleaseSRWLockExclusive(&impl()->lock); }
#if defined(JNATIVE_PLATFORM_TRY_LOCK)
bool Mutex::try_lock() { return TryAcquireSRWLockExclusive(&impl()->lock) != 0; }
#endif
struct Condition::Impl { CONDITION_VARIABLE condition; Impl() { InitializeConditionVariable(&condition); } };
Condition::Condition() {
    static_assert(sizeof(Impl) <= sizeof(storage_) && alignof(Impl) <= alignof(std::max_align_t), "Platform condition storage");
    new (storage_) Impl();
}
Condition::~Condition() { impl()->~Impl(); }
void Condition::notify_one() noexcept { WakeConditionVariable(&impl()->condition); }
void Condition::notify_all() noexcept { WakeAllConditionVariable(&impl()->condition); }
void Condition::wait(std::unique_lock<Mutex>& lock) {
    if (!SleepConditionVariableSRW(&impl()->condition, &lock.mutex()->impl()->lock, INFINITE, 0))
        throw std::system_error(GetLastError(), std::system_category(), "wait");
}
bool Condition::wait_deadline(Mutex& mutex, std::int64_t end) {
    const std::int64_t remaining = end - monotonic_nanos();
    if (remaining <= 0) return false;
    const std::int64_t millis = remaining / 1000000 + (remaining % 1000000 != 0);
    DWORD timeout = static_cast<DWORD>((std::min)(millis, std::int64_t(INFINITE - 1)));
    if (SleepConditionVariableSRW(&impl()->condition, &mutex.impl()->lock, timeout, 0)) return true;
    if (GetLastError() == ERROR_TIMEOUT) return monotonic_nanos() < end;
    throw std::system_error(GetLastError(), std::system_category(), "timed wait");
}
namespace {
unsigned __stdcall run_thread(void* data) {
    std::unique_ptr<std::function<void()> > body(static_cast<std::function<void()>*>(data));
    try { (*body)(); } catch (...) { std::terminate(); }
    return 0;
}
struct LocalValue { void* value; void (*destroy)(void*); };
void NTAPI destroy_local(void* data) {
    std::unique_ptr<LocalValue> slot(static_cast<LocalValue*>(data));
    if (slot) slot->destroy(slot->value);
}
}
void start_thread(std::function<void()> body) {
    std::unique_ptr<std::function<void()> > owned(new std::function<void()>(std::move(body)));
    HANDLE thread = reinterpret_cast<HANDLE>(_beginthreadex(nullptr, 0, run_thread, owned.get(), 0, nullptr));
    if (!thread) throw std::system_error(errno, std::generic_category(), "start thread");
    owned.release();
    CloseHandle(thread);
}
void yield_thread() noexcept { SwitchToThread(); }
LocalKey::LocalKey(void (*destroy)(void*)) : key_(FlsAlloc(destroy_local)), destroy_(destroy) {
    if (key_ == FLS_OUT_OF_INDEXES) throw std::bad_alloc();
}
LocalKey::~LocalKey() { FlsFree(static_cast<DWORD>(key_)); }
void* LocalKey::get() const noexcept {
    LocalValue* slot = static_cast<LocalValue*>(FlsGetValue(static_cast<DWORD>(key_)));
    return slot ? slot->value : nullptr;
}
void LocalKey::set(void* value) {
    LocalValue* existing = static_cast<LocalValue*>(FlsGetValue(static_cast<DWORD>(key_)));
    if (existing) { existing->value = value; return; }
    std::unique_ptr<LocalValue> slot(new LocalValue{value, destroy_});
    if (!FlsSetValue(static_cast<DWORD>(key_), slot.get())) throw std::bad_alloc();
    slot.release();
}
std::int64_t monotonic_nanos() noexcept {
    LARGE_INTEGER frequency, ticks;
    QueryPerformanceFrequency(&frequency);
    QueryPerformanceCounter(&ticks);
    return (ticks.QuadPart / frequency.QuadPart) * 1000000000
        + (ticks.QuadPart % frequency.QuadPart) * 1000000000 / frequency.QuadPart;
}
std::int64_t wall_millis() noexcept {
    FILETIME time;
    GetSystemTimeAsFileTime(&time);
    ULARGE_INTEGER value; value.LowPart = time.dwLowDateTime; value.HighPart = time.dwHighDateTime;
    return static_cast<std::int64_t>(value.QuadPart / 10000) - 11644473600000LL;
}
bool interactive_console() noexcept {
    DWORD input, output;
    return GetConsoleMode(GetStdHandle(STD_INPUT_HANDLE), &input)
        && GetConsoleMode(GetStdHandle(STD_OUTPUT_HANDLE), &output);
}
void pause_console() {
    const char text[] = "\nPress any key to exit...";
    write_output(false, text, sizeof(text) - 1);
    _getwch();
    write_output(false, "\n", 1);
}
void exit_process(int status) { ExitProcess(static_cast<UINT>(status)); }
namespace {
std::error_code file_error(DWORD code) {
    switch (code) {
        case ERROR_FILE_NOT_FOUND: case ERROR_PATH_NOT_FOUND: return std::make_error_code(std::errc::no_such_file_or_directory);
        case ERROR_ACCESS_DENIED: case ERROR_SHARING_VIOLATION: return std::make_error_code(std::errc::permission_denied);
        case ERROR_DIR_NOT_EMPTY: return std::make_error_code(std::errc::directory_not_empty);
        case ERROR_ALREADY_EXISTS: case ERROR_FILE_EXISTS: return std::make_error_code(std::errc::file_exists);
        default: return std::error_code(code, std::system_category());
    }
}
}
bool windows_paths() noexcept { return true; }
int compare_paths(const Path& a, const Path& b) {
    std::wstring first = a.wstring(), second = b.wstring();
    if (first.empty() || second.empty()) return first.compare(second);
    int result = CompareStringOrdinal(first.c_str(), static_cast<int>(first.size()),
        second.c_str(), static_cast<int>(second.size()), TRUE);
    if (!result) throw FileError("compare", a, file_error(GetLastError()));
    return result - CSTR_EQUAL;
}
Path current_path() {
    DWORD count = GetCurrentDirectoryW(0, nullptr);
    if (!count) throw FileError("current directory", Path(), file_error(GetLastError()));
    std::vector<wchar_t> buffer(count);
    DWORD size = GetCurrentDirectoryW(count, buffer.data());
    if (!size || size >= count) throw FileError("current directory", Path(), file_error(GetLastError()));
    return Path(std::wstring(buffer.data(), size));
}
Path absolute(const Path& path) {
    if (path.empty()) return current_path();
    std::wstring name = path.wstring();
    DWORD count = GetFullPathNameW(name.c_str(), 0, nullptr, nullptr);
    if (!count) throw FileError("absolute path", path, file_error(GetLastError()));
    std::vector<wchar_t> buffer(count);
    DWORD size = GetFullPathNameW(name.c_str(), count, buffer.data(), nullptr);
    if (!size || size >= count) throw FileError("absolute path", path, file_error(GetLastError()));
    return Path(std::wstring(buffer.data(), size));
}
FileInfo file_info(const Path& path, std::error_code& error) {
    error.clear(); FileInfo info; WIN32_FILE_ATTRIBUTE_DATA data;
    if (!GetFileAttributesExW(path.wstring().c_str(), GetFileExInfoStandard, &data)) {
        DWORD code = GetLastError();
        if (code != ERROR_FILE_NOT_FOUND && code != ERROR_PATH_NOT_FOUND) error = file_error(code);
        return info;
    }
    info.exists = true;
    info.directory = (data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
    info.regular = !info.directory;
    info.symlink = (data.dwFileAttributes & FILE_ATTRIBUTE_REPARSE_POINT) != 0;
    info.size = (std::uint64_t(data.nFileSizeHigh) << 32) | data.nFileSizeLow;
    info.modified = (std::uint64_t(data.ftLastWriteTime.dwHighDateTime) << 32) | data.ftLastWriteTime.dwLowDateTime;
    return info;
}
bool remove(const Path& path, std::error_code& error) {
    FileInfo info = file_info(path, error);
    if (error || !info.exists) return false;
    bool result = info.directory ? RemoveDirectoryW(path.wstring().c_str()) : DeleteFileW(path.wstring().c_str());
    if (!result) error = file_error(GetLastError());
    return result;
}
void create_directory(const Path& path) {
    if (!CreateDirectoryW(path.wstring().c_str(), nullptr) && !is_directory(path))
        throw FileError("create directory", path, file_error(GetLastError()));
}
std::vector<Path> directory_entries(const Path& path) {
    std::vector<Path> entries; WIN32_FIND_DATAW item;
    HANDLE search = FindFirstFileW((path / "*").wstring().c_str(), &item);
    if (search == INVALID_HANDLE_VALUE) throw FileError("list directory", path, file_error(GetLastError()));
    try {
        do {
            std::wstring name(item.cFileName);
            if (name != L"." && name != L"..") entries.push_back(path / Path(name));
        } while (FindNextFileW(search, &item));
        DWORD error = GetLastError();
        if (error != ERROR_NO_MORE_FILES) throw FileError("list directory", path, file_error(error));
    } catch (...) { FindClose(search); throw; }
    FindClose(search);
    return entries;
}
InputFile::InputFile(const Path& path, std::ios::openmode mode) : std::ifstream(path.wstring().c_str(), mode) {}
OutputFile::OutputFile(const Path& path, std::ios::openmode mode) : std::ofstream(path.wstring().c_str(), mode) {}
} }

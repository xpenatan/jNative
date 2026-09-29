#include <pthread.h>
#include <sched.h>
#include <unistd.h>
#include <time.h>
#include <cerrno>
#include <cstdlib>
#include <system_error>
#include "jn_platform_desktop.hpp"
#include "jn_platform_files.hpp"
#include <dirent.h>
#include <sys/stat.h>

namespace jnative { namespace platform {
namespace { void check(int code) { if (code) throw std::system_error(code, std::generic_category()); } }
struct Mutex::Impl {
    pthread_mutex_t lock;
    Impl() { check(pthread_mutex_init(&lock, nullptr)); }
    ~Impl() { pthread_mutex_destroy(&lock); }
};
Mutex::Mutex() {
    static_assert(sizeof(Impl) <= sizeof(storage_) && alignof(Impl) <= alignof(std::max_align_t), "Platform mutex storage");
    new (storage_) Impl();
}
Mutex::~Mutex() { impl()->~Impl(); }
void Mutex::lock() { check(pthread_mutex_lock(&impl()->lock)); }
void Mutex::unlock() { check(pthread_mutex_unlock(&impl()->lock)); }
#if defined(JNATIVE_PLATFORM_TRY_LOCK)
bool Mutex::try_lock() {
    int result = pthread_mutex_trylock(&impl()->lock);
    if (result == EBUSY) return false;
    check(result);
    return true;
}
#endif
struct Condition::Impl {
    pthread_cond_t condition;
    Impl() {
        pthread_condattr_t attributes;
        check(pthread_condattr_init(&attributes));
        int result = pthread_condattr_setclock(&attributes, CLOCK_MONOTONIC);
        if (!result) result = pthread_cond_init(&condition, &attributes);
        pthread_condattr_destroy(&attributes);
        check(result);
    }
    ~Impl() { pthread_cond_destroy(&condition); }
};
Condition::Condition() {
    static_assert(sizeof(Impl) <= sizeof(storage_) && alignof(Impl) <= alignof(std::max_align_t), "Platform condition storage");
    new (storage_) Impl();
}
Condition::~Condition() { impl()->~Impl(); }
void Condition::notify_one() noexcept { pthread_cond_signal(&impl()->condition); }
void Condition::notify_all() noexcept { pthread_cond_broadcast(&impl()->condition); }
void Condition::wait(std::unique_lock<Mutex>& lock) {
    check(pthread_cond_wait(&impl()->condition, &lock.mutex()->impl()->lock));
}
bool Condition::wait_deadline(Mutex& mutex, std::int64_t end) {
    if (end <= monotonic_nanos()) return false;
    timespec time;
    time.tv_sec = static_cast<time_t>(end / 1000000000);
    time.tv_nsec = static_cast<long>(end % 1000000000);
    int result = pthread_cond_timedwait(&impl()->condition, &mutex.impl()->lock, &time);
    if (result == ETIMEDOUT) return false;
    check(result);
    return true;
}
namespace {
void* run_thread(void* data) {
    std::unique_ptr<std::function<void()> > body(static_cast<std::function<void()>*>(data));
    try { (*body)(); } catch (...) { std::terminate(); }
    return nullptr;
}
}
void start_thread(std::function<void()> body) {
    std::unique_ptr<std::function<void()> > owned(new std::function<void()>(std::move(body)));
    pthread_attr_t attributes;
    check(pthread_attr_init(&attributes));
    int result = pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_DETACHED);
    pthread_t thread;
    if (!result) result = pthread_create(&thread, &attributes, run_thread, owned.get());
    pthread_attr_destroy(&attributes);
    check(result);
    owned.release();
}
void yield_thread() noexcept { sched_yield(); }
LocalKey::LocalKey(void (*destroy)(void*)) : key_(0), destroy_(destroy) {
    pthread_key_t key;
    check(pthread_key_create(&key, destroy));
    key_ = key;
}
LocalKey::~LocalKey() {
    pthread_key_t key = static_cast<pthread_key_t>(key_);
    void* value = pthread_getspecific(key);
    pthread_setspecific(key, nullptr);
    if (value) destroy_(value);
    pthread_key_delete(key);
}
void* LocalKey::get() const noexcept { return pthread_getspecific(static_cast<pthread_key_t>(key_)); }
void LocalKey::set(void* value) { check(pthread_setspecific(static_cast<pthread_key_t>(key_), value)); }
std::int64_t monotonic_nanos() noexcept {
    timespec value; clock_gettime(CLOCK_MONOTONIC, &value);
    return std::int64_t(value.tv_sec) * 1000000000 + value.tv_nsec;
}
std::int64_t wall_millis() noexcept {
    timespec value; clock_gettime(CLOCK_REALTIME, &value);
    return std::int64_t(value.tv_sec) * 1000 + value.tv_nsec / 1000000;
}
bool interactive_console() noexcept { return isatty(STDIN_FILENO) && isatty(STDOUT_FILENO); }
void pause_console() {
    const char text[] = "\nPress Enter to exit...";
    write_output(false, text, sizeof(text) - 1);
    int key;
    do { key = std::getchar(); } while (key != '\n' && key != EOF);
    write_output(false, "\n", 1);
}
void exit_process(int status) { std::_Exit(status); }
bool windows_paths() noexcept { return false; }
int compare_paths(const Path& first, const Path& second) { return first.string().compare(second.string()); }
Path current_path() {
    std::vector<char> buffer(256);
    while (!getcwd(buffer.data(), buffer.size())) {
        if (errno != ERANGE) throw FileError("current directory", Path(), std::error_code(errno, std::generic_category()));
        buffer.resize(buffer.size() * 2);
    }
    return Path(buffer.data());
}
Path absolute(const Path& path) { return path.is_absolute() ? path : current_path() / path; }
FileInfo file_info(const Path& path, std::error_code& error) {
    error.clear(); FileInfo info; struct stat data;
    if (lstat(path.string().c_str(), &data) != 0) {
        if (errno != ENOENT && errno != ENOTDIR) error = std::error_code(errno, std::generic_category());
        return info;
    }
    info.symlink = S_ISLNK(data.st_mode);
    if (info.symlink && stat(path.string().c_str(), &data) != 0) {
        if (errno != ENOENT && errno != ENOTDIR) error = std::error_code(errno, std::generic_category());
        return info;
    }
    info.exists = true; info.directory = S_ISDIR(data.st_mode); info.regular = S_ISREG(data.st_mode);
    info.size = static_cast<std::uint64_t>(data.st_size);
    info.modified = std::uint64_t(data.st_mtim.tv_sec) * 1000000000 + data.st_mtim.tv_nsec;
    return info;
}
bool remove(const Path& path, std::error_code& error) {
    error.clear();
    if (::remove(path.string().c_str()) == 0) return true;
    if (errno != ENOENT && errno != ENOTDIR) error = std::error_code(errno, std::generic_category());
    return false;
}
void create_directory(const Path& path) {
    if (mkdir(path.string().c_str(), 0777) != 0 && !is_directory(path))
        throw FileError("create directory", path, std::error_code(errno, std::generic_category()));
}
std::vector<Path> directory_entries(const Path& path) {
    std::vector<Path> entries;
    DIR* directory = opendir(path.string().c_str());
    if (!directory) throw FileError("list directory", path, std::error_code(errno, std::generic_category()));
    try {
        for (;;) {
            errno = 0; dirent* item = readdir(directory);
            if (!item) { if (errno) throw FileError("list directory", path, std::error_code(errno, std::generic_category())); break; }
            std::string name(item->d_name);
            if (name != "." && name != "..") entries.push_back(path / Path(name));
        }
    } catch (...) { closedir(directory); throw; }
    closedir(directory);
    return entries;
}
InputFile::InputFile(const Path& path, std::ios::openmode mode) : std::ifstream(path.string().c_str(), mode) {}
OutputFile::OutputFile(const Path& path, std::ios::openmode mode) : std::ofstream(path.string().c_str(), mode) {}
} }

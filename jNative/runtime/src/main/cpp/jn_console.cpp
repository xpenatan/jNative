#include "jn_console.hpp"
#include "jn_runtime.hpp"
namespace jnative {
void pause_console_on_exit() {
#if defined(JNATIVE_PAUSE_ON_EXIT) && JNATIVE_PAUSE_ON_EXIT
    if (!platform::interactive_console()) return;
    NativeRegion waiting;
    platform::pause_console();
#endif
}
[[noreturn]] void exit_process(int status) {
    platform::exit_process(status);
    std::terminate();
}
}

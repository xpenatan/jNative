#pragma once

namespace jnative {
// Wait only when both standard input and output refer to an interactive console.
void pause_console_on_exit();
[[noreturn]] void exit_process(int status);
}

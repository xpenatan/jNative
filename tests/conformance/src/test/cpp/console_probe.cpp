#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <cstdlib>
#include <cwchar>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {
struct ChildConsole {
    PROCESS_INFORMATION process{};
    HANDLE input = INVALID_HANDLE_VALUE;
    HANDLE output = INVALID_HANDLE_VALUE;
    bool attached = false;

    ~ChildConsole() {
        if (input != INVALID_HANDLE_VALUE) CloseHandle(input);
        if (output != INVALID_HANDLE_VALUE) CloseHandle(output);
        if (attached) FreeConsole();
        if (process.hProcess) {
            if (WaitForSingleObject(process.hProcess, 0) == WAIT_TIMEOUT) {
                TerminateProcess(process.hProcess, 99);
                WaitForSingleObject(process.hProcess, 5000);
            }
            CloseHandle(process.hProcess);
            CloseHandle(process.hThread);
        }
    }
};

void require(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(std::string(message) + " (Windows error "
            + std::to_string(GetLastError()) + ")");
}

std::wstring screen_text(HANDLE output) {
    CONSOLE_SCREEN_BUFFER_INFO info{};
    require(GetConsoleScreenBufferInfo(output, &info), "Cannot inspect console buffer");
    DWORD length = static_cast<DWORD>(info.dwSize.X) * (info.dwCursorPosition.Y + 1);
    std::wstring text(length, L' ');
    DWORD read = 0;
    require(ReadConsoleOutputCharacterW(output, text.data(), length, COORD{0, 0}, &read),
            "Cannot read console output");
    text.resize(read);
    return text;
}

void verify(const wchar_t* executable, DWORD expected_status, bool pause, const wchar_t* argument) {
    ChildConsole child;
    STARTUPINFOW startup{};
    startup.cb = sizeof(startup);
    startup.dwFlags = STARTF_USESHOWWINDOW;
    startup.wShowWindow = SW_HIDE;
    std::wstring command = L"\"" + std::wstring(executable) + L"\"";
    if (argument) command += L" \"" + std::wstring(argument) + L"\"";
    require(CreateProcessW(executable, command.data(), nullptr, nullptr, FALSE,
            CREATE_NEW_CONSOLE, nullptr, nullptr, &startup, &child.process), "Cannot launch console executable");

    if (pause) {
        FreeConsole();
        auto deadline = GetTickCount64() + 10000;
        while (!(child.attached = AttachConsole(child.process.dwProcessId) != FALSE)) {
            require(GetTickCount64() < deadline, "Cannot attach to child console");
            require(WaitForSingleObject(child.process.hProcess, 10) == WAIT_TIMEOUT,
                    "Executable exited before its console could be inspected");
        }
        child.input = CreateFileW(L"CONIN$", GENERIC_READ | GENERIC_WRITE,
                FILE_SHARE_READ | FILE_SHARE_WRITE, nullptr, OPEN_EXISTING, 0, nullptr);
        child.output = CreateFileW(L"CONOUT$", GENERIC_READ | GENERIC_WRITE,
                FILE_SHARE_READ | FILE_SHARE_WRITE, nullptr, OPEN_EXISTING, 0, nullptr);
        require(child.input != INVALID_HANDLE_VALUE && child.output != INVALID_HANDLE_VALUE,
                "Cannot open child console handles");
        while (screen_text(child.output).find(L"Press any key to exit...") == std::wstring::npos) {
            require(GetTickCount64() < deadline, "Console prompt was not displayed");
            require(WaitForSingleObject(child.process.hProcess, 10) == WAIT_TIMEOUT,
                    "Executable exited without waiting for input");
        }
        require(WaitForSingleObject(child.process.hProcess, 250) == WAIT_TIMEOUT,
                "Console closed without a keypress");
        INPUT_RECORD events[2]{};
        events[0].EventType = KEY_EVENT;
        events[0].Event.KeyEvent.bKeyDown = TRUE;
        events[0].Event.KeyEvent.wRepeatCount = 1;
        events[0].Event.KeyEvent.wVirtualKeyCode = 'X';
        events[0].Event.KeyEvent.wVirtualScanCode = 0x2D;
        events[0].Event.KeyEvent.uChar.UnicodeChar = L'x';
        events[1] = events[0];
        events[1].Event.KeyEvent.bKeyDown = FALSE;
        DWORD written = 0;
        require(WriteConsoleInputW(child.input, events, 2, &written) && written == 2,
                "Cannot send console keypress");
    }
    require(WaitForSingleObject(child.process.hProcess, 10000) == WAIT_OBJECT_0,
            "Executable did not exit");
    DWORD status = 0;
    require(GetExitCodeProcess(child.process.hProcess, &status) && status == expected_status,
            "Exit status changed");
}
}

int wmain(int argc, wchar_t** argv) {
    try {
        require(argc == 4 || argc == 5, "Expected executable, status, pause/normal, and optional argument");
        bool pause = std::wstring(argv[3]) == L"pause";
        require(pause || std::wstring(argv[3]) == L"normal", "Unknown console mode");
        verify(argv[1], static_cast<DWORD>(std::wcstoul(argv[2], nullptr, 10)), pause,
                argc == 5 ? argv[4] : nullptr);
        std::cout << "Console behavior verified\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}

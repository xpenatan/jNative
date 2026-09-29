#include "jn_diagnostics.hpp"
#include "jn_crash_protocol.hpp"
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <exception>
#include <algorithm>
#include <map>
#include <mutex>
#ifdef JNATIVE_HAVE_BUILD_ID
#include "jnative_identity.hpp"
#else
#define JNATIVE_BUILD_ID "unversioned"
#endif
#ifdef _WIN32
#include <psapi.h>
#else
#include <dlfcn.h>
#include <pthread.h>
#include <execinfo.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <ucontext.h>
#endif

extern "C" { volatile char jnative_live_build_identity[33]{}; }

namespace jnative {
namespace {
using namespace diagnostics;
::jnative::platform::Path report_directory;
std::mutex modules_mutex;
struct CachedModule { std::shared_ptr<const Module> module; std::uint64_t modified; std::uintmax_t size; };
std::map<std::uintptr_t,CachedModule> modules;
std::atomic<unsigned> sessions{0};
std::atomic<unsigned long long> report_sequence{0};
thread_local std::string thread_name="native";
const char* capability="off";
std::terminate_handler previous_terminate=nullptr;
bool handlers_installed=false;
::jnative::platform::Path fallback_path;
char main_module_json[2048]{};
std::uintptr_t main_start=0,main_end=0;
std::uint64_t thread_id() {
#ifdef _WIN32
    return GetCurrentThreadId();
#else
    return static_cast<std::uint64_t>(syscall(SYS_gettid));
#endif
}
::jnative::platform::Path executable_path() {
#ifdef _WIN32
    wchar_t buffer[32768];auto n=GetModuleFileNameW(nullptr,buffer,32768);
    return n ? ::jnative::platform::Path(std::wstring(buffer,n)) : ::jnative::platform::Path{};
#else
    char buffer[4096];auto n=readlink("/proc/self/exe",buffer,sizeof(buffer));
    return n>0 ? ::jnative::platform::Path(std::string(buffer,n)) : ::jnative::platform::Path{};
#endif
}
std::shared_ptr<const Module> identify(std::uintptr_t address) {
    std::uintptr_t base=0;::jnative::platform::Path path;
#ifdef _WIN32
    HMODULE image=nullptr;
    if(!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            reinterpret_cast<LPCWSTR>(address),&image)) return {};
    wchar_t name[32768];auto n=GetModuleFileNameW(image,name,32768);
    if(!n) return {};
    base=reinterpret_cast<std::uintptr_t>(image);path=std::wstring(name,n);
#else
    Dl_info info{};
    if(!dladdr(reinterpret_cast<void*>(address),&info) || !info.dli_fname) return {};
    base=reinterpret_cast<std::uintptr_t>(info.dli_fbase);path=info.dli_fname;
#endif
    std::lock_guard<std::mutex> lock(modules_mutex);
    auto modified=::jnative::platform::last_write_time(path);
    auto size=::jnative::platform::file_size(path);
    auto found=modules.find(base);
    if(found!=modules.end() && found->second.module->name==path.filename().u8string()
            && found->second.modified==modified && found->second.size==size) return found->second.module;
    auto module=std::make_shared<Module>();
    module->base=base;module->name=path.filename().u8string();
    module->sha256=hash_file(path);module->preferred=preferred_base(path,base);
    modules[base]={module,modified,size};
    return module;
}
// Handler-only output routines use fixed buffers and integer arithmetic.
char* append_text(char* out,const char* text) { while(*text) *out++=*text++;return out; }
char* append_hex(char* out,std::uint64_t n) {
    out=append_text(out,"0x");char value[16];unsigned count=0;
    do { value[count++]="0123456789abcdef"[n&15];n>>=4; }while(n);
    while(count) *out++=value[--count];
    return out;
}
char* append_number(char* out,std::uint64_t n) {
    char value[24];unsigned count=0;do {value[count++]=char('0'+n%10);n/=10;}while(n);
    while(count) *out++=value[--count];
    return out;
}
char fallback_prefix[4096]{};
std::atomic_flag handling=ATOMIC_FLAG_INIT;
#ifdef _WIN32
HANDLE mapping=nullptr,request_event=nullptr,done_event=nullptr,parent_handle=nullptr,helper_process=nullptr;
HANDLE fallback_file=INVALID_HANDLE_VALUE;
CrashRequest* request=nullptr;
LPTOP_LEVEL_EXCEPTION_FILTER previous_filter=nullptr;
UINT previous_error_mode=0;
bool error_mode_changed=false;
LONG WINAPI fatal_filter(EXCEPTION_POINTERS* error) {
    if(handling.test_and_set()) return EXCEPTION_EXECUTE_HANDLER;
    char bytes[8192];char* end=append_text(bytes,fallback_prefix);
    end=append_number(end,GetCurrentThreadId());
    end=append_text(end,",\"code\":\"");
    end=append_hex(end,error->ExceptionRecord->ExceptionCode);
    end=append_text(end,"\",\"frames\":[{\"pc\":\"");end=append_hex(end,error->ContextRecord->Rip);
    end=append_text(end,"\",\"kind\":\"instruction\"");
    if(error->ContextRecord->Rip>=main_start && error->ContextRecord->Rip<main_end) {
        end=append_text(end,",\"module\":");end=append_text(end,main_module_json);
    }
    end=append_text(end,"}],\"status\":\"context-only\"}\n");
    if(fallback_file!=INVALID_HANDLE_VALUE) {DWORD written=0;WriteFile(fallback_file,bytes,DWORD(end-bytes),&written,nullptr);FlushFileBuffers(fallback_file);}
    if(request && request->ready && helper_process && WaitForSingleObject(helper_process,0)==WAIT_TIMEOUT) {
        request->thread=GetCurrentThreadId();
        request->context=*error->ContextRecord;
        request->exception=*error->ExceptionRecord;request->exception.ExceptionRecord=nullptr;
        SetEvent(request_event);
        if(WaitForSingleObject(done_event,8000)!=WAIT_OBJECT_0) TerminateProcess(helper_process,5);
    }
    if(previous_filter) previous_filter(error);
    return EXCEPTION_EXECUTE_HANDLER;
}
void terminate_capture() noexcept {
    CONTEXT context{};RtlCaptureContext(&context);
    EXCEPTION_RECORD record{};record.ExceptionCode=0xe04a4e01;
    record.ExceptionAddress=reinterpret_cast<void*>(context.Rip);
    EXCEPTION_POINTERS pointers{&record,&context};fatal_filter(&pointers);
    TerminateProcess(GetCurrentProcess(),134);
}
void start_helper(const ::jnative::platform::Path& executable) {
    SECURITY_ATTRIBUTES inherit{sizeof(SECURITY_ATTRIBUTES),nullptr,TRUE};
    mapping=CreateFileMappingW(INVALID_HANDLE_VALUE,&inherit,PAGE_READWRITE,0,sizeof(CrashRequest),nullptr);
    if(!mapping) return;
    request=static_cast<CrashRequest*>(MapViewOfFile(mapping,FILE_MAP_ALL_ACCESS,0,0,sizeof(CrashRequest)));
    if(!request) return;
    *request=CrashRequest{};request->process=GetCurrentProcessId();
    if(const char* include=std::getenv("JNATIVE_INCLUDE_DUMP")) request->include_dump=std::strcmp(include,"0")!=0;
    auto directory=report_directory.wstring();
    if(directory.size()>=1024) return;
    std::copy(directory.begin(),directory.end(),request->directory);
    std::strncpy(request->build,JNATIVE_BUILD_ID,sizeof(request->build)-1);
    request_event=CreateEventW(&inherit,FALSE,FALSE,nullptr);done_event=CreateEventW(&inherit,FALSE,FALSE,nullptr);
    parent_handle=OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ|PROCESS_DUP_HANDLE|SYNCHRONIZE,TRUE,GetCurrentProcessId());
    if(!request_event || !done_event || !parent_handle) return;
    auto helper=executable.parent_path()/L"jnative-diagnostics.exe";
    std::wstring command=L"\""+helper.wstring()+L"\" --watch "+std::to_wstring(reinterpret_cast<std::uintptr_t>(mapping))
        +L" "+std::to_wstring(reinterpret_cast<std::uintptr_t>(request_event))
        +L" "+std::to_wstring(reinterpret_cast<std::uintptr_t>(done_event))
        +L" "+std::to_wstring(reinterpret_cast<std::uintptr_t>(parent_handle));
    STARTUPINFOEXW startup{};startup.StartupInfo.cb=sizeof(startup);
    SIZE_T size=0;InitializeProcThreadAttributeList(nullptr,1,0,&size);
    std::vector<unsigned char> storage(size);
    startup.lpAttributeList=reinterpret_cast<PPROC_THREAD_ATTRIBUTE_LIST>(storage.data());
    if(!InitializeProcThreadAttributeList(startup.lpAttributeList,1,0,&size)) return;
    HANDLE handles[]{mapping,request_event,done_event,parent_handle};
    bool configured=UpdateProcThreadAttribute(startup.lpAttributeList,0,PROC_THREAD_ATTRIBUTE_HANDLE_LIST,handles,sizeof(handles),nullptr,nullptr);
    PROCESS_INFORMATION process{};
    bool started=configured && CreateProcessW(helper.wstring().c_str(),&command[0],nullptr,nullptr,TRUE,
        CREATE_NO_WINDOW|EXTENDED_STARTUPINFO_PRESENT,nullptr,nullptr,&startup.StartupInfo,&process);
    DeleteProcThreadAttributeList(startup.lpAttributeList);
    if(started) {
        CloseHandle(process.hThread);helper_process=process.hProcess;
        if(WaitForSingleObject(done_event,3000)==WAIT_OBJECT_0 && request->ready) capability="windows-minidump";
    }
}
#else
int fallback_fd=-1;
struct sigaction previous_actions[5]{};
constexpr int crash_signals[5]{SIGSEGV,SIGBUS,SIGILL,SIGFPE,SIGABRT};
thread_local void* alternate_stack=nullptr;
thread_local stack_t previous_stack{};
thread_local unsigned diagnostic_attachments=0;
void fatal_signal(int signal,siginfo_t* information,void* raw) {
    if(handling.test_and_set()) _exit(128+signal);
    auto* context=static_cast<ucontext_t*>(raw);
#if defined(__x86_64__)
    std::uintptr_t pc=static_cast<std::uintptr_t>(context->uc_mcontext.gregs[REG_RIP]);
#else
    std::uintptr_t pc=0;
#endif
    char bytes[8192];char* end=append_text(bytes,fallback_prefix);
    end=append_number(end,static_cast<std::uint64_t>(syscall(SYS_gettid)));
    end=append_text(end,",\"signal\":");end=append_number(end,signal);
    end=append_text(end,",\"frames\":[{\"pc\":\"");end=append_hex(end,pc);
    end=append_text(end,"\",\"kind\":\"instruction\"");
    if(pc>=main_start && pc<main_end) {end=append_text(end,",\"module\":");end=append_text(end,main_module_json);}
    end=append_text(end,"}],\"status\":\"context-only; inspect system core for other frames\"}\n");
    if(fallback_fd>=0) {auto ignored=write(fallback_fd,bytes,static_cast<std::size_t>(end-bytes));(void)ignored;}
    for(unsigned i=0;i<5;i++) if(crash_signals[i]==signal) {
        const auto& previous=previous_actions[i];
        if(previous.sa_handler!=SIG_DFL && previous.sa_handler!=SIG_IGN) {
            if(previous.sa_flags&SA_SIGINFO) previous.sa_sigaction(signal,information,raw);
            else previous.sa_handler(signal);
        }
    }
    struct sigaction action{};action.sa_handler=SIG_DFL;sigemptyset(&action.sa_mask);sigaction(signal,&action,nullptr);
    syscall(SYS_tgkill,getpid(),syscall(SYS_gettid),signal);
    // The signal is pending until this handler returns.
}
void terminate_capture() noexcept { raise(SIGABRT);_exit(134); }
#endif
}

::jnative::platform::Path diagnostics_report_directory() {return report_directory;}
const char* diagnostics_build_id() noexcept {return JNATIVE_BUILD_ID;}
const char* diagnostics_capability() noexcept {return capability;}
void diagnostics_thread_name(const std::string& name) noexcept {
    try {
        thread_name=name.substr(0,256);
#ifdef _WIN32
        using SetName=HRESULT(WINAPI*)(HANDLE,PCWSTR);
        auto set=reinterpret_cast<SetName>(GetProcAddress(GetModuleHandleW(L"kernel32.dll"),"SetThreadDescription"));
        if(set) {
            int size=MultiByteToWideChar(CP_UTF8,0,thread_name.data(),int(thread_name.size()),nullptr,0);
            std::wstring wide(size,L'\0');
            if(size>0)MultiByteToWideChar(CP_UTF8,0,thread_name.data(),int(thread_name.size()),&wide[0],size);
            set(GetCurrentThread(),wide.c_str());
        }
#else
        pthread_setname_np(pthread_self(),thread_name.substr(0,15).c_str());
#endif
    }catch(...) {}
}
NativeTrace capture_native_trace() noexcept {
    NativeTrace trace;
#if JNATIVE_NATIVE_TRACES
    try {
        trace.thread=thread_id();trace.thread_name=thread_name;
        void* frames[66]{};
#ifdef _WIN32
        auto count=static_cast<int>(RtlCaptureStackBackTrace(1,65,frames,nullptr));
#else
        int count=backtrace(frames,66);
        if(count>0) {--count;std::memmove(frames,frames+1,static_cast<std::size_t>(count)*sizeof(void*));}
#endif
        trace.truncated=count>64;
        for(unsigned i=0;i<static_cast<unsigned>(std::min(count,64));i++) {
            trace.count=i+1;
            trace.frames[i].pc=reinterpret_cast<std::uintptr_t>(frames[i]);
            trace.frames[i].module=identify(trace.frames[i].pc);
        }
    } catch(...) {trace.truncated=true;}
#endif
    return trace;
}
void print_native_trace(const NativeTrace& trace,std::ostream& out) {
    out << "\tNative stack (build " << JNATIVE_BUILD_ID << ", thread " << trace.thread << "):\n";
    for(unsigned i=0;i<trace.count;i++) {
        const auto& frame=trace.frames[i];out<<"\t#"<<i<<" ";
        if(frame.module) out<<frame.module->name<<"+"<<diagnostics::hex(frame.pc-frame.module->base);
        else out<<diagnostics::hex(frame.pc);
        out<<'\n';
    }
    if(trace.truncated) out<<"\t[capture truncated]\n";
}
namespace {
void trace_json(std::ostream& out,const NativeTrace& trace) {
    out<<"[";
    for(unsigned i=0;i<trace.count;i++) {
        if(i) out<<',';
        out<<diagnostics::frame_json(trace.frames[i].pc,true,
                trace.frames[i].module ? *trace.frames[i].module : diagnostics::Module{});
    }
    out<<"]";
}
const char* platform_name() {
#ifdef _WIN32
    return "windows-x64";
#else
    return "linux-x64";
#endif
}
}
std::string save_native_report(const char* event,const std::string& message,const NativeTrace& trace,
        const char* capture_site,const std::vector<NativeCause>& causes) noexcept {
    try {
        if(report_directory.empty()) return "";
        auto sequence=report_sequence.fetch_add(1);
        auto stamp=std::chrono::system_clock::now().time_since_epoch().count();
        auto path=report_directory/(std::to_string(stamp)+"-"+std::to_string(thread_id())+"-"+std::to_string(sequence)+".json");
        ::jnative::platform::OutputFile out(path,std::ios::binary);if(!out)return "";
        out<<"{\"schema\":1,\"buildId\":"<<diagnostics::quote(JNATIVE_BUILD_ID)
           <<",\"platform\":"<<diagnostics::quote(platform_name())
           <<",\"captureSite\":"<<diagnostics::quote(capture_site)
           <<",\"event\":"<<diagnostics::quote(event)<<",\"message\":"<<diagnostics::quote(message.substr(0,8192))
           <<",\"thread\":"<<trace.thread<<",\"threadName\":"<<diagnostics::quote(trace.thread_name)
           <<",\"status\":"<<diagnostics::quote(trace.truncated?"truncated":"captured")<<",\"frames\":";
        trace_json(out,trace);
        out<<",\"causes\":[";
        for(unsigned i=0;i<causes.size() && i<16;i++) {
            if(i)out<<',';
            out<<"{\"message\":"<<diagnostics::quote(causes[i].message.substr(0,8192))
               <<",\"captureSite\":"<<diagnostics::quote(causes[i].capture_site)
               <<",\"status\":"<<diagnostics::quote(causes[i].trace->truncated?"truncated":"captured")<<",\"frames\":";
            trace_json(out,*causes[i].trace);out<<'}';
        }
        out<<"],\"causesTruncated\":"<<(causes.size()>16?"true":"false")<<"}\n";
        out.close();return out ? path.u8string() : "";
    }catch(...) {return "";}
}
void report_native_exception(const std::exception& error,std::ostream& out) noexcept {
    try {
        const auto* owned=dynamic_cast<const NativeException*>(&error);
        auto trace=owned ? owned->native_trace() : capture_native_trace();
        const char* site=owned ? "native-construction" : "catch-site; throw-site unavailable";
        out<<error.what()<<" ["<<site<<"]\n";
        print_native_trace(trace,out);
        auto report=save_native_report("native-exception",error.what(),trace,site);
        if(!report.empty())out<<"Crash report: "<<report<<'\n';
    }catch(...) {}
}
void diagnostics_attach_thread() noexcept {
#if JNATIVE_CRASH_REPORTS
#ifdef _WIN32
    ULONG bytes=32768;SetThreadStackGuarantee(&bytes);
#else
    if(diagnostic_attachments++>0) return;
    stack_t current{};if(sigaltstack(nullptr,&current)!=0 || !(current.ss_flags&SS_DISABLE)) return;
    void* memory=mmap(nullptr,65536,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
    if(memory==MAP_FAILED)return;
    stack_t next{};next.ss_sp=memory;next.ss_size=65536;
    if(sigaltstack(&next,&previous_stack)!=0) {munmap(memory,65536);return;}
    alternate_stack=memory;
#endif
#endif
}
void diagnostics_detach_thread() noexcept {
#if JNATIVE_CRASH_REPORTS && !defined(_WIN32)
    if(!diagnostic_attachments || --diagnostic_attachments>0) return;
    if(alternate_stack) {sigaltstack(&previous_stack,nullptr);munmap(alternate_stack,65536);alternate_stack=nullptr;}
#endif
}
DiagnosticSession::DiagnosticSession() noexcept {
    if(sessions.fetch_add(1)!=0) return;
    const char identity[]=JNATIVE_BUILD_ID;
    for(unsigned i=0;i<sizeof(identity) && i<33;i++) jnative_live_build_identity[i]=identity[i];
    handling.clear();
    handlers_installed=false;
#if JNATIVE_CRASH_REPORTS
    try {
#ifdef _WIN32
        previous_error_mode=GetErrorMode();
        SetErrorMode(previous_error_mode|SEM_FAILCRITICALERRORS|SEM_NOGPFAULTERRORBOX);
        error_mode_changed=true;
#endif
        auto executable=executable_path();
        if(const char* override_path=std::getenv("JNATIVE_REPORT_DIR")) report_directory=::jnative::platform::Path(override_path);
        else {
#ifdef _WIN32
            wchar_t local[32768];auto size=GetEnvironmentVariableW(L"LOCALAPPDATA",local,32768);
            if(!size || size>=32768) return;
            report_directory=::jnative::platform::Path(local)/L"jNative"/executable.stem()/L"crashes";
#else
            const char* state=std::getenv("XDG_STATE_HOME");
            const char* home=std::getenv("HOME");
            if(state) report_directory=state;
            else if(home) report_directory=::jnative::platform::Path(home)/".local/state";
            else return;
            report_directory/=::jnative::platform::Path("jnative")/executable.stem()/"crashes";
#endif
        }
        ::jnative::platform::create_directories(report_directory);
        auto stamp=std::chrono::system_clock::now().time_since_epoch().count();
        auto fallback=report_directory/(std::to_string(stamp)+"-fatal.json");
        fallback_path=fallback;
        std::string prefix="{\"schema\":1,\"platform\":"+diagnostics::quote(platform_name())+",\"captureSite\":\"fault\",\"buildId\":"+diagnostics::quote(JNATIVE_BUILD_ID)+",\"event\":\"crash\",\"thread\":";
        if(prefix.size()>=sizeof(fallback_prefix)) return;
        std::strcpy(fallback_prefix,prefix.c_str());
#ifdef _WIN32
        fallback_file=CreateFileW(fallback.wstring().c_str(),GENERIC_WRITE,FILE_SHARE_READ,nullptr,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,nullptr);
        capability=fallback_file!=INVALID_HANDLE_VALUE ? "windows-context-only" : "unavailable";
        auto module=identify(reinterpret_cast<std::uintptr_t>(&capture_native_trace));
        if(module) {
            auto text=module->json();if(text.size()<sizeof(main_module_json)) std::strcpy(main_module_json,text.c_str());
            main_start=module->base;
            auto* dos=reinterpret_cast<const IMAGE_DOS_HEADER*>(main_start);
            auto* pe=reinterpret_cast<const IMAGE_NT_HEADERS64*>(main_start+dos->e_lfanew);
            main_end=main_start+pe->OptionalHeader.SizeOfImage;
        }
        start_helper(executable);
        previous_filter=SetUnhandledExceptionFilter(fatal_filter);
#else
        fallback_fd=open(fallback.string().c_str(),O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0600);
        auto module=identify(reinterpret_cast<std::uintptr_t>(&capture_native_trace));
        if(module) {
            std::string text=module->json();
            if(text.size()<sizeof(main_module_json)) std::strcpy(main_module_json,text.c_str());
            ::jnative::platform::InputFile maps("/proc/self/maps");std::string line;
            while(std::getline(maps,line)) if(line.find(executable.string())!=std::string::npos) {
                unsigned long long start=0,end=0;
                if(std::sscanf(line.c_str(),"%llx-%llx",&start,&end)==2) {
                    if(!main_start || start<main_start) main_start=start;
                    if(end>main_end) main_end=end;
                }
            }
        }
        capability=fallback_fd>=0 ? "linux-context; system-core-if-configured" : "unavailable";
        diagnostics_attach_thread();
        for(unsigned i=0;i<5;i++) {
            struct sigaction action{};action.sa_sigaction=fatal_signal;action.sa_flags=SA_SIGINFO|SA_ONSTACK;
            sigemptyset(&action.sa_mask);sigaction(crash_signals[i],&action,&previous_actions[i]);
        }
#endif
        handlers_installed=true;
        previous_terminate=std::set_terminate(terminate_capture);
    } catch(...) {capability="unavailable";}
#endif
}
DiagnosticSession::~DiagnosticSession() {
    if(sessions.fetch_sub(1)!=1) return;
#if JNATIVE_CRASH_REPORTS
    if(previous_terminate) std::set_terminate(previous_terminate);
#ifdef _WIN32
    if(handlers_installed) SetUnhandledExceptionFilter(previous_filter);
    if(error_mode_changed) {SetErrorMode(previous_error_mode);error_mode_changed=false;}
    if(helper_process) {
        if(request && request_event) {request->stop=1;SetEvent(request_event);}
        if(WaitForSingleObject(helper_process,1000)==WAIT_TIMEOUT) {
            TerminateProcess(helper_process,0);
            WaitForSingleObject(helper_process,1000);
        }
    }
    if(request) UnmapViewOfFile(request);
    request=nullptr;
    for(HANDLE handle:{mapping,request_event,done_event,parent_handle,helper_process}) if(handle) CloseHandle(handle);
    if(fallback_file!=INVALID_HANDLE_VALUE) CloseHandle(fallback_file);
    mapping=request_event=done_event=parent_handle=helper_process=nullptr;
    fallback_file=INVALID_HANDLE_VALUE;
#else
    if(handlers_installed) for(unsigned i=0;i<5;i++) sigaction(crash_signals[i],&previous_actions[i],nullptr);
    if(fallback_fd>=0) close(fallback_fd);
    fallback_fd=-1;
    diagnostics_detach_thread();
#endif
    if(!fallback_path.empty()) {std::error_code ignored;if(::jnative::platform::file_size(fallback_path,ignored)==0)::jnative::platform::remove(fallback_path,ignored);}
    report_directory.clear();fallback_path.clear();
    previous_terminate=nullptr;handlers_installed=false;capability="off";main_start=main_end=0;
    main_module_json[0]=fallback_prefix[0]=0;
#endif
}
}

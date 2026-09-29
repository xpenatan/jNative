#include "jn_crash_protocol.hpp"
#include "jn_report_files.hpp"
#include "jn_diagnostic_common.hpp"
#include <iostream>
#include <algorithm>
#include <set>
#include <stdexcept>
#ifdef _WIN32
#include <dbghelp.h>
#include <psapi.h>
#include <tlhelp32.h>

using namespace jnative::diagnostics;
namespace {
std::string utf8(const std::wstring& text) {
    int count=WideCharToMultiByte(CP_UTF8,0,text.data(),int(text.size()),nullptr,0,nullptr,nullptr);
    std::string result(count,'\0');
    if(count>0)WideCharToMultiByte(CP_UTF8,0,text.data(),int(text.size()),&result[0],count,nullptr,nullptr);
    return result;
}
std::string thread_description(HANDLE thread) {
    using GetDescription=HRESULT(WINAPI*)(HANDLE,PWSTR*);
    auto get=reinterpret_cast<GetDescription>(GetProcAddress(GetModuleHandleW(L"kernel32.dll"),"GetThreadDescription"));
    PWSTR value=nullptr;
    if(!get || FAILED(get(thread,&value)) || !value)return "";
    auto result=utf8(value);LocalFree(value);return result;
}
struct Loaded { Module module;::jnative::platform::Path file;std::uint32_t timestamp=0;std::vector<unsigned char> bytes; };
std::vector<Loaded> loaded_modules;
Module module_at(std::uint64_t pc) {
    for(auto& image:loaded_modules) if(pc>=image.module.base && pc<image.module.end)return image.module;
    return {};
}
PVOID CALLBACK archived_function_table(HANDLE,DWORD64);
DWORD64 CALLBACK archived_module_base(HANDLE,DWORD64 address) {return module_at(address).base;}
bool read_memory(HANDLE process,PREAD_PROCESS_MEMORY_ROUTINE64 read,DWORD64 address,void* output,DWORD size) {
    if(read) {DWORD count=0;return read(process,address,output,size,&count) && count==size;}
    SIZE_T count=0;return ReadProcessMemory(process,reinterpret_cast<void*>(address),output,size,&count) && count==size;
}
bool unwind_mingw_stack_probe(HANDLE process,PREAD_PROCESS_MEMORY_ROUTINE64 read,CONTEXT& context) {
    // GCC's x64 ___chkstk_ms has two volatile-register pushes and no pdata.
    // A fault at either probe instruction needs its real three-word frame,
    // rather than interpreting saved RAX/RCX as return addresses. Match all
    // instruction bytes; unfamiliar prologues remain explicitly partial.
    static const unsigned char probe[]{
        0x51,0x50,0x48,0x3d,0x00,0x10,0x00,0x00,0x48,0x8d,0x4c,0x24,0x18,0x72,0x19,
        0x48,0x81,0xe9,0x00,0x10,0x00,0x00,0x48,0x83,0x09,0x00,0x48,0x2d,0x00,0x10,
        0x00,0x00,0x48,0x3d,0x00,0x10,0x00,0x00,0x77,0xe7,0x48,0x29,0xc1,0x48,0x83,
        0x09,0x00,0x58,0x59,0xc3};
    for(DWORD64 offset:{22ULL,43ULL}) {
        unsigned char bytes[sizeof(probe)];
        if(context.Rip<offset || !read_memory(process,read,context.Rip-offset,bytes,sizeof(bytes))
                || std::memcmp(bytes,probe,sizeof(bytes))!=0)continue;
        DWORD64 saved[3];
        if(!read_memory(process,read,context.Rsp,saved,sizeof(saved)) || !module_at(saved[2]).base)return false;
        context.Rax=saved[0];context.Rcx=saved[1];context.Rip=saved[2];context.Rsp+=sizeof(saved);
        return true;
    }
    return false;
}
std::vector<std::pair<std::uint64_t,bool>> walk(HANDLE process,HANDLE thread,CONTEXT context,PREAD_PROCESS_MEMORY_ROUTINE64 read=nullptr) {
    std::vector<std::pair<std::uint64_t,bool>> frames;
    STACKFRAME64 frame{};frame.AddrPC.Offset=context.Rip;frame.AddrFrame.Offset=context.Rbp;frame.AddrStack.Offset=context.Rsp;
    frame.AddrPC.Mode=frame.AddrFrame.Mode=frame.AddrStack.Mode=AddrModeFlat;
    std::set<std::pair<DWORD64,DWORD64>> visited;
    bool initial_repeat=true;
    for(unsigned i=0;i<130 && frames.size()<64 && frame.AddrPC.Offset;i++) {
        const auto module=module_at(frame.AddrPC.Offset);
        if(i>0 && !module.base)break;
        bool new_frame=visited.emplace(frame.AddrPC.Offset,frame.AddrStack.Offset).second;
        if(new_frame)frames.emplace_back(frame.AddrPC.Offset,frames.size()!=0);
        else if(initial_repeat)initial_repeat=false;
        else break;
        if(i==0 && unwind_mingw_stack_probe(process,read,context)) {
            initial_repeat=true;
            frame=STACKFRAME64{};frame.AddrPC.Offset=context.Rip;frame.AddrFrame.Offset=context.Rbp;frame.AddrStack.Offset=context.Rsp;
            frame.AddrPC.Mode=frame.AddrFrame.Mode=frame.AddrStack.Mode=AddrModeFlat;
            continue;
        }
        if(read && module.sha256.empty())break; // Unavailable image: retain the PC, never guess through it.
        if(!StackWalk64(IMAGE_FILE_MACHINE_AMD64,process,thread,&frame,&context,read,read?archived_function_table:SymFunctionTableAccess64,read?archived_module_base:SymGetModuleBase64,nullptr))break;
    }
    return frames;
}
void frames_json(std::ostream& out,const std::vector<std::pair<std::uint64_t,bool>>& frames) {
    out<<'[';bool first=true;
    for(auto& frame:frames){if(!first)out<<',';first=false;out<<frame_json(frame.first,frame.second,module_at(frame.first));}
    out<<']';
}
void live_modules(HANDLE process) {
    HMODULE modules[2048];DWORD required=0;
    if(!EnumProcessModulesEx(process,modules,sizeof(modules),&required,LIST_MODULES_ALL))return;
    for(unsigned i=0;i<std::min<unsigned>(required/sizeof(HMODULE),2048);i++) {
        wchar_t path[32768];MODULEINFO info{};
        DWORD count=GetModuleFileNameExW(process,modules[i],path,32768);
        if(!count || !GetModuleInformation(process,modules[i],&info,sizeof(info)))continue;
        Loaded item;item.file=std::wstring(path,count);
        item.module.name=item.file.filename().u8string();item.module.sha256=hash_file(item.file);
        item.module.base=reinterpret_cast<std::uintptr_t>(info.lpBaseOfDll);item.module.end=item.module.base+info.SizeOfImage;
        item.module.preferred=preferred_base(item.file,item.module.base);loaded_modules.push_back(std::move(item));
    }
}
int watch(int argc,wchar_t** argv) {
    if(argc!=6)return 2;
    auto handle=[](const wchar_t* text){return reinterpret_cast<HANDLE>(std::stoull(text));};
    HANDLE mapping=handle(argv[2]),event=handle(argv[3]),done=handle(argv[4]),process=handle(argv[5]);
    auto* request=static_cast<CrashRequest*>(MapViewOfFile(mapping,FILE_MAP_ALL_ACCESS,0,0,sizeof(CrashRequest)));
    if(!request)return 3;
    request->ready=1;SetEvent(done);
    HANDLE waits[]{event,process};
    if(WaitForMultipleObjects(2,waits,FALSE,INFINITE)!=WAIT_OBJECT_0)return 0;
    if(request->stop) {UnmapViewOfFile(request);return 0;}
    ::jnative::platform::Path dir=request->directory;
    auto stem=std::to_string(request->process)+"-"+std::to_string(request->thread)+"-"+std::to_string(GetTickCount64());
    auto dump_path=dir/(stem+".dmp"),json_path=dir/(stem+".json");
    std::string metadata="{\"schema\":1,\"buildId\":"+quote(request->build)+"}";
    MINIDUMP_USER_STREAM stream{};stream.Type=0x476a0001;stream.BufferSize=ULONG(metadata.size());stream.Buffer=&metadata[0];
    MINIDUMP_USER_STREAM_INFORMATION user{1,&stream};
    EXCEPTION_POINTERS pointers{&request->exception,&request->context};
    MINIDUMP_EXCEPTION_INFORMATION exception{request->thread,&pointers,FALSE};
    HANDLE dump=request->include_dump ? CreateFileW(dump_path.wstring().c_str(),GENERIC_WRITE,0,nullptr,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,nullptr) : INVALID_HANDLE_VALUE;
    BOOL saved=FALSE;
    if(dump!=INVALID_HANDLE_VALUE) {
        saved=MiniDumpWriteDump(process,request->process,dump,
            static_cast<MINIDUMP_TYPE>(MiniDumpWithThreadInfo|MiniDumpWithUnloadedModules|MiniDumpWithIndirectlyReferencedMemory),
            &exception,&user,nullptr);
        CloseHandle(dump);
        if(saved && ::jnative::platform::file_size(dump_path)>1024ULL*1024*1024) {::jnative::platform::remove(dump_path);saved=FALSE;}
    }
    live_modules(process);
    bool symbols=SymInitialize(process,nullptr,TRUE)!=FALSE;
    auto fault_thread=OpenThread(THREAD_GET_CONTEXT|THREAD_QUERY_INFORMATION,FALSE,request->thread);
    auto frames=walk(process,fault_thread,request->context);
    ::jnative::platform::OutputFile out(json_path,std::ios::binary);
    out<<"{\"schema\":1,\"buildId\":"<<quote(request->build)
       <<",\"event\":"<<quote(request->exception.ExceptionCode==0xe04a4e01?"terminate":"crash")
       <<",\"platform\":\"windows-x64\",\"captureSite\":\"fault\",\"thread\":"<<request->thread
       <<",\"threadName\":"<<quote(thread_description(fault_thread))
       <<",\"code\":"<<quote(hex(request->exception.ExceptionCode))
       <<",\"status\":"<<quote(saved?"minidump":request->include_dump?"dump-unavailable":"dump-disabled")<<",\"dump\":"<<quote(saved?dump_path.filename().u8string():"")
       <<",\"frames\":";frames_json(out,frames);out<<",\"unwindStatus\":\"best-effort; optimized or corrupted frames may be absent\",\"truncated\":"<<(frames.size()>=64?"true":"false")<<",\"threads\":[";
    HANDLE snapshot=CreateToolhelp32Snapshot(TH32CS_SNAPTHREAD,0);
    THREADENTRY32 entry{};entry.dwSize=sizeof(entry);bool first=true;unsigned thread_count=0;
    if(snapshot!=INVALID_HANDLE_VALUE && Thread32First(snapshot,&entry))do{
        if(entry.th32OwnerProcessID!=request->process || entry.th32ThreadID==request->thread)continue;
        if(++thread_count>128)break;
        HANDLE thread=OpenThread(THREAD_GET_CONTEXT|THREAD_QUERY_INFORMATION|THREAD_SUSPEND_RESUME,FALSE,entry.th32ThreadID);
        if(!thread)continue;
        CONTEXT context{};context.ContextFlags=CONTEXT_FULL;
        if(SuspendThread(thread)!=DWORD(-1)) {
            if(GetThreadContext(thread,&context)) {
                if(!first)out<<',';first=false;
                out<<"{\"thread\":"<<entry.th32ThreadID<<",\"threadName\":"<<quote(thread_description(thread))<<",\"frames\":";frames_json(out,walk(process,thread,context));out<<'}';
            }
            ResumeThread(thread);
        }
        CloseHandle(thread);
    }while(Thread32Next(snapshot,&entry));
    if(snapshot!=INVALID_HANDLE_VALUE)CloseHandle(snapshot);
    out<<"]}\n";out.close();
    if(fault_thread)CloseHandle(fault_thread);
    if(symbols)SymCleanup(process);
    request->saved=out ? 1 : 0;int result=request->saved?0:4;SetEvent(done);UnmapViewOfFile(request);
    return result;
}
struct Memory {std::uint64_t base,size,rva;};
struct Dump {
    std::vector<unsigned char> bytes;std::vector<Memory> memory;
    std::vector<MINIDUMP_DIRECTORY> streams;
    const unsigned char* range(std::uint64_t pos,std::uint64_t length) const {
        if(pos>bytes.size() || length>bytes.size()-pos)throw std::runtime_error("Invalid minidump bounds");
        return bytes.data()+pos;
    }
    template<class T>T get(std::uint64_t pos)const {T value;std::memcpy(&value,range(pos,sizeof(T)),sizeof(T));return value;}
    const MINIDUMP_DIRECTORY* stream(unsigned type)const {
        for(auto& stream:streams)if(stream.StreamType==type)return &stream;return nullptr;
    }
    explicit Dump(const ::jnative::platform::Path& path) {
        auto size=::jnative::platform::file_size(path);if(size>1024ULL*1024*1024)throw std::runtime_error("Dump exceeds 1 GiB limit");
        bytes.resize(static_cast<std::size_t>(size));::jnative::platform::InputFile in(path,std::ios::binary);in.read(reinterpret_cast<char*>(bytes.data()),bytes.size());
        if(!in)throw std::runtime_error("Cannot read dump");
        auto header=get<MINIDUMP_HEADER>(0);
        if(header.Signature!=0x504d444d || header.NumberOfStreams>4096)throw std::runtime_error("Invalid dump header");
        for(unsigned i=0;i<header.NumberOfStreams;i++) {
            auto d=get<MINIDUMP_DIRECTORY>(std::uint64_t(header.StreamDirectoryRva)+i*sizeof(MINIDUMP_DIRECTORY));
            range(d.Location.Rva,d.Location.DataSize);streams.push_back(d);
        }
        if(auto list=stream(MemoryListStream)) {
            auto count=get<ULONG>(list->Location.Rva);if(count>100000)throw std::runtime_error("Too many memory ranges");
            for(unsigned i=0;i<count;i++) {
                auto m=get<MINIDUMP_MEMORY_DESCRIPTOR>(std::uint64_t(list->Location.Rva)+4+i*sizeof(MINIDUMP_MEMORY_DESCRIPTOR));
                range(m.Memory.Rva,m.Memory.DataSize);memory.push_back({m.StartOfMemoryRange,m.Memory.DataSize,m.Memory.Rva});
            }
        }
        if(auto list=stream(Memory64ListStream)) {
            auto header64=get<MINIDUMP_MEMORY64_LIST>(list->Location.Rva);
            if(header64.NumberOfMemoryRanges>100000)throw std::runtime_error("Too many memory ranges");
            auto rva=header64.BaseRva;
            for(std::uint64_t i=0;i<header64.NumberOfMemoryRanges;i++) {
                auto m=get<MINIDUMP_MEMORY_DESCRIPTOR64>(std::uint64_t(list->Location.Rva)+16+i*sizeof(MINIDUMP_MEMORY_DESCRIPTOR64));
                range(rva,m.DataSize);memory.push_back({m.StartOfMemoryRange,m.DataSize,rva});rva+=m.DataSize;
            }
        }
        if(auto list=stream(ThreadListStream)) {
            auto count=get<ULONG>(list->Location.Rva);if(count>100000)throw std::runtime_error("Too many threads");
            for(unsigned i=0;i<count;i++) {
                auto t=get<MINIDUMP_THREAD>(std::uint64_t(list->Location.Rva)+4+i*sizeof(MINIDUMP_THREAD));
                range(t.Stack.Memory.Rva,t.Stack.Memory.DataSize);
                memory.push_back({t.Stack.StartOfMemoryRange,t.Stack.Memory.DataSize,t.Stack.Memory.Rva});
            }
        }
    }
};
Dump* active_dump=nullptr;
BOOL CALLBACK read_dump(HANDLE,DWORD64 address,PVOID output,DWORD size,LPDWORD read) {
    *read=0;
    for(auto& m:active_dump->memory) if(address>=m.base && address-m.base<m.size && size<=m.size-(address-m.base)) {
        std::memcpy(output,active_dump->range(m.rva+(address-m.base),size),size);*read=size;return TRUE;
    }
    for(auto& module:loaded_modules)if(address>=module.module.base && address-module.module.base<module.module.end-module.module.base) {
        auto rva=address-module.module.base;
        if(module.bytes.size()<sizeof(IMAGE_DOS_HEADER))continue;
        auto* dos=reinterpret_cast<const IMAGE_DOS_HEADER*>(module.bytes.data());
        if(dos->e_lfanew<0 || std::size_t(dos->e_lfanew)+sizeof(IMAGE_NT_HEADERS64)>module.bytes.size())continue;
        auto* nt=reinterpret_cast<const IMAGE_NT_HEADERS64*>(module.bytes.data()+dos->e_lfanew);
        auto section_offset=std::size_t(dos->e_lfanew)+24+nt->FileHeader.SizeOfOptionalHeader;
        for(unsigned i=0;i<nt->FileHeader.NumberOfSections;i++) {
            if(section_offset+(i+1)*sizeof(IMAGE_SECTION_HEADER)>module.bytes.size())break;
            auto* section=reinterpret_cast<const IMAGE_SECTION_HEADER*>(module.bytes.data()+section_offset+i*sizeof(IMAGE_SECTION_HEADER));
            if(rva>=section->VirtualAddress && rva-section->VirtualAddress<section->SizeOfRawData
                    && size<=section->SizeOfRawData-(rva-section->VirtualAddress)) {
                auto offset=section->PointerToRawData+(rva-section->VirtualAddress);
                if(offset+size>module.bytes.size())return FALSE;
                std::memcpy(output,module.bytes.data()+offset,size);*read=size;return TRUE;
            }
        }
    }
    return FALSE;
}
// DbgHelp may not expose function tables for a stripped MinGW PE image. Read
// the x64 exception directory from the verified image; StackWalk64 still performs
// the platform unwinding and obtains stack/xdata bytes through read_dump.
PVOID CALLBACK archived_function_table(HANDLE process,DWORD64 address) {
    static RUNTIME_FUNCTION function;
    for(auto& image:loaded_modules) {
        if(address<image.module.base || address>=image.module.end || image.bytes.empty())continue;
        auto* dos=reinterpret_cast<const IMAGE_DOS_HEADER*>(image.bytes.data());
        auto* nt=reinterpret_cast<const IMAGE_NT_HEADERS64*>(image.bytes.data()+dos->e_lfanew);
        auto directory=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_EXCEPTION];
        std::uint64_t low=0,high=directory.Size/sizeof(RUNTIME_FUNCTION);
        if(high>1000000)return nullptr;
        auto relative=address-image.module.base;
        while(low<high) {
            auto middle=(low+high)/2;DWORD copied=0;
            if(!read_dump(process,image.module.base+directory.VirtualAddress+middle*sizeof(function),&function,sizeof(function),&copied))return nullptr;
            if(relative<function.BeginAddress)high=middle;
            else if(relative>=function.EndAddress)low=middle+1;
            else return &function;
        }
    }
    return nullptr;
}
BOOL CALLBACK symbol_callback(HANDLE process,ULONG action,ULONG64 data,ULONG64) {
    if(action!=CBA_READ_MEMORY)return FALSE;
    auto* request=reinterpret_cast<IMAGEHLP_CBA_READ_MEMORY*>(data);
    return read_dump(process,request->addr,request->buf,request->bytes,request->bytesread);
}
int decode_dump(const ::jnative::platform::Path& path,const ::jnative::platform::Path& images) {
    Dump dump(path);active_dump=&dump;
    auto sys=dump.stream(SystemInfoStream);
    if(!sys || dump.get<MINIDUMP_SYSTEM_INFO>(sys->Location.Rva).ProcessorArchitecture!=PROCESSOR_ARCHITECTURE_AMD64)
        throw std::runtime_error("Only x64 minidumps are supported");
    HANDLE token=reinterpret_cast<HANDLE>(std::uintptr_t(0x4a4e));
    if(!SymInitialize(token,nullptr,FALSE))throw std::runtime_error("Cannot initialize dump unwinder");
    SymRegisterCallback64(token,symbol_callback,0);
    if(auto list=dump.stream(ModuleListStream)) {
        auto count=dump.get<ULONG>(list->Location.Rva);if(count>4096)throw std::runtime_error("Too many modules");
        for(unsigned i=0;i<count;i++) {
            auto m=dump.get<MINIDUMP_MODULE>(std::uint64_t(list->Location.Rva)+4+i*sizeof(MINIDUMP_MODULE));
            auto length=dump.get<ULONG>(m.ModuleNameRva);if(length>65536 || length%2)throw std::runtime_error("Invalid module name");
            std::wstring original(length/2,L'\0');std::memcpy(&original[0],dump.range(std::uint64_t(m.ModuleNameRva)+4,length),length);
            Loaded image;image.file=images/::jnative::platform::Path(original).filename();
            image.module.name=image.file.filename().u8string();image.module.base=m.BaseOfImage;image.module.end=m.BaseOfImage+m.SizeOfImage;
            if(::jnative::platform::is_regular_file(image.file)) {
                auto size=::jnative::platform::file_size(image.file);
                if(size<sizeof(IMAGE_DOS_HEADER) || size>512ULL*1024*1024)throw std::runtime_error("Invalid module image size");
                image.bytes.resize(static_cast<std::size_t>(size));::jnative::platform::InputFile in(image.file,std::ios::binary);in.read(reinterpret_cast<char*>(image.bytes.data()),size);
                auto* dos=reinterpret_cast<const IMAGE_DOS_HEADER*>(image.bytes.data());
                if(dos->e_magic!=IMAGE_DOS_SIGNATURE || dos->e_lfanew<0 || std::size_t(dos->e_lfanew)+sizeof(IMAGE_NT_HEADERS64)>size)
                    throw std::runtime_error("Invalid module image");
                auto* pe=reinterpret_cast<const IMAGE_NT_HEADERS64*>(image.bytes.data()+dos->e_lfanew);
                if(pe->Signature!=IMAGE_NT_SIGNATURE || pe->FileHeader.TimeDateStamp!=m.TimeDateStamp || pe->OptionalHeader.SizeOfImage!=m.SizeOfImage)
                    throw std::runtime_error("Module does not match minidump");
                image.module.sha256=hash_file(image.file);image.module.preferred=preferred_base(image.file,m.BaseOfImage);
                SymLoadModuleExW(token,nullptr,image.file.wstring().c_str(),nullptr,m.BaseOfImage,m.SizeOfImage,nullptr,0);
            }
            loaded_modules.push_back(std::move(image));
        }
    }
    auto exception_stream=dump.stream(ExceptionStream);
    if(!exception_stream)throw std::runtime_error("Dump has no faulting context");
    auto exception=dump.get<MINIDUMP_EXCEPTION_STREAM>(exception_stream->Location.Rva);
    if(exception.ThreadContext.DataSize<sizeof(CONTEXT))throw std::runtime_error("Incomplete thread context");
    auto context=dump.get<CONTEXT>(exception.ThreadContext.Rva);
    std::string metadata="{}";
    if(auto user=dump.stream(0x476a0001))metadata.assign(reinterpret_cast<const char*>(dump.range(user->Location.Rva,user->Location.DataSize)),user->Location.DataSize);
    std::cout<<"{\"schema\":1,\"metadata\":"<<metadata<<",\"event\":\"crash\",\"thread\":"<<exception.ThreadId<<",\"frames\":";
    frames_json(std::cout,walk(token,reinterpret_cast<HANDLE>(std::uintptr_t(exception.ThreadId)),context,read_dump));
    std::cout<<",\"threads\":[";
    bool first=true;
    if(auto list=dump.stream(ThreadListStream)) {
        auto count=dump.get<ULONG>(list->Location.Rva);
        if(count>4096)throw std::runtime_error("Too many dump threads");
        for(unsigned i=0;i<std::min<unsigned>(count,128);i++) {
            auto thread=dump.get<MINIDUMP_THREAD>(std::uint64_t(list->Location.Rva)+4+i*sizeof(MINIDUMP_THREAD));
            if(thread.ThreadId==exception.ThreadId || thread.ThreadContext.DataSize<sizeof(CONTEXT))continue;
            if(!first)std::cout<<',';first=false;
            std::cout<<"{\"thread\":"<<thread.ThreadId<<",\"frames\":";
            frames_json(std::cout,walk(token,reinterpret_cast<HANDLE>(std::uintptr_t(thread.ThreadId)),dump.get<CONTEXT>(thread.ThreadContext.Rva),read_dump));
            std::cout<<'}';
        }
    }
    std::cout<<"]}\n";SymCleanup(token);return 0;
}
}
int wmain(int argc,wchar_t** argv) {
    try {
        if(argc==3 && std::wstring(argv[1])==L"--reports") {
            for(const auto& report:pending_reports(argv[2]))std::cout<<report.u8string()<<'\n';return 0;
        }
        if((argc==4 || argc==5) && std::wstring(argv[1])==L"--export") {
            bool include=argc==4;
            if(argc==5 && std::wstring(argv[4])!=L"--without-dump")throw std::runtime_error("Unknown export option");
            std::cout<<export_report(argv[2],argv[3],include).u8string()<<'\n';return 0;
        }
        if(argc>=2 && std::wstring(argv[1])==L"--watch")return watch(argc,argv);
        if(argc==4 && std::wstring(argv[1])==L"--dump")return decode_dump(argv[2],argv[3]);
        if(argc==3 && std::wstring(argv[1])==L"--hash"){std::cout<<hash_file(argv[2])<<'\n';return 0;}
        std::cerr<<"Usage: jnative-diagnostics --dump <minidump> <archived-images>\n";return 2;
    }catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 2;}
}
#else
int main(int argc,char** argv) {
    try {
        if(argc==3 && std::string(argv[1])=="--reports") {
            for(const auto& report:jnative::diagnostics::pending_reports(argv[2]))std::cout<<report.u8string()<<'\n';return 0;
        }
        if((argc==4 || argc==5) && std::string(argv[1])=="--export") {
            if(argc==5 && std::string(argv[4])!="--without-dump")throw std::runtime_error("Unknown export option");
            std::cout<<jnative::diagnostics::export_report(argv[2],argv[3],argc==4).u8string()<<'\n';return 0;
        }
    }catch(const std::exception& error){std::cerr<<error.what()<<'\n';return 2;}
    if(argc==3 && std::string(argv[1])=="--hash"){std::cout<<jnative::diagnostics::hash_file(argv[2])<<'\n';return 0;}
    std::cerr<<"Use jnative decode for Linux reports; use gdb with the exact archived image for system cores.\n";return 2;
}
#endif

#pragma once
#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <sstream>
#include <iomanip>
#include "jn_platform_files.hpp"
#include <vector>
#include <string>

namespace jnative { namespace diagnostics {
inline std::string quote(const std::string& text) {
    std::ostringstream out; out << '"';
    for (unsigned char c : text) {
        switch(c) {
            case '\\': out << "\\\\"; break; case '"': out << "\\\""; break;
            case '\n': out << "\\n"; break; case '\r': out << "\\r"; break; case '\t': out << "\\t"; break;
            default: if(c < 32) out << "\\u" << std::hex << std::setw(4) << std::setfill('0') << unsigned(c);
                     else out << char(c);
        }
    }
    return out.str() + '"';
}
inline std::string hex(std::uint64_t value) { std::ostringstream out; out << "0x" << std::hex << value; return out.str(); }

// SHA-256 of exact module bytes. Called only in ordinary execution or the helper,
// never in a fatal signal/exception handler.
class Sha256 {
    std::array<std::uint32_t,8> state_{{0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19}};
    std::array<unsigned char,64> buffer_{};
    std::uint64_t size_=0; std::size_t used_=0;
    static std::uint32_t rotate(std::uint32_t x, unsigned n) { return (x>>n)|(x<<(32-n)); }
    void block(const unsigned char* b) {
        static constexpr std::uint32_t k[64]={
            0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
            0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
            0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
            0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
            0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
            0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
            0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
            0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2};
        std::uint32_t w[64];
        for(int i=0;i<16;i++) w[i]=(std::uint32_t(b[i*4])<<24)|(std::uint32_t(b[i*4+1])<<16)|(std::uint32_t(b[i*4+2])<<8)|b[i*4+3];
        for(int i=16;i<64;i++) {
            auto x=w[i-15], y=w[i-2];
            w[i]=w[i-16]+(rotate(x,7)^rotate(x,18)^(x>>3))+w[i-7]+(rotate(y,17)^rotate(y,19)^(y>>10));
        }
        auto a=state_[0],b0=state_[1],c=state_[2],d=state_[3],e=state_[4],f=state_[5],g=state_[6],h=state_[7];
        for(int i=0;i<64;i++) {
            auto t1=h+(rotate(e,6)^rotate(e,11)^rotate(e,25))+((e&f)^(~e&g))+k[i]+w[i];
            auto t2=(rotate(a,2)^rotate(a,13)^rotate(a,22))+((a&b0)^(a&c)^(b0&c));
            h=g;g=f;f=e;e=d+t1;d=c;c=b0;b0=a;a=t1+t2;
        }
        state_[0]+=a;state_[1]+=b0;state_[2]+=c;state_[3]+=d;state_[4]+=e;state_[5]+=f;state_[6]+=g;state_[7]+=h;
    }
public:
    void update(const char* data,std::size_t count) {
        size_+=count;
        while(count) { auto n=std::min(count,64-used_); std::memcpy(buffer_.data()+used_,data,n);used_+=n;data+=n;count-=n;
            if(used_==64) { block(buffer_.data());used_=0; }
        }
    }
    std::string finish() {
        auto bits=size_*8; char one=char(0x80);update(&one,1);char zero=0;
        while(used_!=56) update(&zero,1);
        unsigned char length[8];for(int i=0;i<8;i++) length[7-i]=static_cast<unsigned char>(bits>>(i*8));
        update(reinterpret_cast<char*>(length),8);
        std::ostringstream out;for(auto x:state_) out<<std::hex<<std::setw(8)<<std::setfill('0')<<x;
        return out.str();
    }
};
inline std::string hash_file(const ::jnative::platform::Path& path) {
    ::jnative::platform::InputFile input(path,std::ios::binary); if(!input) return "";
    Sha256 hash; std::array<char,16384> data;
    while(input) { input.read(data.data(),data.size());hash.update(data.data(),static_cast<std::size_t>(input.gcount())); }
    return input.bad() ? "" : hash.finish();
}
inline std::uint64_t preferred_base(const ::jnative::platform::Path& path, std::uint64_t loaded_base=0) {
    ::jnative::platform::InputFile in(path,std::ios::binary); unsigned char head[64]{};
    if(!in.read(reinterpret_cast<char*>(head),64)) return 0;
    if(head[0]=='M' && head[1]=='Z') {
        std::uint32_t pe=0;std::memcpy(&pe,head+60,4);
        in.seekg(pe+24);unsigned char opt[32]{};
        if(!in.read(reinterpret_cast<char*>(opt),32)) return 0;
        std::uint16_t magic=0;std::memcpy(&magic,opt,2);
        if(magic==0x20b) { std::uint64_t base=0;std::memcpy(&base,opt+24,8);return base; }
        if(magic==0x10b) { std::uint32_t base=0;std::memcpy(&base,opt+28,4);return base; }
    }
    // ELF ET_EXEC uses its linked absolute address; ET_DYN is relocated.
    if(head[0]==0x7f && head[1]=='E' && head[2]=='L' && head[3]=='F' && head[16]==2) return loaded_base;
    return 0;
}
struct Module {
    std::string name, sha256;
    std::uint64_t base=0, preferred=0, end=0;
    std::string json() const {
        return "{\"name\":"+quote(name)+",\"sha256\":"+quote(sha256)+",\"base\":"+quote(hex(base))
            +",\"preferredBase\":"+quote(hex(preferred))+"}";
    }
};
inline std::string frame_json(std::uint64_t pc,bool returned,const Module& module) {
    return "{\"pc\":"+quote(hex(pc))+",\"kind\":"+quote(returned?"return":"instruction")+",\"module\":"+module.json()+"}";
}
} }

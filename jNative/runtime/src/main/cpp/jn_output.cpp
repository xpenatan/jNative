#include "jn_platform.hpp"
#include <streambuf>
namespace jnative { namespace platform {
namespace {
class OutputBuffer : public std::streambuf {
    bool error_;
public:
    explicit OutputBuffer(bool error) : error_(error) {}
    std::streamsize xsputn(const char* data, std::streamsize count) override {
        if (count > 0) write_output(error_, data, static_cast<std::size_t>(count));
        return count;
    }
    int_type overflow(int_type value) override {
        if (!traits_type::eq_int_type(value, traits_type::eof())) {
            char character = traits_type::to_char_type(value);
            write_output(error_, &character, 1);
        }
        return traits_type::not_eof(value);
    }
};
}
std::ostream& output() { static OutputBuffer buffer(false); static std::ostream stream(&buffer); return stream; }
std::ostream& error_output() { static OutputBuffer buffer(true); static std::ostream stream(&buffer); return stream; }
} }

#include "jn_runtime.hpp"
#include "jn_string_kernels.hpp"
#include <algorithm>
#include <regex>

namespace jnative {
namespace {
struct NativeRegex final : Object {
    std::wregex expression;
    ManagedField<Object*> literal;
    explicit NativeRegex(std::wregex&& value) : expression(std::move(value)) {}
    explicit NativeRegex(Object* value) : literal(value) {}
    void trace(Tracer& tracer) override { tracer.visit(literal.get()); }
};
bool literal_pattern(const std::u16string& source, int flags) {
    if (flags & 2) return false;
    if (flags & 16) return true;
    for (std::size_t i = 0; i < source.size(); ++i) {
        switch (source[i]) {
            case u'\\': case u'.': case u'^': case u'$': case u'|': case u'(':
            case u')': case u'[': case u']': case u'{': case u'}': case u'*':
            case u'+': case u'?': return false;
            default: break;
        }
        if ((i & 1023) == 1023) safepoint();
    }
    return true;
}
std::wstring regex_pattern(const std::u16string& source, int flags) {
    std::wstring result;
    bool bracket = false;
    std::uint32_t work = 0;
    for (std::size_t i = 0; i < source.size(); ++i) {
        if ((++work & 1023) == 0) safepoint();
        wchar_t value = source[i];
        if (flags & 16) {
            if (std::wstring(L"\\.^$|()[]{}*+?").find(value) != std::wstring::npos) result += L'\\';
            result += value;
        } else if (value == L'\\' && i + 1 < source.size()) {
            wchar_t next = source[++i];
            if (next == L'R' && !bracket) result += L"(?:\\r\\n|[\\n\\r\\v\\f\\u0085\\u2028\\u2029])";
            else { result += L'\\'; result += next; }
        } else if (value == L'.' && !bracket) {
            result += flags & 32 ? L"[\\s\\S]" : L"[^\\n\\r\\u0085\\u2028\\u2029]";
        } else {
            if (value == L'[') bracket = true;
            else if (value == L']') bracket = false;
            result += value;
        }
    }
    return result;
}
}
Object* regex_compile(Object* pattern, int flags) {
    LocalRoot<> pattern_root(pattern);
    if (literal_pattern(as_string(pattern)->value, flags)) return allocate<NativeRegex>(pattern);
    auto source = regex_pattern(as_string(pattern)->value, flags);
    auto options = std::regex_constants::ECMAScript;
    if (flags & 2) options |= std::regex_constants::icase;
    try {
        std::wregex expression;
        {
            NativeRegion blocked(ThreadState::blocked_managed);
            expression.assign(source, options);
        }
        return allocate<NativeRegex>(std::move(expression));
    }
    catch (const std::regex_error& error) { raise("java/lang/IllegalArgumentException", error.what()); }
}
Object* regex_find(Object* compiled, Object* input, int offset, bool whole) {
    RootFrame<3> roots;
    FrameRoot<> compiled_root(roots.slot(0), compiled), input_root(roots.slot(1), input), result(roots.slot(2));
    auto regex = dynamic_cast<NativeRegex*>(require_non_null(compiled));
    if (!regex) raise("java/lang/IllegalArgumentException", "Invalid compiled regex");
    const auto& source = as_string(input)->value;
    if (offset < 0 || static_cast<std::size_t>(offset) > source.size()) raise("java/lang/IndexOutOfBoundsException");
    if (auto literal = regex->literal.get()) {
        // Plain delimiters need neither UTF-16 staging nor the general regex engine.
        const auto start = whole ? (string_equals(input, literal) ? 0 : -1)
            : string_index_of_text(input, literal, offset);
        if (start < 0) return nullptr;
        const auto end = start + static_cast<int>(as_string(literal)->value.size());
        result.set(new_array("[I", 2));
        array_set<std::int32_t>(result.get(), 0, start);
        array_set<std::int32_t>(result.get(), 1, end);
        return result.get();
    }
    std::wstring text(source.size(), L'\0');
    for (std::size_t begin = 0; begin < source.size();) {
        const auto end = begin + std::min(std::size_t(1024), source.size() - begin);
        std::copy(source.begin() + begin, source.begin() + end, text.begin() + begin);
        begin = end;
        if (begin < source.size()) safepoint();
    }
    std::match_results<std::wstring::const_iterator> matches;
    auto flags = std::regex_constants::match_default;
    if (offset > 0) flags |= std::regex_constants::match_prev_avail;
    bool found;
    try {
        // The standard engine cannot poll internally. Its immutable expression
        // remains rooted while other managed threads are allowed to collect.
        NativeRegion blocked(ThreadState::blocked_managed);
        found = whole ? std::regex_match(text.cbegin(), text.cend(), matches, regex->expression)
                : std::regex_search(text.cbegin() + offset, text.cend(), matches, regex->expression, flags);
    } catch (const std::regex_error& error) { raise("java/lang/IllegalStateException", error.what()); }
    if (!found) return nullptr;
    if (matches.size() > static_cast<std::size_t>(INT32_MAX / 2)) raise("java/lang/OutOfMemoryError");
    result.set(new_array("[I", static_cast<int>(matches.size() * 2)));
    for (std::size_t i = 0; i < matches.size(); ++i) {
        int start = matches[i].matched ? static_cast<int>(matches[i].first - text.cbegin()) : -1;
        int end = matches[i].matched ? static_cast<int>(matches[i].second - text.cbegin()) : -1;
        array_set<std::int32_t>(result.get(), static_cast<int>(i * 2), start);
        array_set<std::int32_t>(result.get(), static_cast<int>(i * 2 + 1), end);
        if ((i & 1023) == 1023) safepoint();
    }
    return result.get();
}
}

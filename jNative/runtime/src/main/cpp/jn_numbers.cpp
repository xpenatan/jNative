#include "jn_runtime.hpp"
#include <regex>
#include <iomanip>
#include <locale>
#include <cstdlib>
#include <locale.h>

namespace jnative {
namespace {
#if defined(__MINGW32__)
// The legacy Windows CRT has no _strtof_l. MinGW supplies the C99 parser;
// select its numeric locale per thread so native callers' locales stay intact.
struct NumericLocale {
    std::string previous;
    int mode;
    NumericLocale() : previous(setlocale(LC_NUMERIC, nullptr)),
            mode(_configthreadlocale(_ENABLE_PER_THREAD_LOCALE)) {
        setlocale(LC_NUMERIC, "C");
    }
    ~NumericLocale() {
        setlocale(LC_NUMERIC, previous.c_str());
        _configthreadlocale(mode);
    }
};
#endif
std::string decimal_input(Object* text) {
    std::string value = utf8(as_string(text)->value);
    auto first = value.find_first_not_of(" \t\r\n\f\v");
    auto last = value.find_last_not_of(" \t\r\n\f\v");
    if (first == std::string::npos) raise("java/lang/NumberFormatException", "Empty string");
    value = value.substr(first, last - first + 1);
    static const std::regex grammar("[+-]?(NaN|Infinity|((([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?)|(0[xX]([0-9a-fA-F]+(\\.[0-9a-fA-F]*)?|\\.[0-9a-fA-F]+)[pP][+-]?[0-9]+))[fFdD]?)");
    if (!std::regex_match(value, grammar)) raise("java/lang/NumberFormatException", value.c_str());
    if (value.back() == 'f' || value.back() == 'F' || value.back() == 'd' || value.back() == 'D') value.pop_back();
    return value;
}
}
double parse_double(Object* text) {
    std::string value = decimal_input(text);
#if defined(__MINGW32__)
    NumericLocale locale;
    return std::strtod(value.c_str(), nullptr);
#elif defined(_WIN32)
    static _locale_t locale = _create_locale(LC_NUMERIC, "C");
    return _strtod_l(value.c_str(), nullptr, locale);
#elif defined(__unix__) || defined(__APPLE__)
    static locale_t locale = newlocale(LC_NUMERIC_MASK, "C", nullptr);
    return strtod_l(value.c_str(), nullptr, locale);
#else
    return std::strtod(value.c_str(), nullptr);
#endif
}
float parse_float(Object* text) {
    std::string value = decimal_input(text);
#if defined(__MINGW32__)
    NumericLocale locale;
    return std::strtof(value.c_str(), nullptr);
#elif defined(_WIN32)
    static _locale_t locale = _create_locale(LC_NUMERIC, "C");
    return _strtof_l(value.c_str(), nullptr, locale);
#elif defined(__unix__) || defined(__APPLE__)
    static locale_t locale = newlocale(LC_NUMERIC_MASK, "C", nullptr);
    return strtof_l(value.c_str(), nullptr, locale);
#else
    return std::strtof(value.c_str(), nullptr);
#endif
}
Object* format_decimal(double value, int precision, int conversion) {
    if (precision < 0) raise("java/lang/IllegalArgumentException", "Negative precision");
    if (std::isnan(value)) return literal(u"NaN");
    if (std::isinf(value)) return literal(value < 0 ? u"-Infinity" : u"Infinity");
    std::ostringstream output;
    output.imbue(std::locale::classic());
    if (conversion == 'f') output << std::fixed;
    else if (conversion == 'e') output << std::scientific;
    output << std::setprecision(precision) << value;
    return allocate<String>(utf16(output.str()));
}
}

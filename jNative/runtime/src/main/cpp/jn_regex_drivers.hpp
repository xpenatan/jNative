#pragma once
#include "jn_runtime.hpp"
#include "jn_classlib.hpp"
#include <utility>
#include <vector>

namespace jnative {
namespace regex_driver_detail {
class ReplacementPoll {
    LoopSafepoint safepoint_;
    unsigned remaining_ = 1024;
public:
    void step() { if (--remaining_ == 0) { safepoint_.poll(); remaining_ = 1024; } }
};
// Stage immutable UTF-16 text without retaining an unrooted managed reference.
inline Object* slice(Object* input, std::int32_t from, std::int32_t to) {
    LocalRoot<> text(require_non_null(input));
    const auto& source = as_string(text.get())->value;
    if (from < 0 || to < from || static_cast<std::size_t>(to) > source.size())
        raise("java/lang/StringIndexOutOfBoundsException");
    if (from == 0 && static_cast<std::size_t>(to) == source.size()) return text.get();
    std::u16string result;
    result.reserve(static_cast<std::size_t>(to - from));
    LoopSafepoint poll;
    for (auto i = from; i < to; ++i) {
        result += source[static_cast<std::size_t>(i)];
        if (((i - from) & 1023) == 1023) poll.poll();
    }
    return allocate<String>(std::move(result));
}
inline PrimitiveArray<std::int32_t>* checked_groups(Object* value, std::int32_t group) {
    if (!value) raise("java/lang/IllegalStateException", "No match");
    auto groups = primitive_array<std::int32_t>(value);
    if (group < 0 || group >= groups->length / 2) raise("java/lang/IndexOutOfBoundsException");
    return groups;
}
}

template<class Find, class Groups>
Object* regex_split(Object* matcher, Object* input, std::int32_t limit, Find find, Groups groups_field) {
    RootFrame<5> roots;
    FrameRoot<> target(roots.slot(0), require_non_null(matcher));
    FrameRoot<> text(roots.slot(1), require_non_null(input));
    FrameRoot<> groups(roots.slot(2));
    FrameRoot<> result(roots.slot(3));
    FrameRoot<> part(roots.slot(4));
    std::vector<std::pair<std::int32_t, std::int32_t>> ranges;
    std::int32_t offset = 0;
    LoopSafepoint poll;
    while ((limit <= 0 || ranges.size() < static_cast<std::size_t>(limit - 1)) && find(target.get())) {
        groups.set(groups_field.get(target.get()));
        auto matches = regex_driver_detail::checked_groups(groups.get(), 0);
        const auto start = matches->elements[0].get(), end = matches->elements[1].get();
        if (offset != 0 || start != 0 || end != 0) {
            ranges.emplace_back(offset, start);
            offset = end;
        }
        poll.poll();
    }
    const auto length = static_cast<std::int32_t>(as_string(text.get())->value.size());
    if (offset == 0) ranges.emplace_back(0, length);
    else {
        ranges.emplace_back(offset, length);
        if (limit == 0) {
            while (!ranges.empty() && ranges.back().first == ranges.back().second) {
                ranges.pop_back();
                poll.poll();
            }
        }
    }
    if (ranges.size() > static_cast<std::size_t>(INT32_MAX)) raise("java/lang/OutOfMemoryError");
    result.set(new_array("[Ljava/lang/String;", static_cast<std::int32_t>(ranges.size())));
    for (std::size_t i = 0; i < ranges.size(); ++i) {
        part.set(regex_driver_detail::slice(text.get(), ranges[i].first, ranges[i].second));
        reference_set(result.get(), static_cast<std::int32_t>(i), part.get());
        poll.poll();
    }
    return result.get();
}

template<class AppendText, class AppendChar, class Groups, class Input, class Append>
void regex_append_replacement(Object* matcher, Object* output, Object* replacement,
                              AppendText append_text, AppendChar append_char,
                              Groups groups_field, Input input_field, Append append_field) {
    RootFrame<6> roots;
    FrameRoot<> target(roots.slot(0), require_non_null(matcher));
    FrameRoot<> destination(roots.slot(1), output);
    FrameRoot<> pattern(roots.slot(2), replacement);
    FrameRoot<> groups(roots.slot(3));
    FrameRoot<> input(roots.slot(4));
    FrameRoot<> part(roots.slot(5));
    groups.set(groups_field.get(target.get()));
    auto matches = regex_driver_detail::checked_groups(groups.get(), 0);
    require_non_null(pattern.get());
    input.set(input_field.get(target.get()));
    part.set(regex_driver_detail::slice(input.get(), append_field.get(target.get()), matches->elements[0].get()));
    append_text(destination.get(), part.get());
    const auto& source = as_string(pattern.get())->value;
    regex_driver_detail::ReplacementPoll poll;
    for (std::size_t i = 0; i < source.size(); ++i) {
        poll.step();
        auto value = source[i];
        if (value == u'\\') {
            if (++i == source.size()) raise("java/lang/IllegalArgumentException", "Trailing escape in replacement");
            append_char(destination.get(), static_cast<std::int32_t>(source[i]));
        } else if (value == u'$') {
            if (++i == source.size() || source[i] < u'0' || source[i] > u'9')
                raise("java/lang/IllegalArgumentException", "Missing replacement group");
            std::int32_t group = source[i] - u'0';
            matches = regex_driver_detail::checked_groups(groups.get(), group);
            while (i + 1 < source.size() && source[i + 1] >= u'0' && source[i + 1] <= u'9') {
                const auto candidate = add(mul(group, std::int32_t(10)), std::int32_t(source[i + 1] - u'0'));
                if (candidate >= matches->length / 2) break;
                group = candidate;
                ++i;
                poll.step();
            }
            matches = regex_driver_detail::checked_groups(groups.get(), group);
            const auto start = matches->elements[group * 2].get();
            if (start >= 0) {
                part.set(regex_driver_detail::slice(input.get(), start, matches->elements[group * 2 + 1].get()));
                append_text(destination.get(), part.get());
            }
        } else append_char(destination.get(), static_cast<std::int32_t>(value));
    }
    append_field.set(target.get(), primitive_array<std::int32_t>(groups.get())->elements[1].get());
}

template<class Find, class AppendText, class AppendChar, class Groups, class Input, class Append>
void regex_replace(Object* matcher, Object* output, Object* replacement, std::uint8_t first,
                   Find find, AppendText append_text, AppendChar append_char,
                   Groups groups_field, Input input_field, Append append_field) {
    RootFrame<5> roots;
    FrameRoot<> target(roots.slot(0), require_non_null(matcher));
    FrameRoot<> destination(roots.slot(1), output);
    FrameRoot<> pattern(roots.slot(2), replacement);
    FrameRoot<> input(roots.slot(3));
    FrameRoot<> part(roots.slot(4));
    LoopSafepoint poll;
    while (find(target.get())) {
        regex_append_replacement(target.get(), destination.get(), pattern.get(),
                append_text, append_char, groups_field, input_field, append_field);
        if (first) break;
        poll.poll();
    }
    input.set(input_field.get(target.get()));
    part.set(regex_driver_detail::slice(input.get(), append_field.get(target.get()),
            static_cast<std::int32_t>(as_string(input.get())->value.size())));
    append_text(destination.get(), part.get());
}
}

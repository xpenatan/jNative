#pragma once
#include "jn_runtime.hpp"

namespace jnative {
// A transient value. References must remain rooted by the invocation/field caller.
struct ReflectionValue {
    char kind = 'V';
    std::int64_t integer = 0;
    double floating = 0;
    Object* object = nullptr;
    ReflectionValue(char kind = 'V', std::int64_t integer = 0, double floating = 0, Object* object = nullptr)
        : kind(kind), integer(integer), floating(floating), object(object) {}
    static ReflectionValue integral(char kind, std::int64_t value) { return {kind, value, 0, nullptr}; }
    static ReflectionValue decimal(char kind, double value) { return {kind, 0, value, nullptr}; }
    static ReflectionValue reference(Object* value) { return {'L', 0, 0, value}; }
};
struct ReflectField {
    std::string owner, name, descriptor;
    int modifiers;
    ReflectionValue (*get)(Object*) = nullptr;
    void (*set)(Object*, ReflectionValue) = nullptr;
    ReflectField(std::string owner, std::string name, std::string descriptor, int modifiers,
            ReflectionValue (*get)(Object*) = nullptr, void (*set)(Object*, ReflectionValue) = nullptr)
        : owner(std::move(owner)), name(std::move(name)), descriptor(std::move(descriptor)),
          modifiers(modifiers), get(get), set(set) {}
};
struct ReflectMethod {
    std::string owner, name, descriptor;
    int modifiers;
    ReflectionValue (*invoke)(Object*, const std::vector<ReflectionValue>&) = nullptr;
    ReflectMethod(std::string owner, std::string name, std::string descriptor, int modifiers,
            ReflectionValue (*invoke)(Object*, const std::vector<ReflectionValue>&) = nullptr)
        : owner(std::move(owner)), name(std::move(name)), descriptor(std::move(descriptor)),
          modifiers(modifiers), invoke(invoke) {}
};
struct ReflectType {
    std::string name, parent;
    std::vector<std::string> interfaces;
    int modifiers = 1;
    bool members = false;
    void (*initialize)() = nullptr;
    std::vector<ReflectField> fields;
    std::vector<ReflectMethod> methods;
    bool named_lookup = false;
    ReflectType(std::string name = "", std::string parent = "", std::vector<std::string> interfaces = {},
            int modifiers = 1, bool members = false, void (*initialize)() = nullptr,
            std::vector<ReflectField> fields = {}, std::vector<ReflectMethod> methods = {}, bool named_lookup = false)
        : name(std::move(name)), parent(std::move(parent)), interfaces(std::move(interfaces)),
          modifiers(modifiers), members(members), initialize(initialize), fields(std::move(fields)),
          methods(std::move(methods)), named_lookup(named_lookup) {}
};
struct ReflectionMember final : Object {
    const ReflectField* field = nullptr;
    const ReflectMethod* method = nullptr;
    explicit ReflectionMember(const ReflectField* value) : field(value) {}
    explicit ReflectionMember(const ReflectMethod* value) : method(value) {}
    const char* type_name() const override {
        return field ? "java/lang/reflect/Field" : method->name == "<init>"
                ? "java/lang/reflect/Constructor" : "java/lang/reflect/Method";
    }
};

// Called only during generated program initialization, before user code/threads.
void register_reflection_type(ReflectType type);
void register_reflection_boxing(Object* (*box)(ReflectionValue), bool (*unbox)(Object*, ReflectionValue&));
Object* reflection_class(const std::string& descriptor);
Object* reflection_for_name(Object* name);
Object* reflection_class_name(Object* type, bool display = false);
Object* reflection_simple_name(Object* type);
Object* reflection_new_array(Object* component, std::int32_t length);
void register_enum_values(const std::string& name, Object* (*values)());
Object* reflection_enum_values(Object* type);
Object* reflection_superclass(Object* type);
Object* reflection_interfaces(Object* type);
Object* reflection_component(Object* type);
int reflection_modifiers(Object* value);
bool reflection_is_primitive(Object* type);
bool reflection_is_array(Object* type);
bool reflection_is_assignable(Object* expected, Object* actual);
bool reflection_is_instance(Object* type, Object* value);
Object* reflection_cast(Object* type, Object* value);
Object* reflection_class_string(Object* type);
Object* reflection_field(Object* type, Object* name);
Object* reflection_fields(Object* type);
Object* reflection_method(Object* type, Object* name, Object* parameters, bool constructor);
Object* reflection_methods(Object* type, bool constructors);
Object* reflection_member_name(Object* member);
Object* reflection_declaring_class(Object* member);
Object* reflection_member_type(Object* member);
Object* reflection_parameters(Object* member);
int reflection_parameter_count(Object* member);
bool reflection_member_equals(Object* first, Object* second);
std::int32_t reflection_member_hash(Object* member);
ReflectionValue reflection_get(Object* member, Object* receiver, char target);
Object* reflection_get_boxed(Object* member, Object* receiver);
void reflection_set(Object* member, Object* receiver, ReflectionValue value);
void reflection_set_boxed(Object* member, Object* receiver, Object* value);
Object* reflection_invoke(Object* member, Object* receiver, Object* arguments);
std::int32_t float_bits(float value, bool canonical);
float bits_float(std::int32_t bits);
std::int64_t double_bits(double value, bool canonical);
double bits_double(std::int64_t bits);
inline std::int32_t float_canonical_bits(float value) { return float_bits(value, true); }
inline std::int32_t float_raw_bits(float value) { return float_bits(value, false); }
inline std::int64_t double_canonical_bits(double value) { return double_bits(value, true); }
inline std::int64_t double_raw_bits(double value) { return double_bits(value, false); }

}

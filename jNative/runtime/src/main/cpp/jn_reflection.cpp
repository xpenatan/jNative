#include "jn_reflection.hpp"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <map>
#include <set>

namespace jnative {
namespace {
std::map<std::string, ReflectType>& registry() {
    static std::map<std::string, ReflectType> value;
    return value;
}
Object* (*box_value)(ReflectionValue) = nullptr;
std::map<std::string, Object* (*)()> enum_values;
bool (*unbox_value)(Object*, ReflectionValue&) = nullptr;
const std::string primitive_codes = "VZBCSIJFD";
const std::vector<std::string> primitive_names = {"void", "boolean", "byte", "char", "short", "int", "long", "float", "double"};
std::string type_name(const std::string& descriptor) {
    if (descriptor.size() == 1) {
        auto index = primitive_codes.find(descriptor[0]);
        if (index != std::string::npos) return primitive_names[index];
    }
    if (!descriptor.empty() && descriptor[0] == 'L' && descriptor.back() == ';')
        return descriptor.substr(1, descriptor.size() - 2);
    return descriptor;
}
std::string binary(std::string name) { std::replace(name.begin(), name.end(), '/', '.'); return name; }
bool primitive(const std::string& name) {
    return std::find(primitive_names.begin(), primitive_names.end(), name) != primitive_names.end();
}
ClassObject* as_class(Object* object) {
    if (require_non_null(object)->runtime_kind != RuntimeKind::class_type)
        raise("java/lang/IllegalArgumentException", "Expected a Class");
    return static_cast<ClassObject*>(object);
}
ReflectionMember* as_member(Object* object) {
    auto value = dynamic_cast<ReflectionMember*>(require_non_null(object));
    if (!value) raise("java/lang/IllegalArgumentException", "Expected a reflection member");
    return value;
}
const ReflectType& info(const std::string& name) {
    auto found = registry().find(name);
    if (found == registry().end()) raise("java/lang/UnsupportedOperationException", ("Missing class metadata: " + binary(name)).c_str());
    return found->second;
}
const ReflectType& members(const std::string& name) {
    const auto& value = info(name);
    if (!value.members) raise("java/lang/UnsupportedOperationException", ("Missing reflection metadata registration: " + binary(name)).c_str());
    return value;
}
void missing_access(const std::string& owner, const std::string& name) {
    raise("java/lang/UnsupportedOperationException", ("Missing reflection access registration: " + binary(owner) + "." + name).c_str());
}
bool valid_type(const std::string& name) {
    if (name.empty()) return false;
    if (name[0] != '[') return registry().count(name) != 0;
    auto index = name.find_first_not_of('[');
    if (index == std::string::npos || index > 255) return false;
    std::string component = name.substr(index);
    if (component.size() == 1) return primitive_codes.find(component[0]) != std::string::npos && component != "V";
    return component.front() == 'L' && component.back() == ';' && registry().count(type_name(component)) != 0;
}
std::vector<std::string> parameters(const std::string& descriptor) {
    std::vector<std::string> result;
    for (std::size_t i = 1; descriptor[i] != ')'; ) {
        std::size_t begin = i;
        while (descriptor[i] == '[') ++i;
        if (descriptor[i] == 'L') i = descriptor.find(';', i) + 1;
        else ++i;
        result.push_back(descriptor.substr(begin, i - begin));
    }
    return result;
}
std::string result_type(const std::string& descriptor) { return descriptor.substr(descriptor.find(')') + 1); }
Object* class_array(const std::vector<std::string>& names) {
    LocalRoot<> array(new_array("[Ljava/lang/Class;", static_cast<std::int32_t>(names.size())));
    for (std::size_t i = 0; i < names.size(); ++i) reference_set(array.get(), static_cast<std::int32_t>(i), reflection_class(names[i]));
    return array.get();
}
bool widens(char from, char to) {
    if (from == to) return true;
    switch (from) {
        case 'B': return std::string("SIJFD").find(to) != std::string::npos;
        case 'S': case 'C': return std::string("IJFD").find(to) != std::string::npos;
        case 'I': return std::string("JFD").find(to) != std::string::npos;
        case 'J': return to == 'F' || to == 'D';
        case 'F': return to == 'D';
        default: return false;
    }
}
ReflectionValue convert(ReflectionValue value, const std::string& descriptor) {
    char target = descriptor[0];
    if (target == '[' || target == 'L') {
        if (value.kind != 'L' || (value.object && !assignable(value.object->type_name(), type_name(descriptor))))
            raise("java/lang/IllegalArgumentException", "Incompatible reference value");
        return value;
    }
    if (!widens(value.kind, target)) raise("java/lang/IllegalArgumentException", "Incompatible primitive value");
    if (target == 'F') value.floating = value.kind == 'F' ? value.floating : static_cast<float>(value.integer);
    if (target == 'D' && value.kind != 'F' && value.kind != 'D') value.floating = static_cast<double>(value.integer);
    value.kind = target;
    return value;
}
ReflectionValue unbox(Object* object, const std::string& descriptor) {
    if (descriptor[0] == 'L' || descriptor[0] == '[') return convert(ReflectionValue::reference(object), descriptor);
    ReflectionValue value;
    if (!object || !unbox_value || !unbox_value(object, value))
        raise("java/lang/IllegalArgumentException", "Expected a boxed primitive");
    return convert(value, descriptor);
}
Object* boxed(ReflectionValue value) {
    if (value.kind == 'V') return nullptr;
    if (value.kind == 'L') return value.object;
    if (!box_value) raise("java/lang/UnsupportedOperationException", "No reflection boxing adapters registered");
    return box_value(value);
}
void access(const std::string& owner, int modifiers, Object* receiver, bool constructor = false) {
    if ((modifiers & 1) == 0 || (info(owner).modifiers & 1) == 0)
        raise("java/lang/IllegalAccessException", "Only public members of public classes are accessible");
    if (!constructor && (modifiers & 8) == 0) {
        require_non_null(receiver);
        if (!assignable(receiver->type_name(), owner)) raise("java/lang/IllegalArgumentException", "Incompatible reflection receiver");
    }
}
void initialize(const std::string& owner) { if (info(owner).initialize) info(owner).initialize(); }
const ReflectField* find_field(const std::string& owner, const std::string& name) {
    const auto& type = members(owner);
    for (const auto& field : type.fields) if (field.name == name) return &field;
    for (const auto& face : type.interfaces) if (auto field = find_field(face, name)) return field;
    return type.parent.empty() || (type.modifiers & 0x200) ? nullptr : find_field(type.parent, name);
}
void collect_fields(const std::string& owner, std::vector<const ReflectField*>& result, std::set<std::string>& seen) {
    if (!seen.insert(owner).second) return;
    const auto& type = members(owner);
    for (const auto& field : type.fields) result.push_back(&field);
    for (const auto& face : type.interfaces) collect_fields(face, result, seen);
    if (!type.parent.empty() && !(type.modifiers & 0x200)) collect_fields(type.parent, result, seen);
}
void collect_methods(const std::string& owner, std::vector<const ReflectMethod*>& result,
                     std::set<std::string>& seen, bool root) {
    if (!seen.insert(owner).second) return;
    const auto& type = members(owner);
    for (const auto& method : type.methods) {
        if (method.name == "<init>") continue;
        if (!root && (type.modifiers & 0x200) && (method.modifiers & 8)) continue;
        auto same = std::find_if(result.begin(), result.end(), [&](const ReflectMethod* other) {
            return other->name == method.name && other->descriptor == method.descriptor;
        });
        if (same == result.end()) result.push_back(&method);
        else if (assignable(method.owner, (*same)->owner)
                || ((info((*same)->owner).modifiers & 0x200) && !(type.modifiers & 0x200))) *same = &method;
    }
    if (!type.parent.empty() && !(type.modifiers & 0x200)) collect_methods(type.parent, result, seen, false);
    for (const auto& face : type.interfaces) collect_methods(face, result, seen, false);
}
std::vector<const ReflectMethod*> method_list(const std::string& owner, bool constructors) {
    std::vector<const ReflectMethod*> result;
    if (constructors) {
        for (const auto& method : members(owner).methods) if (method.name == "<init>") result.push_back(&method);
    } else {
        std::set<std::string> seen;
        collect_methods(owner, result, seen, true);
    }
    return result;
}
std::string member_owner(ReflectionMember* member) { return member->field ? member->field->owner : member->method->owner; }
}

void register_reflection_type(ReflectType type) { registry()[type.name] = std::move(type); }
void register_reflection_boxing(Object* (*box)(ReflectionValue), bool (*unbox)(Object*, ReflectionValue&)) {
    box_value = box; unbox_value = unbox;
}
Object* reflection_class(const std::string& descriptor) { return class_object(type_name(descriptor)); }
void register_enum_values(const std::string& name, Object* (*values)()) { enum_values[name] = values; }
Object* reflection_enum_values(Object* type) {
    std::string name = as_class(type)->name;
    auto found = enum_values.find(name);
    if (found == enum_values.end()) return nullptr;
    initialize(name);
    return found->second();
}
Object* reflection_for_name(Object* object) {
    std::string name = utf8(as_string(object)->value);
    if (name.find('/') != std::string::npos) raise("java/lang/ClassNotFoundException", name.c_str());
    std::replace(name.begin(), name.end(), '.', '/');
    if (primitive(name) || !valid_type(name)) raise("java/lang/ClassNotFoundException", binary(name).c_str());
    if (name[0] != '[') {
        if (!info(name).named_lookup)
            raise("java/lang/UnsupportedOperationException", ("Missing reflection class registration: " + binary(name)).c_str());
        initialize(name);
    }
    return class_object(name);
}
Object* reflection_class_name(Object* type, bool display) {
    std::string name = as_class(type)->name;
    if (display && !name.empty() && name[0] == '[') {
        auto dimensions = name.find_first_not_of('[');
        name = binary(type_name(name.substr(dimensions)));
        for (std::size_t i = 0; i < dimensions; ++i) name += "[]";
    } else name = binary(name);
    return allocate<String>(utf16(name));
}
Object* reflection_superclass(Object* type) {
    const auto& name = as_class(type)->name;
    if (primitive(name)) return nullptr;
    if (name[0] == '[') return class_object("java/lang/Object");
    const auto& value = info(name);
    return value.parent.empty() || (value.modifiers & 0x200) ? nullptr : class_object(value.parent);
}
Object* reflection_simple_name(Object* type) {
    std::string name = as_class(type)->name;
    std::size_t dimensions = name.find_first_not_of('[');
    if (dimensions != 0 && dimensions != std::string::npos) name = type_name(name.substr(dimensions));
    else dimensions = 0;
    auto last = name.find_last_of("/$");
    if (last != std::string::npos) {
        bool nested = name[last] == '$';
        name = name.substr(last + 1);
        if (nested) { auto first = name.find_first_not_of("0123456789"); name = first == std::string::npos ? "" : name.substr(first); }
    }
    for (std::size_t i = 0; i < dimensions; ++i) name += "[]";
    return allocate<String>(utf16(name));
}
Object* reflection_new_array(Object* component, std::int32_t length) {
    const auto& name = as_class(component)->name;
    if (name == "void") raise("java/lang/IllegalArgumentException", "void component");
    auto primitive_index = std::find(primitive_names.begin(), primitive_names.end(), name);
    std::string descriptor = "[";
    if (primitive_index != primitive_names.end()) descriptor += primitive_codes[primitive_index - primitive_names.begin()];
    else descriptor += name[0] == '[' ? name : "L" + name + ";";
    return new_array(descriptor.c_str(), length);
}
Object* reflection_interfaces(Object* type) {
    const auto& name = as_class(type)->name;
    if (primitive(name)) return class_array({});
    if (name[0] == '[') return class_array({"java/lang/Cloneable", "java/io/Serializable"});
    return class_array(info(name).interfaces);
}
Object* reflection_component(Object* type) {
    const auto& name = as_class(type)->name;
    return name[0] == '[' ? reflection_class(name.substr(1)) : nullptr;
}
int reflection_modifiers(Object* value) {
    if (auto type = dynamic_cast<ClassObject*>(require_non_null(value))) {
        if (primitive(type->name)) return 0x411;
        if (type->name[0] == '[') {
            auto base = type_name(type->name.substr(type->name.find_first_not_of('[')));
            return 0x410 | (primitive(base) ? 1 : info(base).modifiers & 7);
        }
        return info(type->name).modifiers;
    }
    auto member = as_member(value);
    return member->field ? member->field->modifiers : member->method->modifiers;
}
bool reflection_is_primitive(Object* type) { return primitive(as_class(type)->name); }
bool reflection_is_array(Object* type) { return as_class(type)->name[0] == '['; }
bool reflection_is_assignable(Object* expected, Object* actual) {
    auto to = as_class(expected);
    auto from = as_class(actual);
    if (to->represented_type_id && from->represented_type_id)
        return generated_assignable(from->represented_type_id, to->represented_type_id);
    return primitive(to->name) || primitive(from->name) ? to->name == from->name
        : assignable(from->name, to->name);
}
bool reflection_is_instance(Object* type, Object* value) {
    auto target = as_class(type);
    if (target->represented_type_id)
        return instance_of(value, target->name.c_str(), target->represented_type_id);
    return value && !primitive(target->name) && instance_of(value, target->name.c_str());
}
Object* reflection_cast(Object* type, Object* value) {
    if (!reflection_is_instance(type, value) && value) raise("java/lang/ClassCastException");
    return value;
}
Object* reflection_class_string(Object* type) {
    LocalRoot<> name(reflection_class_name(type));
    std::u16string prefix = reflection_is_primitive(type) ? u"" : (reflection_modifiers(type) & 0x200) ? u"interface " : u"class ";
    return allocate<String>(prefix + as_string(name.get())->value);
}
Object* reflection_field(Object* type, Object* name) {
    std::string text = utf8(as_string(name)->value);
    if (reflection_is_primitive(type) || reflection_is_array(type)) raise("java/lang/NoSuchFieldException", text.c_str());
    auto field = find_field(as_class(type)->name, text);
    if (!field) raise("java/lang/NoSuchFieldException", text.c_str());
    return allocate<ReflectionMember>(field);
}
Object* reflection_fields(Object* type) {
    if (reflection_is_primitive(type) || reflection_is_array(type)) return new_array("[Ljava/lang/reflect/Field;", 0);
    std::vector<const ReflectField*> fields;
    std::set<std::string> seen;
    collect_fields(as_class(type)->name, fields, seen);
    LocalRoot<> result(new_array("[Ljava/lang/reflect/Field;", static_cast<std::int32_t>(fields.size())));
    for (std::size_t i = 0; i < fields.size(); ++i) reference_set(result.get(), static_cast<std::int32_t>(i), allocate<ReflectionMember>(fields[i]));
    return result.get();
}
Object* reflection_method(Object* type, Object* name, Object* parameter_types, bool constructor) {
    std::string text = constructor ? "<init>" : utf8(as_string(name)->value);
    std::vector<std::string> arguments;
    if (parameter_types) for (int i = 0; i < array_length(parameter_types); ++i)
        arguments.push_back(as_class(reference_get(parameter_types, i))->name);
    const ReflectMethod* selected = nullptr;
    std::string owner = as_class(type)->name;
    auto list = primitive(owner) || (constructor && owner[0] == '[') ? std::vector<const ReflectMethod*>{}
            : method_list(owner[0] == '[' ? "java/lang/Object" : owner, constructor);
    for (const auto* method : list) {
        if (method->name != text) continue;
        auto types = parameters(method->descriptor);
        if (types.size() != arguments.size()) continue;
        bool matches = true;
        for (std::size_t i = 0; i < types.size(); ++i) if (type_name(types[i]) != arguments[i]) matches = false;
        if (!matches) continue;
        if (!selected || assignable(type_name(result_type(method->descriptor)), type_name(result_type(selected->descriptor))))
            selected = method;
    }
    if (!selected) raise("java/lang/NoSuchMethodException", text.c_str());
    return allocate<ReflectionMember>(selected);
}
Object* reflection_methods(Object* type, bool constructors) {
    std::string owner = as_class(type)->name;
    auto list = primitive(owner) || (constructors && owner[0] == '[') ? std::vector<const ReflectMethod*>{}
            : method_list(owner[0] == '[' ? "java/lang/Object" : owner, constructors);
    LocalRoot<> result(new_array(constructors ? "[Ljava/lang/reflect/Constructor;" : "[Ljava/lang/reflect/Method;", static_cast<std::int32_t>(list.size())));
    for (std::size_t i = 0; i < list.size(); ++i) reference_set(result.get(), static_cast<std::int32_t>(i), allocate<ReflectionMember>(list[i]));
    return result.get();
}
Object* reflection_member_name(Object* member) {
    auto value = as_member(member);
    auto name = value->field ? value->field->name : value->method->name == "<init>" ? binary(value->method->owner) : value->method->name;
    return allocate<String>(utf16(name));
}
Object* reflection_declaring_class(Object* member) { return class_object(member_owner(as_member(member))); }
Object* reflection_member_type(Object* member) {
    auto value = as_member(member);
    return reflection_class(value->field ? value->field->descriptor : result_type(value->method->descriptor));
}
Object* reflection_parameters(Object* member) { return class_array(parameters(as_member(member)->method->descriptor)); }
int reflection_parameter_count(Object* member) { return static_cast<int>(parameters(as_member(member)->method->descriptor).size()); }
bool reflection_member_equals(Object* first, Object* second) {
    auto a = as_member(first), b = dynamic_cast<ReflectionMember*>(second);
    return b && a->field == b->field && a->method == b->method;
}
std::int32_t reflection_member_hash(Object* member) {
    auto value = as_member(member);
    LocalRoot<> owner(allocate<String>(utf16(binary(member_owner(value)))));
    std::int32_t hash = string_hash(owner.get());
    if (value->method && value->method->name == "<init>") return hash;
    LocalRoot<> name(reflection_member_name(member));
    return hash ^ string_hash(name.get());
}
ReflectionValue reflection_get(Object* member, Object* receiver, char target) {
    const auto& field = *as_member(member)->field;
    access(field.owner, field.modifiers, receiver);
    if (!field.get) missing_access(field.owner, field.name);
    if (field.modifiers & 8) initialize(field.owner);
    ReflectionValue value = field.get(receiver);
    return target ? convert(value, std::string(1, target)) : value;
}
Object* reflection_get_boxed(Object* member, Object* receiver) {
    LocalRoot<> keep_receiver(receiver);
    return boxed(reflection_get(member, receiver, 0));
}
void reflection_set(Object* member, Object* receiver, ReflectionValue value) {
    const auto& field = *as_member(member)->field;
    access(field.owner, field.modifiers, receiver);
    if (field.modifiers & 0x10) raise("java/lang/IllegalAccessException", "Reflective writes to final fields are outside the public reflection profile");
    if (!field.set) missing_access(field.owner, field.name);
    value = convert(value, field.descriptor);
    if (field.modifiers & 8) initialize(field.owner);
    field.set(receiver, value);
}
void reflection_set_boxed(Object* member, Object* receiver, Object* value) {
    LocalRoot<> keep_receiver(receiver);
    LocalRoot<> keep_value(value);
    const auto& field = *as_member(member)->field;
    // Preserve access/final errors before attempting value conversion.
    access(field.owner, field.modifiers, receiver);
    if (field.modifiers & 0x10) raise("java/lang/IllegalAccessException", "Cannot write a final field");
    reflection_set(member, receiver, unbox(value, field.descriptor));
}
Object* reflection_invoke(Object* member, Object* receiver, Object* arguments) {
    const auto& method = *as_member(member)->method;
    bool constructor = method.name == "<init>";
    LocalRoot<> keep_receiver(receiver);
    LocalRoot<> keep_arguments(arguments);
    access(method.owner, method.modifiers, receiver, constructor);
    if (constructor && (info(method.owner).modifiers & 0x600)) raise("java/lang/InstantiationException");
    if (!method.invoke) missing_access(method.owner, method.name);
    auto expected = parameters(method.descriptor);
    int count = arguments ? array_length(arguments) : 0;
    if (count != static_cast<int>(expected.size())) raise("java/lang/IllegalArgumentException", "Wrong reflection argument count");
    // Snapshot references so a concurrent change to Object[] cannot remove an invocation root.
    LocalRoot<> snapshot(new_array("[Ljava/lang/Object;", count));
    for (int i = 0; i < count; ++i) reference_set(snapshot.get(), i, reference_get(arguments, i));
    std::vector<ReflectionValue> values;
    for (int i = 0; i < count; ++i) values.push_back(unbox(reference_get(snapshot.get(), i), expected[i]));
    if (constructor || (method.modifiers & 8)) initialize(method.owner);
    ReflectionValue result;
    try { result = method.invoke(receiver, values); }
    catch (const Thrown& thrown) {
        LocalRoot<Throwable> wrapped(allocate<Throwable>("java/lang/reflect/InvocationTargetException"));
        wrapped->cause.set(thrown.object());
        throw Thrown(wrapped.get());
    }
    LocalRoot<> keep_result(result.object);
    return boxed(result);
}
std::int32_t float_bits(float value, bool canonical) {
    if (canonical && std::isnan(value)) return 0x7fc00000;
    std::uint32_t bits; std::memcpy(&bits, &value, sizeof(bits)); return signed32(bits);
}
float bits_float(std::int32_t bits) { float value; std::memcpy(&value, &bits, sizeof(value)); return value; }
std::int64_t double_bits(double value, bool canonical) {
    if (canonical && std::isnan(value)) return INT64_C(0x7ff8000000000000);
    std::uint64_t bits; std::memcpy(&bits, &value, sizeof(bits)); return signed64(bits);
}
double bits_double(std::int64_t bits) { double value; std::memcpy(&value, &bits, sizeof(value)); return value; }
}

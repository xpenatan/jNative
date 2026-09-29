#pragma once
#include "jn_classlib.hpp"
#include "jn_console.hpp"
#include "jn_abi.hpp"
#include <algorithm>
namespace jnative {
inline void platform_IOException_initialize_c2f18a8416(::jnative::Object* self) {
    (void)0;
}
inline void platform_IOException_initialize_199d7d150b(::jnative::Object* self, ::jnative::Object* arg0) {
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->message.set(arg0);
    (void)0;
}
inline void platform_IOException_initialize_29c514e70d(::jnative::Object* self, ::jnative::Object* arg0, ::jnative::Object* arg1) {
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->message.set(arg0);
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->cause.set(arg1);
    (void)0;
}
inline void platform_IOException_initialize_79140ec83e(::jnative::Object* self, ::jnative::Object* arg0, ::jnative::Object* (*to_string)(::jnative::Object*)) {
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->cause.set(arg0);
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->message.set(arg0 == nullptr ? nullptr : to_string(arg0));
    (void)0;
}
inline void platform_IOException_addSuppressed_051e0eb658(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::add_suppressed(self, arg0);
}
inline ::jnative::Object* platform_IOException_getCause_9ca74ef4d3(::jnative::Object* self) {
    return static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->cause.get();
}
inline ::jnative::Object* platform_IOException_getMessage_41154c8426(::jnative::Object* self) {
    return static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->message.get();
}
inline ::jnative::Object* platform_IOException_getSuppressed_6306cc2fd4(::jnative::Object* self) {
    return ::jnative::get_suppressed(self);
}
inline void platform_IOException_printStackTrace_1aa2e1b53c(::jnative::Object* self) {
    ::jnative::print_stack_trace(self);
}
inline void platform_IOException_printStackTrace_b1f8b4d932(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::print_stack_trace(self, ::jnative::require_non_null(arg0));
}
inline ::jnative::Object* platform_IOException_toString_95f805c195(::jnative::Object* self) {
    return ::jnative::allocate<::jnative::String>(::jnative::utf16_cooperative(::jnative::Thrown(self).what()));
}
inline void platform_PrintStream_flush_569bacc77f(::jnative::Object* self) {
    ::jnative::flush(self);
}
inline void platform_PrintStream_print_b4edf72725(::jnative::Object* self, std::int32_t arg0) {
    ::jnative::print(self, ::jnative::character(arg0), false);
}
inline void platform_PrintStream_print_3f9f64fcc0(::jnative::Object* self, double arg0) {
    ::jnative::print(self, ::jnative::utf8(::jnative::to_text(arg0)), false);
}
inline void platform_PrintStream_print_f685585226(::jnative::Object* self, float arg0) {
    ::jnative::print(self, ::jnative::utf8(::jnative::to_text(arg0)), false);
}
inline void platform_PrintStream_print_214528a1a0(::jnative::Object* self, std::int32_t arg0) {
    ::jnative::print(self, arg0, false);
}
inline void platform_PrintStream_print_b1db16f7dd(::jnative::Object* self, std::int64_t arg0) {
    ::jnative::print(self, arg0, false);
}
inline void platform_PrintStream_print_5e3eff6064(::jnative::Object* self, ::jnative::Object* arg0, ::jnative::Object* (*to_string)(::jnative::Object*)) {
    ::jnative::print(self, (arg0 ? to_string(arg0) : nullptr), false);
}
inline void platform_PrintStream_print_a9d9b8201d(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::print(self, arg0, false);
}
inline void platform_PrintStream_print_e0c3d489f8(::jnative::Object* self, std::int32_t arg0) {
    ::jnative::print(self, std::string(arg0 ? "true" : "false"), false);
}
inline void platform_PrintStream_println_9ea8d825fc(::jnative::Object* self) {
    ::jnative::print(self, std::string{}, true);
}
inline void platform_PrintStream_println_11928e47be(::jnative::Object* self, std::int32_t arg0) {
    ::jnative::print(self, ::jnative::character(arg0), true);
}
inline void platform_PrintStream_println_4783062b70(::jnative::Object* self, double arg0) {
    ::jnative::print(self, ::jnative::utf8(::jnative::to_text(arg0)), true);
}
inline void platform_PrintStream_println_8a9895feec(::jnative::Object* self, float arg0) {
    ::jnative::print(self, ::jnative::utf8(::jnative::to_text(arg0)), true);
}
inline void platform_PrintStream_println_16e84a65e9(::jnative::Object* self, std::int32_t arg0) {
    ::jnative::print(self, arg0, true);
}
inline void platform_PrintStream_println_8c831a058a(::jnative::Object* self, std::int64_t arg0) {
    ::jnative::print(self, arg0, true);
}
inline void platform_PrintStream_println_3dfcfb275c(::jnative::Object* self, ::jnative::Object* arg0, ::jnative::Object* (*to_string)(::jnative::Object*)) {
    ::jnative::print(self, (arg0 ? to_string(arg0) : nullptr), true);
}
inline void platform_PrintStream_println_9e65445681(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::print(self, arg0, true);
}
inline void platform_PrintStream_println_80e32983cd(::jnative::Object* self, std::int32_t arg0) {
    ::jnative::print(self, std::string(arg0 ? "true" : "false"), true);
}
inline ::jnative::Object* platform_Class_cast_5ab4d049a8(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::reflection_cast(self, arg0);
}
inline ::jnative::Object* platform_Class_forName_4555fab9c9(::jnative::Object* arg0) {
    return ::jnative::reflection_for_name(arg0);
}
inline ::jnative::Object* platform_Class_getComponentType_a0f295f093(::jnative::Object* self) {
    return ::jnative::reflection_component(self);
}
inline ::jnative::Object* platform_Class_getConstructor_fdc9396388(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::reflection_method(self, nullptr, arg0, true);
}
inline ::jnative::Object* platform_Class_getConstructors_33b1acac37(::jnative::Object* self) {
    return ::jnative::reflection_methods(self, true);
}
inline ::jnative::Object* platform_Class_getEnumConstants_2ba838d3c0(::jnative::Object* self) {
    return ::jnative::reflection_enum_values(self);
}
inline ::jnative::Object* platform_Class_getField_06d5979494(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::reflection_field(self, arg0);
}
inline ::jnative::Object* platform_Class_getFields_91ef953787(::jnative::Object* self) {
    return ::jnative::reflection_fields(self);
}
inline ::jnative::Object* platform_Class_getInterfaces_d575e86f7a(::jnative::Object* self) {
    return ::jnative::reflection_interfaces(self);
}
inline ::jnative::Object* platform_Class_getMethod_4a85120706(::jnative::Object* self, ::jnative::Object* arg0, ::jnative::Object* arg1) {
    return ::jnative::reflection_method(self, arg0, arg1, false);
}
inline ::jnative::Object* platform_Class_getMethods_4d42adf11a(::jnative::Object* self) {
    return ::jnative::reflection_methods(self, false);
}
inline std::int32_t platform_Class_getModifiers_8fa4949662(::jnative::Object* self) {
    return ::jnative::reflection_modifiers(self);
}
inline ::jnative::Object* platform_Class_getName_0c0d418469(::jnative::Object* self) {
    return ::jnative::reflection_class_name(self, false);
}
inline ::jnative::Object* platform_Class_getSimpleName_1a8f4996a4(::jnative::Object* self) {
    return ::jnative::reflection_simple_name(self);
}
inline ::jnative::Object* platform_Class_getSuperclass_774876542c(::jnative::Object* self) {
    return ::jnative::reflection_superclass(self);
}
inline ::jnative::Object* platform_Class_getTypeName_f6f2f32fb5(::jnative::Object* self) {
    return ::jnative::reflection_class_name(self, true);
}
inline std::int32_t platform_Class_isArray_c12396b319(::jnative::Object* self) {
    return ::jnative::reflection_is_array(self);
}
inline std::int32_t platform_Class_isAssignableFrom_16f562e333(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::reflection_is_assignable(self, arg0);
}
inline std::int32_t platform_Class_isInstance_4c4a3c30ae(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::reflection_is_instance(self, arg0);
}
inline std::int32_t platform_Class_isInterface_85f61f03bf(::jnative::Object* self) {
    return ((::jnative::reflection_modifiers(self) & 512) != 0);
}
inline std::int32_t platform_Class_isPrimitive_8b25024279(::jnative::Object* self) {
    return ::jnative::reflection_is_primitive(self);
}
inline ::jnative::Object* platform_Class_toString_6d3b9c2dba(::jnative::Object* self) {
    return ::jnative::reflection_class_string(self);
}
inline void platform_IndexOutOfBoundsException_initialize_a2147fa036(::jnative::Object* self, std::int32_t arg0) {
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->message.set(::jnative::allocate<::jnative::String>(std::u16string(u"Index out of range: ") + ::jnative::to_text(arg0)));
}
inline std::int32_t platform_Number_byteValue_c683afad90(::jnative::Object* self) {
    return ([&]() -> std::int32_t { if (auto value = dynamic_cast<::jnative::AtomicInteger*>(self)) return (((::jnative::signed32(static_cast<std::uint32_t>(value->value.get())) & 255) ^ 128) - 128); if (auto value = dynamic_cast<::jnative::AtomicLong*>(self)) return (((::jnative::signed32(static_cast<std::uint32_t>(value->value.get())) & 255) ^ 128) - 128); ::jnative::raise("java/lang/AbstractMethodError"); }());
}
inline double platform_Number_doubleValue_941a7cea4d(::jnative::Object* self) {
    return ([&]() -> double { if (auto value = dynamic_cast<::jnative::AtomicInteger*>(self)) return static_cast<double>(value->value.get()); if (auto value = dynamic_cast<::jnative::AtomicLong*>(self)) return static_cast<double>(value->value.get()); ::jnative::raise("java/lang/AbstractMethodError"); }());
}
inline float platform_Number_floatValue_c10cb04e38(::jnative::Object* self) {
    return ([&]() -> float { if (auto value = dynamic_cast<::jnative::AtomicInteger*>(self)) return static_cast<float>(value->value.get()); if (auto value = dynamic_cast<::jnative::AtomicLong*>(self)) return static_cast<float>(value->value.get()); ::jnative::raise("java/lang/AbstractMethodError"); }());
}
inline std::int32_t platform_Number_intValue_5abe74ab1f(::jnative::Object* self) {
    return ([&]() -> std::int32_t { if (auto value = dynamic_cast<::jnative::AtomicInteger*>(self)) return ::jnative::signed32(static_cast<std::uint32_t>(value->value.get())); if (auto value = dynamic_cast<::jnative::AtomicLong*>(self)) return ::jnative::signed32(static_cast<std::uint32_t>(value->value.get())); ::jnative::raise("java/lang/AbstractMethodError"); }());
}
inline std::int64_t platform_Number_longValue_d79f9ab79b(::jnative::Object* self) {
    return ([&]() -> std::int64_t { if (auto value = dynamic_cast<::jnative::AtomicInteger*>(self)) return static_cast<std::int64_t>(value->value.get()); if (auto value = dynamic_cast<::jnative::AtomicLong*>(self)) return static_cast<std::int64_t>(value->value.get()); ::jnative::raise("java/lang/AbstractMethodError"); }());
}
inline std::int32_t platform_Number_shortValue_bfb67e8778(::jnative::Object* self) {
    return ([&]() -> std::int32_t { if (auto value = dynamic_cast<::jnative::AtomicInteger*>(self)) return (((::jnative::signed32(static_cast<std::uint32_t>(value->value.get())) & 65535) ^ 32768) - 32768); if (auto value = dynamic_cast<::jnative::AtomicLong*>(self)) return (((::jnative::signed32(static_cast<std::uint32_t>(value->value.get())) & 65535) ^ 32768) - 32768); ::jnative::raise("java/lang/AbstractMethodError"); }());
}
inline std::int32_t platform_Object_equals_3e07e34c31(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::object_equals(self, arg0);
}
inline ::jnative::Object* platform_Object_getClass_3bfb230c80(::jnative::Object* self) {
    return ::jnative::class_object(::jnative::require_non_null(self)->type_name());
}
inline std::int32_t platform_Object_hashCode_339f483e8c(::jnative::Object* self) {
    return ::jnative::object_hash(self);
}
inline void platform_Object_notify_474c2f2e3b(::jnative::Object* self) {
    ::jnative::monitor_notify(self, false);
}
inline void platform_Object_notifyAll_d04ed98017(::jnative::Object* self) {
    ::jnative::monitor_notify(self, true);
}
inline ::jnative::Object* platform_Object_toString_18a112723c(::jnative::Object* self) {
    return ::jnative::object_string(self);
}
inline void platform_Object_wait_d16e9f03ca(::jnative::Object* self) {
    ::jnative::monitor_wait(self, 0, 0);
}
inline void platform_Object_wait_1df4e5ea91(::jnative::Object* self, std::int64_t arg0) {
    ::jnative::monitor_wait(self, arg0, 0);
}
inline void platform_Object_wait_f4f6c4ba64(::jnative::Object* self, std::int64_t arg0, std::int32_t arg1) {
    ::jnative::monitor_wait(self, arg0, arg1);
}
inline void platform_Runnable_run_53e103ffd3(::jnative::Object* self) {
    ::jnative::raise("java/lang/AbstractMethodError");
}
inline void platform_String_initialize_d7e6a88c7a(::jnative::Object* self) {
    ::jnative::as_string(self)->value = std::u16string{};
    (void)0;
}
inline void platform_String_initialize_02a41a989d(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::as_string(self)->value = ::jnative::as_string(arg0)->value;
    (void)0;
}
inline void platform_String_initialize_e2427f6bf8(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::string_from_chars(self, arg0);
}
inline void platform_String_initialize_09257d1619(::jnative::Object* self, ::jnative::Object* arg0, std::int32_t arg1, std::int32_t arg2) {
    ::jnative::string_from_chars(self, arg0, arg1, arg2);
}
inline std::int32_t platform_String_charAt_5c9be27598(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::string_char(self, arg0);
}
inline std::int32_t platform_String_compareTo_59a2e06096(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::string_compare(self, arg0);
}
inline ::jnative::Object* platform_String_concat_cf618bfbe6(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::string_concat(self, arg0);
}
inline std::int32_t platform_String_equals_e3dd1f40ec(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::string_equals(self, arg0);
}
inline std::int32_t platform_String_hashCode_74fe13f362(::jnative::Object* self) {
    return ::jnative::string_hash(self);
}
inline std::int32_t platform_String_isEmpty_ac0293776c(::jnative::Object* self) {
    return ::jnative::as_string(self)->value.empty();
}
inline std::int32_t platform_String_length_ff7a5a86ad(::jnative::Object* self) {
    return static_cast<std::int32_t>(::jnative::as_string(self)->value.size());
}
inline ::jnative::Object* platform_String_substring_47ce72e87f(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::substring(self, arg0, static_cast<std::int32_t>(::jnative::as_string(self)->value.size()));
}
inline ::jnative::Object* platform_String_substring_6300fff947(::jnative::Object* self, std::int32_t arg0, std::int32_t arg1) {
    return ::jnative::substring(self, arg0, arg1);
}
inline ::jnative::Object* platform_String_toString_1dec10508a(::jnative::Object* self) {
    return ::jnative::require_non_null(self);
}
inline ::jnative::Object* platform_String_trim_9502992899(::jnative::Object* self) {
    return ::jnative::string_trim(self);
}
inline ::jnative::Object* platform_String_valueOf_66a68b15d3(std::int32_t arg0) {
    return ::jnative::allocate<::jnative::String>(std::u16string(1, char16_t(arg0)));
}
inline ::jnative::Object* platform_String_valueOf_36abe0a8e0(double arg0) {
    return ::jnative::allocate<::jnative::String>(::jnative::to_text(arg0));
}
inline ::jnative::Object* platform_String_valueOf_e217bb627e(float arg0) {
    return ::jnative::allocate<::jnative::String>(::jnative::to_text(arg0));
}
inline ::jnative::Object* platform_String_valueOf_77bb0617b4(std::int32_t arg0) {
    return ::jnative::allocate<::jnative::String>(::jnative::to_text(arg0));
}
inline ::jnative::Object* platform_String_valueOf_05c966fa11(std::int64_t arg0) {
    return ::jnative::allocate<::jnative::String>(::jnative::to_text(arg0));
}
inline ::jnative::Object* platform_String_valueOf_f3f690310c(::jnative::Object* arg0, ::jnative::Object* (*to_string)(::jnative::Object*)) {
    return (arg0 ? to_string(arg0) : ::jnative::literal(u"null"));
}
inline ::jnative::Object* platform_String_valueOf_12e41bcdc0(std::int32_t arg0) {
    return (arg0 ? ::jnative::literal(u"true") : ::jnative::literal(u"false"));
}
inline void platform_System_arraycopy_7b15f0890b(::jnative::Object* arg0, std::int32_t arg1, ::jnative::Object* arg2, std::int32_t arg3, std::int32_t arg4) {
    ::jnative::array_copy_cooperative(arg0, arg1, arg2, arg3, arg4);
}
inline ::jnative::Object* platform_System_clearProperty_f0493b8d44(::jnative::Object* arg0) {
    return ::jnative::set_system_property(arg0, nullptr, true);
}
inline std::int64_t platform_System_currentTimeMillis_8e3c800ee9() {
    return ::jnative::platform::wall_millis();
}
inline void platform_System_gc_01ad5d387f() {
    ::jnative::Heap::instance().collect();
}
inline ::jnative::Object* platform_System_getProperty_96986fbc5b(::jnative::Object* arg0) {
    return ::jnative::system_property(arg0, nullptr);
}
inline ::jnative::Object* platform_System_getProperty_f04ecb2061(::jnative::Object* arg0, ::jnative::Object* arg1) {
    return ::jnative::system_property(arg0, arg1);
}
inline std::int32_t platform_System_identityHashCode_012516278d(::jnative::Object* arg0) {
    return ::jnative::identity_hash(arg0);
}
inline std::int64_t platform_System_nanoTime_490d23097b() {
    return ::jnative::platform::monotonic_nanos();
}
inline ::jnative::Object* platform_System_setProperty_03d4db1313(::jnative::Object* arg0, ::jnative::Object* arg1) {
    return ::jnative::set_system_property(arg0, arg1, false);
}
inline void platform_Thread_initialize_2e1fc8a903(::jnative::Object* self) {
    ::jnative::initialize_thread(self, nullptr, nullptr);
}
inline void platform_Thread_initialize_b4d7f5241e(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::initialize_thread(self, arg0, nullptr);
}
inline void platform_Thread_initialize_6e868cf185(::jnative::Object* self, ::jnative::Object* arg0, ::jnative::Object* arg1) {
    ::jnative::require_non_null(arg1);
    ::jnative::initialize_thread(self, arg0, arg1);
}
inline void platform_Thread_initialize_82c2c25344(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::require_non_null(arg0);
    ::jnative::initialize_thread(self, nullptr, arg0);
}
inline ::jnative::Object* platform_Thread_currentThread_b9c9e64fd9() {
    return ::jnative::current_java_thread();
}
inline std::int64_t platform_Thread_getId_b79446e798(::jnative::Object* self) {
    return ::jnative::as_thread(self)->control->id;
}
inline ::jnative::Object* platform_Thread_getName_dfea60f5af(::jnative::Object* self) {
    return ::jnative::as_thread(self)->name.get();
}
inline std::int32_t platform_Thread_holdsLock_8b665dd3a7(::jnative::Object* arg0) {
    return ::jnative::holds_monitor(arg0);
}
inline void platform_Thread_interrupt_1da2f2ca75(::jnative::Object* self) {
    ::jnative::thread_interrupt(self);
}
inline std::int32_t platform_Thread_interrupted_260cf35ae7() {
    return ::jnative::thread_interrupted(::jnative::current_java_thread(), true);
}
inline std::int32_t platform_Thread_isAlive_0a6fe2b1df(::jnative::Object* self) {
    return ::jnative::thread_alive(self);
}
inline std::int32_t platform_Thread_isDaemon_20bf151f0b(::jnative::Object* self) {
    return ::jnative::thread_daemon(self);
}
inline std::int32_t platform_Thread_isInterrupted_6736e453bf(::jnative::Object* self) {
    return ::jnative::thread_interrupted(self, false);
}
inline void platform_Thread_join_1a25eb1a8a(::jnative::Object* self) {
    ::jnative::thread_join(self, 0, 0);
}
inline void platform_Thread_join_52180908f5(::jnative::Object* self, std::int64_t arg0) {
    ::jnative::thread_join(self, arg0, 0);
}
inline void platform_Thread_join_df352605a4(::jnative::Object* self, std::int64_t arg0, std::int32_t arg1) {
    ::jnative::thread_join(self, arg0, arg1);
}
inline void platform_Thread_run_5b09d1c532(::jnative::Object* self, void (*run_runnable)(::jnative::Object*)) {
    ([&] { ::jnative::LocalRoot<> target( ::jnative::as_thread(self)->target.get()); if (target.get()) run_runnable(target.get()); }());
}
inline void platform_Thread_setDaemon_753e002dc7(::jnative::Object* self, std::int32_t arg0) {
    ::jnative::thread_set_daemon(self, arg0 != 0);
}
inline void platform_Thread_setName_6254ccd854(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::as_thread(self)->name.set(::jnative::as_string(arg0));
}
inline void platform_Thread_sleep_bcf91178bf(std::int64_t arg0) {
    ::jnative::thread_sleep(arg0, 0);
}
inline void platform_Thread_sleep_59ff56b193(std::int64_t arg0, std::int32_t arg1) {
    ::jnative::thread_sleep(arg0, arg1);
}
inline void platform_Thread_start_142b8288d1(::jnative::Object* self, void (*run_thread)(::jnative::Object*)) {
    ::jnative::thread_start(self, run_thread);
}
inline void platform_Thread_yield_6461fc6583() {
    ::jnative::thread_yield();
}
inline ::jnative::Object* platform_ThreadLocal_get_35577f265a(::jnative::Object* self, ::jnative::Object* (*initial_value)(::jnative::Object*)) {
    return ::jnative::thread_local_get(self, initial_value);
}
inline ::jnative::Object* platform_ThreadLocal_initialValue_4964262b11(::jnative::Object* self) {
    return nullptr;
}
inline void platform_ThreadLocal_remove_b3dc9a2db3(::jnative::Object* self) {
    ::jnative::thread_local_remove(self);
}
inline void platform_ThreadLocal_set_d3ef418bff(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::thread_local_set(self, arg0);
}
inline ::jnative::Object* platform_Array_newInstance_9619452bde(::jnative::Object* arg0, std::int32_t arg1) {
    return ::jnative::reflection_new_array(arg0, arg1);
}
inline std::int32_t platform_Constructor_equals_bb6b5ae28f(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::reflection_member_equals(self, arg0);
}
inline ::jnative::Object* platform_Constructor_getDeclaringClass_3e4d3990a7(::jnative::Object* self) {
    return ::jnative::reflection_declaring_class(self);
}
inline ::jnative::Object* platform_Constructor_getName_797aa96a5d(::jnative::Object* self) {
    return ::jnative::reflection_member_name(self);
}
inline std::int32_t platform_Constructor_getParameterCount_0d32c1172d(::jnative::Object* self) {
    return ::jnative::reflection_parameter_count(self);
}
inline ::jnative::Object* platform_Constructor_getParameterTypes_d8c024b1c3(::jnative::Object* self) {
    return ::jnative::reflection_parameters(self);
}
inline std::int32_t platform_Constructor_hashCode_2253d72613(::jnative::Object* self) {
    return ::jnative::reflection_member_hash(self);
}
inline std::int32_t platform_Constructor_isSynthetic_eedeefcb1d(::jnative::Object* self) {
    return ((::jnative::reflection_modifiers(self) & 4096) != 0);
}
inline std::int32_t platform_Constructor_isVarArgs_1893752b9b(::jnative::Object* self) {
    return ((::jnative::reflection_modifiers(self) & 128) != 0);
}
inline ::jnative::Object* platform_Constructor_newInstance_b9112c4eba(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::reflection_invoke(self, nullptr, arg0);
}
inline ::jnative::Object* platform_Field_get_23089c7505(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::reflection_get_boxed(self, arg0);
}
inline std::int32_t platform_Field_getBoolean_c400957ce2(::jnative::Object* self, ::jnative::Object* arg0) {
    return static_cast<std::int32_t>((::jnative::reflection_get(self, arg0, 'Z')).integer);
}
inline std::int32_t platform_Field_getByte_3fde3e6234(::jnative::Object* self, ::jnative::Object* arg0) {
    return static_cast<std::int32_t>((::jnative::reflection_get(self, arg0, 'B')).integer);
}
inline std::int32_t platform_Field_getChar_5333c7561d(::jnative::Object* self, ::jnative::Object* arg0) {
    return static_cast<std::int32_t>((::jnative::reflection_get(self, arg0, 'C')).integer);
}
inline double platform_Field_getDouble_6631983c96(::jnative::Object* self, ::jnative::Object* arg0) {
    return static_cast<double>((::jnative::reflection_get(self, arg0, 'D')).floating);
}
inline float platform_Field_getFloat_d5d556785e(::jnative::Object* self, ::jnative::Object* arg0) {
    return static_cast<float>((::jnative::reflection_get(self, arg0, 'F')).floating);
}
inline std::int32_t platform_Field_getInt_f78e295c14(::jnative::Object* self, ::jnative::Object* arg0) {
    return static_cast<std::int32_t>((::jnative::reflection_get(self, arg0, 'I')).integer);
}
inline std::int64_t platform_Field_getLong_f13792172d(::jnative::Object* self, ::jnative::Object* arg0) {
    return static_cast<std::int64_t>((::jnative::reflection_get(self, arg0, 'J')).integer);
}
inline std::int32_t platform_Field_getShort_2de1364950(::jnative::Object* self, ::jnative::Object* arg0) {
    return static_cast<std::int32_t>((::jnative::reflection_get(self, arg0, 'S')).integer);
}
inline ::jnative::Object* platform_Field_getType_e2bccc8d35(::jnative::Object* self) {
    return ::jnative::reflection_member_type(self);
}
inline void platform_Field_set_5ba6fe7b2c(::jnative::Object* self, ::jnative::Object* arg0, ::jnative::Object* arg1) {
    ::jnative::reflection_set_boxed(self, arg0, arg1);
}
inline void platform_Field_setBoolean_bba6734de3(::jnative::Object* self, ::jnative::Object* arg0, std::int32_t arg1) {
    ::jnative::reflection_set(self, arg0, ::jnative::ReflectionValue::integral('Z', arg1));
}
inline void platform_Field_setByte_41314f6dcb(::jnative::Object* self, ::jnative::Object* arg0, std::int32_t arg1) {
    ::jnative::reflection_set(self, arg0, ::jnative::ReflectionValue::integral('B', arg1));
}
inline void platform_Field_setChar_fcb009193d(::jnative::Object* self, ::jnative::Object* arg0, std::int32_t arg1) {
    ::jnative::reflection_set(self, arg0, ::jnative::ReflectionValue::integral('C', arg1));
}
inline void platform_Field_setDouble_bd5162adab(::jnative::Object* self, ::jnative::Object* arg0, double arg1) {
    ::jnative::reflection_set(self, arg0, ::jnative::ReflectionValue::decimal('D', arg1));
}
inline void platform_Field_setFloat_d3ff605cb9(::jnative::Object* self, ::jnative::Object* arg0, float arg1) {
    ::jnative::reflection_set(self, arg0, ::jnative::ReflectionValue::decimal('F', arg1));
}
inline void platform_Field_setInt_bcec207ab3(::jnative::Object* self, ::jnative::Object* arg0, std::int32_t arg1) {
    ::jnative::reflection_set(self, arg0, ::jnative::ReflectionValue::integral('I', arg1));
}
inline void platform_Field_setLong_70b20a29d3(::jnative::Object* self, ::jnative::Object* arg0, std::int64_t arg1) {
    ::jnative::reflection_set(self, arg0, ::jnative::ReflectionValue::integral('J', arg1));
}
inline void platform_Field_setShort_c3cc653003(::jnative::Object* self, ::jnative::Object* arg0, std::int32_t arg1) {
    ::jnative::reflection_set(self, arg0, ::jnative::ReflectionValue::integral('S', arg1));
}
inline void platform_InvocationTargetException_initialize_0fd17643e4(::jnative::Object* self) {
    (void)0;
}
inline void platform_InvocationTargetException_initialize_47bc903c83(::jnative::Object* self, ::jnative::Object* arg0) {
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->cause.set(arg0);
    (void)0;
}
inline void platform_InvocationTargetException_initialize_834399d988(::jnative::Object* self, ::jnative::Object* arg0, ::jnative::Object* arg1) {
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->cause.set(arg0);
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->message.set(arg1);
    (void)0;
}
inline void platform_InvocationTargetException_addSuppressed_6d135f1c1c(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::add_suppressed(self, arg0);
}
inline ::jnative::Object* platform_InvocationTargetException_getCause_593ed0015d(::jnative::Object* self) {
    return static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->cause.get();
}
inline ::jnative::Object* platform_InvocationTargetException_getMessage_44e8a61b92(::jnative::Object* self) {
    return static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->message.get();
}
inline ::jnative::Object* platform_InvocationTargetException_getSuppressed_62012ad588(::jnative::Object* self) {
    return ::jnative::get_suppressed(self);
}
inline ::jnative::Object* platform_InvocationTargetException_getTargetException_df43a3eed7(::jnative::Object* self) {
    return static_cast<::jnative::Throwable*>(self)->cause.get();
}
inline void platform_InvocationTargetException_printStackTrace_25ece660ac(::jnative::Object* self) {
    ::jnative::print_stack_trace(self);
}
inline void platform_InvocationTargetException_printStackTrace_f5e4a3cc94(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::print_stack_trace(self, ::jnative::require_non_null(arg0));
}
inline ::jnative::Object* platform_InvocationTargetException_toString_2651e15a35(::jnative::Object* self) {
    return ::jnative::allocate<::jnative::String>(::jnative::utf16_cooperative(::jnative::Thrown(self).what()));
}
inline ::jnative::Object* platform_Method_invoke_4c1d5611e7(::jnative::Object* self, ::jnative::Object* arg0, ::jnative::Object* arg1) {
    return ::jnative::reflection_invoke(self, arg0, arg1);
}
inline std::int32_t platform_Method_isBridge_d4a85ab0e0(::jnative::Object* self) {
    return ((::jnative::reflection_modifiers(self) & 64) != 0);
}
inline std::int32_t platform_Modifier_isAbstract_4192e46d45(std::int32_t arg0) {
    return ((arg0 & 1024) != 0);
}
inline std::int32_t platform_Modifier_isFinal_cdfc274904(std::int32_t arg0) {
    return ((arg0 & 16) != 0);
}
inline std::int32_t platform_Modifier_isInterface_3a36365273(std::int32_t arg0) {
    return ((arg0 & 512) != 0);
}
inline std::int32_t platform_Modifier_isNative_c047137374(std::int32_t arg0) {
    return ((arg0 & 256) != 0);
}
inline std::int32_t platform_Modifier_isPrivate_7a96cf0f94(std::int32_t arg0) {
    return ((arg0 & 2) != 0);
}
inline std::int32_t platform_Modifier_isProtected_9c69de4edd(std::int32_t arg0) {
    return ((arg0 & 4) != 0);
}
inline std::int32_t platform_Modifier_isPublic_5fc73927a1(std::int32_t arg0) {
    return ((arg0 & 1) != 0);
}
inline std::int32_t platform_Modifier_isStatic_efe2d32a83(std::int32_t arg0) {
    return ((arg0 & 8) != 0);
}
inline std::int32_t platform_Modifier_isStrict_3ad5a46380(std::int32_t arg0) {
    return ((arg0 & 2048) != 0);
}
inline std::int32_t platform_Modifier_isSynchronized_9e2ad905b1(std::int32_t arg0) {
    return ((arg0 & 32) != 0);
}
inline std::int32_t platform_Modifier_isTransient_b4ab27bcea(std::int32_t arg0) {
    return ((arg0 & 128) != 0);
}
inline std::int32_t platform_Modifier_isVolatile_fd5775723a(std::int32_t arg0) {
    return ((arg0 & 64) != 0);
}
inline std::int32_t platform_Buffer_capacity_f85319a0ad(::jnative::Object* self) {
    return ::jnative::as_byte_buffer(self)->capacity;
}
inline ::jnative::Object* platform_Buffer_clear_c5a572ddd7(::jnative::Object* self) {
    return ::jnative::buffer_clear(self);
}
inline ::jnative::Object* platform_Buffer_flip_456b0a8455(::jnative::Object* self) {
    return ::jnative::buffer_flip(self);
}
inline std::int32_t platform_Buffer_hasArray_afd4b38e9c(::jnative::Object* self) {
    return (::jnative::as_byte_buffer(self)->element == 'B' && ::jnative::as_byte_buffer(self)->heap_array.get() && !::jnative::as_byte_buffer(self)->read_only);
}
inline std::int32_t platform_Buffer_hasRemaining_967b5bd7f6(::jnative::Object* self) {
    return (::jnative::as_byte_buffer(self)->position.get() < ::jnative::as_byte_buffer(self)->limit.get());
}
inline std::int32_t platform_Buffer_isDirect_01a1f5fd2d(::jnative::Object* self) {
    return ::jnative::as_byte_buffer(self)->direct;
}
inline std::int32_t platform_Buffer_isReadOnly_54ac9e39fb(::jnative::Object* self) {
    return ::jnative::as_byte_buffer(self)->read_only;
}
inline std::int32_t platform_Buffer_limit_4047fe1337(::jnative::Object* self) {
    return ::jnative::as_byte_buffer(self)->limit.get();
}
inline ::jnative::Object* platform_Buffer_limit_ec4f081260(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::buffer_limit(self, arg0);
}
inline ::jnative::Object* platform_Buffer_mark_3f16916c1d(::jnative::Object* self) {
    return ::jnative::buffer_mark(self);
}
inline std::int32_t platform_Buffer_position_d9688558f7(::jnative::Object* self) {
    return ::jnative::as_byte_buffer(self)->position.get();
}
inline ::jnative::Object* platform_Buffer_position_e78fb31a90(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::buffer_position(self, arg0);
}
inline std::int32_t platform_Buffer_remaining_0cb642caae(::jnative::Object* self) {
    return (::jnative::as_byte_buffer(self)->limit.get() - ::jnative::as_byte_buffer(self)->position.get());
}
inline ::jnative::Object* platform_Buffer_reset_790dfc0c66(::jnative::Object* self) {
    return ::jnative::buffer_reset(self);
}
inline ::jnative::Object* platform_Buffer_rewind_060dddde23(::jnative::Object* self) {
    return ::jnative::buffer_rewind(self);
}
inline void platform_BufferOverflowException_initialize_cf74e29223(::jnative::Object* self) {
    (void)0;
}
inline void platform_BufferOverflowException_initialize_290ae12af7(::jnative::Object* self, ::jnative::Object* arg0) {
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->message.set(arg0);
    (void)0;
}
inline void platform_BufferOverflowException_initialize_d3c48fbc2b(::jnative::Object* self, ::jnative::Object* arg0, ::jnative::Object* arg1) {
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->message.set(arg0);
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->cause.set(arg1);
    (void)0;
}
inline void platform_BufferOverflowException_initialize_4716cc1a62(::jnative::Object* self, ::jnative::Object* arg0, ::jnative::Object* (*to_string)(::jnative::Object*)) {
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->cause.set(arg0);
    static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->message.set(arg0 == nullptr ? nullptr : to_string(arg0));
    (void)0;
}
inline void platform_BufferOverflowException_addSuppressed_63c2c5022d(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::add_suppressed(self, arg0);
}
inline ::jnative::Object* platform_BufferOverflowException_getCause_3553792c2b(::jnative::Object* self) {
    return static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->cause.get();
}
inline ::jnative::Object* platform_BufferOverflowException_getMessage_6ad4c57731(::jnative::Object* self) {
    return static_cast<::jnative::Throwable*>(::jnative::require_non_null(self))->message.get();
}
inline ::jnative::Object* platform_BufferOverflowException_getSuppressed_47b3c123ab(::jnative::Object* self) {
    return ::jnative::get_suppressed(self);
}
inline void platform_BufferOverflowException_printStackTrace_2b2aaf2e24(::jnative::Object* self) {
    ::jnative::print_stack_trace(self);
}
inline void platform_BufferOverflowException_printStackTrace_e2dd9a0dfc(::jnative::Object* self, ::jnative::Object* arg0) {
    ::jnative::print_stack_trace(self, ::jnative::require_non_null(arg0));
}
inline ::jnative::Object* platform_BufferOverflowException_toString_7fee3d47f1(::jnative::Object* self) {
    return ::jnative::allocate<::jnative::String>(::jnative::utf16_cooperative(::jnative::Thrown(self).what()));
}
inline ::jnative::Object* platform_ByteBuffer_allocate_ee1be77b1a(std::int32_t arg0) {
    return ::jnative::buffer_allocate_heap(arg0);
}
inline ::jnative::Object* platform_ByteBuffer_allocateDirect_b795c14e1e(std::int32_t arg0) {
    return ::jnative::buffer_allocate(arg0);
}
inline ::jnative::Object* platform_ByteBuffer_array_ef57a9fa54(::jnative::Object* self) {
    return ::jnative::buffer_array(self);
}
inline std::int32_t platform_ByteBuffer_arrayOffset_d2679c19b5(::jnative::Object* self) {
    return (::jnative::buffer_array(self), ::jnative::as_byte_buffer(self)->offset);
}
inline ::jnative::Object* platform_ByteBuffer_asDoubleBuffer_c74737fa23(::jnative::Object* self) {
    return ::jnative::buffer_typed_view(self, 'D');
}
inline ::jnative::Object* platform_ByteBuffer_asFloatBuffer_3b377092b5(::jnative::Object* self) {
    return ::jnative::buffer_typed_view(self, 'F');
}
inline ::jnative::Object* platform_ByteBuffer_asIntBuffer_3be9ddc12e(::jnative::Object* self) {
    return ::jnative::buffer_typed_view(self, 'I');
}
inline ::jnative::Object* platform_ByteBuffer_asLongBuffer_8941a832f8(::jnative::Object* self) {
    return ::jnative::buffer_typed_view(self, 'J');
}
inline ::jnative::Object* platform_ByteBuffer_asReadOnlyBuffer_464b8df185(::jnative::Object* self) {
    return ::jnative::buffer_view(self, false, true);
}
inline ::jnative::Object* platform_ByteBuffer_asShortBuffer_938511936d(::jnative::Object* self) {
    return ::jnative::buffer_typed_view(self, 'S');
}
inline ::jnative::Object* platform_ByteBuffer_duplicate_558ee004a4(::jnative::Object* self) {
    return ::jnative::buffer_view(self, false, false);
}
inline std::int32_t platform_ByteBuffer_get_f9684695b7(::jnative::Object* self) {
    return (((::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint8_t, 'B', true>(self, 0))) & 255) ^ 128) - 128);
}
inline std::int32_t platform_ByteBuffer_get_fa4b81cb8c(::jnative::Object* self, std::int32_t arg0) {
    return (((::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint8_t, 'B', false>(self, arg0))) & 255) ^ 128) - 128);
}
inline ::jnative::Object* platform_ByteBuffer_get_63822f546f(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::buffer_bulk(self, arg0, 0, ::jnative::array_length(arg0), false);
}
inline ::jnative::Object* platform_ByteBuffer_get_cdc1ced698(::jnative::Object* self, ::jnative::Object* arg0, std::int32_t arg1, std::int32_t arg2) {
    return ::jnative::buffer_bulk(self, arg0, arg1, arg2, false);
}
inline std::int32_t platform_ByteBuffer_getChar_06c02a1840(::jnative::Object* self) {
    return (::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint16_t, 'B', true>(self, 0))) & 65535);
}
inline std::int32_t platform_ByteBuffer_getChar_9dceb01fb9(::jnative::Object* self, std::int32_t arg0) {
    return (::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint16_t, 'B', false>(self, arg0))) & 65535);
}
inline double platform_ByteBuffer_getDouble_cf95f5c3b9(::jnative::Object* self) {
    return ::jnative::bits_double(::jnative::signed64(::jnative::buffer_read_scalar<std::uint64_t, 'B', true>(self, 0)));
}
inline double platform_ByteBuffer_getDouble_746a876414(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::bits_double(::jnative::signed64(::jnative::buffer_read_scalar<std::uint64_t, 'B', false>(self, arg0)));
}
inline float platform_ByteBuffer_getFloat_4b34ddfbd7(::jnative::Object* self) {
    return ::jnative::bits_float(::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint32_t, 'B', true>(self, 0))));
}
inline float platform_ByteBuffer_getFloat_567dd4286f(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::bits_float(::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint32_t, 'B', false>(self, arg0))));
}
inline std::int32_t platform_ByteBuffer_getInt_7595877f21(::jnative::Object* self) {
    return ::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint32_t, 'B', true>(self, 0)));
}
inline std::int32_t platform_ByteBuffer_getInt_f76208b97e(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint32_t, 'B', false>(self, arg0)));
}
inline std::int64_t platform_ByteBuffer_getLong_8375eb2e7e(::jnative::Object* self) {
    return ::jnative::signed64(::jnative::buffer_read_scalar<std::uint64_t, 'B', true>(self, 0));
}
inline std::int64_t platform_ByteBuffer_getLong_b1704d1438(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::signed64(::jnative::buffer_read_scalar<std::uint64_t, 'B', false>(self, arg0));
}
inline std::int32_t platform_ByteBuffer_getShort_01c2790e3c(::jnative::Object* self) {
    return (((::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint16_t, 'B', true>(self, 0))) & 65535) ^ 32768) - 32768);
}
inline std::int32_t platform_ByteBuffer_getShort_a44227b4fe(::jnative::Object* self, std::int32_t arg0) {
    return (((::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint16_t, 'B', false>(self, arg0))) & 65535) ^ 32768) - 32768);
}
inline ::jnative::Object* platform_ByteBuffer_order_1e14eb6811(::jnative::Object* self) {
    return ::jnative::byte_order(::jnative::as_byte_buffer(self)->little_endian.get() != 0);
}
inline ::jnative::Object* platform_ByteBuffer_order_79bbe87457(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::buffer_order(self, arg0);
}
inline ::jnative::Object* platform_ByteBuffer_put_8e67d8e24f(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::buffer_write_scalar<std::uint8_t, 'B', true>(self, 0, static_cast<std::uint64_t>(arg0));
}
inline ::jnative::Object* platform_ByteBuffer_put_6eaee5e384(::jnative::Object* self, std::int32_t arg0, std::int32_t arg1) {
    return ::jnative::buffer_write_scalar<std::uint8_t, 'B', false>(self, arg0, static_cast<std::uint64_t>(arg1));
}
inline ::jnative::Object* platform_ByteBuffer_put_d7f86c157c(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::buffer_copy(self, arg0);
}
inline ::jnative::Object* platform_ByteBuffer_put_4a1975d465(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::buffer_bulk(self, arg0, 0, ::jnative::array_length(arg0), true);
}
inline ::jnative::Object* platform_ByteBuffer_put_3d2788e8cb(::jnative::Object* self, ::jnative::Object* arg0, std::int32_t arg1, std::int32_t arg2) {
    return ::jnative::buffer_bulk(self, arg0, arg1, arg2, true);
}
inline ::jnative::Object* platform_ByteBuffer_putChar_1bbf371906(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::buffer_write_scalar<std::uint16_t, 'B', true>(self, 0, static_cast<std::uint64_t>(arg0));
}
inline ::jnative::Object* platform_ByteBuffer_putChar_be987fb8d5(::jnative::Object* self, std::int32_t arg0, std::int32_t arg1) {
    return ::jnative::buffer_write_scalar<std::uint16_t, 'B', false>(self, arg0, static_cast<std::uint64_t>(arg1));
}
inline ::jnative::Object* platform_ByteBuffer_putDouble_e01d38309d(::jnative::Object* self, double arg0) {
    return ::jnative::buffer_write_scalar<std::uint64_t, 'B', true>(self, 0, static_cast<std::uint64_t>(::jnative::double_bits(arg0, false)));
}
inline ::jnative::Object* platform_ByteBuffer_putDouble_6dd28aad87(::jnative::Object* self, std::int32_t arg0, double arg1) {
    return ::jnative::buffer_write_scalar<std::uint64_t, 'B', false>(self, arg0, static_cast<std::uint64_t>(::jnative::double_bits(arg1, false)));
}
inline ::jnative::Object* platform_ByteBuffer_putFloat_fc5e735e73(::jnative::Object* self, float arg0) {
    return ::jnative::buffer_write_scalar<std::uint32_t, 'B', true>(self, 0, static_cast<std::uint64_t>(::jnative::float_bits(arg0, false)));
}
inline ::jnative::Object* platform_ByteBuffer_putFloat_3b2ac4d678(::jnative::Object* self, std::int32_t arg0, float arg1) {
    return ::jnative::buffer_write_scalar<std::uint32_t, 'B', false>(self, arg0, static_cast<std::uint64_t>(::jnative::float_bits(arg1, false)));
}
inline ::jnative::Object* platform_ByteBuffer_putInt_b501b45ee6(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::buffer_write_scalar<std::uint32_t, 'B', true>(self, 0, static_cast<std::uint64_t>(arg0));
}
inline ::jnative::Object* platform_ByteBuffer_putInt_488dd98615(::jnative::Object* self, std::int32_t arg0, std::int32_t arg1) {
    return ::jnative::buffer_write_scalar<std::uint32_t, 'B', false>(self, arg0, static_cast<std::uint64_t>(arg1));
}
inline ::jnative::Object* platform_ByteBuffer_putLong_825c6a003a(::jnative::Object* self, std::int32_t arg0, std::int64_t arg1) {
    return ::jnative::buffer_write_scalar<std::uint64_t, 'B', false>(self, arg0, static_cast<std::uint64_t>(arg1));
}
inline ::jnative::Object* platform_ByteBuffer_putLong_a7ccb60957(::jnative::Object* self, std::int64_t arg0) {
    return ::jnative::buffer_write_scalar<std::uint64_t, 'B', true>(self, 0, static_cast<std::uint64_t>(arg0));
}
inline ::jnative::Object* platform_ByteBuffer_putShort_0e3f8a56ad(::jnative::Object* self, std::int32_t arg0, std::int32_t arg1) {
    return ::jnative::buffer_write_scalar<std::uint16_t, 'B', false>(self, arg0, static_cast<std::uint64_t>(arg1));
}
inline ::jnative::Object* platform_ByteBuffer_putShort_04a321ec51(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::buffer_write_scalar<std::uint16_t, 'B', true>(self, 0, static_cast<std::uint64_t>(arg0));
}
inline ::jnative::Object* platform_ByteBuffer_slice_3d9484bebb(::jnative::Object* self) {
    return ::jnative::buffer_view(self, true, false);
}
inline ::jnative::Object* platform_ByteBuffer_wrap_728ccf7a7c(::jnative::Object* arg0) {
    return ::jnative::buffer_wrap(arg0, 0, ::jnative::array_length(arg0));
}
inline ::jnative::Object* platform_ByteBuffer_wrap_1b2a009105(::jnative::Object* arg0, std::int32_t arg1, std::int32_t arg2) {
    return ::jnative::buffer_wrap(arg0, arg1, arg2);
}
inline ::jnative::Object* platform_ByteOrder_nativeOrder_dad3d4bd2c() {
    return ::jnative::native_byte_order();
}
inline ::jnative::Object* platform_ByteOrder_toString_ea9b2ee65c(::jnative::Object* self) {
    return ::jnative::byte_order_string(self);
}
inline double platform_DoubleBuffer_get_3958b87034(::jnative::Object* self) {
    return ::jnative::bits_double(::jnative::signed64(::jnative::buffer_read_scalar<std::uint64_t, 'D', true>(self, 0)));
}
inline double platform_DoubleBuffer_get_6223711dd1(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::bits_double(::jnative::signed64(::jnative::buffer_read_scalar<std::uint64_t, 'D', false>(self, arg0)));
}
inline ::jnative::Object* platform_DoubleBuffer_put_147a2e8390(::jnative::Object* self, double arg0) {
    return ::jnative::buffer_write_scalar<std::uint64_t, 'D', true>(self, 0, static_cast<std::uint64_t>(::jnative::double_bits(arg0, false)));
}
inline ::jnative::Object* platform_DoubleBuffer_put_9bf9ff1ae3(::jnative::Object* self, std::int32_t arg0, double arg1) {
    return ::jnative::buffer_write_scalar<std::uint64_t, 'D', false>(self, arg0, static_cast<std::uint64_t>(::jnative::double_bits(arg1, false)));
}
inline float platform_FloatBuffer_get_eb1e76693f(::jnative::Object* self) {
    return ::jnative::bits_float(::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint32_t, 'F', true>(self, 0))));
}
inline float platform_FloatBuffer_get_56952d7312(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::bits_float(::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint32_t, 'F', false>(self, arg0))));
}
inline ::jnative::Object* platform_FloatBuffer_put_558aff6187(::jnative::Object* self, float arg0) {
    return ::jnative::buffer_write_scalar<std::uint32_t, 'F', true>(self, 0, static_cast<std::uint64_t>(::jnative::float_bits(arg0, false)));
}
inline ::jnative::Object* platform_FloatBuffer_put_efd42c3223(::jnative::Object* self, std::int32_t arg0, float arg1) {
    return ::jnative::buffer_write_scalar<std::uint32_t, 'F', false>(self, arg0, static_cast<std::uint64_t>(::jnative::float_bits(arg1, false)));
}
inline std::int32_t platform_IntBuffer_get_33d8e0b9c4(::jnative::Object* self) {
    return ::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint32_t, 'I', true>(self, 0)));
}
inline std::int32_t platform_IntBuffer_get_0b2ba3eca1(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint32_t, 'I', false>(self, arg0)));
}
inline ::jnative::Object* platform_IntBuffer_put_a934313b20(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::buffer_write_scalar<std::uint32_t, 'I', true>(self, 0, static_cast<std::uint64_t>(arg0));
}
inline ::jnative::Object* platform_IntBuffer_put_25d6823bbd(::jnative::Object* self, std::int32_t arg0, std::int32_t arg1) {
    return ::jnative::buffer_write_scalar<std::uint32_t, 'I', false>(self, arg0, static_cast<std::uint64_t>(arg1));
}
inline std::int64_t platform_LongBuffer_get_e6c9bc5a0f(::jnative::Object* self) {
    return ::jnative::signed64(::jnative::buffer_read_scalar<std::uint64_t, 'J', true>(self, 0));
}
inline std::int64_t platform_LongBuffer_get_db38278900(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::signed64(::jnative::buffer_read_scalar<std::uint64_t, 'J', false>(self, arg0));
}
inline ::jnative::Object* platform_LongBuffer_put_bbb2f82990(::jnative::Object* self, std::int32_t arg0, std::int64_t arg1) {
    return ::jnative::buffer_write_scalar<std::uint64_t, 'J', false>(self, arg0, static_cast<std::uint64_t>(arg1));
}
inline ::jnative::Object* platform_LongBuffer_put_c46cccd8f3(::jnative::Object* self, std::int64_t arg0) {
    return ::jnative::buffer_write_scalar<std::uint64_t, 'J', true>(self, 0, static_cast<std::uint64_t>(arg0));
}
inline std::int32_t platform_ShortBuffer_get_6b0c1c6235(::jnative::Object* self) {
    return (((::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint16_t, 'S', true>(self, 0))) & 65535) ^ 32768) - 32768);
}
inline std::int32_t platform_ShortBuffer_get_0a54d54aa3(::jnative::Object* self, std::int32_t arg0) {
    return (((::jnative::signed32(static_cast<std::uint32_t>(::jnative::buffer_read_scalar<std::uint16_t, 'S', false>(self, arg0))) & 65535) ^ 32768) - 32768);
}
inline ::jnative::Object* platform_ShortBuffer_put_bcbea28d13(::jnative::Object* self, std::int32_t arg0, std::int32_t arg1) {
    return ::jnative::buffer_write_scalar<std::uint16_t, 'S', false>(self, arg0, static_cast<std::uint64_t>(arg1));
}
inline ::jnative::Object* platform_ShortBuffer_put_a123412b1c(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::buffer_write_scalar<std::uint16_t, 'S', true>(self, 0, static_cast<std::uint64_t>(arg0));
}
inline void platform_Files_delete_2f9b7147bb(::jnative::Object* arg0) {
    ::jnative::file_delete(arg0, true);
}
inline std::int32_t platform_Files_deleteIfExists_a4f0f637ea(::jnative::Object* arg0) {
    return ::jnative::file_delete(arg0, false);
}
inline std::int32_t platform_Files_exists_52f52b8c08(::jnative::Object* arg0, ::jnative::Object* arg1) {
    return ::jnative::file_exists(arg0, arg1);
}
inline ::jnative::Object* platform_Files_readAllBytes_8a22c8f106(::jnative::Object* arg0) {
    return ::jnative::file_read(arg0, false);
}
inline ::jnative::Object* platform_Files_readString_5150861ab9(::jnative::Object* arg0) {
    return ::jnative::file_read(arg0, true);
}
inline ::jnative::Object* platform_Files_write_efb1409a93(::jnative::Object* arg0, ::jnative::Object* arg1, ::jnative::Object* arg2) {
    return ::jnative::file_write(arg0, arg1, arg2, false);
}
inline ::jnative::Object* platform_Files_writeString_a8383b7e99(::jnative::Object* arg0, ::jnative::Object* arg1, ::jnative::Object* arg2, ::jnative::Object* (*to_string)(::jnative::Object*)) {
    return ::jnative::file_write(arg0, to_string(::jnative::require_non_null(arg1)), arg2, true);
}
inline std::int32_t platform_Path_compareTo_9750be30b9(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::path_compare(self, arg0);
}
inline ::jnative::Object* platform_Path_getFileName_e88758c243(::jnative::Object* self) {
    return ::jnative::path_filename(self);
}
inline ::jnative::Object* platform_Path_getParent_2bd72ebc12(::jnative::Object* self) {
    return ::jnative::path_parent(self);
}
inline ::jnative::Object* platform_Path_normalize_a8bad16ac6(::jnative::Object* self) {
    return ::jnative::path_normalize(self);
}
inline ::jnative::Object* platform_Path_of_e918b9c819(::jnative::Object* arg0, ::jnative::Object* arg1) {
    return ::jnative::path_get(arg0, arg1);
}
inline ::jnative::Object* platform_Path_resolve_134a148b96(::jnative::Object* self, ::jnative::Object* arg0) {
    return ::jnative::path_resolve(self, arg0);
}
inline ::jnative::Object* platform_Path_toAbsolutePath_b52376450e(::jnative::Object* self) {
    return ::jnative::path_absolute(self);
}
inline ::jnative::Object* platform_Path_toString_f226956f83(::jnative::Object* self) {
    return ::jnative::path_string(self);
}
inline void platform_AtomicInteger_initialize_98e5b91b74(::jnative::Object* self) {
    static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value.set(0);
}
inline void platform_AtomicInteger_initialize_e77a1a0072(::jnative::Object* self, std::int32_t arg0) {
    static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value.set(arg0);
}
inline std::int32_t platform_AtomicInteger_addAndGet_a48e5fabaf(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::atomic_add(static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value, arg0, true);
}
inline std::int32_t platform_AtomicInteger_compareAndSet_b7516866c5(::jnative::Object* self, std::int32_t arg0, std::int32_t arg1) {
    return ([&] { std::int32_t expected = arg0; return static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value.compare_exchange(expected, arg1); }());
}
inline std::int32_t platform_AtomicInteger_decrementAndGet_875ee982e2(::jnative::Object* self) {
    return ::jnative::atomic_add(static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value, std::int32_t(-1), true);
}
inline double platform_AtomicInteger_doubleValue_5d9a774217(::jnative::Object* self) {
    return static_cast<double>(static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value.get());
}
inline float platform_AtomicInteger_floatValue_9e0dc8679a(::jnative::Object* self) {
    return static_cast<float>(static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value.get());
}
inline std::int32_t platform_AtomicInteger_get_5254dac96e(::jnative::Object* self) {
    return static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value.get();
}
inline std::int32_t platform_AtomicInteger_getAndAdd_7f05bae453(::jnative::Object* self, std::int32_t arg0) {
    return ::jnative::atomic_add(static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value, arg0, false);
}
inline std::int32_t platform_AtomicInteger_getAndDecrement_49112bee2f(::jnative::Object* self) {
    return ::jnative::atomic_add(static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value, std::int32_t(-1), false);
}
inline std::int32_t platform_AtomicInteger_getAndIncrement_7819566d19(::jnative::Object* self) {
    return ::jnative::atomic_add(static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value, std::int32_t(1), false);
}
inline std::int32_t platform_AtomicInteger_getAndSet_75bde512cb(::jnative::Object* self, std::int32_t arg0) {
    return static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value.exchange(arg0);
}
inline std::int32_t platform_AtomicInteger_incrementAndGet_9b1824cf4c(::jnative::Object* self) {
    return ::jnative::atomic_add(static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value, std::int32_t(1), true);
}
inline std::int64_t platform_AtomicInteger_longValue_41274bb5fb(::jnative::Object* self) {
    return static_cast<std::int64_t>(static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value.get());
}
inline ::jnative::Object* platform_AtomicInteger_toString_d5efe908d3(::jnative::Object* self) {
    return ::jnative::allocate<::jnative::String>(::jnative::to_text(static_cast<::jnative::AtomicInteger*>(::jnative::require_non_null(self))->value.get()));
}
inline void platform_AtomicLong_initialize_be304dd814(::jnative::Object* self) {
    static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value.set(0);
}
inline void platform_AtomicLong_initialize_02f56cacb8(::jnative::Object* self, std::int64_t arg0) {
    static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value.set(arg0);
}
inline std::int64_t platform_AtomicLong_addAndGet_b23e6f696c(::jnative::Object* self, std::int64_t arg0) {
    return ::jnative::atomic_add(static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value, arg0, true);
}
inline std::int32_t platform_AtomicLong_compareAndSet_5aa60de7d8(::jnative::Object* self, std::int64_t arg0, std::int64_t arg1) {
    return ([&] { std::int64_t expected = arg0; return static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value.compare_exchange(expected, arg1); }());
}
inline std::int64_t platform_AtomicLong_decrementAndGet_6e3babb184(::jnative::Object* self) {
    return ::jnative::atomic_add(static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value, std::int64_t(-1), true);
}
inline double platform_AtomicLong_doubleValue_4aa1a842c8(::jnative::Object* self) {
    return static_cast<double>(static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value.get());
}
inline float platform_AtomicLong_floatValue_7b5fe2b2ca(::jnative::Object* self) {
    return static_cast<float>(static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value.get());
}
inline std::int64_t platform_AtomicLong_get_e896fdfab7(::jnative::Object* self) {
    return static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value.get();
}
inline std::int64_t platform_AtomicLong_getAndAdd_d24eb67c15(::jnative::Object* self, std::int64_t arg0) {
    return ::jnative::atomic_add(static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value, arg0, false);
}
inline std::int64_t platform_AtomicLong_getAndDecrement_0cbb8e4e5f(::jnative::Object* self) {
    return ::jnative::atomic_add(static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value, std::int64_t(-1), false);
}
inline std::int64_t platform_AtomicLong_getAndIncrement_e6e1d95ed1(::jnative::Object* self) {
    return ::jnative::atomic_add(static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value, std::int64_t(1), false);
}
inline std::int64_t platform_AtomicLong_getAndSet_237f6a45b3(::jnative::Object* self, std::int64_t arg0) {
    return static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value.exchange(arg0);
}
inline std::int64_t platform_AtomicLong_incrementAndGet_d8a56ca2da(::jnative::Object* self) {
    return ::jnative::atomic_add(static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value, std::int64_t(1), true);
}
inline std::int32_t platform_AtomicLong_intValue_0b8acce88a(::jnative::Object* self) {
    return ::jnative::signed32(static_cast<std::uint32_t>(static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value.get()));
}
inline std::int64_t platform_AtomicLong_longValue_efba78d3fc(::jnative::Object* self) {
    return static_cast<std::int64_t>(static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value.get());
}
inline ::jnative::Object* platform_AtomicLong_toString_defd705b88(::jnative::Object* self) {
    return ::jnative::allocate<::jnative::String>(::jnative::to_text(static_cast<::jnative::AtomicLong*>(::jnative::require_non_null(self))->value.get()));
}
inline ::jnative::Object* platform_Object_clone_530a324f6a(::jnative::Object* self) {
    return ::jnative::array_clone(self);
}
inline ::jnative::Object* platform_System_out() { return ::jnative::standard_out(); }
inline ::jnative::Object* platform_System_err() { return ::jnative::standard_error(); }
inline ::jnative::Object* platform_ByteOrder_LITTLE_ENDIAN() { return ::jnative::byte_order(true); }
inline ::jnative::Object* platform_ByteOrder_BIG_ENDIAN() { return ::jnative::byte_order(false); }
}

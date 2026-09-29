package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteMethod;

/** Exact managed bindings for the supported platform API profile. */
@NativeInclude("jn_platform_bindings.hpp")
public final class NativeReflection {
    private NativeReflection() {}
    @SubstituteMethod(owner = "java.lang.Class", name = "cast", descriptor = "(Ljava/lang/Object;)Ljava/lang/Object;")
    @NativeImport(value = "jnative::platform_Class_cast_5ab4d049a8", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/lang/Class.cast(Ljava/lang/Object;)Ljava/lang/Object;"})
    public static native Object platform_Class_cast_5ab4d049a8(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.Class", name = "forName", descriptor = "(Ljava/lang/String;)Ljava/lang/Class;")
    @NativeImport(value = "jnative::platform_Class_forName_4555fab9c9", managed = true, instance = false,
            targets = {"java/lang/Class.forName(Ljava/lang/String;)Ljava/lang/Class;"})
    public static native Object platform_Class_forName_4555fab9c9(Object arg0);

    @SubstituteMethod(owner = "java.lang.Class", name = "getComponentType", descriptor = "()Ljava/lang/Class;")
    @NativeImport(value = "jnative::platform_Class_getComponentType_a0f295f093", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getComponentType()Ljava/lang/Class;"})
    public static native Object platform_Class_getComponentType_a0f295f093(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "getConstructor", descriptor = "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;")
    @NativeImport(value = "jnative::platform_Class_getConstructor_fdc9396388", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getConstructor([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;"})
    public static native Object platform_Class_getConstructor_fdc9396388(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.Class", name = "getConstructors", descriptor = "()[Ljava/lang/reflect/Constructor;")
    @NativeImport(value = "jnative::platform_Class_getConstructors_33b1acac37", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getConstructors()[Ljava/lang/reflect/Constructor;"})
    public static native Object platform_Class_getConstructors_33b1acac37(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "getEnumConstants", descriptor = "()[Ljava/lang/Object;")
    @NativeImport(value = "jnative::platform_Class_getEnumConstants_2ba838d3c0", managed = true, instance = true,
            targets = {"java/lang/Class.getEnumConstants()[Ljava/lang/Object;"})
    public static native Object platform_Class_getEnumConstants_2ba838d3c0(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "getField", descriptor = "(Ljava/lang/String;)Ljava/lang/reflect/Field;")
    @NativeImport(value = "jnative::platform_Class_getField_06d5979494", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getField(Ljava/lang/String;)Ljava/lang/reflect/Field;"})
    public static native Object platform_Class_getField_06d5979494(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.Class", name = "getFields", descriptor = "()[Ljava/lang/reflect/Field;")
    @NativeImport(value = "jnative::platform_Class_getFields_91ef953787", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getFields()[Ljava/lang/reflect/Field;"})
    public static native Object platform_Class_getFields_91ef953787(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "getInterfaces", descriptor = "()[Ljava/lang/Class;")
    @NativeImport(value = "jnative::platform_Class_getInterfaces_d575e86f7a", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getInterfaces()[Ljava/lang/Class;"})
    public static native Object platform_Class_getInterfaces_d575e86f7a(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "getMethod", descriptor = "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;")
    @NativeImport(value = "jnative::platform_Class_getMethod_4a85120706", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"})
    public static native Object platform_Class_getMethod_4a85120706(Object self, Object arg0, Object arg1);

    @SubstituteMethod(owner = "java.lang.Class", name = "getMethods", descriptor = "()[Ljava/lang/reflect/Method;")
    @NativeImport(value = "jnative::platform_Class_getMethods_4d42adf11a", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getMethods()[Ljava/lang/reflect/Method;"})
    public static native Object platform_Class_getMethods_4d42adf11a(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "getModifiers", descriptor = "()I")
    @SubstituteMethod(owner = "java.lang.reflect.Constructor", name = "getModifiers", descriptor = "()I")
    @SubstituteMethod(owner = "java.lang.reflect.Executable", name = "getModifiers", descriptor = "()I")
    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "getModifiers", descriptor = "()I")
    @SubstituteMethod(owner = "java.lang.reflect.Member", name = "getModifiers", descriptor = "()I")
    @SubstituteMethod(owner = "java.lang.reflect.Method", name = "getModifiers", descriptor = "()I")
    @NativeImport(value = "jnative::platform_Class_getModifiers_8fa4949662", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getModifiers()I",
                    "java/lang/reflect/Constructor.getModifiers()I",
                    "java/lang/reflect/Executable.getModifiers()I",
                    "java/lang/reflect/Field.getModifiers()I",
                    "java/lang/reflect/Member.getModifiers()I",
                    "java/lang/reflect/Method.getModifiers()I"})
    public static native int platform_Class_getModifiers_8fa4949662(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "getName", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_Class_getName_0c0d418469", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getName()Ljava/lang/String;"})
    public static native Object platform_Class_getName_0c0d418469(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "getSimpleName", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_Class_getSimpleName_1a8f4996a4", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getSimpleName()Ljava/lang/String;"})
    public static native Object platform_Class_getSimpleName_1a8f4996a4(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "getSuperclass", descriptor = "()Ljava/lang/Class;")
    @NativeImport(value = "jnative::platform_Class_getSuperclass_774876542c", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getSuperclass()Ljava/lang/Class;"})
    public static native Object platform_Class_getSuperclass_774876542c(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "getTypeName", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_Class_getTypeName_f6f2f32fb5", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.getTypeName()Ljava/lang/String;"})
    public static native Object platform_Class_getTypeName_f6f2f32fb5(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "isArray", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Class_isArray_c12396b319", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/lang/Class.isArray()Z"})
    public static native boolean platform_Class_isArray_c12396b319(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "isAssignableFrom", descriptor = "(Ljava/lang/Class;)Z")
    @NativeImport(value = "jnative::platform_Class_isAssignableFrom_16f562e333", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/lang/Class.isAssignableFrom(Ljava/lang/Class;)Z"})
    public static native boolean platform_Class_isAssignableFrom_16f562e333(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.Class", name = "isInstance", descriptor = "(Ljava/lang/Object;)Z")
    @NativeImport(value = "jnative::platform_Class_isInstance_4c4a3c30ae", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/lang/Class.isInstance(Ljava/lang/Object;)Z"})
    public static native boolean platform_Class_isInstance_4c4a3c30ae(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.Class", name = "isInterface", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Class_isInterface_85f61f03bf", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.isInterface()Z"})
    public static native boolean platform_Class_isInterface_85f61f03bf(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "isPrimitive", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Class_isPrimitive_8b25024279", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/lang/Class.isPrimitive()Z"})
    public static native boolean platform_Class_isPrimitive_8b25024279(Object self);

    @SubstituteMethod(owner = "java.lang.Class", name = "toString", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_Class_toString_6d3b9c2dba", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Class.toString()Ljava/lang/String;"})
    public static native Object platform_Class_toString_6d3b9c2dba(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.Array", name = "newInstance", descriptor = "(Ljava/lang/Class;I)Ljava/lang/Object;")
    @NativeImport(value = "jnative::platform_Array_newInstance_9619452bde", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/reflect/Array.newInstance(Ljava/lang/Class;I)Ljava/lang/Object;"})
    public static native Object platform_Array_newInstance_9619452bde(Object arg0, int arg1);

    @SubstituteMethod(owner = "java.lang.reflect.Constructor", name = "equals", descriptor = "(Ljava/lang/Object;)Z")
    @SubstituteMethod(owner = "java.lang.reflect.Executable", name = "equals", descriptor = "(Ljava/lang/Object;)Z")
    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "equals", descriptor = "(Ljava/lang/Object;)Z")
    @SubstituteMethod(owner = "java.lang.reflect.Member", name = "equals", descriptor = "(Ljava/lang/Object;)Z")
    @SubstituteMethod(owner = "java.lang.reflect.Method", name = "equals", descriptor = "(Ljava/lang/Object;)Z")
    @NativeImport(value = "jnative::platform_Constructor_equals_bb6b5ae28f", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/Constructor.equals(Ljava/lang/Object;)Z",
                    "java/lang/reflect/Executable.equals(Ljava/lang/Object;)Z",
                    "java/lang/reflect/Field.equals(Ljava/lang/Object;)Z",
                    "java/lang/reflect/Member.equals(Ljava/lang/Object;)Z",
                    "java/lang/reflect/Method.equals(Ljava/lang/Object;)Z"})
    public static native boolean platform_Constructor_equals_bb6b5ae28f(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Constructor", name = "getDeclaringClass", descriptor = "()Ljava/lang/Class;")
    @SubstituteMethod(owner = "java.lang.reflect.Executable", name = "getDeclaringClass", descriptor = "()Ljava/lang/Class;")
    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "getDeclaringClass", descriptor = "()Ljava/lang/Class;")
    @SubstituteMethod(owner = "java.lang.reflect.Member", name = "getDeclaringClass", descriptor = "()Ljava/lang/Class;")
    @SubstituteMethod(owner = "java.lang.reflect.Method", name = "getDeclaringClass", descriptor = "()Ljava/lang/Class;")
    @NativeImport(value = "jnative::platform_Constructor_getDeclaringClass_3e4d3990a7", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/Constructor.getDeclaringClass()Ljava/lang/Class;",
                    "java/lang/reflect/Executable.getDeclaringClass()Ljava/lang/Class;",
                    "java/lang/reflect/Field.getDeclaringClass()Ljava/lang/Class;",
                    "java/lang/reflect/Member.getDeclaringClass()Ljava/lang/Class;",
                    "java/lang/reflect/Method.getDeclaringClass()Ljava/lang/Class;"})
    public static native Object platform_Constructor_getDeclaringClass_3e4d3990a7(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.Constructor", name = "getName", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.reflect.Executable", name = "getName", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "getName", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.reflect.Member", name = "getName", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.reflect.Method", name = "getName", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_Constructor_getName_797aa96a5d", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/Constructor.getName()Ljava/lang/String;",
                    "java/lang/reflect/Executable.getName()Ljava/lang/String;",
                    "java/lang/reflect/Field.getName()Ljava/lang/String;",
                    "java/lang/reflect/Member.getName()Ljava/lang/String;",
                    "java/lang/reflect/Method.getName()Ljava/lang/String;"})
    public static native Object platform_Constructor_getName_797aa96a5d(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.Constructor", name = "getParameterCount", descriptor = "()I")
    @SubstituteMethod(owner = "java.lang.reflect.Executable", name = "getParameterCount", descriptor = "()I")
    @SubstituteMethod(owner = "java.lang.reflect.Member", name = "getParameterCount", descriptor = "()I")
    @SubstituteMethod(owner = "java.lang.reflect.Method", name = "getParameterCount", descriptor = "()I")
    @NativeImport(value = "jnative::platform_Constructor_getParameterCount_0d32c1172d", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/Constructor.getParameterCount()I",
                    "java/lang/reflect/Executable.getParameterCount()I",
                    "java/lang/reflect/Member.getParameterCount()I",
                    "java/lang/reflect/Method.getParameterCount()I"})
    public static native int platform_Constructor_getParameterCount_0d32c1172d(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.Constructor", name = "getParameterTypes", descriptor = "()[Ljava/lang/Class;")
    @SubstituteMethod(owner = "java.lang.reflect.Executable", name = "getParameterTypes", descriptor = "()[Ljava/lang/Class;")
    @SubstituteMethod(owner = "java.lang.reflect.Member", name = "getParameterTypes", descriptor = "()[Ljava/lang/Class;")
    @SubstituteMethod(owner = "java.lang.reflect.Method", name = "getParameterTypes", descriptor = "()[Ljava/lang/Class;")
    @NativeImport(value = "jnative::platform_Constructor_getParameterTypes_d8c024b1c3", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/Constructor.getParameterTypes()[Ljava/lang/Class;",
                    "java/lang/reflect/Executable.getParameterTypes()[Ljava/lang/Class;",
                    "java/lang/reflect/Member.getParameterTypes()[Ljava/lang/Class;",
                    "java/lang/reflect/Method.getParameterTypes()[Ljava/lang/Class;"})
    public static native Object platform_Constructor_getParameterTypes_d8c024b1c3(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.Constructor", name = "hashCode", descriptor = "()I")
    @SubstituteMethod(owner = "java.lang.reflect.Executable", name = "hashCode", descriptor = "()I")
    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "hashCode", descriptor = "()I")
    @SubstituteMethod(owner = "java.lang.reflect.Member", name = "hashCode", descriptor = "()I")
    @SubstituteMethod(owner = "java.lang.reflect.Method", name = "hashCode", descriptor = "()I")
    @NativeImport(value = "jnative::platform_Constructor_hashCode_2253d72613", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/Constructor.hashCode()I",
                    "java/lang/reflect/Executable.hashCode()I",
                    "java/lang/reflect/Field.hashCode()I",
                    "java/lang/reflect/Member.hashCode()I",
                    "java/lang/reflect/Method.hashCode()I"})
    public static native int platform_Constructor_hashCode_2253d72613(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.Constructor", name = "isSynthetic", descriptor = "()Z")
    @SubstituteMethod(owner = "java.lang.reflect.Executable", name = "isSynthetic", descriptor = "()Z")
    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "isSynthetic", descriptor = "()Z")
    @SubstituteMethod(owner = "java.lang.reflect.Member", name = "isSynthetic", descriptor = "()Z")
    @SubstituteMethod(owner = "java.lang.reflect.Method", name = "isSynthetic", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Constructor_isSynthetic_eedeefcb1d", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/Constructor.isSynthetic()Z",
                    "java/lang/reflect/Executable.isSynthetic()Z",
                    "java/lang/reflect/Field.isSynthetic()Z",
                    "java/lang/reflect/Member.isSynthetic()Z",
                    "java/lang/reflect/Method.isSynthetic()Z"})
    public static native boolean platform_Constructor_isSynthetic_eedeefcb1d(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.Constructor", name = "isVarArgs", descriptor = "()Z")
    @SubstituteMethod(owner = "java.lang.reflect.Executable", name = "isVarArgs", descriptor = "()Z")
    @SubstituteMethod(owner = "java.lang.reflect.Member", name = "isVarArgs", descriptor = "()Z")
    @SubstituteMethod(owner = "java.lang.reflect.Method", name = "isVarArgs", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Constructor_isVarArgs_1893752b9b", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/Constructor.isVarArgs()Z",
                    "java/lang/reflect/Executable.isVarArgs()Z",
                    "java/lang/reflect/Member.isVarArgs()Z",
                    "java/lang/reflect/Method.isVarArgs()Z"})
    public static native boolean platform_Constructor_isVarArgs_1893752b9b(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.Constructor", name = "newInstance", descriptor = "([Ljava/lang/Object;)Ljava/lang/Object;")
    @NativeImport(value = "jnative::platform_Constructor_newInstance_b9112c4eba", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Constructor.newInstance([Ljava/lang/Object;)Ljava/lang/Object;"})
    public static native Object platform_Constructor_newInstance_b9112c4eba(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "get", descriptor = "(Ljava/lang/Object;)Ljava/lang/Object;")
    @NativeImport(value = "jnative::platform_Field_get_23089c7505", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.get(Ljava/lang/Object;)Ljava/lang/Object;"})
    public static native Object platform_Field_get_23089c7505(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "getBoolean", descriptor = "(Ljava/lang/Object;)Z")
    @NativeImport(value = "jnative::platform_Field_getBoolean_c400957ce2", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.getBoolean(Ljava/lang/Object;)Z"})
    public static native boolean platform_Field_getBoolean_c400957ce2(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "getByte", descriptor = "(Ljava/lang/Object;)B")
    @NativeImport(value = "jnative::platform_Field_getByte_3fde3e6234", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.getByte(Ljava/lang/Object;)B"})
    public static native byte platform_Field_getByte_3fde3e6234(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "getChar", descriptor = "(Ljava/lang/Object;)C")
    @NativeImport(value = "jnative::platform_Field_getChar_5333c7561d", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.getChar(Ljava/lang/Object;)C"})
    public static native char platform_Field_getChar_5333c7561d(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "getDouble", descriptor = "(Ljava/lang/Object;)D")
    @NativeImport(value = "jnative::platform_Field_getDouble_6631983c96", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.getDouble(Ljava/lang/Object;)D"})
    public static native double platform_Field_getDouble_6631983c96(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "getFloat", descriptor = "(Ljava/lang/Object;)F")
    @NativeImport(value = "jnative::platform_Field_getFloat_d5d556785e", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.getFloat(Ljava/lang/Object;)F"})
    public static native float platform_Field_getFloat_d5d556785e(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "getInt", descriptor = "(Ljava/lang/Object;)I")
    @NativeImport(value = "jnative::platform_Field_getInt_f78e295c14", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.getInt(Ljava/lang/Object;)I"})
    public static native int platform_Field_getInt_f78e295c14(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "getLong", descriptor = "(Ljava/lang/Object;)J")
    @NativeImport(value = "jnative::platform_Field_getLong_f13792172d", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.getLong(Ljava/lang/Object;)J"})
    public static native long platform_Field_getLong_f13792172d(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "getShort", descriptor = "(Ljava/lang/Object;)S")
    @NativeImport(value = "jnative::platform_Field_getShort_2de1364950", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.getShort(Ljava/lang/Object;)S"})
    public static native short platform_Field_getShort_2de1364950(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "getType", descriptor = "()Ljava/lang/Class;")
    @SubstituteMethod(owner = "java.lang.reflect.Method", name = "getReturnType", descriptor = "()Ljava/lang/Class;")
    @NativeImport(value = "jnative::platform_Field_getType_e2bccc8d35", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/Field.getType()Ljava/lang/Class;",
                    "java/lang/reflect/Method.getReturnType()Ljava/lang/Class;"})
    public static native Object platform_Field_getType_e2bccc8d35(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "set", descriptor = "(Ljava/lang/Object;Ljava/lang/Object;)V")
    @NativeImport(value = "jnative::platform_Field_set_5ba6fe7b2c", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.set(Ljava/lang/Object;Ljava/lang/Object;)V"})
    public static native void platform_Field_set_5ba6fe7b2c(Object self, Object arg0, Object arg1);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "setBoolean", descriptor = "(Ljava/lang/Object;Z)V")
    @NativeImport(value = "jnative::platform_Field_setBoolean_bba6734de3", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.setBoolean(Ljava/lang/Object;Z)V"})
    public static native void platform_Field_setBoolean_bba6734de3(Object self, Object arg0, boolean arg1);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "setByte", descriptor = "(Ljava/lang/Object;B)V")
    @NativeImport(value = "jnative::platform_Field_setByte_41314f6dcb", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.setByte(Ljava/lang/Object;B)V"})
    public static native void platform_Field_setByte_41314f6dcb(Object self, Object arg0, byte arg1);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "setChar", descriptor = "(Ljava/lang/Object;C)V")
    @NativeImport(value = "jnative::platform_Field_setChar_fcb009193d", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.setChar(Ljava/lang/Object;C)V"})
    public static native void platform_Field_setChar_fcb009193d(Object self, Object arg0, char arg1);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "setDouble", descriptor = "(Ljava/lang/Object;D)V")
    @NativeImport(value = "jnative::platform_Field_setDouble_bd5162adab", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.setDouble(Ljava/lang/Object;D)V"})
    public static native void platform_Field_setDouble_bd5162adab(Object self, Object arg0, double arg1);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "setFloat", descriptor = "(Ljava/lang/Object;F)V")
    @NativeImport(value = "jnative::platform_Field_setFloat_d3ff605cb9", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.setFloat(Ljava/lang/Object;F)V"})
    public static native void platform_Field_setFloat_d3ff605cb9(Object self, Object arg0, float arg1);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "setInt", descriptor = "(Ljava/lang/Object;I)V")
    @NativeImport(value = "jnative::platform_Field_setInt_bcec207ab3", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.setInt(Ljava/lang/Object;I)V"})
    public static native void platform_Field_setInt_bcec207ab3(Object self, Object arg0, int arg1);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "setLong", descriptor = "(Ljava/lang/Object;J)V")
    @NativeImport(value = "jnative::platform_Field_setLong_70b20a29d3", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.setLong(Ljava/lang/Object;J)V"})
    public static native void platform_Field_setLong_70b20a29d3(Object self, Object arg0, long arg1);

    @SubstituteMethod(owner = "java.lang.reflect.Field", name = "setShort", descriptor = "(Ljava/lang/Object;S)V")
    @NativeImport(value = "jnative::platform_Field_setShort_c3cc653003", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Field.setShort(Ljava/lang/Object;S)V"})
    public static native void platform_Field_setShort_c3cc653003(Object self, Object arg0, short arg1);

    @NativeImport(value = "jnative::platform_InvocationTargetException_initialize_0fd17643e4", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/InvocationTargetException.<init>()V"})
    public static native void platform_InvocationTargetException_initialize_0fd17643e4(Object self);

    @NativeImport(value = "jnative::platform_InvocationTargetException_initialize_47bc903c83", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/InvocationTargetException.<init>(Ljava/lang/Throwable;)V"})
    public static native void platform_InvocationTargetException_initialize_47bc903c83(Object self, Object arg0);

    @NativeImport(value = "jnative::platform_InvocationTargetException_initialize_834399d988", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/InvocationTargetException.<init>(Ljava/lang/Throwable;Ljava/lang/String;)V"})
    public static native void platform_InvocationTargetException_initialize_834399d988(Object self, Object arg0, Object arg1);

    @SubstituteMethod(owner = "java.lang.reflect.InvocationTargetException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @NativeImport(value = "jnative::platform_InvocationTargetException_addSuppressed_6d135f1c1c", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/InvocationTargetException.addSuppressed(Ljava/lang/Throwable;)V"})
    public static native void platform_InvocationTargetException_addSuppressed_6d135f1c1c(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.InvocationTargetException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @NativeImport(value = "jnative::platform_InvocationTargetException_getCause_593ed0015d", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/InvocationTargetException.getCause()Ljava/lang/Throwable;"})
    public static native Object platform_InvocationTargetException_getCause_593ed0015d(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.InvocationTargetException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_InvocationTargetException_getMessage_44e8a61b92", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/InvocationTargetException.getMessage()Ljava/lang/String;"})
    public static native Object platform_InvocationTargetException_getMessage_44e8a61b92(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.InvocationTargetException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @NativeImport(value = "jnative::platform_InvocationTargetException_getSuppressed_62012ad588", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/InvocationTargetException.getSuppressed()[Ljava/lang/Throwable;"})
    public static native Object platform_InvocationTargetException_getSuppressed_62012ad588(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.InvocationTargetException", name = "getTargetException", descriptor = "()Ljava/lang/Throwable;")
    @NativeImport(value = "jnative::platform_InvocationTargetException_getTargetException_df43a3eed7", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/InvocationTargetException.getTargetException()Ljava/lang/Throwable;"})
    public static native Object platform_InvocationTargetException_getTargetException_df43a3eed7(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.InvocationTargetException", name = "printStackTrace", descriptor = "()V")
    @NativeImport(value = "jnative::platform_InvocationTargetException_printStackTrace_25ece660ac", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/InvocationTargetException.printStackTrace()V"})
    public static native void platform_InvocationTargetException_printStackTrace_25ece660ac(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.InvocationTargetException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @NativeImport(value = "jnative::platform_InvocationTargetException_printStackTrace_f5e4a3cc94", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/InvocationTargetException.printStackTrace(Ljava/io/PrintStream;)V"})
    public static native void platform_InvocationTargetException_printStackTrace_f5e4a3cc94(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.reflect.InvocationTargetException", name = "toString", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_InvocationTargetException_toString_2651e15a35", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/InvocationTargetException.toString()Ljava/lang/String;"})
    public static native Object platform_InvocationTargetException_toString_2651e15a35(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.Method", name = "invoke", descriptor = "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")
    @NativeImport(value = "jnative::platform_Method_invoke_4c1d5611e7", managed = true, registeredReflection = true, instance = true,
            targets = {"java/lang/reflect/Method.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"})
    public static native Object platform_Method_invoke_4c1d5611e7(Object self, Object arg0, Object arg1);

    @SubstituteMethod(owner = "java.lang.reflect.Method", name = "isBridge", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Method_isBridge_d4a85ab0e0", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/reflect/Method.isBridge()Z"})
    public static native boolean platform_Method_isBridge_d4a85ab0e0(Object self);

    @SubstituteMethod(owner = "java.lang.reflect.Modifier", name = "isAbstract", descriptor = "(I)Z")
    @NativeImport(value = "jnative::platform_Modifier_isAbstract_4192e46d45", managed = true, instance = false,
            targets = {"java/lang/reflect/Modifier.isAbstract(I)Z"})
    public static native boolean platform_Modifier_isAbstract_4192e46d45(int arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Modifier", name = "isFinal", descriptor = "(I)Z")
    @NativeImport(value = "jnative::platform_Modifier_isFinal_cdfc274904", managed = true, instance = false,
            targets = {"java/lang/reflect/Modifier.isFinal(I)Z"})
    public static native boolean platform_Modifier_isFinal_cdfc274904(int arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Modifier", name = "isInterface", descriptor = "(I)Z")
    @NativeImport(value = "jnative::platform_Modifier_isInterface_3a36365273", managed = true, instance = false,
            targets = {"java/lang/reflect/Modifier.isInterface(I)Z"})
    public static native boolean platform_Modifier_isInterface_3a36365273(int arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Modifier", name = "isNative", descriptor = "(I)Z")
    @NativeImport(value = "jnative::platform_Modifier_isNative_c047137374", managed = true, instance = false,
            targets = {"java/lang/reflect/Modifier.isNative(I)Z"})
    public static native boolean platform_Modifier_isNative_c047137374(int arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Modifier", name = "isPrivate", descriptor = "(I)Z")
    @NativeImport(value = "jnative::platform_Modifier_isPrivate_7a96cf0f94", managed = true, instance = false,
            targets = {"java/lang/reflect/Modifier.isPrivate(I)Z"})
    public static native boolean platform_Modifier_isPrivate_7a96cf0f94(int arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Modifier", name = "isProtected", descriptor = "(I)Z")
    @NativeImport(value = "jnative::platform_Modifier_isProtected_9c69de4edd", managed = true, instance = false,
            targets = {"java/lang/reflect/Modifier.isProtected(I)Z"})
    public static native boolean platform_Modifier_isProtected_9c69de4edd(int arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Modifier", name = "isPublic", descriptor = "(I)Z")
    @NativeImport(value = "jnative::platform_Modifier_isPublic_5fc73927a1", managed = true, instance = false,
            targets = {"java/lang/reflect/Modifier.isPublic(I)Z"})
    public static native boolean platform_Modifier_isPublic_5fc73927a1(int arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Modifier", name = "isStatic", descriptor = "(I)Z")
    @NativeImport(value = "jnative::platform_Modifier_isStatic_efe2d32a83", managed = true, instance = false,
            targets = {"java/lang/reflect/Modifier.isStatic(I)Z"})
    public static native boolean platform_Modifier_isStatic_efe2d32a83(int arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Modifier", name = "isStrict", descriptor = "(I)Z")
    @NativeImport(value = "jnative::platform_Modifier_isStrict_3ad5a46380", managed = true, instance = false,
            targets = {"java/lang/reflect/Modifier.isStrict(I)Z"})
    public static native boolean platform_Modifier_isStrict_3ad5a46380(int arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Modifier", name = "isSynchronized", descriptor = "(I)Z")
    @NativeImport(value = "jnative::platform_Modifier_isSynchronized_9e2ad905b1", managed = true, instance = false,
            targets = {"java/lang/reflect/Modifier.isSynchronized(I)Z"})
    public static native boolean platform_Modifier_isSynchronized_9e2ad905b1(int arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Modifier", name = "isTransient", descriptor = "(I)Z")
    @NativeImport(value = "jnative::platform_Modifier_isTransient_b4ab27bcea", managed = true, instance = false,
            targets = {"java/lang/reflect/Modifier.isTransient(I)Z"})
    public static native boolean platform_Modifier_isTransient_b4ab27bcea(int arg0);

    @SubstituteMethod(owner = "java.lang.reflect.Modifier", name = "isVolatile", descriptor = "(I)Z")
    @NativeImport(value = "jnative::platform_Modifier_isVolatile_fd5775723a", managed = true, instance = false,
            targets = {"java/lang/reflect/Modifier.isVolatile(I)Z"})
    public static native boolean platform_Modifier_isVolatile_fd5775723a(int arg0);

}

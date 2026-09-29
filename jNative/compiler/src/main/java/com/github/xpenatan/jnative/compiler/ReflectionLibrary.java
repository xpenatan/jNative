package com.github.xpenatan.jnative.compiler;

import java.util.Set;

/**
 * Public reflection profile. Unsupported access-control and generic APIs are diagnosed.
 */
public final class ReflectionLibrary {
    private ReflectionLibrary() {
    }

    public static boolean method(String owner, String name, String descriptor) {
        String signature = name + descriptor;
        if(owner.equals("java/lang/reflect/Array"))
            return signature.equals("newInstance(Ljava/lang/Class;I)Ljava/lang/Object;");
        if(owner.equals("java/lang/Class"))
            return Set.of(
                            "forName(Ljava/lang/String;)Ljava/lang/Class;",
                            "getName()Ljava/lang/String;",
                            "getSimpleName()Ljava/lang/String;",
                            "getEnumConstants()[Ljava/lang/Object;",
                            "getTypeName()Ljava/lang/String;",
                            "getSuperclass()Ljava/lang/Class;",
                            "getInterfaces()[Ljava/lang/Class;",
                            "getComponentType()Ljava/lang/Class;",
                            "getModifiers()I",
                            "isInterface()Z",
                            "isArray()Z",
                            "isPrimitive()Z",
                            "isInstance(Ljava/lang/Object;)Z",
                            "isAssignableFrom(Ljava/lang/Class;)Z",
                            "cast(Ljava/lang/Object;)Ljava/lang/Object;",
                            "getField(Ljava/lang/String;)Ljava/lang/reflect/Field;",
                            "getFields()[Ljava/lang/reflect/Field;",
                            "getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
                            "getMethods()[Ljava/lang/reflect/Method;",
                            "getConstructor([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;",
                            "getConstructors()[Ljava/lang/reflect/Constructor;",
                            "toString()Ljava/lang/String;")
                    .contains(signature);
        if(owner.equals("java/lang/reflect/Modifier"))
            return Set.of(
                            "isPublic",
                            "isPrivate",
                            "isProtected",
                            "isStatic",
                            "isFinal",
                            "isSynchronized",
                            "isVolatile",
                            "isTransient",
                            "isNative",
                            "isInterface",
                            "isAbstract",
                            "isStrict")
                    .contains(name)
                    && descriptor.equals("(I)Z");
        if(owner.equals("java/lang/reflect/InvocationTargetException"))
            return signature.equals("getTargetException()Ljava/lang/Throwable;");
        if(!Set.of(
                        "java/lang/reflect/Method",
                        "java/lang/reflect/Field",
                        "java/lang/reflect/Constructor",
                        "java/lang/reflect/Member",
                        "java/lang/reflect/Executable")
                .contains(owner)) return false;
        if(Set.of(
                        "getName()Ljava/lang/String;",
                        "getDeclaringClass()Ljava/lang/Class;",
                        "getModifiers()I",
                        "isSynthetic()Z",
                        "equals(Ljava/lang/Object;)Z",
                        "hashCode()I")
                .contains(signature)) return true;
        if(owner.equals("java/lang/reflect/Field")) {
            if(Set.of(
                            "getType()Ljava/lang/Class;",
                            "get(Ljava/lang/Object;)Ljava/lang/Object;",
                            "set(Ljava/lang/Object;Ljava/lang/Object;)V")
                    .contains(signature)) return true;
            String[] words = {"Boolean", "Byte", "Char", "Short", "Int", "Long", "Float", "Double"};
            String types = "ZBCSIJFD";
            for(int i = 0; i < words.length; ++i)
                if(signature.equals("get" + words[i] + "(Ljava/lang/Object;)" + types.charAt(i))
                        || signature.equals(
                        "set" + words[i] + "(Ljava/lang/Object;" + types.charAt(i) + ")V"))
                    return true;
            return false;
        }
        if(Set.of("getParameterTypes()[Ljava/lang/Class;", "getParameterCount()I", "isVarArgs()Z")
                .contains(signature)) return true;
        return owner.equals("java/lang/reflect/Method")
                && Set.of(
                        "invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                        "getReturnType()Ljava/lang/Class;",
                        "isBridge()Z")
                .contains(signature)
                || owner.equals("java/lang/reflect/Constructor")
                && signature.equals("newInstance([Ljava/lang/Object;)Ljava/lang/Object;");
    }
}

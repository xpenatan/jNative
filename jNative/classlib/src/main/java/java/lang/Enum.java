package java.lang;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

/**
 * Base state and identity semantics shared by compiled enum constants.
 */
@NativeInclude("jn_string_kernels.hpp")
public abstract class Enum<E extends Enum<E>> implements Comparable<E>, java.io.Serializable {
    private final String name;
    private final int ordinal;

    @NativeImport(value = "jnative::enum_value_of", managed = true, runtimeOnly = true, callbacksSynchronous = true,
            callbacks = {"java/lang/Class.getEnumConstants()[Ljava/lang/Object;",
                    "java/lang/Enum.name()Ljava/lang/String;",
                    "java/lang/Class.toString()Ljava/lang/String;",
                    "java/lang/Class.getName()Ljava/lang/String;"},
            callbackReceivers = {0, -1, 0, 0})
    public static native <T extends Enum<T>> T valueOf(Class<T> type, String name);

    protected Enum(String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }

    public final String name() {
        return name;
    }

    public final int ordinal() {
        return ordinal;
    }

    public String toString() {
        return name;
    }

    public final boolean equals(Object other) {
        return this == other;
    }

    public final int hashCode() {
        return super.hashCode();
    }

    public final int compareTo(E other) {
        Enum<?> value = other;
        if(value == this) return 0;
        if(getClass() != value.getClass() && getDeclaringClass() != value.getDeclaringClass())
            throw new ClassCastException();
        return ordinal - value.ordinal;
    }

    @SuppressWarnings("unchecked")
    public final Class<E> getDeclaringClass() {
        Class<?> actual = getClass();
        Class<?> parent = actual.getSuperclass();
        return (Class<E>)(parent == Enum.class ? actual : parent);
    }
}

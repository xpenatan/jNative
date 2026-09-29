package com.github.xpenatan.jnative.interop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exports a static Java method through a generated C ABI function.
 * The function returns a status; its final output parameter receives the Java
 * result. References use owned handles. Java exceptions become native error state.
 * Use the builder's exportClass option for classes unreachable from the main entry.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface NativeExport {
    /**
     * Unique C-linkage function name, outside the reserved jn_ namespace.
     */
    String value();
}

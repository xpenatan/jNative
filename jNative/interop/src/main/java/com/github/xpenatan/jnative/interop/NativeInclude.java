package com.github.xpenatan.jnative.interop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Packaged or project-relative header declaring native functions used by a binding class.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface NativeInclude {
    /**
     * Project-relative header name.
     */
    String value();
}

package com.github.xpenatan.jnative;

/**
 * Physical source organization; C++ logical names are independent of this choice.
 * Compiler-generated lambdas are nested C++ types and share their enclosing
 * class's header and source file in either layout. Java identities remain distinct
 * in the generated source map.
 */
public enum SourceLayout {
    PACKAGE_DIRECTORIES,
    PACKAGE_FILENAME
}

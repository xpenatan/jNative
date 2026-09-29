package com.github.xpenatan.jnative.classlib.java.util.regex;

import java.util.regex.*;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

@NativeInclude("jn_regex_drivers.hpp")
final class NativeRegex {
    private NativeRegex() {
    }

    @NativeImport(value = "jnative::regex_compile", managed = true, runtimeOnly = true, managesRoots = true)
    static native Object compile(String pattern, int flags);

    @NativeImport(value = "jnative::regex_find", managed = true, runtimeOnly = true, managesRoots = true)
    static native int[] find(Object compiled, String input, int offset, boolean whole);

    @NativeImport(value = "jnative::regex_split", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/regex/Matcher.find()Z"},
            fields = {"java/util/regex/Matcher.groups:[I"})
    static native String[] split(Matcher matcher, String input, int limit);

    @NativeImport(value = "jnative::regex_append_replacement", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/lang/StringBuffer.append(Ljava/lang/String;)Ljava/lang/StringBuffer;",
                    "java/lang/StringBuffer.append(C)Ljava/lang/StringBuffer;"},
            callbackReceivers = {1, 1},
            fields = {"java/util/regex/Matcher.groups:[I", "java/util/regex/Matcher.input:Ljava/lang/String;",
                    "java/util/regex/Matcher.append:I"})
    static native void appendReplacement(Matcher matcher, StringBuffer output, String replacement);

    @NativeImport(value = "jnative::regex_replace", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/regex/Matcher.find()Z",
                    "java/lang/StringBuffer.append(Ljava/lang/String;)Ljava/lang/StringBuffer;",
                    "java/lang/StringBuffer.append(C)Ljava/lang/StringBuffer;"},
            callbackReceivers = {0, 1, 1},
            fields = {"java/util/regex/Matcher.groups:[I", "java/util/regex/Matcher.input:Ljava/lang/String;",
                    "java/util/regex/Matcher.append:I"})
    static native void replace(Matcher matcher, StringBuffer output, String replacement, boolean first);
}

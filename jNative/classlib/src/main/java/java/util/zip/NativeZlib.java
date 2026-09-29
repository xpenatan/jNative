package java.util.zip;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

@NativeInclude("jn_runtime.hpp")
final class NativeZlib {
    private NativeZlib() {
    }

    @NativeImport(value = "jnative::zlib_open", managed = true, runtimeOnly = true)
    static native Object open(boolean compress, int level, boolean raw);

    @NativeImport(value = "jnative::zlib_input", managed = true, runtimeOnly = true)
    static native void input(Object state, byte[] bytes, int offset, int length);

    @NativeImport(value = "jnative::zlib_process", managed = true, runtimeOnly = true)
    static native int process(Object state, byte[] bytes, int offset, int length, boolean finish);

    @NativeImport(value = "jnative::zlib_status", managed = true, runtimeOnly = true)
    static native int status(Object state, int kind);

    @NativeImport(value = "jnative::zlib_close", managed = true, runtimeOnly = true)
    static native void close(Object state);
}

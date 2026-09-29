package java.io;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

import java.nio.file.Path;

@NativeInclude("jn_files.hpp")
final class NativeFiles {
    @NativeImport(value = "jnative::io_write_byte", managed = true, runtimeOnly = true)
    static native void writeByte(Object handle, int value) throws IOException;

    @NativeImport(value = "jnative::io_path_text", managed = true, runtimeOnly = true)
    static native String pathText(Path path, int mode);
    private NativeFiles() {
    }

    @NativeImport(value = "jnative::io_open", managed = true, runtimeOnly = true)
    static native Object open(String path, boolean write, boolean append) throws IOException;

    @NativeImport(value = "jnative::io_read", managed = true, runtimeOnly = true)
    static native int read(Object handle, byte[] bytes, int offset, int length) throws IOException;

    @NativeImport(value = "jnative::io_write", managed = true, runtimeOnly = true)
    static native void write(Object handle, byte[] bytes, int offset, int length)
            throws IOException;

    @NativeImport(value = "jnative::io_close", managed = true, runtimeOnly = true)
    static native void close(Object handle) throws IOException;

    @NativeImport(value = "jnative::io_flush", managed = true, runtimeOnly = true)
    static native void flush(Object handle) throws IOException;

    @NativeImport(value = "jnative::io_status", managed = true, runtimeOnly = true)
    static native int status(Path path);

    @NativeImport(value = "jnative::io_length", managed = true, runtimeOnly = true)
    static native long length(Path path);

    @NativeImport(value = "jnative::io_mkdirs", managed = true, runtimeOnly = true)
    static native boolean mkdirs(Path path);
}

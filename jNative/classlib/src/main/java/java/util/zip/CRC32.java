package java.util.zip;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

@NativeInclude("jn_classlib_drivers.hpp")
public class CRC32 implements Checksum {
    @NativeImport(value = "jnative::checksum_update", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/zip/CRC32.update(I)V"},
            fields = {"java/util/zip/CRC32.value:I"})
    private static native void updateAll(CRC32 receiver, byte[] bytes, int offset, int length);
    private int value = -1;

    @NativeImport(value = "jnative::crc32_update", managed = true, runtimeOnly = true, bounded = true)
    private static native int updateByte(int state, int next);

    public void update(int next) {
        value = updateByte(value, next);
    }

    public void update(byte[] bytes) {
        update(bytes, 0, bytes.length);
    }

    public void update(byte[] bytes, int offset, int length) {
        java.util.Objects.requireNonNull(bytes);
        if(offset < 0 || length < 0 || offset > bytes.length - length)
            throw new ArrayIndexOutOfBoundsException();
        if(length == 0) return;
        // Subclasses may override update(int), including its observable callbacks.
        updateAll(this, bytes, offset, length);
    }

    public long getValue() {
        return (~value) & 0xffffffffL;
    }

    public void reset() {
        value = -1;
    }
}

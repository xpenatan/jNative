package java.io;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

@NativeInclude("jn_io_drivers.hpp")
public class FileInputStream extends InputStream {
    @NativeImport(value = "jnative::file_input_read", managed = true, runtimeOnly = true, callbacksSynchronous = true,
            callbacks = {"java/io/FileInputStream.read([BII)I"})
    private static native int readNative(FileInputStream stream, Object handle) throws IOException;
    private final Object handle;

    public FileInputStream(String path) throws FileNotFoundException {
        try {
            handle = NativeFiles.open(path, false, false);
        } catch(IOException failure) {
            throw new FileNotFoundException(failure.getMessage());
        }
    }

    public FileInputStream(File file) throws FileNotFoundException {
        this(file.getPath());
    }

    public int read() throws IOException {
        return readNative(this, handle);
    }

    public int read(byte[] bytes, int offset, int length) throws IOException {
        return NativeFiles.read(handle, bytes, offset, length);
    }

    public void close() throws IOException {
        NativeFiles.close(handle);
    }
}

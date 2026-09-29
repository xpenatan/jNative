package java.io;

public class ByteArrayInputStream extends InputStream {
    protected byte[] buf;
    protected int pos, count, mark;

    public ByteArrayInputStream(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public ByteArrayInputStream(byte[] bytes, int offset, int length) {
        if(offset < 0 || length < 0 || offset > bytes.length)
            throw new IndexOutOfBoundsException();
        buf = bytes;
        pos = mark = offset;
        count = offset + Math.min(length, bytes.length - offset);
    }

    public synchronized int read() {
        return pos >= count ? -1 : buf[pos++] & 255;
    }

    public synchronized int read(byte[] bytes, int offset, int length) {
        if(offset < 0 || length < 0 || offset > bytes.length - length)
            throw new IndexOutOfBoundsException();
        if(pos >= count) return -1;
        int size = Math.min(length, count - pos);
        System.arraycopy(buf, pos, bytes, offset, size);
        pos += size;
        return size;
    }

    public synchronized long skip(long length) {
        int size = (int)Math.max(0L, Math.min(length, count - pos));
        pos += size;
        return size;
    }

    public synchronized int available() {
        return count - pos;
    }

    public synchronized void reset() {
        pos = mark;
    }

    public void mark(int limit) {
        mark = pos;
    }

    public boolean markSupported() {
        return true;
    }

    public void close() {
    }
}

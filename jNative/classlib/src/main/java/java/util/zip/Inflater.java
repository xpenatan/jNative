package java.util.zip;

public class Inflater {
    private final Object state;

    public Inflater() {
        this(false);
    }

    public Inflater(boolean nowrap) {
        state = NativeZlib.open(false, 0, nowrap);
    }

    public synchronized void setInput(byte[] bytes) {
        setInput(bytes, 0, bytes.length);
    }

    public synchronized void setInput(byte[] bytes, int offset, int length) {
        NativeZlib.input(state, bytes, offset, length);
    }

    public synchronized int inflate(byte[] bytes) throws DataFormatException {
        return inflate(bytes, 0, bytes.length);
    }

    public synchronized int inflate(byte[] bytes, int offset, int length)
            throws DataFormatException {
        return NativeZlib.process(state, bytes, offset, length, false);
    }

    public synchronized int getRemaining() {
        return NativeZlib.status(state, 0);
    }

    public synchronized boolean finished() {
        return NativeZlib.status(state, 1) != 0;
    }

    public synchronized boolean needsInput() {
        return getRemaining() == 0;
    }

    public synchronized boolean needsDictionary() {
        return NativeZlib.status(state, 2) != 0;
    }

    public synchronized void end() {
        NativeZlib.close(state);
    }
}

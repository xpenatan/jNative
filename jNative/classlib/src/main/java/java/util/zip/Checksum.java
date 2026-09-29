package java.util.zip;

public interface Checksum {
    void update(int value);

    void update(byte[] bytes, int offset, int length);

    long getValue();

    void reset();

    default void update(byte[] bytes) {
        update(bytes, 0, bytes.length);
    }
}

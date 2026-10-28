import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Payload changes every pass; setup and complete verification are outside timing. */
public class TransferIsolation {
    static volatile Object published;

    static void arrays(int size, int rounds, boolean report) {
        float[] source = new float[size], target = new float[size];
        for(int i = 0; i < size; i++) source[i] = (i & 255) * 0.25f;
        long start = System.nanoTime();
        for(int r = 0; r < rounds; r++) {
            source[r % size] += 0.25f;
            System.arraycopy(source, 0, target, 0, size);
            published = target;
        }
        long elapsed = System.nanoTime() - start;
        long checksum = 0;
        for(int i = 0; i < size; i++) {
            if(Float.floatToRawIntBits(target[i]) != Float.floatToRawIntBits(source[i]))
                throw new IllegalStateException("array value " + i);
            checksum += (long)(target[i] * 4) * (1 + i % 7);
        }
        published = null;
        emit("array-copy", size, rounds, elapsed, checksum, report);
    }

    static void buffers(String operation, int size, int rounds, boolean report) {
        ByteOrder order = ByteOrder.nativeOrder();
        ByteOrder other = order == ByteOrder.LITTLE_ENDIAN ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        FloatBuffer source = ByteBuffer.allocateDirect(size * 4).order(order).asFloatBuffer();
        FloatBuffer target = ByteBuffer.allocateDirect(size * 4)
                .order(operation.equals("buffer-swap") ? other : order).asFloatBuffer();
        for(int i = 0; i < size; i++) source.put(i, (i & 255) * 0.25f);
        long start = System.nanoTime();
        for(int r = 0; r < rounds; r++) {
            int index = r % size;
            source.put(index, source.get(index) + 0.25f);
            source.position(0);
            target.position(0);
            target.put(source);
            published = target;
        }
        long elapsed = System.nanoTime() - start;
        long checksum = 0;
        for(int i = 0; i < size; i++) {
            float value = target.get(i);
            if(Float.floatToRawIntBits(value) != Float.floatToRawIntBits(source.get(i)))
                throw new IllegalStateException("buffer value " + i);
            checksum += (long)(value * 4) * (1 + i % 7);
        }
        if(source.position() != size || target.position() != size)
            throw new IllegalStateException("position");
        published = null;
        emit(operation, size, rounds, elapsed, checksum, report);
    }

    static void emit(String operation, int size, int rounds, long elapsed, long checksum, boolean report) {
        if(report) System.out.println("BENCH," + operation + "," + size + "," + rounds + "," + elapsed + "," + checksum);
    }

    static void sample(String operation, int size, int rounds, boolean report) {
        if(operation.equals("array-copy")) arrays(size, rounds, report);
        else if(operation.equals("buffer-copy") || operation.equals("buffer-swap")) buffers(operation, size, rounds, report);
        else throw new IllegalArgumentException(operation);
    }

    public static void main(String[] args) {
        int size = Integer.parseInt(args[1]), rounds = Integer.parseInt(args[2]), samples = Integer.parseInt(args[3]);
        if(size < 1 || rounds < 1 || samples < 1) throw new IllegalArgumentException();
        for(int i = 0; i < 3; i++) sample(args[0], size, rounds, false);
        for(int i = 0; i < samples; i++) sample(args[0], size, rounds, true);
    }
}

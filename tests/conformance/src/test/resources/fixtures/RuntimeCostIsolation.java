import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Diagnostic only. Setup, complete result verification and cleanup are outside timing. */
public class RuntimeCostIsolation {
    static volatile Object published;
    static final class Cell { }

    static long rows(float[][] source, float[][] target, int rounds) {
        Object marker = source[0];
        long sum = 0;
        for(int r = 0; r < rounds; r++) {
            int offset = r % source.length;
            for(int i = 0; i < source.length; i++) {
                int index = i + offset;
                if(index >= source.length) index -= source.length;
                float[] value = source[index];
                target[i] = value;
                if(value == marker) sum++;
            }
            published = target;
        }
        return sum;
    }

    static long objects(Object[] source, Object[] target, int rounds) {
        Object marker = source[0];
        long sum = 0;
        for(int r = 0; r < rounds; r++) {
            int offset = r % source.length;
            for(int i = 0; i < source.length; i++) {
                int index = i + offset;
                if(index >= source.length) index -= source.length;
                Object value = source[index];
                target[i] = value;
                if(value == marker) sum++;
            }
            published = target;
        }
        return sum;
    }

    static long cells(Cell[] source, Cell[] target, int rounds) {
        Object marker = source[0];
        long sum = 0;
        for(int r = 0; r < rounds; r++) {
            int offset = r % source.length;
            for(int i = 0; i < source.length; i++) {
                int index = i + offset;
                if(index >= source.length) index -= source.length;
                Cell value = source[index];
                target[i] = value;
                if(value == marker) sum++;
            }
            published = target;
        }
        return sum;
    }

    static void bulk(float[] source, FloatBuffer target, int rounds) {
        for(int r = 0; r < rounds; r++) {
            source[r % source.length] += 0.25f;
            target.position(0);
            target.put(source);
            published = target;
        }
    }

    static void scalar(float[] source, FloatBuffer target, int rounds) {
        for(int r = 0; r < rounds; r++) {
            source[r % source.length] += 0.25f;
            target.position(0);
            for(int i = 0; i < source.length; i++) target.put(i, source[i]);
            target.position(source.length);
            published = target;
        }
    }

    static void buffers(String operation, int size, int rounds, boolean report) {
        float[] source = new float[size];
        for(int i = 0; i < size; i++) source[i] = (i & 255) * 0.25f;
        FloatBuffer target = ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        long start = System.nanoTime();
        if(operation.equals("buffer-bulk")) bulk(source, target, rounds);
        else scalar(source, target, rounds);
        long elapsed = System.nanoTime() - start;
        long checksum = 0;
        for(int i = 0; i < size; i++) {
            float actual = target.get(i);
            if(actual != source[i]) throw new IllegalStateException("buffer value " + i);
            checksum += (long) (actual * 4) * (1 + i % 7);
        }
        if(target.position() != size) throw new IllegalStateException("buffer position");
        published = null;
        emit(operation, size, rounds, elapsed, checksum, report);
    }

    static void references(String operation, int size, int rounds, boolean report) {
        Object[] source, target;
        if(operation.equals("store-cells")) {
            Cell[] values = new Cell[size];
            for(int i = 0; i < size; i++) values[i] = new Cell();
            source = values;
            target = new Cell[size];
        } else {
            source = new float[size][1];
            target = operation.equals("store-rows") ? new float[size][] : new Object[size];
        }
        long start = System.nanoTime();
        long checksum;
        if(operation.equals("store-rows")) checksum = rows((float[][])source, (float[][])target, rounds);
        else if(operation.equals("store-cells")) checksum = cells((Cell[])source, (Cell[])target, rounds);
        else checksum = objects(source, target, rounds);
        long elapsed = System.nanoTime() - start;
        for(int i = 0; i < size; i++)
            if(target[i] != source[(i + (rounds - 1) % size) % size])
                throw new IllegalStateException("reference value " + i);
        if(checksum != rounds) throw new IllegalStateException("reference count");
        published = null;
        emit(operation, size, rounds, elapsed, checksum, report);
    }

    static void emit(String operation, int size, int rounds, long elapsed, long checksum, boolean report) {
        if(report) System.out.println("BENCH," + operation + "," + size + "," + rounds + "," + elapsed + "," + checksum);
    }

    static void sample(String operation, int size, int rounds, boolean report) {
        if(operation.startsWith("buffer-")) buffers(operation, size, rounds, report);
        else references(operation, size, rounds, report);
    }

    public static void main(String[] args) {
        String operation = args[0];
        int size = Integer.parseInt(args[1]), rounds = Integer.parseInt(args[2]), samples = Integer.parseInt(args[3]);
        if(size < 1 || rounds < 1 || samples < 1) throw new IllegalArgumentException();
        if(!operation.equals("store-rows") && !operation.equals("store-objects") && !operation.equals("store-cells")
                && !operation.equals("buffer-bulk") && !operation.equals("buffer-scalar"))
            throw new IllegalArgumentException(operation);
        for(int i = 0; i < 3; i++) sample(operation, size, rounds, false);
        for(int i = 0; i < samples; i++) sample(operation, size, rounds, true);
    }
}

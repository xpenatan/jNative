/** Ordinary array copies: allocation, initialization and verification are outside the timer. */
public class NestedArrayIsolation {
    static volatile Object published;

    static void rows8(float[][] source, float[] target, int rounds) {
        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < source.length; i++) {
                source[i][0] += 1;
                System.arraycopy(source[i], 0, target, i * 24, 8);
            }
            published = target;
        }
    }

    static void rows24(float[][] source, float[] target, int rounds) {
        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < source.length; i++) {
                source[i][0] += 1;
                System.arraycopy(source[i], 0, target, i * 24, 24);
            }
            published = target;
        }
    }

    static void variable(float[][] source, float[] target, int rounds, int width) {
        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < source.length; i++) {
                source[i][0] += 1;
                System.arraycopy(source[i], 0, target, i * 24, width);
            }
            published = target;
        }
    }

    static void flat(float[] source, float[] target, int rounds) {
        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < source.length; i += 24) {
                source[i] += 1;
                System.arraycopy(source, i, target, i, 24);
            }
            published = target;
        }
    }

    static void sample(String operation, int size, int rounds, boolean report) {
        float[][] rows = new float[size][24];
        float[] source = new float[size * 24], target = new float[size * 24];
        for (int i = 0; i < size; i++)
            for (int j = 0; j < 24; j++) rows[i][j] = source[i * 24 + j] = (i * 17 + j) & 255;
        long start = System.nanoTime();
        switch (operation) {
            case "rows-8": rows8(rows, target, rounds); break;
            case "rows-24": rows24(rows, target, rounds); break;
            case "rows-variable": variable(rows, target, rounds, 24); break;
            case "flat-24": flat(source, target, rounds); break;
            default: throw new IllegalArgumentException(operation);
        }
        long elapsed = System.nanoTime() - start;
        long checksum = 0;
        for (int i = 0; i < target.length; i++) checksum += (long) target[i] * (1 + i % 7);
        published = null;
        if (report) System.out.println("BENCH," + operation + "," + size + "," + rounds + "," + elapsed + "," + checksum);
    }

    public static void main(String[] args) {
        String operation = args[0];
        int size = Integer.parseInt(args[1]), rounds = Integer.parseInt(args[2]), samples = Integer.parseInt(args[3]);
        if (size < 1 || rounds < 1 || samples < 1) throw new IllegalArgumentException();
        for (int i = 0; i < 3; i++) sample(operation, size, rounds, false);
        for (int i = 0; i < samples; i++) sample(operation, size, rounds, true);
    }
}

/** Library-independent probes. Allocation, verification and cleanup are outside timing. */
public class NumericPipelineIsolation {
    static volatile Object published;

    static final class Table {
        static final float[] values = new float[16384];
        static {
            // Exact binary fractions avoid differences between platform transcendental functions.
            for(int i = 0; i < values.length; i++) values[i] = ((i & 255) - 128) / 128f;
        }
    }

    static final class Lookup {
        static float first(float angle) { return Table.values[(int)(angle * (16384f / 360f)) & 16383]; }
        static float second(float angle) { return Table.values[(int)((angle + 90f) * (16384f / 360f)) & 16383]; }
    }

    static final class Item {
        float angle, scale = 1, x, y, first, second;
        final float[] values = new float[24];

        void update(float amount) {
            angle += 0.25f;
            if(angle >= 360f) angle -= 360f;
            scale = amount;
        }

        void lookup() {
            first = Lookup.first(angle);
            second = Lookup.second(angle);
        }

        void pack() {
            float left = -16f * scale, right = 16f * scale;
            float bottom = -8f * scale, top = 8f * scale;
            values[0] = first * left - second * bottom + x;
            values[1] = second * left + first * bottom + y;
            values[6] = first * left - second * top + x;
            values[7] = second * left + first * top + y;
            values[12] = first * right - second * top + x;
            values[13] = second * right + first * top + y;
            values[18] = first * right - second * bottom + x;
            values[19] = second * right + first * bottom + y;
        }
    }

    static final class Holder {
        Item[] items;
        Holder(Item[] items) { this.items = items; }
    }

    static void repeatedLocal(Item[] items, int rounds) {
        for(int r = 0; r < rounds; r++) {
            float scale = 0.5f + (r & 31) / 64f;
            for(int i = 0; i < items.length; i++) {
                items[i].update(scale);
                items[i].pack();
            }
            published = items;
        }
    }

    static void repeatedField(Holder holder, int rounds) {
        for(int r = 0; r < rounds; r++) {
            float scale = 0.5f + (r & 31) / 64f;
            for(int i = 0; i < holder.items.length; i++) {
                holder.items[i].update(scale);
                holder.items[i].pack();
            }
            published = holder;
        }
    }

    static void fields(Item[] items, int rounds) {
        for(int r = 0; r < rounds; r++) {
            float scale = 0.5f + (r & 31) / 64f;
            for(Item item : items) item.update(scale);
            published = items;
        }
    }

    static void lookup(Item[] items, int rounds) {
        for(int r = 0; r < rounds; r++) {
            for(Item item : items) {
                item.angle += 0.25f;
                item.lookup();
            }
            published = items;
        }
    }

    static void pack(Item[] items, int rounds) {
        for(int r = 0; r < rounds; r++) {
            float scale = 0.5f + (r & 31) / 64f;
            for(Item item : items) {
                item.scale = scale;
                item.pack();
            }
            published = items;
        }
    }

    static void pipeline(Item[] items, float[] output, int rounds) {
        for(int r = 0; r < rounds; r++) {
            float scale = 0.5f + (r & 31) / 64f;
            for(int i = 0; i < items.length; i++) {
                Item item = items[i];
                item.update(scale);
                item.lookup();
                item.pack();
                System.arraycopy(item.values, 0, output, i * 24, 24);
            }
            published = output;
        }
    }

    static long mix(long hash, float value) { return hash * 31 + Float.floatToRawIntBits(value); }

    static void sample(String operation, int size, int rounds, boolean report) {
        Item[] items = new Item[size];
        float[] output = new float[size * 24];
        for(int i = 0; i < size; i++) {
            Item item = new Item();
            item.angle = (i & 255) * 0.5f;
            item.x = (i & 31) * 0.25f;
            item.y = (i & 15) * -0.5f;
            item.lookup();
            items[i] = item;
        }
        Holder holder = new Holder(items);
        long start = System.nanoTime();
        switch(operation) {
            case "fields": fields(items, rounds); break;
            case "lookup": lookup(items, rounds); break;
            case "pack": pack(items, rounds); break;
            case "pipeline": pipeline(items, output, rounds); break;
            case "repeat-local": repeatedLocal(items, rounds); break;
            case "repeat-field": repeatedField(holder, rounds); break;
            default: throw new IllegalArgumentException(operation);
        }
        long elapsed = System.nanoTime() - start;
        long hash = 1;
        for(Item item : items) {
            hash = mix(hash, item.angle);
            hash = mix(hash, item.scale);
            hash = mix(hash, item.x);
            hash = mix(hash, item.y);
            hash = mix(hash, item.first);
            hash = mix(hash, item.second);
            for(float value : item.values) hash = mix(hash, value);
        }
        for(float value : output) hash = mix(hash, value);
        published = null;
        if(report) System.out.println("BENCH," + operation + "," + size + "," + rounds + "," + elapsed + "," + hash);
    }

    public static void main(String[] args) {
        String operation = args[0];
        int size = Integer.parseInt(args[1]), rounds = Integer.parseInt(args[2]), samples = Integer.parseInt(args[3]);
        if(size < 1 || rounds < 1 || samples < 1) throw new IllegalArgumentException();
        for(int i = 0; i < 3; i++) sample(operation, size, rounds, false);
        for(int i = 0; i < samples; i++) sample(operation, size, rounds, true);
    }
}

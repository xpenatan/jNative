public class LazyRootRegions {
    static class Box { int[] values; Box(int seed) { values = new int[80]; values[0] = seed; } }
    static int order;
    static Box from, to;
    static class Target {
        int[] values;
        int stamp;
        Target(int seed) { values = new int[80]; values[0] = seed; stamp = seed + 3; }
        final int apply(int[] source, int[] target, int[] unused, int count, boolean cold) {
            int[] derived = values;
            values = null;
            if (cold) { System.gc(); derived[0]++; }
            int i = 0;
            while (i < count) { System.gc(); derived[0] += source[0]; i++; }
            System.arraycopy(source, 0, target, 0, count);
            return derived[0] + stamp + source[0] + target[0];
        }
    }
    static class ReceiverHolder {
        Target target;
        ReceiverHolder(int seed) { target = new Target(seed); }
    }
    static Target detachReceiver(ReceiverHolder holder) {
        Target result = holder.target; holder.target = null; return result;
    }
    static int terminalInstance(ReceiverHolder receiver, Box source, Box target, Box unused,
                                int count, boolean cold) {
        return detachReceiver(receiver).apply(detach(source), detach(target), detach(unused), count, cold);
    }
    static class FirstUse {
        static int value = initialize();
        static int initialize() { System.gc(); return 17; }
        static int apply(int[] source, int[] target, int count) {
            System.arraycopy(source, 0, target, 0, count);
            return source[0] + target[0] + value;
        }
    }
    static class FailedFirstUse {
        static int value = initialize();
        static int initialize() { System.gc(); throw new IllegalStateException("lazy first use"); }
        static int apply(int[] source, int[] target, int count) {
            System.arraycopy(source, 0, target, 0, count);
            return source[0] + target[0] + value;
        }
    }
    static int terminalFirstUse(Box source, Box target, int count) {
        return FirstUse.apply(detach(source), detach(target), count);
    }
    static int terminalFailure(Box source, Box target, int count) {
        return FailedFirstUse.apply(detach(source), detach(target), count);
    }
    static int cycleA(int[] values, int count) { return cycleB(values, count); }
    static int cycleB(int[] values, int count) { if (count > 0) return cycleA(values, count - 1); return values[0]; }

    static int loops(Box holder, int count, boolean cold) {
        int[] values = holder.values;
        holder.values = null;
        if (cold) System.gc();
        int i = 0;
        while (i < count) { System.gc(); values[0]++; i++; }
        return values[0];
    }
    static int twoLoops(Box holder, int count) {
        int[] values = holder.values;
        holder.values = null;
        int i = 0;
        while (i < count) { values[0]++; i++; }
        i = 0;
        while (i < count) { System.gc(); values[0]++; i++; }
        return values[0];
    }
    static int mutation(Box first, Box second, int count) {
        int[] a = first.values, b = second.values;
        first.values = null; second.values = null;
        int[] selected = a;
        int i = 0;
        while (i < count) { System.gc(); selected = b; i++; }
        selected = a;
        if (count != 0) System.gc();
        selected = b;
        if (count != 0) System.gc();
        return selected[0] + a[0];
    }
    static int copy(Box source, Box target, int count, boolean cold) {
        if (cold) System.gc();
        System.arraycopy(detach(source), 0, detach(target), 0, count);
        return count;
    }
    static int[] detach(Box holder) { int[] result = holder.values; holder.values = null; return result; }
    static int coldLastUse(Box holder, int[] source, int[] target, int count, boolean cold) {
        int[] derived = holder.values;
        holder.values = null;
        int result = 0;
        if (cold) { result = derived[0]; derived = null; System.gc(); }
        System.arraycopy(source, 0, target, 0, count);
        return result;
    }
    static int loopLastUse(Box holder, int count) {
        int[] derived = holder.values;
        holder.values = null;
        int i = 0, result = 0;
        while (i < count) { result += derived[0]; derived = null; System.gc(); i++; }
        return result;
    }
    static int scalarLoop(int count) {
        int i = 0, result = 0;
        while (i < count) { result += i; i++; }
        return result;
    }
    static int nestedFrontier(Box first, Box second, int[] source, int[] target, int count,
                              boolean outer, boolean cold) {
        int[] selected = detach(first), other = detach(second);
        int result = 0;
        if (outer) { System.gc(); selected = other; }
        else { if (cold) { result = selected[0]; selected = other; System.gc(); } }
        System.arraycopy(source, 0, target, 0, count);
        if (cold) System.gc();
        return result + selected[0];
    }
    static int rollback(Box holder, int[] source, int[] target, int count, boolean outer, boolean cold) {
        int[] values = detach(holder);
        if (outer) {
            if (cold) System.gc();
            System.arraycopy(source, 0, target, 0, count);
            System.gc();
        }
        System.arraycopy(source, 0, target, 0, count);
        return values[0];
    }
    static int duplicatedTail(Box firstBox, Box secondBox, int[] source, int[] target, int count,
                              boolean first, boolean second, boolean cold) {
        int[] selected = detach(firstBox), other = detach(secondBox);
        int result = 0;
        if (first || second) {
            if (cold) { result = selected[0]; selected = null; System.gc(); selected = other; }
            result++;
        }
        System.arraycopy(source, 0, target, 0, count);
        return result + (selected == null ? 0 : selected[0]);
    }
    static int largeFallback(int[] values, boolean outer, boolean cold, int count) {
        int result = 0;
        if (outer) {
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            result += values[0];
            if (cold) System.gc();
        }
        int i = 0;
        while (i < count) { values[0]++; i++; }
        return result;
    }
    static void checkedCopy(int[] a, int from, int[] b, int to, int count) {
        System.arraycopy(a, from, b, to, count);
    }
    static int[] nextSource() { order = order * 10 + 1; int[] result = from.values; from.values = null; return result; }
    static int sourceOffset() { order = order * 10 + 2; return 0; }
    static int[] nextTarget() { order = order * 10 + 3; int[] result = to.values; to.values = null; return result; }
    static int targetOffset() { order = order * 10 + 4; return 0; }
    static int copyCount(int count) { order = order * 10 + 5; return count; }
    static int orderedCopy(int count) {
        System.arraycopy(nextSource(), sourceOffset(), nextTarget(), targetOffset(), copyCount(count));
        return order;
    }
    static int recursive(int[] values, int depth) { if (depth > 0) return recursive(values, depth - 1); return values[0]; }
    static int nested(int[] values, int count) {
        int i = 0; while (i < count) { int j = 0; while (j < count) { System.gc(); values[0]++; j++; } i++; }
        return values[0];
    }
    static int caught(int[] values, int count) {
        try { int i = 0; while (i < count) { System.gc(); i++; } }
        catch (RuntimeException error) { System.gc(); }
        return values[0];
    }
    static int monitor(int[] values, int count) {
        synchronized (values) { int i = 0; while (i < count) { System.gc(); i++; } }
        return values[0];
    }
    static void error(int[] a, int from, int[] b, int to, int count) {
        try { checkedCopy(a, from, b, to, count); System.out.println("ok"); }
        catch (RuntimeException failure) { System.out.println(failure.getClass().getName()); }
    }
    static void wrongKind(Object a, Object b) {
        try { System.arraycopy(a, 0, b, 0, 1); System.out.println("kind-ok"); }
        catch (RuntimeException failure) { System.out.println(failure.getClass().getName()); }
    }
    public static void main(String[] args) {
        for (int count : new int[]{0, 1, 3, 65}) {
            System.out.println(loops(new Box(10), count, false));
            System.out.println(loops(new Box(20), count, true));
            System.out.println(twoLoops(new Box(30), count));
            System.out.println(mutation(new Box(40), new Box(50), count));
        }
        for (int count : new int[]{-1, 0, 64, 65}) {
            try { System.out.println(copy(new Box(60), new Box(70), count, false)); }
            catch (RuntimeException failure) { System.out.println(failure.getClass().getName()); }
            try { System.out.println(copy(new Box(80), new Box(90), count, true)); }
            catch (RuntimeException failure) { System.out.println(failure.getClass().getName()); }
            from = new Box(100); to = new Box(110); order = 0;
            try { System.out.println(orderedCopy(count)); }
            catch (RuntimeException failure) { System.out.println(failure.getClass().getName()); }
            System.out.println(order);
        }
        int[] values = new int[80];
        for (int i = 0; i < values.length; i++) values[i] = i;
        checkedCopy(values, 0, values, 1, 64);
        checkedCopy(values, 1, values, 0, 65);
        System.out.println(values[0] + values[63] + values[64] + values[65]);
        for (int count : new int[]{-1, 0, 64, 65}) {
            error(null, -1, null, -1, count);
            error(values, -1, values, 0, count);
            error(values, 0, values, 80, count);
            error(values, 81, values, 0, count);
        }
        wrongKind(new byte[80], new int[80]);
        wrongKind(new Object[80], new int[80]);
        wrongKind(null, new int[80]);
        System.out.println(coldLastUse(new Box(121), values, values, 0, true));
        System.out.println(coldLastUse(new Box(122), values, values, 0, false));
        System.out.println(loopLastUse(new Box(123), 0));
        System.out.println(loopLastUse(new Box(124), 1));
        System.out.println(scalarLoop(0) + scalarLoop(1) + scalarLoop(65));
        for (int count : new int[]{0, 1, 65}) {
            for (boolean cold : new boolean[]{false, true}) {
                System.out.println(nestedFrontier(new Box(210), new Box(220), values, values, count, false, cold));
                System.out.println(nestedFrontier(new Box(230), new Box(240), values, values, count, true, cold));
                System.out.println(rollback(new Box(250), values, values, count, true, cold));
                System.out.println(rollback(new Box(260), values, values, count, false, cold));
                System.out.println(duplicatedTail(new Box(270), new Box(280), values, values, count, true, false, cold));
                System.out.println(duplicatedTail(new Box(290), new Box(300), values, values, count, false, true, cold));
                System.out.println(duplicatedTail(new Box(310), new Box(320), values, values, count, false, false, cold));
            }
        }
        System.out.println(largeFallback(values, true, true, 0));
        System.out.println(largeFallback(values, true, false, 1));
        System.out.println(largeFallback(values, false, false, 65));
        for (int count : new int[]{0, 1, 65}) {
            System.out.println(terminalInstance(new ReceiverHolder(130), new Box(2), new Box(3), new Box(4), count, false));
            System.out.println(terminalInstance(new ReceiverHolder(140), new Box(5), new Box(6), new Box(7), count, true));
        }
        System.out.println(terminalFirstUse(new Box(150), new Box(160), 65));
        System.out.println(terminalFirstUse(new Box(170), new Box(180), 0));
        for (int attempt = 0; attempt < 2; attempt++) {
            try { System.out.println(terminalFailure(new Box(190), new Box(200), 65)); }
            catch (Throwable failure) { System.out.println(failure.getClass().getName()); }
        }
        System.out.println(cycleA(values, 3));
        System.out.println(recursive(values, 3) + nested(values, 2) + caught(values, 2) + monitor(values, 2));
    }
}

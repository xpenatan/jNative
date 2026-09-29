package vendor;

/** An ordinary dependency whose private state is retained by a method substitution. */
public class Counter {
    private int value;

    public int add(int amount) {
        value += amount;
        return value;
    }

    public int unchanged() {
        return 42;
    }
}

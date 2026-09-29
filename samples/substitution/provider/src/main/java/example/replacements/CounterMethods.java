package example.replacements;

import com.github.xpenatan.jnative.substitution.*;
import vendor.Counter;

public class CounterMethods {
    @SubstituteMethod(owner = "vendor.Counter", name = "add", descriptor = "(I)I")
    public static int add(Counter self, int amount) {
        int afterOriginal = originalAdd(self, amount);
        writeValue(self, afterOriginal + amount);
        return readValue(self);
    }

    @OriginalMethod(owner = "vendor.Counter", name = "add", descriptor = "(I)I")
    public static native int originalAdd(Counter self, int amount);

    @TargetField(owner = "vendor.Counter", name = "value", descriptor = "I", access = FieldAccess.GET)
    public static native int readValue(Counter self);

    @TargetField(owner = "vendor.Counter", name = "value", descriptor = "I", access = FieldAccess.SET)
    public static native void writeValue(Counter self, int value);
}

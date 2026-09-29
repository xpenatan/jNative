package java.util.stream;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

import java.util.Objects;
import java.util.function.IntPredicate;

@NativeInclude("jn_classlib_drivers.hpp")
public final class IntStream implements AutoCloseable {
    @NativeImport(value = "jnative::stream_all_match", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/lang/CharSequence.length()I", "java/lang/CharSequence.charAt(I)C",
                    "java/util/function/IntPredicate.test(I)Z"},
            callbackReceivers = {0, 0, 1},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE,
                    NativeImport.Invocation.INTERFACE})
    private static native boolean allMatchNative(CharSequence text, IntPredicate predicate);
    private final CharSequence text;
    private boolean consumed;

    public IntStream(CharSequence text) {
        this.text = Objects.requireNonNull(text);
    }

    public boolean allMatch(IntPredicate predicate) {
        Objects.requireNonNull(predicate);
        if(consumed) throw new IllegalStateException("Stream already operated upon or closed");
        consumed = true;
        return allMatchNative(text, predicate);
    }

    public void close() {
        consumed = true;
    }
}

package java.nio;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

@NativeInclude("jn_charsets.hpp")
public final class CharBuffer implements CharSequence {
    @NativeImport(value = "jnative::char_sequence_range", managed = true, runtimeOnly = true, callbacksSynchronous = true,
            callbacks = {"java/lang/CharSequence.charAt(I)C"},
            callbackKinds = {NativeImport.Invocation.INTERFACE})
    private static native String range(CharSequence text, int from, int to);

    private final CharSequence text;
    private final int start, end;

    private CharBuffer(CharSequence text, int start, int end) {
        if(start < 0 || end < start || end > text.length()) throw new IndexOutOfBoundsException();
        this.text = text;
        this.start = start;
        this.end = end;
    }

    public static CharBuffer wrap(CharSequence text) {
        return new CharBuffer(text, 0, text.length());
    }

    public int length() {
        return end - start;
    }

    public char charAt(int index) {
        if(index < 0 || index >= length()) throw new IndexOutOfBoundsException();
        return text.charAt(start + index);
    }

    public CharBuffer subSequence(int first, int last) {
        if(first < 0 || last < first || last > length()) throw new IndexOutOfBoundsException();
        return new CharBuffer(text, start + first, start + last);
    }

    public String toString() {
        if(text instanceof String) return ((String)text).substring(start, end);
        if(text instanceof StringBuilder) return ((StringBuilder)text).substring(start, end);
        if(text instanceof StringBuffer) return ((StringBuffer)text).subSequence(start, end).toString();
        return range(text, start, end);
    }
}

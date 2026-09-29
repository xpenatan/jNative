package com.github.xpenatan.jnative.samples.portability;

import com.github.xpenatan.jnative.interop.NativeExport;
import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

@NativeInclude("portable.h")
public final class PortableApplication {
    @NativeExport("portable_java_twice")
    public static int twice(int value) {
        return value * 2;
    }

    @NativeImport("portable_native_callback")
    public static int nativeCallback(int value) {
        return twice(value);
    }

    public static void main(String[] args) throws Exception {
        Player[] players = {new Player("Player Ω"), new Player("Guest")};
        Thread worker = new Thread(() -> players[0].score = nativeCallback(21));
        worker.start();
        worker.join();
        var score = Player.class.getField("score");
        if(score.getInt(players[0]) != 42)
            throw new IllegalStateException("Callback result mismatch");
        score.setInt(players[1], 7);
        var describe = Player.class.getMethod("describe");
        System.out.println(describe.invoke(players[0]));
        System.out.println(describe.invoke(players[1]));
        System.out.println("Arguments: " + args.length);
    }
}

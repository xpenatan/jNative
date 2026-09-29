package com.github.xpenatan.jnative.samples.diagnostics;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

/**
 * Native player/launcher example: local reports and manual attachment.
 */
@NativeInclude("jnative_imports.h")
public final class Demo {
    private Demo() {
    }

    @NativeImport("demo_action")
    private static int action(int mode) {
        return 0;
    }

    public static void main(String[] args) throws Exception {
        if(args.length == 0 || args[0].equals("reports")) {
            action(0);
        }
        else if(args[0].equals("crash")) {
            action(1);
        }
        else if(args[0].equals("worker")) {
            Thread worker = new Thread(() -> action(1), "render");
            worker.start();
            worker.join();
        }
        else if(args[0].equals("exception")) {
            try {
                action(2);
            } catch(RuntimeException error) {
                error.printStackTrace();
            }
        }
        else if(args[0].equals("export")) {
            action(3);
        }
        else {
            System.out.println("Commands: reports, crash, worker, exception, export");
        }
    }
}

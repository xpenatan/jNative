package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LazyWrapperCachesTest {
    @TempDir Path directory;

    @Test void primitiveOperationsDoNotReachBoxingCachesOrInstanceDispatch() throws Exception {
        Path source = directory.resolve("PrimitiveNumbers.java");
        Files.writeString(source, """
                public class PrimitiveNumbers {
                    public static void main(String[] args) {
                        System.out.println(Integer.parseInt("42") + Long.parseLong("17")
                                + Byte.hashCode((byte)3) + Short.compare((short)4, (short)5)
                                + Character.toUpperCase('a'));
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "PrimitiveNumbers", directory.resolve("out"),
                null, null, "primitive-numbers", BuildType.RELEASE, false);
        var program = new BytecodeCompiler().compile(request);
        var initialization = new ClassInitializationEffects(program);
        for (String name : List.of("Integer", "Long", "Byte", "Short", "Character")) {
            String owner = "java/lang/" + name;
            assertTrue(initialization.empty(owner), owner);
            assertFalse(program.instantiatedClasses().contains(owner), owner);
            assertTrue(program.methods().keySet().stream().noneMatch(method ->
                    method.owner().endsWith("/" + name + "$Cache")), owner);
        }
    }
}

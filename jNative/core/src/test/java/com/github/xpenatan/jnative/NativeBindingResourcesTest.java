package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.compiler.ClassLibrary;
import com.github.xpenatan.jnative.compiler.NativeBinding;
import com.github.xpenatan.jnative.interop.NativeImport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.*;

class NativeBindingResourcesTest {
    @TempDir Path temporary;

    @Test void includesMustBeAvailableAndAllClasslibBindingHeadersArePackaged() throws Exception {
        Path source = temporary.resolve("Imported.java");
        Files.writeString(source, """
                import com.github.xpenatan.jnative.interop.*;
                @NativeInclude("application_native.h")
                public class Imported {
                    @NativeImport("application_value") public static native int value();
                    public static void main(String[] args) { System.out.println(value()); }
                }
                """);
        Path classes = Files.createDirectories(temporary.resolve("classes"));
        Path annotations = Path.of(NativeImport.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-cp", annotations.toString(), "-d", classes.toString(), source.toString()));
        var builder = NativeBuilder.create().classpath(classes).mainClass("Imported")
                .buildRoot(temporary.resolve("out"));
        var error = assertThrows(CompilerException.class, builder::generate);
        assertTrue(error.getMessage().contains("NativeInclude header is not packaged: application_native.h"));
        Path header = temporary.resolve("application_native.h");
        Files.writeString(header, "#pragma once\nextern \"C\" int application_value();\n");
        var generated = builder.nativeFile(header).generate();
        Path project = generated.request().buildRoot().resolve("native");
        assertEquals(Files.readString(header), Files.readString(project.resolve("user/application_native.h")));
        for(String name : ClassLibrary.classes()) {
            var owner = new ClassNode();
            new ClassReader(ClassLibrary.read(name)).accept(owner, ClassReader.SKIP_CODE);
            for(var method : owner.methods) {
                var binding = NativeBinding.read(owner, method);
                if(binding != null)
                    assertTrue(Files.isRegularFile(project.resolve("runtime").resolve(binding.include())),
                            "Missing resource for " + name + "." + method.name + method.desc + ": " + binding.include());
            }
        }
    }
}

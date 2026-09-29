package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.SourceLayout;
import com.github.xpenatan.jnative.compiler.Program;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

class CppNamesTest {
    @ParameterizedTest @EnumSource(SourceLayout.class)
    void longProviderPackagesKeepLogicalNamesAndDistinctPortableSourcePaths(SourceLayout layout) {
        String first = "example/very/lengthy/provider/package/with/many/segments/nativeimpl/replacements/PortableList";
        String second = first.replace("example/", "another/");
        var classes = new LinkedHashMap<String, ClassNode>();
        for(String owner : new String[]{first, second, "app/Main"}) {
            var node = new ClassNode(); node.name = owner;
            classes.put(owner, node);
        }
        var program = new Program(new Program.MethodId("app/Main", "main", "([Ljava/lang/String;)V"), classes, Map.of(), Set.of());
        var names = new CppNames(program, layout);
        assertTrue(names.path(first).length() <= 56, names.path(first));
        assertNotEquals(names.path(first), names.path(second));
        assertEquals("::generated::" + first.replace("/", "::"), names.className(first));
        assertEquals(layout == SourceLayout.PACKAGE_FILENAME ? "classes/app.Main" : "classes/app/Main", names.path("app/Main"));
        assertEquals(names.path(first), new CppNames(program, layout).path(first));
    }
}

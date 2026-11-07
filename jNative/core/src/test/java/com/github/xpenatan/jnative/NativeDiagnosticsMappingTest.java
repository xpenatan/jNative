package com.github.xpenatan.jnative;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDiagnosticsMappingTest {
    @Test
    void sharedInlineDefinitionsKeepTheirOwnVerifiedClassAndRanges() throws Exception {
        var first = new LinkedHashMap<>(mapping("game.First", "::generated::game::First", 10, 20));
        var second = new LinkedHashMap<>(mapping("game.Second", "::generated::game::Second", 30, 40));
        first.put("cppFile", "java_inline.hpp");
        second.put("cppFile", "java_inline.hpp");
        var sourceMap = Map.<String, Object>of("classes", List.of(), "inlineClasses", List.of(first, second));
        var selected = annotate(sourceMap, "src/java_inline.hpp", "hash", 35, "??");
        assertEquals("game.Second", selected.get("javaClass"));
        assertEquals("::generated::game::Second::apply", selected.get("function"));
        assertFalse(annotate(sourceMap, "src/java_inline.hpp", "hash", 25, "??").containsKey("javaClass"));
        var stale = annotate(sourceMap, "src/java_inline.hpp", "edited", 35, "??");
        assertEquals("stale-after-native-edit", stale.get("javaMappingStatus"));
        assertFalse(stale.containsKey("javaClass"));
    }

    @Test
    void sharedSourcesSelectVerifiedMethodOwnerAndNeverGuessAfterEdits() throws Exception {
        var parent = mapping("game.Parent", "::generated::game::Parent", 10, 20);
        var lambda = mapping("game.Parent$jNativeLambda$0", "::generated::game::Parent::Lambda0", 30, 40);
        var sourceMap = Map.<String, Object>of("classes", List.of(parent, lambda));
        var selected = annotate(sourceMap, "hash", 35, "??");
        assertEquals(lambda.get("javaClass"), selected.get("javaClass"));
        assertEquals("::generated::game::Parent::Lambda0::apply", selected.get("function"));
        assertEquals("verified-generated-cpp-range", selected.get("functionSource"));
        assertEquals(parent.get("javaClass"), annotate(sourceMap, "hash", 15, "known").get("javaClass"));
        assertFalse(annotate(sourceMap, "hash", 25, "??").containsKey("javaClass"));
        var stale = annotate(sourceMap, "edited", 35, "??");
        assertEquals("stale-after-native-edit", stale.get("javaMappingStatus"));
        assertFalse(stale.containsKey("javaClass"));
        assertEquals("??", stale.get("function"));
        assertEquals(parent.get("javaClass"),
                annotate(Map.of("classes", List.of(parent)), "edited", 15, "??").get("javaClass"));
    }

    private static Map<String, Object> mapping(String javaClass, String cppClass, int start, int end) {
        return Map.of("javaClass", javaClass, "cppClass", cppClass, "cppFile", "classes/game/Parent.cpp",
                "sha256", "hash", "methods", List.of(Map.of("cppMethod", "apply",
                        "cppLineStart", start, "cppLineEnd", end)));
    }

    private static Map<String, Object> annotate(Map<String, Object> sourceMap, String hash,
            int line, String function) throws Exception {
        return annotate(sourceMap, "src/classes/game/Parent.cpp", hash, line, function);
    }

    private static Map<String, Object> annotate(Map<String, Object> sourceMap, String source, String hash,
            int line, String function) throws Exception {
        var location = new LinkedHashMap<String, Object>();
        location.put("location", source + ":" + line + ":1");
        location.put("function", function);
        var annotate = NativeDiagnostics.class.getDeclaredMethod("annotateLocation", Map.class, Map.class, Map.class);
        annotate.setAccessible(true);
        annotate.invoke(null, location, sourceMap, Map.of(source, hash));
        return location;
    }
}

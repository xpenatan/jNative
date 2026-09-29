package com.github.xpenatan.jnative;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDiagnosticsMappingTest {
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
        var location = new LinkedHashMap<String, Object>();
        location.put("location", "src/classes/game/Parent.cpp:" + line + ":1");
        location.put("function", function);
        var annotate = NativeDiagnostics.class.getDeclaredMethod("annotateLocation", Map.class, Map.class, Map.class);
        annotate.setAccessible(true);
        annotate.invoke(null, location, sourceMap, Map.of("src/classes/game/Parent.cpp", hash));
        return location;
    }
}

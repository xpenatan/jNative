package com.github.xpenatan.jnative.internal;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.CompilerException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFilesTest {
    @TempDir
    Path temporary;

    @Test
    void regenerationPreservesUnchangedTimestampsAndProtectsEdits() throws Exception {
        Path project = temporary.resolve("native");
        Path header = project.resolve("src/application.hpp");
        Path source = project.resolve("src/application.cpp");
        byte[] declarations = "// declarations\n".getBytes(StandardCharsets.UTF_8);
        byte[] original = "// version one\n".getBytes(StandardCharsets.UTF_8);
        var content = Map.of(header, declarations, source, original);
        ProjectFiles.installOwned(project, content);
        FileTime recorded = FileTime.fromMillis(1_000_000);
        Files.setLastModifiedTime(header, recorded);
        Files.setLastModifiedTime(source, recorded);
        Path manifest = project.resolve("jnative.manifest");
        Files.setLastModifiedTime(manifest, recorded);

        ProjectFiles.installOwned(project, content);
        assertEquals(recorded, Files.getLastModifiedTime(header));
        assertEquals(recorded, Files.getLastModifiedTime(source));
        assertEquals(recorded, Files.getLastModifiedTime(manifest));

        ProjectFiles.installOwned(
                project,
                Map.of(
                        header,
                        declarations,
                        source,
                        "// version two\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals(recorded, Files.getLastModifiedTime(header));
        assertEquals("// version two\n", Files.readString(source));
        Files.writeString(source, "// user edit\n");
        assertThrows(CompilerException.class, () -> ProjectFiles.installOwned(project, content));
        assertEquals("// user edit\n", Files.readString(source));
    }

    @Test
    void unchangedSurvivorsDoNotPreventRemovingObsoleteSources() throws Exception {
        Path project = temporary.resolve("native");
        Path kept = project.resolve("kept.cpp");
        Path obsolete = project.resolve("obsolete.cpp");
        byte[] bytes = "// source\n".getBytes(StandardCharsets.UTF_8);
        ProjectFiles.installOwned(project, Map.of(kept, bytes, obsolete, bytes));
        ProjectFiles.installOwned(project, Map.of(kept, bytes));
        assertTrue(Files.exists(kept));
        assertFalse(Files.exists(obsolete));
        assertNull(
                ProjectFiles.read(project.resolve("jnative.manifest")).getProperty("obsolete.cpp"));
    }
}

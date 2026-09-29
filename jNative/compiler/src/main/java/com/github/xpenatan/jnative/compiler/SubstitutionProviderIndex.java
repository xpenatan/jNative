package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.CompilerException;
import com.github.xpenatan.jnative.internal.Json;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Strict versioned descriptor reader for processor-generated and manual provider indexes. */
record SubstitutionProviderIndex(String id, SubstitutionArtifact artifact, List<String> declarations, String hash) {
    static SubstitutionProviderIndex read(SubstitutionArtifact artifact) {
        var bytes = artifact.read(SubstitutionArtifact.INDEX);
        if(bytes == null) throw error("Provider index missing: " + artifact.path());
        try {
            var json = Json.object(Json.read(new String(bytes.content(), StandardCharsets.UTF_8)));
            if(!(json.get("schemaVersion") instanceof Number version) || !version.toString().equals("1"))
                throw error("Unsupported substitution schemaVersion: " + json.get("schemaVersion") + " in " + artifact.path());
            if(!(json.get("providerId") instanceof String id) || !id.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*"))
                throw error("Invalid substitution providerId in " + artifact.path());
            if(!json.keySet().equals(Set.of("schemaVersion", "providerId", "declarations")))
                throw error("Unknown/missing provider index properties in " + artifact.path());
            var declarations = new LinkedHashSet<String>();
            for(Object value : Json.array(json.get("declarations"))) {
                if(!(value instanceof String name) || name.contains("/") || name.isBlank())
                    throw error("Invalid indexed binary declaration: " + value + " in " + artifact.path());
                SubstitutionArtifact.validateOwner(name.replace('.', '/'));
                if(!declarations.add(name.replace('.', '/'))) throw error("Duplicate indexed declaration: " + name + " in " + artifact.path());
            }
            return new SubstitutionProviderIndex(id, artifact, declarations.stream().sorted().toList(), bytes.sha256());
        } catch(IllegalArgumentException | ClassCastException failure) {
            throw new CompilerException("JN4020 Invalid substitution index in " + artifact.path() + ": " + failure.getMessage(), failure);
        }
    }

    static SubstitutionProviderIndex builtin() {
        try {
            var descriptors = ClassLibrary.class.getClassLoader().getResources(SubstitutionArtifact.INDEX);
            var matches = new ArrayList<SubstitutionProviderIndex>();
            var artifacts = new LinkedHashSet<Path>();
            while(descriptors.hasMoreElements()) {
                var url = descriptors.nextElement();
                Path path;
                if(url.getProtocol().equals("jar")) {
                    var connection = (JarURLConnection)url.openConnection();
                    connection.setUseCaches(false);
                    path = Path.of(connection.getJarFileURL().toURI());
                }
                else if(url.getProtocol().equals("file")) {
                    path = Path.of(url.toURI());
                    path = path.getParent().getParent().getParent();
                }
                else throw error("Unsupported bundled substitution artifact protocol: " + url);
                if(!artifacts.add(path.toAbsolutePath().normalize())) continue;
                var index = read(new SubstitutionArtifact(path));
                if(index.id().equals("jnative.builtin")) matches.add(index);
            }
            if(matches.size() != 1) throw error("Expected one jnative.builtin artifact descriptor, found " + matches.size());
            return matches.getFirst();
        } catch(IOException | URISyntaxException failure) {
            throw new CompilerException("JN4020 Cannot locate bundled substitution artifact", failure);
        }
    }

    private static CompilerException error(String message) { return new CompilerException("JN4020 " + message); }
}

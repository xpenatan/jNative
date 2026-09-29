package com.github.xpenatan.jnative.compiler;

import java.util.List;
import java.util.Map;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/** Remaps typed bytecode and recognized structured metadata, preserving ordinary string literals. */
final class SubstitutionRemapper {
    private final SimpleRemapper remapper;

    SubstitutionRemapper(Map<String, String> aliases) { remapper = new SimpleRemapper(aliases); }

    String owner(String name) { return name.startsWith("[") ? remapper.mapDesc(name) : remapper.mapType(name); }
    String descriptor(String descriptor) {
        return descriptor.startsWith("(") ? remapper.mapMethodDesc(descriptor) : remapper.mapDesc(descriptor);
    }

    ClassNode remap(ClassNode raw) {
        var result = new ClassNode();
        raw.accept(new ClassRemapper(result, remapper));
        annotations(result.visibleAnnotations);
        annotations(result.invisibleAnnotations);
        for(var method : result.methods) {
            annotations(method.visibleAnnotations);
            annotations(method.invisibleAnnotations);
        }
        return result;
    }

    private void annotations(List<AnnotationNode> annotations) {
        if(annotations == null) return;
        for(var annotation : annotations) {
            if(annotation.values == null) continue;
            for(int i = 0; i < annotation.values.size(); i += 2) {
                String key = (String)annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                if(annotation.desc.equals("Lcom/github/xpenatan/jnative/interop/NativeImport;")) {
                    if(List.of("callbacks", "targets", "fields", "staticFields", "types").contains(key)) {
                        @SuppressWarnings("unchecked") var values = (List<Object>)value;
                        for(int item = 0; item < values.size(); item++) values.set(item, metadata((String)values.get(item), key));
                    }
                }
                else if(annotation.desc.startsWith("Lcom/github/xpenatan/jnative/substitution/")) {
                    if(key.equals("owner") || annotation.desc.endsWith("/SubstituteClass;") && key.equals("value"))
                        annotation.values.set(i + 1, owner(((String)value).replace('.', '/')).replace('/', '.'));
                    else if(key.equals("descriptor")) annotation.values.set(i + 1, descriptor((String)value));
                    else if(value instanceof List<?> nested) {
                        for(Object entry : nested) if(entry instanceof AnnotationNode child) annotations(List.of(child));
                    }
                }
            }
        }
    }

    private String metadata(String value, String key) {
        if(key.equals("types")) return owner(value);
        int start = key.equals("fields") || key.equals("staticFields") ? value.indexOf(':') : value.indexOf('(');
        int separator = value.lastIndexOf('.', start);
        if(start < 0 || separator <= 0) return value; // NativeBinding provides the diagnostic for malformed metadata.
        String target = owner(value.substring(0, separator));
        if(value.charAt(start) == ':')
            return target + value.substring(separator, start + 1) + descriptor(value.substring(start + 1));
        return target + value.substring(separator, start) + descriptor(value.substring(start));
    }
}

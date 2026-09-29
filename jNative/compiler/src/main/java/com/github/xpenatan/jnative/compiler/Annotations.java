package com.github.xpenatan.jnative.compiler;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.tree.AnnotationNode;

public final class Annotations {
    private Annotations() {
    }

    public static Object property(List<AnnotationNode> annotations, String simpleName, String property) {
        if(annotations == null) return null;
        String descriptor = "Lcom/github/xpenatan/jnative/interop/" + simpleName + ";";
        for(AnnotationNode annotation : annotations) {
            if(!annotation.desc.equals(descriptor) || annotation.values == null) continue;
            for(int i = 0; i < annotation.values.size(); i += 2)
                if(annotation.values.get(i).equals(property)) return annotation.values.get(i + 1);
        }
        return null;
    }

    public static List<AnnotationNode> all(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        var result = new ArrayList<AnnotationNode>();
        if(visible != null) result.addAll(visible);
        if(invisible != null) result.addAll(invisible);
        return result;
    }

    public static String value(List<AnnotationNode> annotations, String simpleName) {
        if(annotations == null) return null;
        String descriptor = "Lcom/github/xpenatan/jnative/interop/" + simpleName + ";";
        for(AnnotationNode annotation : annotations) {
            if(annotation.desc.equals(descriptor) && annotation.values != null)
                for(int i = 0; i < annotation.values.size(); i += 2)
                    if(annotation.values.get(i).equals("value"))
                        return (String)annotation.values.get(i + 1);
        }
        return null;
    }

    public static boolean flag(
            List<AnnotationNode> annotations, String simpleName, String property) {
        if(annotations == null) return false;
        String descriptor = "Lcom/github/xpenatan/jnative/interop/" + simpleName + ";";
        for(AnnotationNode annotation : annotations) {
            if(!annotation.desc.equals(descriptor) || annotation.values == null) continue;
            for(int i = 0; i < annotation.values.size(); i += 2)
                if(annotation.values.get(i).equals(property))
                    return Boolean.TRUE.equals(annotation.values.get(i + 1));
        }
        return false;
    }
}

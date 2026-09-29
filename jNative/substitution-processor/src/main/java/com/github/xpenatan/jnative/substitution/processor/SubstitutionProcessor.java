package com.github.xpenatan.jnative.substitution.processor;

import com.github.xpenatan.jnative.substitution.*;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

/** Generates an aggregating provider index. Metadata remains in the class annotations. */
@SupportedAnnotationTypes("com.github.xpenatan.jnative.substitution.*")
@SupportedOptions("jnative.substitutionProvider")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class SubstitutionProcessor extends AbstractProcessor {
    public static final String INDEX = "META-INF/jnative/substitutions.json";
    private final Map<String, TypeElement> declarations = new TreeMap<>();
    private final Map<String, Element> rules = new HashMap<>();
    private boolean invalid;
    private boolean written;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if(round.processingOver()) {
            if(!written && !invalid && !round.errorRaised() && !declarations.isEmpty()) writeIndex();
            return true;
        }
        for(Element root : round.getRootElements()) inspect(root);
        return true;
    }

    private void inspect(Element element) {
        boolean annotated = false;
        SubstituteClass classRule = element.getAnnotation(SubstituteClass.class);
        if(classRule != null) {
            annotated = true;
            if(validName(classRule.value(), element)) unique("class:" + classRule.value(), element);
        }
        SubstituteMethod[] methods = element.getAnnotationsByType(SubstituteMethod.class);
        for(SubstituteMethod rule : methods) {
            annotated = true;
            validateMethod(rule.owner(), rule.name(), rule.descriptor(), element);
            unique("method:" + rule.owner() + "." + rule.name() + rule.descriptor(), element);
            if(!element.getModifiers().contains(Modifier.STATIC)) error(element, "SubstituteMethod helpers must be static");
            if(element.getModifiers().contains(Modifier.SYNCHRONIZED)) error(element, "SubstituteMethod helpers must not be synchronized");
        }
        TargetField field = element.getAnnotation(TargetField.class);
        OriginalMethod original = element.getAnnotation(OriginalMethod.class);
        if(field != null || original != null) {
            annotated = true;
            if(!element.getModifiers().containsAll(Set.of(Modifier.STATIC, Modifier.NATIVE)))
                error(element, "TargetField and OriginalMethod aliases must be static native declarations without Java bodies");
            long imports = element.getAnnotationMirrors().stream()
                    .filter(annotation -> annotation.getAnnotationType().toString().equals("com.github.xpenatan.jnative.interop.NativeImport")).count();
            if((field != null && original != null) || imports != 0)
                error(element, "A native alias must have exactly one executable binding annotation");
            if(field != null) {
                validName(field.owner(), element);
                if(field.name().isBlank() || field.name().matches(".*[.;/\\[<>\\s].*")) error(element, "Invalid field name: " + field.name());
                validDescriptor(field.descriptor(), false, element);
            }
            if(original != null) validateMethod(original.owner(), original.name(), original.descriptor(), element);
        }
        if(annotated) {
            Element owner = element;
            while(!(owner instanceof TypeElement) && owner != null) owner = owner.getEnclosingElement();
            if(owner instanceof TypeElement type) declarations.put(processingEnv.getElementUtils().getBinaryName(type).toString(), type);
        }
        for(Element child : element.getEnclosedElements()) inspect(child);
    }

    private void unique(String rule, Element element) {
        if(rules.putIfAbsent(rule, element) != null) error(element, "Duplicate substitution definition: " + rule);
    }

    private void validateMethod(String owner, String name, String descriptor, Element element) {
        validName(owner, element);
        if(name.equals("<init>") || name.equals("<clinit>"))
            error(element, "Constructor and initializer substitutions require a complete class replacement");
        else if(name.isBlank() || name.matches(".*[.;/\\[<>\\s].*")) error(element, "Invalid method name: " + name);
        validDescriptor(descriptor, true, element);
    }

    private boolean validName(String value, Element element) {
        boolean valid = !value.isEmpty();
        for(String part : value.split("\\.", -1)) {
            valid &= !part.isEmpty() && SourceVersion.isIdentifier(part);
        }
        if(!valid) error(element, "Expected a binary class name using dots: " + value);
        return valid;
    }

    private void validDescriptor(String value, boolean method, Element element) {
        try {
            int offset = 0;
            if(method) {
                if(value.charAt(offset++) != '(') throw new IllegalArgumentException();
                int slots = 0;
                while(value.charAt(offset) != ')') {
                    char kind = value.charAt(offset);
                    offset = typeEnd(value, offset, false);
                    slots += kind == 'J' || kind == 'D' ? 2 : 1;
                }
                if(slots > 255) throw new IllegalArgumentException();
                offset = typeEnd(value, offset + 1, true);
            }
            else offset = typeEnd(value, offset, false);
            if(offset != value.length()) throw new IllegalArgumentException();
        } catch(IllegalArgumentException | IndexOutOfBoundsException failure) {
            error(element, "Invalid JVM " + (method ? "method" : "field") + " descriptor: " + value);
        }
    }

    private int typeEnd(String value, int offset, boolean allowVoid) {
        char kind = value.charAt(offset++);
        if(kind == 'V' && allowVoid || "BCDFIJSZ".indexOf(kind) >= 0) return offset;
        if(kind == '[') {
            int start = offset - 1;
            while(value.charAt(offset) == '[') offset++;
            if(offset - start > 255) throw new IllegalArgumentException();
            return typeEnd(value, offset, false);
        }
        if(kind == 'L') {
            int end = value.indexOf(';', offset);
            if(end < 0) throw new IllegalArgumentException();
            String internal = value.substring(offset, end);
            if(internal.contains(".")) throw new IllegalArgumentException();
            for(String part : internal.split("/", -1)) if(!SourceVersion.isIdentifier(part)) throw new IllegalArgumentException();
            return end + 1;
        }
        throw new IllegalArgumentException();
    }

    private void writeIndex() {
        String id = processingEnv.getOptions().get("jnative.substitutionProvider");
        if(id == null || !id.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")) {
            error(null, "Required processor option: -Ajnative.substitutionProvider=<provider-id>");
            return;
        }
        written = true;
        try(Writer writer = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", INDEX,
                declarations.values().toArray(new Element[0])).openWriter()) {
            writer.write("{\n  \"schemaVersion\": 1,\n  \"providerId\": \"" + id + "\",\n  \"declarations\": [\n");
            boolean first = true;
            for(String declaration : declarations.keySet()) {
                if(!first) writer.write(",\n");
                writer.write("    \"" + declaration + "\"");
                first = false;
            }
            writer.write("\n  ]\n}\n");
        } catch(IOException failure) {
            error(null, "Cannot write substitution provider index (use the processor or a manual index, not both): " + failure.getMessage());
        }
    }

    private void error(Element element, String message) {
        invalid = true;
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}

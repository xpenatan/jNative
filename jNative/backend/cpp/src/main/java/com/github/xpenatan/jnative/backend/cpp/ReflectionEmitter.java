package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.CompilerException;
import com.github.xpenatan.jnative.compiler.*;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static com.github.xpenatan.jnative.backend.cpp.CppEmitter.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Emits readable adapters separately from application method bodies.
 */
final class ReflectionEmitter {
    private final Program program;
    private final StringBuilder out =
            new StringBuilder(
                    "#include \"application.hpp\"\n\nnamespace generated {\nnamespace {\n");
    private final Map<MethodId, String> invokers = new LinkedHashMap<>();
    private final Map<ReflectionPlan.FieldId, String> accessors = new LinkedHashMap<>();
    private final CppNames names;

    ReflectionEmitter(Program program, CppNames names) {
        this.program = program;
        this.names = names;
    }

    private String className(String owner) {
        return names.className(owner);
    }

    private String fieldName(String owner, String name, String descriptor) {
        return names.field(owner, name, descriptor);
    }

    private void line(String value) {
        out.append(value).append('\n');
    }

    String emit() {
        for(var id : program.reflection().fields()) {
            String name = "reflect_field_" + accessors.size();
            accessors.put(id, name);
            ClassNode owner = program.classes().get(id.owner());
            FieldNode field =
                    owner.fields.stream()
                            .filter(f -> f.name.equals(id.name()) && f.desc.equals(id.descriptor()))
                            .findFirst()
                            .orElseThrow();
            Type type = Type.getType(id.descriptor());
            String slot =
                    (field.access & ACC_STATIC) != 0
                            ? className(id.owner()) + "::"
                            : "static_cast<" + className(id.owner()) + "*>(receiver)->";
            slot += fieldName(id.owner(), id.name(), id.descriptor());
            line("// " + comment(id.owner().replace('/', '.') + "." + id.name()));
            line(
                    "::jnative::ReflectionValue "
                            + name
                            + "_get(::jnative::Object* receiver) { return "
                            + pack(type, slot + ".get()")
                            + "; }");
            if((field.access & ACC_FINAL) == 0)
                line(
                        "void "
                                + name
                                + "_set(::jnative::Object* receiver, ::jnative::ReflectionValue value) { "
                                + slot
                                + ".set("
                                + unpack(type, "value")
                                + "); }");
        }
        for(MethodId id : program.reflection().methods()) {
            String name = "reflect_call_" + invokers.size();
            invokers.put(id, name);
            ClassNode owner =
                    RuntimeLibrary.platform(id.owner())
                            ? ReflectionPlan.platformClass(id.owner())
                            : program.classes().get(id.owner());
            MethodNode member =
                    owner.methods.stream()
                            .filter(m -> m.name.equals(id.name()) && m.desc.equals(id.descriptor()))
                            .findFirst()
                            .orElseThrow();
            boolean constructor = id.name().equals("<init>"),
                    isStatic = (member.access & ACC_STATIC) != 0;
            line("// " + comment(id.toString()));
            line(
                    "::jnative::ReflectionValue "
                            + name
                            + "(::jnative::Object* receiver, const std::vector<::jnative::ReflectionValue>& arguments) {");
            if(constructor && (owner.access & (ACC_ABSTRACT | ACC_INTERFACE)) != 0) {
                line("    ::jnative::raise(\"java/lang/InstantiationException\");");
            }
            else {
                var args = new ArrayList<String>();
                Type[] types = Type.getArgumentTypes(id.descriptor());
                for(int i = 0; i < types.length; ++i)
                    args.add(unpack(types[i], "arguments[" + i + "]"));
                if(constructor) {
                    line(
                            "    ::jnative::LocalRoot<> instance(::jnative::allocate<"
                                    + className(id.owner())
                                    + ">());");
                    args.addFirst("instance.get()");
                }
                else if(!isStatic) args.addFirst("receiver");
                String invocation =
                        (constructor || isStatic
                                ? symbol(RuntimeLibrary.platform(id.owner()) ? PlatformBindings.find(id).helper() : id)
                                : dispatchName(id))
                                + "("
                                + String.join(", ", args)
                                + ")";
                Type result = Type.getReturnType(id.descriptor());
                if(result.equals(Type.VOID_TYPE)) {
                    line("    " + invocation + ";");
                    line(
                            "    return "
                                    + (constructor
                                    ? "::jnative::ReflectionValue::reference(instance.get())"
                                    : "{}")
                                    + ";");
                }
                else line("    return " + pack(result, invocation) + ";");
            }
            line("}");
        }
        if(!program.reflection().classes().isEmpty()) emitBoxing();
        var classes = new LinkedHashMap<>(program.classes());
        for(String name : program.reflection().classes())
            if(RuntimeLibrary.platform(name))
                classes.put(name, ReflectionPlan.platformClass(name));
        int registeredTypes = 0;
        // Bound temporary metadata storage even when debug compilers retain every stack slot.
        for(ClassNode node : classes.values()) {
            if(registeredTypes % 64 == 0) {
                if(registeredTypes != 0) line("}");
                line("void register_reflection_types_" + registeredTypes / 64 + "() {");
            }
            var enumValues = new MethodId(node.name, "values", "()[L" + node.name + ";");
            if((node.access & ACC_ENUM) != 0 && program.methods().containsKey(enumValues))
                line(
                        "    ::jnative::register_enum_values("
                                + quote(node.name)
                                + ", &"
                                + symbol(enumValues)
                                + ");");
            boolean metadata = program.reflection().classes().contains(node.name);
            int modifiers =
                    node.access
                            & (ACC_PUBLIC
                            | ACC_FINAL
                            | ACC_INTERFACE
                            | ACC_ABSTRACT
                            | ACC_SYNTHETIC
                            | ACC_ANNOTATION
                            | ACC_ENUM);
            for(InnerClassNode inner : node.innerClasses)
                if(node.name.equals(inner.name)) modifiers = inner.access;
            line(
                    "    ::jnative::register_reflection_type({"
                            + quote(node.name)
                            + ", "
                            + quote(node.superName == null ? "" : node.superName)
                            + ", {"
                            + String.join(
                            ", ", node.interfaces.stream().map(CppEmitter::quote).toList())
                            + "}, "
                            + modifiers
                            + ", "
                            + metadata
                            + ", "
                            + (RuntimeLibrary.platform(node.name)
                            ? "nullptr"
                            : "&" + initializer(node.name))
                            + ", {");
            if(metadata)
                for(FieldNode field : node.fields)
                    if((field.access & ACC_PUBLIC) != 0) {
                        var id = new ReflectionPlan.FieldId(node.name, field.name, field.desc);
                        String adapter = accessors.get(id);
                        line(
                                "        {"
                                        + quote(node.name)
                                        + ", "
                                        + quote(field.name)
                                        + ", "
                                        + quote(field.desc)
                                        + ", "
                                        + field.access
                                        + ", "
                                        + (adapter == null ? "nullptr" : "&" + adapter + "_get")
                                        + ", "
                                        + (adapter == null || (field.access & ACC_FINAL) != 0
                                        ? "nullptr"
                                        : "&" + adapter + "_set")
                                        + "},");
                    }
            line("    }, {");
            if(metadata)
                for(MethodNode method : node.methods)
                    if((method.access & ACC_PUBLIC) != 0 && !method.name.equals("<clinit>")) {
                        String adapter =
                                invokers.get(new MethodId(node.name, method.name, method.desc));
                        line(
                                "        {"
                                        + quote(node.name)
                                        + ", "
                                        + quote(method.name)
                                        + ", "
                                        + quote(method.desc)
                                        + ", "
                                        + (method.access & 0xffff)
                                        + ", "
                                        + (adapter == null ? "nullptr" : "&" + adapter)
                                        + "},");
                    }
            line("    }, " + (metadata || RuntimeLibrary.primitive(node.name) != null) + "});");
            registeredTypes++;
        }
        if(registeredTypes != 0) line("}");
        line("}");
        line("void initialize_reflection() {");
        for(int batch = 0; batch * 64 < registeredTypes; batch++)
            line("    register_reflection_types_" + batch + "();");
        if(!program.reflection().classes().isEmpty())
            line(
                    "    ::jnative::register_reflection_boxing(&box_reflection_value, &unbox_reflection_value);");
        line("}\n}");
        return out.toString();
    }

    private void emitBoxing() {
        line("::jnative::Object* box_reflection_value(::jnative::ReflectionValue value) {");
        line("    switch (value.kind) {");
        for(String descriptor : List.of("Z", "B", "S", "C", "I", "J", "F", "D")) {
            String owner = RuntimeLibrary.wrapper(descriptor);
            MethodId factory =
                    new MethodId(owner, "valueOf", "(" + descriptor + ")L" + owner + ";");
            line(
                    "        case '"
                            + descriptor
                            + "': return "
                            + symbol(factory)
                            + "("
                            + unpack(Type.getType(descriptor), "value")
                            + ");");
        }
        line("        default: throw std::logic_error(\"Invalid reflection primitive\");");
        line("    }\n}");
        line(
                "bool unbox_reflection_value(::jnative::Object* object, ::jnative::ReflectionValue& value) {");
        for(String descriptor : List.of("Z", "B", "S", "C", "I", "J", "F", "D")) {
            String owner = RuntimeLibrary.wrapper(descriptor);
            line(
                    "    if (auto boxed = dynamic_cast<"
                            + className(owner)
                            + "*>(object)) { value = "
                            + pack(
                            Type.getType(descriptor),
                            "boxed->" + fieldName(owner, "value", descriptor) + ".get()")
                            + "; return true; }");
        }
        line("    return false;\n}");
    }

    private static String pack(Type type, String value) {
        if(reference(type)) return "::jnative::ReflectionValue::reference(" + value + ")";
        if(type.equals(Type.FLOAT_TYPE) || type.equals(Type.DOUBLE_TYPE))
            return "::jnative::ReflectionValue::decimal('"
                    + type.getDescriptor()
                    + "', "
                    + value
                    + ")";
        return "::jnative::ReflectionValue::integral('"
                + type.getDescriptor()
                + "', "
                + value
                + ")";
    }

    private static String unpack(Type type, String value) {
        if(reference(type)) return value + ".object";
        return "static_cast<"
                + type(type)
                + ">("
                + value
                + (type.equals(Type.FLOAT_TYPE) || type.equals(Type.DOUBLE_TYPE)
                ? ".floating)"
                : ".integer)");
    }

}

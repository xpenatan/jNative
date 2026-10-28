package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.compiler.InitializationOrder;
import com.github.xpenatan.jnative.compiler.Program;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Proves that initialization, including inherited default interfaces, does no work. */
final class ClassInitializationEffects {
    private final Program program;
    private final Map<String, Boolean> empty = new HashMap<>();

    ClassInitializationEffects(Program program) {
        this.program = program;
        for (String owner : program.classes().keySet()) inspect(owner, new HashSet<>());
    }

    boolean empty(String owner) {
        return empty.getOrDefault(owner, false);
    }

    private boolean inspect(String owner, Set<String> active) {
        Boolean known = empty.get(owner);
        if (known != null) return known;
        var type = program.classes().get(owner);
        // These intrinsic bases both lower to jnative::Object and have no Java
        // initialization body. Other absent superclass definitions stay unknown.
        if (type == null) return (owner.equals("java/lang/Object") || owner.equals("java/lang/Number"))
                && program.platform(owner);
        if (!active.add(owner)) return false;
        boolean result = type.methods.stream().noneMatch(method -> method.name.equals("<clinit>"))
                && type.fields.stream().noneMatch(field -> field.value instanceof String);
        if (result && type.superName != null) result = inspect(type.superName, active);
        if (result) {
            for (var face : InitializationOrder.defaultInterfaces(type, program.classes()::get, program::platform)) {
                if (!inspect(face.name, active)) { result = false; break; }
            }
        }
        active.remove(owner);
        empty.put(owner, result);
        return result;
    }
}

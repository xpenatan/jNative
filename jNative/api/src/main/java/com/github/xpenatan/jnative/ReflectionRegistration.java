package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.BuildPaths;
import java.util.Objects;

/**
 * A build-time request to preserve public reflection metadata and optional access adapters.
 * Method and constructor selectors use JVM descriptors, for example {@code (I)Ljava/lang/String;}.
 * Field selectors use their Java name. Registration never loads a class in the host JVM.
 *
 * @param className  binary class name
 * @param kind       scope of the registration
 * @param name       method/field name, or an empty string for class and constructor registrations
 * @param descriptor JVM method descriptor, or an empty string for class and field registrations
 */
public record ReflectionRegistration(String className, Kind kind, String name, String descriptor) {
    /**
     * Public metadata is retained for the selected type and its ancestors in every mode.
     */
    public enum Kind {
        METADATA,
        PUBLIC_MEMBERS,
        METHOD,
        CONSTRUCTOR,
        FIELD
    }

    public ReflectionRegistration {
        className = BuildPaths.text(className, "reflection class");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        if(kind == Kind.METHOD || kind == Kind.FIELD)
            name = BuildPaths.text(name, "reflection member");
        else if(!name.isEmpty())
            throw new IllegalArgumentException("Unexpected reflection member name");
        if(kind == Kind.METHOD || kind == Kind.CONSTRUCTOR)
            descriptor = BuildPaths.text(descriptor, "reflection descriptor");
        else if(!descriptor.isEmpty())
            throw new IllegalArgumentException("Unexpected reflection descriptor");
    }
}

package com.github.xpenatan.jnative.samples.substitution;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuilder;
import java.nio.file.Path;

public class BuildSubstitution {
    public static void main(String[] args) {
        var builder = NativeBuilder.create().classpathFromCurrentJvm()
                .substitutionPath(Path.of(System.getProperty("jnative.provider")))
                .mainClass(SubstitutionExample.class.getName())
                .buildRoot(Path.of(System.getProperty("jnative.buildRoot")))
                .buildType(BuildType.valueOf(System.getProperty("jnative.buildType", "DEBUG")))
                .targetFileName("substitution");
        if(args.length > 0 && args[0].equals("build")) builder.build();
        else builder.generate();
    }
}

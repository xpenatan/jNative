package com.github.xpenatan.jnative.samples.portability;

import com.github.xpenatan.jnative.*;
import java.nio.file.Path;

public final class BuildPortability {
    public static void main(String[] args) {
        var builder =
                NativeBuilder.create()
                        .classpathFromCurrentJvm()
                        .mainClass(PortableApplication.class.getName())
                        .reflectClass(Player.class.getName())
                        .exportClass(PortableApplication.class.getName())
                        .buildRoot(Path.of(System.getProperty("jnative.buildRoot", "build")))
                        .targetFileName("portability")
                        .buildType(
                                BuildType.valueOf(System.getProperty("jnative.buildType", "DEBUG")))
                        .nativeFile(Path.of("src/main/cpp/portable.h"))
                        .nativeFile(Path.of("src/main/cpp/portable.cpp"))
                        .cmakeBuildArgs("--parallel", "2")
                        .log(BuildLog.console());
        String standard = System.getProperty("jnative.cppStandard");
        if(standard != null) builder.cmakeDefine("CMAKE_CXX_STANDARD", standard);
        if(args.length > 0 && args[0].equals("generate")) builder.generate();
        else System.out.println(builder.build().artifact().path());
    }
}

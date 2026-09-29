plugins {
    java
}

dependencies {
    implementation(project(":jNative:core"))
}

val builderMainClass = "com.github.xpenatan.jnative.samples.nativeinterop.BuildNativeInterop"
val nativeBuildType = providers.gradleProperty("nativeBuildType").orElse("DEBUG")

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the C/C++ interop sample builder."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
}

tasks.register<JavaExec>("generate_native") {
    group = "jnative"
    description = "Generate the C/C++ interop sample."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
    args("generate")
}

tasks.register<JavaExec>("build_native") {
    group = "jnative"
    description = "Generate and compile the C/C++ interop sample."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
    args("build")
}

tasks.register<JavaExec>("run_jvm") {
    group = "jnative"
    description = "Run the Java reference implementations of the native functions."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.samples.nativeinterop.NativeInterop")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    workingDir(projectDir)
}

tasks.register<Exec>("run_native") {
    group = "jnative"
    description = "Build and run the C/C++ interop executable."
    dependsOn("build_native")
    workingDir(projectDir)
    val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
    val configuration = nativeBuildType.get().lowercase()
    commandLine(layout.buildDirectory.file("native/$configuration/native-interop$suffix").get().asFile)
}

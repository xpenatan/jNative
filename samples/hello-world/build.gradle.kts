plugins {
    java
}

dependencies {
    implementation(project(":jNative:core"))
}

val builderMainClass = "com.github.xpenatan.jnative.samples.helloworld.BuildHelloWorld"
val nativeBuildType = providers.gradleProperty("nativeBuildType").orElse("DEBUG")

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the hello-world sample builder."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
}

tasks.register<JavaExec>("generate_native") {
    group = "jnative"
    description = "Generates C++ sources and a standalone CMake project."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
    args("generate")
}

tasks.register<JavaExec>("build_native") {
    group = "jnative"
    description = "Generates and compiles the native hello-world executable."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
    args("build")
}

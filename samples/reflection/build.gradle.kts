plugins {
    java
}

dependencies {
    implementation(project(":jNative:core"))
}

val builderMainClass = "com.github.xpenatan.jnative.samples.reflection.BuildReflection"
val nativeBuildType = providers.gradleProperty("nativeBuildType").orElse("DEBUG")

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the reflection sample builder."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
}

tasks.register<JavaExec>("generate_native") {
    group = "jnative"
    description = "Generate the reflection sample."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
    args("generate")
}

tasks.register<JavaExec>("build_native") {
    group = "jnative"
    description = "Generate and compile the reflection sample."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
    args("build")
}

tasks.register<JavaExec>("run_jvm") {
    group = "jnative"
    description = "Run the reflection sample on the JVM."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.samples.reflection.ReflectionExample")
    workingDir(projectDir)
}

tasks.register<Exec>("run_native") {
    group = "jnative"
    description = "Generate, compile and run reflection in a native executable."
    dependsOn("build_native")
    workingDir(projectDir)
    val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
    val configuration = nativeBuildType.get().lowercase()
    commandLine(layout.buildDirectory.file("native/$configuration/reflection$suffix").get().asFile)
}

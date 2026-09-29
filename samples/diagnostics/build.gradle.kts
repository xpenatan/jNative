plugins {
    java
}

dependencies {
    implementation(project(":jNative:core"))
}

val builderMainClass = "com.github.xpenatan.jnative.samples.diagnostics.BuildDemo"
val nativeBuildType = providers.gradleProperty("nativeBuildType").orElse("DEBUG")

tasks.register<JavaExec>("generate_native") {
    group = "jnative"
    description = "Generate the native diagnostics example."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
    args("generate")
}

tasks.register<JavaExec>("build_native") {
    group = "jnative"
    description = "Compile the diagnostics example."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
    args("build")
}

tasks.register<JavaExec>("prepare_release") {
    group = "jnative"
    description = "Build Release and verify its permanent private archive."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.archiveStore", providers.gradleProperty("nativeArchiveStore").orElse("").get())
    args("release")
}

plugins {
    java
}

dependencies {
    implementation(project(":jNative:core"))
    implementation(project(":jNative:interop"))
}

val builderMain = "com.github.xpenatan.jnative.samples.portability.BuildPortability"
val nativeBuildType = providers.gradleProperty("nativeBuildType").orElse("DEBUG").map { it.uppercase() }
val configuration = nativeBuildType.map { if (it == "RELEASE") "Release" else "Debug" }
val isWindows = System.getProperty("os.name").startsWith("Windows")
val suffix = if (isWindows) ".exe" else ""
val generator = if (isWindows) "MinGW Makefiles" else "Unix Makefiles"
val hostBuild = layout.buildDirectory.dir(configuration.map { "host/${it.lowercase()}" })

tasks.register<JavaExec>("generate_native") {
    group = "jnative"
    description = "Generate the portable application shared by both launch paths."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMain)
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
    args("generate")
    mustRunAfter("run_native")
}

tasks.register<JavaExec>("build_native") {
    group = "jnative"
    description = "Generate and build the standalone portable application."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMain)
    workingDir(projectDir)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
    if (providers.gradleProperty("cppStandard").isPresent) {
        systemProperty("jnative.cppStandard", providers.gradleProperty("cppStandard").get())
    }
}

tasks.register<Exec>("run_native") {
    group = "jnative"
    description = "Run objects, arrays, reflection, threads and a native callback."
    dependsOn("build_native")
    workingDir(projectDir)
    commandLine(
        layout.buildDirectory.file("native/${configuration.get().lowercase()}/portability$suffix")
            .get().asFile.absolutePath
    )
}

tasks.register<Exec>("configure_host") {
    group = "jnative"
    description = "Configure a native parent project that embeds the generated application."
    dependsOn("generate_native")
    mustRunAfter("run_native")
    commandLine(
        "cmake", "-S", file("src/host").absolutePath, "-B", hostBuild.get().asFile.absolutePath,
        "-G", generator, "-DCMAKE_BUILD_TYPE=${configuration.get()}",
        "-DJNATIVE_PROJECT=${layout.buildDirectory.dir("native").get().asFile.absolutePath}",
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${hostBuild.get().asFile.absolutePath}"
    )
    if (providers.gradleProperty("cppStandard").isPresent) {
        args("-DCMAKE_CXX_STANDARD=${providers.gradleProperty("cppStandard").get()}")
    }
}

tasks.register<Exec>("build_host") {
    group = "jnative"
    description = "Build the native host and reusable application library."
    dependsOn("configure_host")
    commandLine(
        "cmake",
        "--build",
        hostBuild.get().asFile.absolutePath,
        "--config",
        configuration.get(),
        "--parallel",
        "2"
    )
}

tasks.register<Exec>("run_host") {
    group = "jnative"
    description = "Run the native host and verify lifecycle and callback ownership."
    dependsOn("build_host")
    commandLine(hostBuild.get().file("portability_host$suffix").asFile.absolutePath)
}

tasks.named("check") {
    dependsOn("run_native", "run_host")
}

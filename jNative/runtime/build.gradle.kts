plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("jnative-runtime")
}

java {
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "jnative-runtime"
            from(components["java"])
        }
    }
}

tasks.named<Jar>("javadocJar") {
    from("README.md")
}

sourceSets.main {
    resources.srcDir("src/main")
}

val benchmarkSources = sourceSets.create("benchmark")

tasks.register<JavaExec>("benchmarkIsolation") {
    group = "verification"
    description = "Measure one runtime layer at a time, retaining raw timing samples."
    classpath = benchmarkSources.runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.runtime.RuntimeIsolationBenchmark")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        projectDir.absolutePath,
        layout.buildDirectory.dir("isolation").get().asFile.absolutePath,
        providers.gradleProperty("isolationRun").orElse("baseline").get(),
        providers.gradleProperty("isolationCases").orElse(".*").get(),
        providers.gradleProperty("isolationBaseline").orElse("").get(),
        providers.gradleProperty("isolationGenerator").orElse(
            if (System.getProperty("os.name").startsWith("Windows")) "Visual Studio 18 2026" else "Unix Makefiles"
        ).get()
    )
}

val windows = System.getProperty("os.name").lowercase().contains("windows")
val defaultGenerator = if (windows) "MinGW Makefiles" else "Unix Makefiles"
val cmakeGenerator = providers.gradleProperty("runtimeGenerator").orElse(defaultGenerator).get()
val nativeOutput = if (cmakeGenerator == defaultGenerator) "native"
    else "native/" + cmakeGenerator.replace(Regex("[^a-zA-Z0-9]"), "-")
val debugOutput = layout.buildDirectory.dir("$nativeOutput/Debug")
val releaseOutput = layout.buildDirectory.dir("$nativeOutput/Release")

val configureDebug = tasks.register<Exec>("configureDebug") {
    inputs.property("generator", cmakeGenerator)
    inputs.dir("src/main/cpp")
    inputs.dir("src/test/cpp")
    inputs.file("CMakeLists.txt")
    outputs.file(debugOutput.map { it.file("CMakeCache.txt") })
    commandLine(
        "cmake",
        "-S", projectDir,
        "-B", debugOutput.get().asFile,
        "-G", cmakeGenerator,
        "-DCMAKE_BUILD_TYPE=Debug"
    )
}

val compileDebug = tasks.register<Exec>("compileDebug") {
    dependsOn(configureDebug)
    commandLine("cmake", "--build", debugOutput.get().asFile, "--config", "Debug", "--parallel", "2")
}

val testDebug = tasks.register<Exec>("testDebug") {
    dependsOn(compileDebug)
    commandLine(
        "ctest",
        "--test-dir", debugOutput.get().asFile,
        "--build-config", "Debug",
        "--output-on-failure",
        "--timeout", "60"
    )
}

val configureRelease = tasks.register<Exec>("configureRelease") {
    inputs.property("generator", cmakeGenerator)
    inputs.dir("src/main/cpp")
    inputs.dir("src/test/cpp")
    inputs.file("CMakeLists.txt")
    outputs.file(releaseOutput.map { it.file("CMakeCache.txt") })
    commandLine(
        "cmake",
        "-S", projectDir,
        "-B", releaseOutput.get().asFile,
        "-G", cmakeGenerator,
        "-DCMAKE_BUILD_TYPE=Release"
    )
}

val compileRelease = tasks.register<Exec>("compileRelease") {
    dependsOn(configureRelease)
    commandLine("cmake", "--build", releaseOutput.get().asFile, "--config", "Release", "--parallel", "2")
}

val testRelease = tasks.register<Exec>("testRelease") {
    dependsOn(compileRelease)
    commandLine(
        "ctest",
        "--test-dir", releaseOutput.get().asFile,
        "--build-config", "Release",
        "--output-on-failure",
        "--timeout", "60"
    )
}

tasks.register<Exec>("inspectTransferCode") {
    group = "verification"
    description = "Compile independent atomic transfer controls and emit MSVC assembly for inspection."
    dependsOn(configureRelease)
    commandLine(
        "cmake", "--build", releaseOutput.get().asFile,
        "--config", "Release", "--target", "transfer_code_probe", "--parallel", "2"
    )
}

tasks.named("check") {
    dependsOn(testDebug, testRelease)
}

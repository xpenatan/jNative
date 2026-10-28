import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
}

repositories {
    maven {
        url = uri("https://teavm.org/maven/repository")
        content {
            includeGroupByRegex("org\\.teavm.*")
        }
    }
}

dependencies {
    testImplementation(project(":jNative:core"))
    testImplementation(project(":jNative:cli"))
    testImplementation(project(":jNative:compiler"))
    testImplementation(project(":jNative:substitution"))
    testImplementation(project(":samples:native-interop"))
    testImplementation(project(":samples:native-callbacks"))
    testImplementation(libs.junit.jupiter)
    testImplementation("org.teavm:teavm-tooling:0.16.0")
    testRuntimeOnly("org.teavm:teavm-classlib:0.16.0")
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.register<JavaExec>("benchmarkScores") {
    group = "verification"
    description = "Build current jNative and measure matching native benchmark executables."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BenchmarkScores")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        providers.gradleProperty("benchmarkConfig").orElse("").get(),
        layout.buildDirectory.dir("benchmark-scores").get().asFile.absolutePath
    )
}

tasks.register<JavaExec>("prepareLookupComparison") {
    group = "verification"
    description = "Build identical isolated lookup inputs with jNative, GraalVM and TeaVM."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildLookupComparisons")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        providers.gradleProperty("lookupTools").orElse("").get(),
        layout.buildDirectory.dir("lookup-comparison").get().asFile.absolutePath,
        providers.gradleProperty("lookupRun").orElse("baseline").get()
    )
}

tasks.register<JavaExec>("prepareNestedArrayComparison") {
    group = "verification"
    description = "Build ordinary Java array probes with jNative, GraalVM and TeaVM."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildLookupComparisons")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        providers.gradleProperty("lookupTools").orElse("").get(),
        layout.buildDirectory.dir("nested-array-comparison").get().asFile.absolutePath,
        providers.gradleProperty("arrayRun").orElse("baseline").get(),
        "nested-array"
    )
}

tasks.register<JavaExec>("prepareRuntimeCostComparison") {
    group = "verification"
    description = "Build reference-store and NIO probes with jNative, GraalVM and TeaVM."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildLookupComparisons")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        providers.gradleProperty("lookupTools").orElse("").get(),
        layout.buildDirectory.dir("runtime-cost-comparison").get().asFile.absolutePath,
        providers.gradleProperty("runtimeCostRun").orElse("baseline").get(),
        "runtime-cost"
    )
}

tasks.register<JavaExec>("prepareNumericComparison") {
    group = "verification"
    description = "Build library-independent field, lookup and packing probes with jNative, GraalVM and TeaVM."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildLookupComparisons")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        providers.gradleProperty("lookupTools").orElse("").get(),
        layout.buildDirectory.dir("numeric-comparison").get().asFile.absolutePath,
        providers.gradleProperty("numericRun").orElse("baseline").get(),
        "numeric"
    )
}

tasks.register<JavaExec>("prepareTransferComparison") {
    group = "verification"
    description = "Build matching array and typed-buffer transfer controls with jNative, GraalVM and TeaVM."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildLookupComparisons")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        providers.gradleProperty("lookupTools").orElse("").get(),
        layout.buildDirectory.dir("transfer-comparison").get().asFile.absolutePath,
        providers.gradleProperty("transferRun").orElse("baseline").get(),
        "transfer"
    )
}

tasks.register<JavaExec>("prepareTransferCppComparison") {
    group = "verification"
    description = "Build the equivalent C++ array and typed-buffer payload controls."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildMapCppComparison")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        layout.projectDirectory.file("src/test/cpp/transfer_isolation.cpp").asFile.absolutePath,
        layout.buildDirectory.dir("transfer-cpp-comparison").get().asFile.absolutePath,
        providers.gradleProperty("conformanceGenerator").orElse("Visual Studio 18 2026").get(),
        providers.gradleProperty("transferRun").orElse("baseline").get(),
        "transfer"
    )
}

tasks.register<JavaExec>("prepareNumericCppComparison") {
    group = "verification"
    description = "Build the equivalent C++ numeric payload without managed-runtime checks."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildMapCppComparison")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        layout.projectDirectory.file("src/test/cpp/numeric_pipeline_isolation.cpp").asFile.absolutePath,
        layout.buildDirectory.dir("numeric-cpp-comparison").get().asFile.absolutePath,
        providers.gradleProperty("conformanceGenerator").orElse("Visual Studio 18 2026").get(),
        providers.gradleProperty("numericRun").orElse("baseline").get(),
        "numeric"
    )
}

tasks.register<JavaExec>("prepareRuntimeCostCppComparison") {
    group = "verification"
    description = "Build ordinary and atomic C++ storage controls for runtime-cost probes."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildMapCppComparison")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        layout.projectDirectory.file("src/test/cpp/runtime_cost_isolation.cpp").asFile.absolutePath,
        layout.buildDirectory.dir("runtime-cost-cpp-comparison").get().asFile.absolutePath,
        providers.gradleProperty("conformanceGenerator").orElse("Visual Studio 18 2026").get(),
        providers.gradleProperty("runtimeCostRun").orElse("baseline").get(),
        "runtime-cost"
    )
}

tasks.register<JavaExec>("prepareNestedArrayCppComparison") {
    group = "verification"
    description = "Build the C++ counterpart of the nested array copy probes."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildMapCppComparison")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        layout.projectDirectory.file("src/test/cpp/nested_array_isolation.cpp").asFile.absolutePath,
        layout.buildDirectory.dir("nested-array-cpp-comparison").get().asFile.absolutePath,
        providers.gradleProperty("conformanceGenerator").orElse("Visual Studio 18 2026").get(),
        providers.gradleProperty("cppProbeRun").orElse("baseline").get(),
        "nested-array"
    )
}

tasks.register<JavaExec>("prepareMapProbeComparison") {
    group = "verification"
    description = "Build matching hashing and collision probes with jNative, GraalVM and TeaVM."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildLookupComparisons")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        providers.gradleProperty("lookupTools").orElse("").get(),
        layout.buildDirectory.dir("lookup-comparison").get().asFile.absolutePath,
        providers.gradleProperty("lookupRun").orElse("map-probes-baseline").get(),
        "map-probe"
    )
}

tasks.register<JavaExec>("prepareAllocationComparison") {
    group = "verification"
    description = "Build matching allocation and object-lifetime probes with jNative, GraalVM and TeaVM."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildLookupComparisons")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        providers.gradleProperty("lookupTools").orElse("").get(),
        layout.buildDirectory.dir("allocation-comparison").get().asFile.absolutePath,
        providers.gradleProperty("allocationRun").orElse("baseline").get(),
        "allocation"
    )
}

tasks.register<JavaExec>("prepareMapGrowthComparison") {
    group = "verification"
    description = "Build isolated boxing, insertion and resize probes with jNative, GraalVM and TeaVM."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildLookupComparisons")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        providers.gradleProperty("lookupTools").orElse("").get(),
        layout.buildDirectory.dir("map-growth-comparison").get().asFile.absolutePath,
        providers.gradleProperty("mapGrowthRun").orElse("baseline").get(),
        "map-growth"
    )
}

tasks.register<JavaExec>("prepareMapGrowthCppComparison") {
    group = "verification"
    description = "Build the C++ boxing, insertion and resize probe reference."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildMapCppComparison")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        layout.projectDirectory.file("src/test/cpp/map_growth_isolation.cpp").asFile.absolutePath,
        layout.buildDirectory.dir("map-growth-cpp-comparison").get().asFile.absolutePath,
        providers.gradleProperty("conformanceGenerator").orElse("Visual Studio 18 2026").get(),
        providers.gradleProperty("cppProbeRun").orElse("baseline").get(),
        "map-growth"
    )
}

tasks.register<JavaExec>("prepareMapIterationComparison") {
    group = "verification"
    description = "Build retained Java-tool comparators for dense and sparse map iteration."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildLookupComparisons")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        providers.gradleProperty("lookupTools").orElse("").get(),
        layout.buildDirectory.dir("map-iteration-comparison").get().asFile.absolutePath,
        providers.gradleProperty("lookupRun").orElse("baseline").get(),
        "map-iteration"
    )
}

tasks.register<JavaExec>("prepareMapIterationCppComparison") {
    group = "verification"
    description = "Build the C++ dense and sparse map iteration counterpart."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildMapCppComparison")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        projectDir.resolve("src/test/cpp/map_iteration_isolation.cpp").absolutePath,
        layout.buildDirectory.dir("map-iteration-cpp-comparison").get().asFile.absolutePath,
        providers.gradleProperty("conformanceGenerator").orElse("Visual Studio 18 2026").get(),
        providers.gradleProperty("cppRun").orElse("baseline").get(),
        "map-iteration"
    )
}

tasks.register<JavaExec>("prepareAllocationCppComparison") {
    group = "verification"
    description = "Build the C++ allocation and lifetime probe reference."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildMapCppComparison")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        layout.projectDirectory.file("src/test/cpp/allocation_isolation.cpp").asFile.absolutePath,
        layout.buildDirectory.dir("allocation-cpp-comparison").get().asFile.absolutePath,
        providers.gradleProperty("conformanceGenerator").orElse("Visual Studio 18 2026").get(),
        providers.gradleProperty("cppProbeRun").orElse("baseline").get(),
        "allocation"
    )
}

tasks.register<JavaExec>("prepareMapCppComparison") {
    group = "verification"
    description = "Build the C++ hashing and collision probe reference."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildMapCppComparison")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        layout.projectDirectory.file("src/test/cpp/map_probe_isolation.cpp").asFile.absolutePath,
        layout.buildDirectory.dir("map-cpp-comparison").get().asFile.absolutePath,
        providers.gradleProperty("conformanceGenerator").orElse("Visual Studio 18 2026").get(),
        providers.gradleProperty("cppProbeRun").orElse("baseline").get()
    )
}

tasks.register<JavaExec>("prepareGeneralCppComparison") {
    group = "verification"
    description = "Build C++ allocation, collection-growth, compute and string controls."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildMapCppComparison")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        layout.projectDirectory.file("src/test/cpp/general_performance.cpp").asFile.absolutePath,
        layout.buildDirectory.dir("general-cpp-comparison").get().asFile.absolutePath,
        providers.gradleProperty("conformanceGenerator").orElse("Visual Studio 18 2026").get(),
        providers.gradleProperty("cppProbeRun").orElse("baseline").get(),
        "general"
    )
}

tasks.register<JavaExec>("prepareLookupCppComparison") {
    group = "verification"
    description = "Build the C++ counterpart of the original lookup and reference-scan probes."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.conformance.BuildMapCppComparison")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(
        layout.projectDirectory.file("src/test/cpp/lookup_isolation.cpp").asFile.absolutePath,
        layout.buildDirectory.dir("lookup-cpp-comparison").get().asFile.absolutePath,
        providers.gradleProperty("conformanceGenerator").orElse("Visual Studio 18 2026").get(),
        providers.gradleProperty("cppProbeRun").orElse("baseline").get(),
        "lookup"
    )
}

tasks.test {
    val temporaryDirectory = layout.buildDirectory.dir("tmp")
    systemProperty("java.io.tmpdir", temporaryDirectory.get().asFile.absolutePath)
    systemProperty(
        "junit.jupiter.tempdir.factory.default",
        "com.github.xpenatan.jnative.conformance.NativeTempDirectoryFactory"
    )
    doFirst {
        temporaryDirectory.get().asFile.mkdirs()
    }
    testLogging.exceptionFormat = TestExceptionFormat.FULL
    maxParallelForks = providers.gradleProperty("conformanceForks").orElse("1").get().toInt()
    systemProperty("jnative.root", rootProject.projectDir.absolutePath)
    systemProperty(
        "jnative.test.generator",
        providers.gradleProperty("conformanceGenerator").orElse("").get()
    )
    systemProperty(
        "jnative.test.releaseFlags",
        providers.gradleProperty("conformanceReleaseFlags").orElse("").get()
    )
    systemProperty("jnative.linux", providers.gradleProperty("nativeLinux").orElse("false").get())
}

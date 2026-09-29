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

val windows = System.getProperty("os.name").lowercase().contains("windows")
val cmakeGenerator = if (windows) "MinGW Makefiles" else "Unix Makefiles"
val debugOutput = layout.buildDirectory.dir("native/Debug")
val releaseOutput = layout.buildDirectory.dir("native/Release")

val configureDebug = tasks.register<Exec>("configureDebug") {
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
    commandLine("cmake", "--build", debugOutput.get().asFile, "--parallel", "2")
}

val testDebug = tasks.register<Exec>("testDebug") {
    dependsOn(compileDebug)
    commandLine(
        "ctest",
        "--test-dir", debugOutput.get().asFile,
        "--output-on-failure",
        "--timeout", "60"
    )
}

val configureRelease = tasks.register<Exec>("configureRelease") {
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
    commandLine("cmake", "--build", releaseOutput.get().asFile, "--parallel", "2")
}

val testRelease = tasks.register<Exec>("testRelease") {
    dependsOn(compileRelease)
    commandLine(
        "ctest",
        "--test-dir", releaseOutput.get().asFile,
        "--output-on-failure",
        "--timeout", "60"
    )
}

tasks.named("check") {
    dependsOn(testDebug, testRelease)
}

import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
}

dependencies {
    testImplementation(project(":jNative:core"))
    testImplementation(project(":jNative:cli"))
    testImplementation(project(":jNative:compiler"))
    testImplementation(project(":jNative:substitution"))
    testImplementation(project(":samples:native-interop"))
    testImplementation(project(":samples:native-callbacks"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
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

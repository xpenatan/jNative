import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("jnative-compiler")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "jnative-compiler"
            from(components["java"])
        }
    }
}

dependencies {
    implementation(project(":jNative:classlib"))
    api(project(":jNative:api"))
    api(libs.asm.tree)
    api(libs.asm.analysis)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val classlibSourceRoot = project(":jNative:classlib").layout.projectDirectory.dir("src/main/java")

tasks.named<Test>("test") {
    inputs.dir(classlibSourceRoot)
    systemProperty("jnative.classlib.sourceRoot", classlibSourceRoot.asFile.absolutePath)
}

plugins {
    java
    `maven-publish`
}

base {
    archivesName.set("jnative-cli")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "jnative-cli"
            from(components["java"])
        }
    }
}

dependencies {
    implementation(project(":jNative:core"))
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the jNative CLI with dependencies resolved by Gradle."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.cli.Main")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    workingDir(rootProject.projectDir)
}

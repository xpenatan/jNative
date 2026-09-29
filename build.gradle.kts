import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    base
    alias(libs.plugins.easyPublishing)
}

val compilerGroup = libs.versions.jNativeGroup.get()
val compilerRelease = libs.versions.jNativeRelease.get()
val compilerSnapshot = libs.versions.jNativeSnapshot.get()
val javaVersion = libs.versions.java.get().toInt()
val publishedModules = listOf(
    ":jNative:api",
    ":jNative:core",
    ":jNative:compiler",
    ":jNative:backend:cpp",
    ":jNative:runtime",
    ":jNative:toolchain:cmake",
    ":jNative:interop",
    ":jNative:classlib",
    ":jNative:cli"
)

allprojects {
    group = compilerGroup
    version = compilerSnapshot

    repositories {
        mavenCentral()
    }

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
        }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(javaVersion)
            options.encoding = "UTF-8"
        }
        tasks.withType<Javadoc>().configureEach {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).addBooleanOption("-no-fonts", true)
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
        tasks.withType<Jar>().configureEach {
            from(rootProject.file("LICENSE")) {
                into("META-INF")
            }
        }
    }
}

easyPublishing {
    modules(publishedModules)
    groupId.set(compilerGroup)
    releaseVersion.set(compilerRelease)
    snapshotVersion.set(compilerSnapshot)

    snapshotRepositoryUrl.set("https://central.sonatype.com/repository/maven-snapshots/")
    releaseRepositoryUrl.set("https://central.sonatype.com")
    username.set(providers.environmentVariable("CENTRAL_PORTAL_USERNAME"))
    password.set(providers.environmentVariable("CENTRAL_PORTAL_PASSWORD"))
    signingKey.set(providers.environmentVariable("SIGNING_KEY"))
    signingPassword.set(providers.environmentVariable("SIGNING_PASSWORD"))
    automaticRelease.set(
        providers.environmentVariable("CENTRAL_PUBLISHING_TYPE")
            .map { it.equals("AUTOMATIC", ignoreCase = true) }
            .orElse(false)
    )

    pomName.set("jNative")
    pomDescription.set("Java bytecode to C++ generation and native compilation tooling.")
    licenseName.set("Apache License, Version 2.0")
    licenseUrl.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
    projectUrl.set("https://github.com/xpenatan/jNative")
    developerId.set("Xpe")
    developerName.set("Natan")
    scmUrl.set("https://github.com/xpenatan/jNative")
    scmConnection.set("scm:git:https://github.com/xpenatan/jNative.git")
    scmDeveloperConnection.set("scm:git:ssh://git@github.com/xpenatan/jNative.git")
}

tasks.register("printReleaseVersion") {
    group = "publishing"
    description = "Print the release version configured in the version catalog."
    doLast {
        println(compilerRelease)
    }
}

tasks.named("assemble") {
    dependsOn(
        ":jNative:core:assemble",
        ":jNative:cli:assemble",
        ":samples:hello-world:assemble",
        ":samples:native-interop:assemble",
        ":samples:native-callbacks:assemble",
        ":samples:reflection:assemble",
        ":samples:diagnostics:assemble",
        ":samples:portability:assemble"
    )
}

tasks.named("check") {
    dependsOn(
        ":jNative:api:check",
        ":jNative:core:check",
        ":jNative:runtime:check",
        ":jNative:compiler:check",
        ":tests:conformance:check",
        ":samples:hello-world:check",
        ":samples:native-interop:check",
        ":samples:native-callbacks:check",
        ":samples:reflection:check",
        ":samples:diagnostics:check",
        ":samples:portability:check"
    )
}

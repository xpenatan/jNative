plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("jnative-core")
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(project(":jNative:api"))
    api(project(":jNative:interop"))
    implementation(project(":jNative:compiler"))
    implementation(project(":jNative:backend:cpp"))
    implementation(project(":jNative:toolchain:cmake"))
    implementation(project(":jNative:runtime"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "jnative-core"
            from(components["java"])
        }
    }
}

plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("jnative-substitution-processor")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "jnative-substitution-processor"
            from(components["java"])
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    implementation(project(":jNative:substitution"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

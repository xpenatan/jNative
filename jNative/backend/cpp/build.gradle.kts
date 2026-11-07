plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("jnative-backend-cpp")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "jnative-backend-cpp"
            from(components["java"])
        }
    }
}

dependencies {
    api(project(":jNative:compiler"))
    testImplementation(project(":jNative:interop"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

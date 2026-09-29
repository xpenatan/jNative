plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("jnative-toolchain-cmake")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "jnative-toolchain-cmake"
            from(components["java"])
        }
    }
}

dependencies {
    api(project(":jNative:api"))
}

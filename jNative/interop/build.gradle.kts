plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("jnative-interop")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "jnative-interop"
            from(components["java"])
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

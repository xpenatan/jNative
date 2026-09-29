plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("jnative-substitution")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "jnative-substitution"
            from(components["java"])
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("jnative-api")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "jnative-api"
            from(components["java"])
        }
    }
}

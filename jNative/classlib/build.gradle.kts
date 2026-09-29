plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("jnative-classlib")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "jnative-classlib"
            from(components["java"])
        }
    }
}

tasks.named<Jar>("javadocJar") {
    from("README.md")
}

tasks.named<Javadoc>("javadoc") {
    setSource(files())
}

dependencies {
    compileOnly(project(":jNative:interop"))
    compileOnly(project(":jNative:substitution"))
    annotationProcessor(project(":jNative:substitution-processor"))
}

val compileJava = tasks.named<JavaCompile>("compileJava") {
    options.release.set(17)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Ajnative.substitutionProvider=jnative.builtin")
}

tasks.register("compileLibrary") {
    group = "build"
    description = "Compile the replacement Java class library."
    dependsOn(compileJava)
}

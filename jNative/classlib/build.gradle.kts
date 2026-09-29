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

sourceSets.named("main") {
    // Replacement classes are packaged only as compiler resources under classlib/.
    (output.classesDirs as ConfigurableFileCollection).setFrom(files())
}

dependencies {
    compileOnly(project(":jNative:interop"))
}

val compileJava = tasks.named<JavaCompile>("compileJava") {
    destinationDirectory.set(layout.buildDirectory.dir("classlib-classes"))
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(null as Int?)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf("--patch-module", "java.base=" + file("src/main/java").absolutePath,
            "--add-reads", "java.base=ALL-UNNAMED")
    )
}

tasks.register("compileLibrary") {
    group = "build"
    description = "Compile the replacement Java class library."
    dependsOn(compileJava)
}

val indexLibrary = tasks.register("indexLibrary") {
    dependsOn(compileJava)
    inputs.dir(compileJava.flatMap { it.destinationDirectory })
    outputs.file(layout.buildDirectory.file("classlib-index/classes.list"))
    doLast {
        val root = compileJava.get().destinationDirectory.get().asFile
        val names = root.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map { it.relativeTo(root).invariantSeparatorsPath.removeSuffix(".class") }
            .sorted()
            .toList()
        val index = layout.buildDirectory.file("classlib-index/classes.list").get().asFile
        index.parentFile.mkdirs()
        index.writeText(names.joinToString("\n", postfix = "\n"))
    }
}

tasks.processResources {
    from(compileJava.flatMap { it.destinationDirectory }) {
        into("classlib")
    }
    from(indexLibrary) {
        into("classlib")
    }
}

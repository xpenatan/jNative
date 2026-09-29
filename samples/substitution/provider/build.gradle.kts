plugins {
    `java-library`
}

dependencies {
    compileOnly(project(":samples:substitution:library"))
    compileOnly(project(":jNative:substitution"))
    annotationProcessor(project(":jNative:substitution-processor"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.compilerArgs.add("-Ajnative.substitutionProvider=sample.counter")
}

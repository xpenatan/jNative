plugins {
    java
}

dependencies {
    implementation(project(":jNative:core"))
    implementation(project(":samples:substitution:library"))
}

evaluationDependsOn(":samples:substitution:provider")
val providerJar = project(":samples:substitution:provider").tasks.named<Jar>("jar")
val builderMainClass = "com.github.xpenatan.jnative.samples.substitution.BuildSubstitution"
val nativeBuildType = providers.gradleProperty("nativeBuildType").orElse("DEBUG")

tasks.named("assemble") {
    dependsOn(providerJar)
}

tasks.register<JavaExec>("generate_native") {
    group = "jnative"
    description = "Generate the substitution sample from the unchanged library and provider JARs."
    dependsOn(providerJar)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    workingDir(projectDir)
    systemProperty("jnative.provider", providerJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
    args("generate")
}

tasks.register<JavaExec>("build_native") {
    group = "jnative"
    description = "Generate and compile the substitution sample."
    dependsOn(providerJar)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(builderMainClass)
    workingDir(projectDir)
    systemProperty("jnative.provider", providerJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("jnative.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("jnative.buildType", nativeBuildType.get())
    args("build")
}

tasks.register<JavaExec>("run_jvm") {
    group = "jnative"
    description = "Run the unchanged dependency's JVM behavior."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.github.xpenatan.jnative.samples.substitution.SubstitutionExample")
    workingDir(projectDir)
}

tasks.register<Exec>("run_native") {
    group = "jnative"
    description = "Build and run the selected native substitutions."
    dependsOn("build_native")
    workingDir(projectDir)
    val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
    val configuration = nativeBuildType.get().lowercase()
    commandLine(layout.buildDirectory.file("native/$configuration/substitution$suffix").get().asFile)
}

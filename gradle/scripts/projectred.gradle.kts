import groovy.json.JsonOutput
import com.cleanroommc.gradle.api.ext.CleanroomExtension

buildscript {
    dependencies { classpath(files(rootProject.buildscript.configurations.getByName("classpath"))) }
}

val moduleDependencies = mapOf(
    "api" to emptyList(),
    "core" to listOf("api"),
    "transmission" to listOf("api", "core"),
    "integration" to listOf("api", "core", "transmission"),
    "fabrication" to listOf("api", "core", "transmission", "integration"),
    "illumination" to listOf("api", "core", "transmission"),
    "transportation" to listOf("api", "core"),
    "expansion" to listOf("api", "core", "transportation"),
    "relocation" to listOf("api", "core"),
    "exploration" to listOf("api", "core"),
    "compatibility" to listOf("api", "core", "exploration", "transmission", "transportation")
)
// Export only external dependencies: modules never depend on the root bundle's classes.
val moduleExternalClasspath = configurations.create("moduleExternalClasspath") {
    isCanBeResolved = false
    isCanBeConsumed = true
    extendsFrom(configurations.getByName("implementation"))
}
val mainOutput = extensions.getByType<SourceSetContainer>().getByName("main").output
val targetScalaVersion = providers.gradleProperty("scala_version").get()
val modVersion = version.toString()
val rootNatives = tasks.named("extractNatives")

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "scala")
    apply(plugin = "com.cleanroommc.cleanroomgradle")
    group = rootProject.group
    version = rootProject.version
    extensions.configure<JavaPluginExtension> { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
    extensions.configure<ScalaPluginExtension> { this.scalaVersion.set(targetScalaVersion) }
    repositories { rootProject.repositories.forEach { add(it) } }
    val moduleCleanroom = extensions.getByType<CleanroomExtension>()
    dependencies {
        add("compileOnly", project(mapOf("path" to ":", "configuration" to "moduleExternalClasspath")))
        add("implementation", moduleCleanroom.userdev(rootProject.providers.gradleProperty("cleanroom_version").get()) {
            accessTransformers.from(rootProject.file("src/main/resources/META-INF/projectred-relocation_at.cfg"))
        })
        add("implementation", "org.scala-lang:scala3-library_3:$targetScalaVersion")
        moduleDependencies.getValue(name).forEach { add("api", project(":$it")) }
    }
    val main = extensions.getByType<SourceSetContainer>().getByName("main")
    if (name != "api") {
        // Zinc compiles mutually dependent Java/Scala sources in the same module.
        main.extensions.getByType<ScalaSourceDirectorySet>().srcDir("src/main/java")
        main.java.setSrcDirs(emptyList<String>())
    }
    tasks.withType<ScalaCompile>().configureEach {
        options.encoding = "UTF-8"
        inputs.property("projectredVersion", modVersion)
        // Zinc can leave stale Scala 3 annotation constants after BuildInfo changes.
        // Keep Gradle's task/build caches, but compile the complete affected module.
        scalaCompileOptions.isForce = true
        scalaCompileOptions.additionalParameters = listOf("-release:25", "-Ximport-suggestion-timeout:0")
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
    // Only the root project's universal jar is published. Internal jars are compile dependencies.
    val internalArchiveName = "projectred-$name-internal"
    tasks.withType<Jar>().configureEach { archiveBaseName.set(internalArchiveName) }
    // CleanroomGradle's default run tasks share a cached native directory. A task
    // selector such as `runServer` still resolves disabled child tasks' inputs.
    val moduleNatives = tasks.named("extractNatives") {
        enabled = false
        dependsOn(rootNatives)
    }
    listOf("runClient","runServer").forEach { run ->
        rootProject.tasks.named(run) { mustRunAfter(moduleNatives) }
    }
    tasks.matching { it.name == "runClient" || it.name == "runServer" }.configureEach {
        enabled = false
        setDependsOn(emptyList<String>())
        group = null
    }
    mainOutput.dir(mapOf("builtBy" to tasks.named("classes")), main.output.classesDirs)
    rootProject.tasks.named<Jar>("sourcesJar") { from(main.allSource); exclude("dan200/**") }
}

val core = project(":core")
val generatedVersion = core.layout.buildDirectory.dir("generated/sources/version/scala")
val generateVersion = core.tasks.register("generateVersion") {
    inputs.property("version", modVersion)
    outputs.dir(generatedVersion)
    doLast {
        val target = outputs.files.singleFile.resolve("mrtjp/projectred/core/BuildInfo.scala")
        target.parentFile.mkdirs()
        target.writeText("package mrtjp.projectred.core\nobject BuildInfo { final val VERSION = " + JsonOutput.toJson(inputs.properties.getValue("version")) + " }\n")
    }
}
core.extensions.getByType<SourceSetContainer>().getByName("main").extensions.getByType<ScalaSourceDirectorySet>().srcDir(generateVersion)
tasks.named<Jar>("jar") { exclude("dan200/**"); duplicatesStrategy = DuplicatesStrategy.FAIL }

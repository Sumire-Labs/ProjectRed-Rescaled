// Temporary test dependency, never a release artifact or part of the ProjectRed jar.
// This uses Scalar's actual language adapter with Scala 3.10 runtime libraries.
val scalarSource = providers.gradleProperty("verification_scalar_source")
if (scalarSource.isPresent) {
    val scalarVersion = providers.gradleProperty("scala_version").get()
    val sourceDirectory = file(scalarSource.get())
    val generated = layout.buildDirectory.dir("verification/scalar/source")
    val reference = tasks.register("generateScalarFixtureReference") {
        inputs.property("version", scalarVersion)
        outputs.dir(generated)
        doLast {
            val file = outputs.files.singleFile.resolve("com/cleanroommc/scalar/Reference.java")
            file.parentFile.mkdirs()
            file.writeText("package com.cleanroommc.scalar; public class Reference { public static final String MOD_ID = \"scalar\"; public static final String VERSION = \"${inputs.properties.getValue("version")}\"; }\n")
        }
    }
    val fixtureClasspath = extensions.getByType<SourceSetContainer>().getByName("main").compileClasspath
    val compiled = tasks.register<JavaCompile>("compileScalarFixture") {
        source(fileTree(sourceDirectory.resolve("src/main/java")), reference)
        classpath = fixtureClasspath
        destinationDirectory.set(layout.buildDirectory.dir("verification/scalar/classes"))
        options.release.set(25)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-proc:none")
    }
    val libraries = configurations.create("scalarFixtureLibraries")
    dependencies {
        add(libraries.name, "org.scala-lang:scala-library:$scalarVersion") { isTransitive = false }
        add(libraries.name, "org.scala-lang:scala3-tasty-inspector_3:$scalarVersion") { isTransitive = false }
    }
    tasks.register<Jar>("scalarFixture") {
        from(compiled)
        from(libraries)
        from(sourceDirectory.resolve("LICENSE"))
        destinationDirectory.set(layout.buildDirectory.dir("verification/scalar"))
        archiveFileName.set("scalar-$scalarVersion-fixture.jar")
        manifest.attributes("ModType" to "CRL", "FMLCorePlugin" to "com.cleanroommc.scalar.ScalarLoadingPlugin",
            "ContainedDeps" to "scala-library-$scalarVersion.jar scala3-tasty-inspector_3-$scalarVersion.jar", "NonModDeps" to "true")
    }
}

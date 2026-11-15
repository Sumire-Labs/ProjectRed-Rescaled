import com.cleanroommc.gradle.api.ext.CleanroomExtension

val cleanroom = extensions.getByType<CleanroomExtension>()
val multipartDevelopmentLibraries = configurations.create("multipartDevelopmentLibraries")
repositories {
    // Local publication is opt-in; CI and releases always resolve the pinned remote coordinates.
    if (providers.gradleProperty("cg.repos.enableLocal").orElse("false").get().toBoolean()) mavenLocal()
    maven("https://artifact.sumirelabs.com/maven/releases") {
        content { includeModule("mrtjp.core", "MrTJPCore-Rescaled"); includeModule("codechicken", "CBMultipart-Rescaled") }
    }
    ivy("https://github.com/Refitbench/CodeChickenLib/releases/download") {
        patternLayout { artifact("[revision]/[artifact]-[revision]-universal.[ext]") }
        metadataSources { artifact() }
        content { includeModule("codechicken.lib", "codechickenlib") }
    }
    maven("https://maven.cleanroommc.com") {
        content { includeGroup("mezz") }
        metadataSources { mavenPom(); artifact() }
    }
    maven("https://cursemaven.com") { content { includeGroup("curse.maven") } }
    mavenCentral()
    maven("https://maven.covers1624.net") {
        content { includeModule("io.github.mrtjp", "fabrication-engine") }
    }
}
dependencies {
    add("contain", "io.github.mrtjp:fabrication-engine:0.1.0-alpha-19") { isTransitive = false }
    add("implementation", "org.scala-lang:scala3-library_3:${providers.gradleProperty("scala_version").get()}")
    add("implementation", cleanroom.deobf("mrtjp.core:MrTJPCore-Rescaled:${providers.gradleProperty("tjpcore_version").get()}"))
    add("implementation", cleanroom.deobf("codechicken:CBMultipart-Rescaled:${providers.gradleProperty("fmp_version").get()}"))
    add("implementation", cleanroom.deobf("codechicken.lib:codechickenlib:${providers.gradleProperty("ccl_version").get()}"))
    add("implementation", cleanroom.deobf("mezz:jei:${providers.gradleProperty("jei_version").get()}@jar"))
    add(multipartDevelopmentLibraries.name, "codechicken:CBMultipart-Rescaled:${providers.gradleProperty("fmp_version").get()}") { isTransitive = false }
    // In production FML extracts CBMultipart's ContainedDeps. Expose the same jars in development.
    add("runtimeOnly", files(providers.provider {
        zipTree(multipartDevelopmentLibraries.singleFile).matching { include("*.jar") }.files
    }))
    val scalarJar = providers.gradleProperty("scalar_jar")
    if (scalarJar.isPresent) add("runtimeOnly", cleanroom.deobf(files(scalarJar.get())))
    else add("runtimeOnly", (cleanroom.deobf("curse.maven:scalar-1012168:${providers.gradleProperty("scalar_file").get()}") as ModuleDependency).apply { isTransitive = false })
}

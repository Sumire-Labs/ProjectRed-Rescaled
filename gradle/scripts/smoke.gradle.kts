import com.cleanroommc.gradle.api.task.mc.RunMinecraft
import net.minecraftforge.renamer.gradle.RenameJar
import net.minecraftforge.renamer.gradle.RenamerExtension
import java.time.Duration

val sets = extensions.getByType<SourceSetContainer>()
if (providers.gradleProperty("client_smoke").orElse("false").get().toBoolean()) {
    val directory = layout.projectDirectory.dir("run/smoke-client").asFile
    val result = layout.buildDirectory.file("verification/client-smoke.txt").get().asFile
    tasks.named<RunMinecraft>("runClient") {
        timeout.set(Duration.ofMinutes(5))
        args("--width", "1280", "--height", "800")
        dependsOn(tasks.named("testClasses"))
        classpath(sets.getByName("test").output)
        workingDir(directory)
        systemProperty("projectred.client.result", result.absolutePath)
        doFirst { directory.mkdirs(); result.parentFile.mkdirs(); result.delete() }
        doLast {
            check(result.isFile && result.readText().trim() == "PASS") { "ProjectRed client checks did not finish; inspect run/smoke-client/logs/latest.log" }
            val log = directory.resolve("logs/latest.log").readText()
            check(!log.contains("Error executing task") && !log.contains("Unreported exception thrown!")) {
                "A client task failed despite the completion marker; inspect run/smoke-client/logs/latest.log"
            }
        }
    }
}
if (providers.gradleProperty("smoke_test").orElse("false").get().toBoolean()) {
    val directory = layout.projectDirectory.dir("run/smoke-server").asFile
    val result = layout.buildDirectory.file("verification/server-smoke.txt").get().asFile
    tasks.named<RunMinecraft>("runServer") {
        timeout.set(Duration.ofMinutes(3))
        dependsOn(tasks.named("testClasses"))
        classpath(sets.getByName("test").output)
        workingDir(directory)
        systemProperty("projectred.smoke.result", result.absolutePath)
        systemProperty("terminal.jline", false)
        doFirst {
            directory.mkdirs(); result.parentFile.mkdirs(); result.delete()
            directory.resolve("eula.txt").writeText("eula=true\n")
            directory.resolve("server.properties").writeText("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=FLAT\ngenerate-structures=false\nview-distance=3\n")
        }
        doLast { check(result.isFile && result.readText().trim() == "PASS") { "ProjectRed server checks did not finish; inspect run/smoke-server/logs/latest.log" } }
    }
}
val smokeJar = tasks.register<Jar>("smokeJar") {
    from(sets.getByName("test").output)
    include("mrtjp/projectred/verification/**", "mcmod.info")
    archiveClassifier.set("smoke-mcp")
    destinationDirectory.set(layout.buildDirectory.dir("smoke"))
}
extensions.getByType<RenamerExtension>().classes("reobfSmokeJar", smokeJar) {
    map.setFrom(tasks.named<RenameJar>("reobfJar").map { it.map })
    libraries.from(sets.getByName("main").output, configurations.named("compileClasspath"))
    output.set(layout.buildDirectory.file("smoke/ProjectRed-Rescaled-smoke-tests.jar"))
}
val productionMods = configurations.create("productionSmokeMods")
dependencies {
    add(productionMods.name, "mrtjp.core:MrTJPCore-Rescaled:${providers.gradleProperty("tjpcore_version").get()}") { isTransitive = false }
    add(productionMods.name, "codechicken:CBMultipart-Rescaled:${providers.gradleProperty("fmp_version").get()}") { isTransitive = false }
    add(productionMods.name, "codechicken.lib:codechickenlib:${providers.gradleProperty("ccl_version").get()}") { isTransitive = false }
    if (providers.gradleProperty("scalar_jar").isPresent) add(productionMods.name, files(providers.gradleProperty("scalar_jar").get()))
    else add(productionMods.name, "curse.maven:scalar-1012168:${providers.gradleProperty("scalar_file").get()}") { isTransitive = false }
}
tasks.register<Sync>("stageProductionSmoke") {
    from(productionMods)
    from(tasks.named<RenameJar>("reobfJar").flatMap { it.output }, tasks.named<RenameJar>("reobfSmokeJar").flatMap { it.output })
    into(layout.buildDirectory.dir("production-smoke/mods"))
}

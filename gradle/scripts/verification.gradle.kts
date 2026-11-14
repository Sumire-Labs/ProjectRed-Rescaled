import groovy.json.JsonSlurper
import java.io.DataInputStream
import java.util.jar.JarFile
import net.minecraftforge.renamer.gradle.RenameJar

val artifact = tasks.named<RenameJar>("reobfJar").flatMap { it.output }
val expectedVersion = version.toString()
val verifyDistribution = tasks.register("verifyDistribution") {
    group = "verification"
    description = "Checks the single runtime jar, logical mods, Java/Scala metadata and relocated AT."
    inputs.file(artifact)
    inputs.property("version", expectedVersion)
    doLast {
        val modIds = setOf("core","transmission","integration","fabrication","illumination","transportation","expansion","relocation","exploration","compat").map { "projectred-$it" }.toSet()
        JarFile(inputs.files.singleFile).use { jar ->
            val entries = jar.entries().asSequence().toList()
            val metadata = jar.getJarEntry("mcmod.info") ?: error("Missing mcmod.info")
            val json = jar.getInputStream(metadata).use { it.readBytes().toString(Charsets.UTF_8) }
            check(!json.contains("@{") && !json.contains("\u0024{")) { "Unresolved metadata tokens" }
            val mods = JsonSlurper().parseText(json) as List<Map<String, Any>>
            check(mods.map { it["modid"] }.toSet() == modIds && mods.size == modIds.size) { "Logical mod metadata is incomplete or duplicated" }
            check(mods.all { it["version"] == inputs.properties.getValue("version") }) { "Module versions differ" }
            check(jar.manifest.mainAttributes.getValue("ModType") == "CRL") { "Missing Cleanroom-only marker" }
            check(jar.manifest.mainAttributes.getValue("FMLAT") == "projectred-relocation_at.cfg") { "Missing Relocation AT marker" }
            check(jar.getJarEntry("META-INF/projectred-relocation_at.cfg") != null) { "Missing Relocation AT" }
            check(jar.getJarEntry("META-INF/LICENSE.md") != null) { "Missing MIT notice" }
            check(entries.none { it.name.startsWith("dan200/") || it.name.startsWith("mrtjp/projectred/verification/") }) { "Optional API stubs or regression tests leaked into the runtime jar" }
            val classes = entries.filter { it.name.startsWith("mrtjp/projectred/") && it.name.endsWith(".class") }
            check(classes.size > 500) { "Module classes missing from distribution" }
            check(entries.count { it.name.endsWith(".tasty") } > 150) { "Scala 3 metadata missing" }
            for (entry in classes) DataInputStream(jar.getInputStream(entry)).use { input ->
                check(input.readInt() == 0xCAFEBABE.toInt())
                input.readUnsignedShort()
                check(input.readUnsignedShort() == 69) { "Expected Java 25 bytecode in ${entry.name}" }
                val count = input.readUnsignedShort()
                var index = 1
                var containsCurrentVersion = false
                while (index < count) {
                    when (val tag = input.readUnsignedByte()) {
                        1 -> {
                            val text = input.readUTF()
                            check(!text.contains("@{")) { "Unresolved class token in ${entry.name}" }
                            if (text == inputs.properties.getValue("version")) containsCurrentVersion = true
                        }
                        3, 4 -> input.readInt()
                        5, 6 -> { input.readLong(); index++ }
                        7, 8, 16, 19, 20 -> input.readUnsignedShort()
                        9, 10, 11, 12, 17, 18 -> input.readInt()
                        15 -> { input.readUnsignedByte(); input.readUnsignedShort() }
                        else -> error("Unknown constant-pool tag: $tag")
                    }
                    index++
                }
                if (entry.name.matches(Regex("mrtjp/projectred/ProjectRed[^/$]+\\.class"))) {
                    check(containsCurrentVersion) { "Stale Mod annotation version in ${entry.name}; metadata and class annotations must agree" }
                }
            }
            logger.lifecycle("Verified one distribution jar: {} logical mods, {} classes.", mods.size, classes.size)
        }
    }
}
tasks.named("check") { dependsOn(verifyDistribution) }

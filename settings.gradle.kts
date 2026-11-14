pluginManagement {
    repositories {
        maven("https://maven.cleanroommc.com")
        gradlePluginPortal()
    }
}

plugins {
    id("com.cleanroommc.cleanroomgradle.settings") version "0.23.0"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = rootProject.projectDir.name

include("api", "core", "transmission", "integration", "fabrication", "illumination", "transportation", "expansion", "relocation", "exploration", "compatibility")

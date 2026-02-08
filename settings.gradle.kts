
pluginManagement {
    val quarkusPluginVersion: String by settings
    val quarkusPluginId: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    plugins {
        id("${quarkusPluginId}") version "${quarkusPluginVersion}"
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
        kotlin("jvm") version "2.1.20"
    }
}
rootProject.name = "RytmoServer"
include("server", "library", "models")
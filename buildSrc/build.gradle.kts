plugins {
    `kotlin-dsl`
}
repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.4")
}

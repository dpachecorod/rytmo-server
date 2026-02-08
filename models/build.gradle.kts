plugins {
    kotlin("jvm")
    `java-library`
}
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

val jacocoMinimumCoverage: String by project

plugins {
    id("java")
    id("io.quarkus")
    id("kotlin-conventions")
    id("jacoco")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    val quarkusPlatformGroupId: String by project
    val quarkusPlatformArtifactId: String by project
    val quarkusPlatformVersion: String by project

    implementation(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-micrometer")
    implementation("io.micrometer:micrometer-registry-cloudwatch2:1.13.6")
    implementation("software.amazon.awssdk:cloudwatch")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-jacoco")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")

    implementation(project(":models"))
    implementation(project(":library"))
    implementation("io.privy.api:privy-java:0.52.4")

    implementation("com.nimbusds:nimbus-jose-jwt:10.7")
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.83")

    // AWS DynamoDB
    implementation(platform("software.amazon.awssdk:bom:2.30.26"))
    implementation("software.amazon.awssdk:dynamodb-enhanced")
}

group = "rytmo-server"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.compileJava {
    options.compilerArgs.add("-parameters")
}

tasks.jacocoTestReport {
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it).apply {
                exclude("**/*Scope.class")
                exclude("**/*Config.class")
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    executionData.setFrom(layout.buildDirectory.file("jacoco-quarkus.exec"))

    violationRules {
        rule {
            classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = jacocoMinimumCoverage.toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = jacocoMinimumCoverage.toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Ensure library and models are built before quarkusDev
tasks.named("quarkusDev") {
    dependsOn(":library:jar", ":models:jar")
}

tasks.named("quarkusBuild") {
    dependsOn(":library:jar", ":models:jar")
}

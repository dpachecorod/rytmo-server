val jacocoMinimumCoverage: String by project
plugins {
    id("kotlin-conventions")
    id("jacoco")
    id("org.openapi.generator") version "7.10.0"
}
repositories {
    mavenCentral()
}

openApiGenerate {
    generatorName.set("java")
    inputSpec.set("$projectDir/bridgeapi.json")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.absolutePath)
    apiPackage.set("com.rytmo.library.bridge.api")
    modelPackage.set("com.rytmo.library.bridge.model")
    invokerPackage.set("com.rytmo.library.bridge.invoker")
    skipValidateSpec.set(true)
    configOptions.set(mapOf(
        "library" to "native",
        "dateLibrary" to "java8",
        "useJakartaEe" to "true",
        "openApiNullable" to "false"
    ))
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
        }
    }
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

tasks.named("compileKotlin") {
    dependsOn("openApiGenerate")
}

tasks.matching { it.name.startsWith("kapt") }.configureEach {
    dependsOn("openApiGenerate")
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.mockito:mockito-core:5.17.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.17.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    implementation("io.privy.api:privy-java:0.52.4")
    implementation("com.nimbusds:nimbus-jose-jwt:10.7")
    implementation("org.mapstruct:mapstruct:1.6.3")
    kapt("org.mapstruct:mapstruct-processor:1.6.3")
    implementation(project(":models"))

    // OpenAPI generated client dependencies
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")

    // AWS DynamoDB Enhanced Client
    implementation(platform("software.amazon.awssdk:bom:2.30.26"))
    implementation("software.amazon.awssdk:dynamodb-enhanced")
}


tasks {

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
        failFast = true
        finalizedBy(jacocoTestReport)
        outputs.upToDateWhen { false }
    }

    jacocoTestCoverageVerification {
        dependsOn(jacocoTestReport)
        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "INSTRUCTION"
                    value = "COVEREDRATIO"
                    minimum = jacocoMinimumCoverage.toBigDecimal()
                }
            }

            // Additional rule per class for strict enforcement
            rule {
                element = "CLASS"
                includes = listOf("com.groupieticket.*")
                limit {
                    counter = "INSTRUCTION"
                    value = "COVEREDRATIO"
                    minimum = jacocoMinimumCoverage.toBigDecimal()
                }

            }
        }
        classDirectories.setFrom(jacocoTestReport.get().classDirectories)
    }

    jacocoTestReport {
        dependsOn(test)

        reports {
            xml.required.set(false)
            html.required.set(true)
            csv.required.set(false)
        }

        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) {
                    exclude()
                }
            })
        )
    }
}

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

// The openapi-generator references OneOfUsBankAccountGbBankAccount but fails to generate it.
// This task creates the missing class after generation so the Java compile step succeeds.
val createMissingGeneratedClasses by tasks.registering {
    dependsOn("openApiGenerate")
    val outputDir = layout.buildDirectory.dir("generated/openapi/src/main/java/com/rytmo/library/bridge/model")
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("OneOfUsBankAccountGbBankAccount.java").asFile
        if (!file.exists()) {
            file.writeText("""
package com.rytmo.library.bridge.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.rytmo.library.bridge.invoker.JSON;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One-of wrapper for UsBankAccount / GbBankAccount (generated stub). */
@JsonDeserialize(using = OneOfUsBankAccountGbBankAccount.OneOfDeserializer.class)
@JsonSerialize(using = OneOfUsBankAccountGbBankAccount.OneOfSerializer.class)
public class OneOfUsBankAccountGbBankAccount extends AbstractOpenApiSchema {
    private static final Logger log = Logger.getLogger(OneOfUsBankAccountGbBankAccount.class.getName());

    public static class OneOfSerializer extends StdSerializer<OneOfUsBankAccountGbBankAccount> {
        public OneOfSerializer() { this(null); }
        public OneOfSerializer(Class<OneOfUsBankAccountGbBankAccount> t) { super(t); }
        @Override
        public void serialize(OneOfUsBankAccountGbBankAccount value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException, JsonProcessingException {
            jgen.writeObject(value.getActualInstance());
        }
    }

    public static class OneOfDeserializer extends StdDeserializer<OneOfUsBankAccountGbBankAccount> {
        public OneOfDeserializer() { this(OneOfUsBankAccountGbBankAccount.class); }
        public OneOfDeserializer(Class<?> vc) { super(vc); }
        @Override
        public OneOfUsBankAccountGbBankAccount deserialize(JsonParser jp, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            com.fasterxml.jackson.databind.JsonNode tree = jp.readValueAsTree();
            Object deserialized = null;
            int match = 0;
            try {
                deserialized = tree.traverse(jp.getCodec()).readValueAs(UsBankAccount.class);
                match++;
            } catch (Exception e) {
                log.log(Level.FINER, "Input data does not match schema 'UsBankAccount'", e);
            }
            if (match == 0) {
                try {
                    deserialized = tree.traverse(jp.getCodec()).readValueAs(GbBankAccount.class);
                    match++;
                } catch (Exception e) {
                    log.log(Level.FINER, "Input data does not match schema 'GbBankAccount'", e);
                }
            }
            if (match == 1) {
                OneOfUsBankAccountGbBankAccount ret = new OneOfUsBankAccountGbBankAccount();
                ret.setActualInstance(deserialized);
                return ret;
            }
            throw new IOException("Failed deserialization for OneOfUsBankAccountGbBankAccount: expected UsBankAccount or GbBankAccount");
        }
        @Override
        public OneOfUsBankAccountGbBankAccount getNullValue(DeserializationContext ctxt) throws JsonMappingException {
            throw new JsonMappingException(ctxt.getParser(), "OneOfUsBankAccountGbBankAccount cannot be null");
        }
    }

    public static final Map<String, Class<?>> schemas = new HashMap<>();

    public OneOfUsBankAccountGbBankAccount() { super("oneOf", Boolean.FALSE); }
    public OneOfUsBankAccountGbBankAccount(UsBankAccount o) { super("oneOf", Boolean.FALSE); setActualInstance(o); }
    public OneOfUsBankAccountGbBankAccount(GbBankAccount o) { super("oneOf", Boolean.FALSE); setActualInstance(o); }

    static {
        schemas.put("UsBankAccount", UsBankAccount.class);
        schemas.put("GbBankAccount", GbBankAccount.class);
        JSON.registerDescendants(OneOfUsBankAccountGbBankAccount.class, Collections.unmodifiableMap(schemas));
    }

    @Override
    public Map<String, Class<?>> getSchemas() { return OneOfUsBankAccountGbBankAccount.schemas; }

    @Override
    public void setActualInstance(Object instance) {
        if (JSON.isInstanceOf(UsBankAccount.class, instance, new HashSet<>())) { super.setActualInstance(instance); return; }
        if (JSON.isInstanceOf(GbBankAccount.class, instance, new HashSet<>())) { super.setActualInstance(instance); return; }
        throw new RuntimeException("Invalid instance type. Must be UsBankAccount or GbBankAccount");
    }

    @Override
    public Object getActualInstance() { return super.getActualInstance(); }

    public UsBankAccount getUsBankAccount() throws ClassCastException {
        return (UsBankAccount) super.getActualInstance();
    }

    public GbBankAccount getGbBankAccount() throws ClassCastException {
        return (GbBankAccount) super.getActualInstance();
    }

    public String toUrlQueryString() {
        return toUrlQueryString(null);
    }

    public String toUrlQueryString(String prefix) {
        if (getActualInstance() instanceof UsBankAccount) {
            return ((UsBankAccount) getActualInstance()).toUrlQueryString(prefix);
        }
        if (getActualInstance() instanceof GbBankAccount) {
            return ((GbBankAccount) getActualInstance()).toUrlQueryString(prefix);
        }
        return "";
    }
}
""".trimIndent())
        }
    }
}

tasks.named("compileJava") {
    dependsOn(createMissingGeneratedClasses)
}

tasks.named("compileKotlin") {
    dependsOn("openApiGenerate")
}

tasks.matching { it.name.startsWith("kapt") }.configureEach {
    dependsOn(createMissingGeneratedClasses)
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

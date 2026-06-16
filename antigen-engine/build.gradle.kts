// Antigen engine — pure computation, language/runtime-neutral.
// MUST NOT depend on AspectJ, JUnit, RestAssured, OkHttp, or Apache HttpClient.
// The EngineLayerTest (src/test) enforces this purity; the module's own classpath enforces
// that it never depends on the adapter/cli modules.

plugins {
    `java-library`
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // JSON/YAML parsing
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")

    // OpenAPI spec parsing (analytics/gap analysis)
    implementation("io.swagger.parser.v3:swagger-parser:2.1.22")

    // Math (violation generation)
    api("org.apache.commons:commons-math3:3.6.1")

    // JSON
    implementation("org.json:json:20250107")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.11")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    // Testing — pure JUnit, no weaving
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    // ArchUnit — enforces engine purity (no runtime-library imports) — Phase 2 boundary guard
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}

tasks.test {
    useJUnitPlatform()
}

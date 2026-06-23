// Antigen CLI — the ai.* generation loop (Claude CLI -> build -> test -> simulate) and the
// io.antigen.gradle plugin. Consumes engine *outputs* (the fault report); depends on the engine
// only, never on the test-runner.

plugins {
    `java-library`
    `maven-publish`
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("io.antigen.ai.Antigen")
}

dependencies {
    // Reads FaultSimulationReport as the generation quality gate.
    implementation(project(":antigen-engine"))

    // CLI
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")

    // Config parsing (generation config / api specs)
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")

    // Gradle plugin API (io.antigen.gradle.AntigenPlugin)
    compileOnly(gradleApi())

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.11")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}

tasks.test {
    useJUnitPlatform()
}

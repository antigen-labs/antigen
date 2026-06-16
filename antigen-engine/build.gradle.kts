// Antigen engine — pure computation, language/runtime-neutral.
// MUST NOT depend on AspectJ, JUnit, RestAssured, OkHttp, or Apache HttpClient.
// The EngineLayerTest (src/test) enforces this purity; the module's own classpath enforces
// that it never depends on the adapter/cli modules.

plugins {
    `java-library`
    `maven-publish`
    // Phase 4 — distribution of EngineServer as a runnable artifact a foreign-language adapter
    // spawns. The DEFAULT artifact is a fat (shadow) jar: `java -jar antigen-engine-*-all.jar`.
    // It requires a JVM on the user's machine but builds anywhere, with no reflection-metadata
    // or per-platform-binary overhead. See refactor/phase4.md.
    id("com.gradleup.shadow") version "8.3.5"
    // OPTIONAL native path — GraalVM Community Edition (GPLv2 + Classpath Exception): a JVM-free
    // binary, free to embed in paid products. Applying the plugin is inert without a GraalVM
    // toolchain — only `nativeCompile`/`nativeRun`/`nativeTest` need one, so the ordinary JVM
    // `build`/`test`/`shadowJar` gates are unaffected.
    id("org.graalvm.buildtools.native") version "0.10.3"
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
    // Forward the conformance golden-regeneration switch to the test JVM (off by default).
    systemProperty("antigen.conformance.regenerate",
        providers.systemProperty("antigen.conformance.regenerate").getOrElse("false"))
}

// Phase 4 (default) — runnable fat jar of the protocol server. `EngineServer` as Main-Class so
// `java -jar build/libs/antigen-engine-<version>-all.jar [stdio|http]` starts the engine; an
// adapter in any language spawns it the same way it would the native binary. Bundles the engine's
// runtime deps (Jackson, SnakeYAML, Swagger, logback) — reflection "just works" on a real JVM, so
// no native metadata is needed for this path. The thin library jar published for JitPack is
// untouched; this is an additional artifact.
tasks.shadowJar {
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "io.antigen.core.protocol.transport.EngineServer"
    }
    mergeServiceFiles() // keep ServiceLoader registrations (Jackson modules, etc.) intact
}

// Phase 4 (optional) — native-image of the protocol server (EngineServer). Requires a GraalVM CE
// toolchain
// on the build machine; absent one these tasks no-op-fail with a clear message and nothing else
// in the build is affected.
//
// Reflection-heavy libraries (Jackson, SnakeYAML, Swagger, logback) are covered by the GraalVM
// reachability-metadata repository; our own serialized DTOs are seeded in
// src/main/resources/META-INF/native-image/. To refine app metadata from the real serialization
// paths our tests already exercise:
//   ./gradlew :antigen-engine:test -Pagent            # run tests under the tracing agent
//   ./gradlew :antigen-engine:metadataCopy            # harvest into META-INF/native-image
// Then build the binary:
//   ./gradlew :antigen-engine:nativeCompile            # -> build/native/nativeCompile/antigen-engine
graalvmNative {
    binaries {
        named("main") {
            imageName.set("antigen-engine")
            mainClass.set("io.antigen.core.protocol.transport.EngineServer")
            buildArgs.add("--no-fallback")                 // fail loudly rather than ship a JVM-fallback image
            buildArgs.add("-H:+ReportExceptionStackTraces") // readable build-time reflection errors
        }
    }
    // Pull community-maintained reachability metadata for third-party libs (Jackson et al.).
    metadataRepository {
        enabled.set(true)
    }
    // `-Pagent` runs the harvest workflow above; copy harvested metadata next to our seed.
    agent {
        metadataCopy {
            inputTaskNames.add("test")
            outputDirectories.add("src/main/resources/META-INF/native-image/io.antigen/antigen-engine")
            mergeWithExisting.set(true)
        }
    }
}

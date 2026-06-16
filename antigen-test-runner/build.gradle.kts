// Antigen JVM test-runner adapter — AspectJ/JUnit interception, the runner execution loop,
// and the Apache HttpClient capture impls. Named generically (not "junit") because the runtime
// glue is largely framework-neutral; TestNG support would live here too.
// Depends on the engine via `api`; the engine never depends back.

plugins {
    `java-library`
    `maven-publish`
    id("io.freefair.aspectj.post-compile-weaving")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // The pure engine — exposed transitively to consumers of the adapter.
    api(project(":antigen-engine"))

    // AspectJ — load-time weaving for HTTP/test interception
    implementation("org.aspectj:aspectjrt:1.9.22")
    api("org.aspectj:aspectjweaver:1.9.22")
    compileOnly("org.aspectj:aspectjtools:1.9.22")

    // HTTP clients (interception targets). rest-assured brings Apache HttpClient transitively,
    // which the http.apache impls + AspectExecutor compile against (kept on the main scope as in
    // the pre-split build to preserve resolution).
    implementation("io.rest-assured:rest-assured:5.5.6")
    implementation("io.rest-assured:json-path:5.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // Jackson — ApacheHTTPResponse parses bodies to maps (engine exposes it only as `implementation`)
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    // JUnit platform — GlobalTestExecutionListener implements TestExecutionListener; the @Test
    // pointcut needs jupiter-api at compile time.
    compileOnly("org.junit.jupiter:junit-jupiter-api:5.10.0")
    implementation("org.junit.platform:junit-platform-launcher:1.10.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.11")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    // Testing — integration (WireMock :8089) + demoapi (live :8000), woven with the agent
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("com.github.tomakehurst:wiremock:3.0.1")
}

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Xmx2g", "-Xms512m")

    doFirst {
        val runWithAntigen = System.getProperty("runWithAntigen") == "true"
        jvmArgs("-DrunWithAntigen=$runWithAntigen")

        if (runWithAntigen) {
            val agent = configurations.runtimeClasspath.get()
                .find { it.name.contains("aspectjweaver") }?.absolutePath
            if (agent != null) {
                jvmArgs("-javaagent:$agent")
                jvmArgs("-Daj.weaving.verbose=true")
                jvmArgs("-Dorg.aspectj.weaver.showWeaveInfo=true")
                println("[Antigen] Fault simulation enabled — agent: $agent")

                // Verify aop.xml is on the test classpath
                classpath.files.filter { it.isDirectory }.forEach { dir ->
                    val aopXml = dir.resolve("META-INF/aop.xml")
                    if (aopXml.exists()) println("[Antigen] Found aop.xml at: ${aopXml.absolutePath}")
                }
            } else {
                println("[Antigen] WARNING: aspectjweaver not found on classpath")
            }
        }
    }
}

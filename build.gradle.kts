// Root aggregator. No sources live here — Antigen is split into three modules
// (architecture.md §3, refactor/phase2.md):
//   :antigen-engine       — pure engine (config, invariant, normalizer, report, analytics,
//                           coverage model, plan, simulation scoring, http capture contract)
//   :antigen-test-runner  — JVM adapter (AspectJ/JUnit interceptor, runner loop, Apache HTTP impls)
//   :antigen-cli          — ai.* generation loop + the io.antigen.gradle plugin
// Dependency direction is enforced by the module graph: adapters depend on the engine, never
// the reverse. The freefair AspectJ plugin is declared here and applied only by the adapter.

plugins {
    id("io.freefair.aspectj.post-compile-weaving") version "8.6" apply false
}

allprojects {
    group = "io.antigen"
    version = "1.0.0-SNAPSHOT"
}

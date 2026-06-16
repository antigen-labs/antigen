# Phase 4 — Distributing `EngineServer` (runnable fat jar; GraalVM native optional)

> Status: **done.** The engine ships as a runnable **fat (shadow) jar** —
> `java -jar antigen-engine-<version>-all.jar [stdio|http]` — built and smoke-tested on this
> machine across both transports. The **GraalVM native-image** path is wired and documented as an
> *optional* JVM-free distribution, but is not built here (the box has JDK 18 HotSpot, no GraalVM
> toolchain). The engine server (`io.antigen.core.protocol.transport.EngineServer`, stdio + HTTP)
> landed in Phase 3b; Phase 4 makes it a spawnable artifact for foreign-language adapters.

## Goal

Give a foreign-language (Python/TS) adapter something to spawn: the engine as a self-contained,
runnable artifact speaking the §4 protocol over stdio/HTTP. `EngineLayerTest` (purity guard) is
the standing precondition — distribution packaging must not pull a forbidden runtime lib into the
engine.

## Decision — fat jar is the default, native is the optimization

A native binary is **not required for correctness** — it's purely a distribution optimization
(no JVM needed on the user's machine). Everything the engine does works identically from a jar.
The trade-off:

| | Fat jar (`java -jar …-all.jar`) | GraalVM native binary |
|---|---|---|
| Needs a JVM on the user's machine | yes (Java 17+) | no |
| Build complexity | trivial (shadow plugin) | toolchain + reflection metadata + per-OS CI matrix |
| Reflection (Jackson/Swagger/SnakeYAML) | just works on a real JVM | needs metadata config |
| Startup | ~0.5–1s JVM warmup | ~tens of ms |
| Artifact | one cross-platform jar | one binary per OS/arch |
| Buildable on a stock JDK box | yes | no (needs GraalVM CE) |

**Decision:** ship the **fat jar as the default**. The Java adapter (`antigen-test-runner`) links
the engine in-process and needs neither artifact — so this only affects *foreign-language*
adapters, for which "requires Java 17+" is an acceptable prerequisite. Keep the **GraalVM native
path wired and documented** for when the distribution story wants a JVM-free binary (clean
`pip install` / `npm i`).

## What landed — fat jar (default)

- **`com.gradleup.shadow` 8.3.5** applied in `antigen-engine/build.gradle.kts`.
- **`shadowJar` config**: classifier `all`, `Main-Class: io.antigen.core.protocol.transport.EngineServer`,
  `mergeServiceFiles()` so ServiceLoader registrations (Jackson modules etc.) survive shading.
- Bundles the engine's runtime deps (Jackson, SnakeYAML, Swagger, logback) — reflection works on a
  real JVM, so **no native metadata is needed for this path**.
- The thin library jar published for JitPack is **untouched**; the `-all.jar` is an additional
  artifact, not a replacement.

### Build & run

```bash
./gradlew :antigen-engine:shadowJar
#   -> antigen-engine/build/libs/antigen-engine-<version>-all.jar

# stdio (default): one request envelope per line in, one response envelope per line out
echo '{"method":"session/start","params":{"protocolVersion":"1","adapter":{"name":"x","version":"0"}}}' \
  | java -jar antigen-engine/build/libs/antigen-engine-<version>-all.jar

# http: prints ANTIGEN_PORT=<n> on stdout, then POST envelopes to http://127.0.0.1:<n>/
java -jar antigen-engine/build/libs/antigen-engine-<version>-all.jar http
```

## What landed — GraalVM native (optional)

Build with **GraalVM Community Edition (CE)** — GPLv2 **with the Classpath Exception**, so the
produced binary is ours: free to embed in paid Antigen products, no copyleft on our code, no fee.
(Not Oracle GraalVM/GFTC, which is free-of-charge but non-OSS with redistribution restrictions CE
avoids; Mandrel and Liberica NIK are acceptable CE-lineage equivalents.) Ship native-image
*output*, not the relabeled CE toolchain. Licensing note written at a Jan-2026 cutoff; a
commercial release should have legal skim the then-current CE / Classpath-Exception terms.

Wired (inert without a GraalVM toolchain — only `nativeCompile`/`nativeRun`/`nativeTest` need one,
so the JVM `build`/`test`/`shadowJar` gates are unaffected):

- **`org.graalvm.buildtools.native` 0.10.3**; image `antigen-engine`, main class `EngineServer`,
  build args `--no-fallback` and `-H:+ReportExceptionStackTraces`.
- **Metadata = repo + agent, not blind hand-authoring.** `metadataRepository.enabled` pulls
  community reachability metadata for the reflection-heavy libs; `agent { metadataCopy }` harvests
  *app* metadata from the serialization paths our tests already drive (`ProtocolDispatcherTest`,
  `ConformanceVectorTest`, config-loading tests).
- **Seed for our own DTOs**:
  `src/main/resources/META-INF/native-image/io.antigen/antigen-engine/reflect-config.json` —
  coarse per-class registration (constructors + methods + fields) for every Antigen type Jackson
  binds: protocol DTOs (incl. nested `AdapterInfo`/`Summary`), the YAML config model, and the
  serialized report graph. A from-scratch build starts with app classes registered; the agent
  refines/extends it. (This seed is also harmless to the fat-jar path — it's just resource files.)

### Build & verify (needs GraalVM CE 17+: set `GRAALVM_HOME`, `native-image` on PATH)

```bash
./gradlew :antigen-engine:test -Pagent       # (optional) refine app metadata from real test paths
./gradlew :antigen-engine:metadataCopy        #            merge into META-INF/native-image/
./gradlew :antigen-engine:nativeCompile        # -> build/native/nativeCompile/antigen-engine[.exe]
```

If `nativeCompile` reports a missing reflection/resource registration, that class wasn't covered
by the metadata repo or our seed — add it (or re-run the agent against a test that exercises it).
Per-platform binaries come from running `nativeCompile` on each target OS/arch in CI.

## Verification (this machine)

- **Fat jar built and smoke-tested.** `:antigen-engine:shadowJar` → 16M `-all.jar`. **stdio**:
  piping a `session/start` envelope returns `{"result":{"sessionId":"…"}}` on stdout (engine logs
  correctly on stderr). **http**: process prints `ANTIGEN_PORT=<n>`, a POSTed `session/start`
  returns the same result envelope.
- `:antigen-engine:test` — **green** (GraalVM + shadow plugins both resolved and applied without
  disturbing configuration; `EngineLayerTest` purity, config/protocol tests, 4 conformance vectors).
- Integration tier (`:antigen-test-runner:test --tests "io.antigen.core.integration.*"
  -DrunWithAntigen=true`, WireMock :8089) — **green**; LTW weaving intact, fault report shows
  **0 infrastructure errors**.
- **Not verified here:** `nativeCompile` itself (no GraalVM toolchain on this box). The
  reflect-config seed + metadata wiring are *authored but unproven*; the agent + first real
  `nativeCompile` will confirm/extend them.

## Follow-ups carried from Phase 3b

- Route the engine's `System.out.println` diagnostics through SLF4J (then the `EngineServer`
  stdout redirect becomes belt-and-suspenders rather than load-bearing).
- `specPath` loading (OpenAPI-derived invariants over the protocol) — `session/start` accepts the
  field but the engine does not yet act on it.
- Multi-line / streaming request framing for stdio if a single capture body ever exceeds a sane
  line length; v1 assumes one-line envelopes.
- Run `nativeCompile` on a GraalVM CE machine / CI matrix to produce and validate the JVM-free
  per-platform binaries.

# Phase 2a — Engine/adapter boundary (in-place, behavior-preserving)

> Status: **done**. Phase 2b (physical Gradle module split: `:antigen-engine` / `:antigen-junit`
> / `:antigen-cli`) deferred — see "Deferred to 2b" below.

## Goal

Make the engine ← adapter ← cli dependency direction (architecture.md §3, Phase 2) **real and
enforced inside the single Gradle project**, before doing the physical module split. Breaking the
coupling here — where the whole suite still runs in one JVM — de-risks 2b: once direction is
clean and guarded, the module split is mechanical (every package already sits on the correct side
of the line, so `:antigen-engine` compiles with zero AspectJ/JUnit/HttpClient on its classpath by
construction).

## Layering (the line 2b will turn into module boundaries)

| Layer | Packages | Rule |
|---|---|---|
| **engine** (pure) | `config`, `invariant`, `injection`, `normalizer`, `report`, `analytics`, `coverage`, `plan`, `simulation` (scoring), and `http` — the capture *contract* only (`Request`/`Response` interfaces + `RequestResponsePair`) | must not depend on adapter or cli |
| **adapter** (JVM glue) | `interceptor` (AspectJ/JUnit), `runner` (execution loop), `http.apache` (Apache HttpClient impls) | depends on engine only |
| **cli** | `ai.*`, `gradle` (plugin) | top of the stack |

## What changed

The only genuinely illegal edge was **`plan.FaultPlanner` → `interceptor.TestContext`** (via the
nested `RequestResponsePair`). `http.Request`/`http.Response` were already pure interfaces
(engine-appropriate); `coverage → http.Response` is engine→engine. Three moves made every package
homogeneously one layer:

1. **`RequestResponsePair` extracted to the engine.** Was a nested class of
   `interceptor.TestContext`; now top-level `io.antigen.core.http.RequestResponsePair`, pairing the
   pure `Request`/`Response` interfaces. `FaultPlanner` consumes `List<RequestResponsePair>` and no
   longer imports anything from `interceptor`. (This is the minimal resolution of phase1.md
   decision #3, which deferred the input-type question to here. A transport-shaped `Capture` DTO
   with `index`/headers is still deferred to Phase 3 when the protocol server lands — no consumer
   yet.)
2. **Apache HttpClient impls → `io.antigen.core.http.apache`.** `ApacheHTTPRequest`,
   `ApacheHTTPResponse`, `HTTPFactory` moved out of `http` (which now holds only the engine-side
   contract) into an adapter sub-package. The Apache dependency is now physically isolated from the
   engine's view of `http`.
3. **`Runner` → `io.antigen.core.runner`.** The adapter execution loop moved out of `simulation`
   (which now holds only the engine-side scoring types: `FaultSimulationReport`,
   `EndpointFaultResults`, `TestLevelSimulationResults`, `FaultSimulationResult`). `Runner` keeps
   its (legal) adapter→engine deps on those.

Consumers updated: `AspectExecutor`, `TestContext`, `FaultPlanner`, and the two affected tests
(`ApacheHTTPRequestTest`, `FaultPlannerTest`). No logic changed — pure relocation + import fixes.

## The guard

`src/test/java/io/antigen/core/unit/arch/LayerDependencyTest.java` (ArchUnit, unit tier) enforces
the three direction rules: engine ↛ adapter, engine ↛ cli, adapter ↛ cli. This is the rule the
modules will enforce by classpath in 2b, asserted now by package. Package-set note: engine `http`
is matched **exactly** (`"io.antigen.core.http"`), so the `http.apache` sub-package is correctly
classified as adapter, not engine. New dep: `com.tngtech.archunit:archunit-junit5:1.3.0`
(`testImplementation`).

## Key decisions

1. **Interfaces are engine; impls are adapter.** `Request`/`Response` describe captured data — the
   engine's input contract — so they stay engine-side. Only the Apache-specific realization carries
   a runtime dependency, so only it moves to `http.apache`. This is why the doc's "`http` → junit"
   grouping (architecture.md §5, Phase 2) is split rather than moved wholesale.
2. **`RequestResponsePair`, not a new `Capture` DTO.** Smallest change that breaks the edge.
   Inventing the transport DTO now would be churn without a consumer; it belongs with the protocol
   work (Phase 3).
3. **Enforce in-place with ArchUnit before splitting modules.** The dependency direction is the
   hard, error-prone part; proving it in one project (one JVM, whole suite runnable) means 2b is a
   build-topology change against an already-clean graph, not a debugging exercise across a module
   wall.
4. **AspectJ untouched.** `AspectExecutor` stayed in `interceptor`, so the `@Test` /
   `CloseableHttpClient.execute` pointcuts and `aop.xml` are unaffected — confirmed by the
   integration/e2e weave logs.

## Verification

- `compileJava` / `compileTestJava` — clean (only the documented benign `adviceDidNotMatch` + a
  pre-existing unchecked note).
- Unit tier (`*.unit.*`, `runWithAntigen=false`) — green, **including the new
  `LayerDependencyTest`** (boundary holds) and the relocated `ApacheHTTPRequestTest` /
  `FaultPlannerTest`.
- Integration tier (`integration.*`, WireMock :8089, `runWithAntigen=true`) — green; weaving still
  applies to `AspectExecutor`.
- E2e tier (`*demoapi.*`, live oms-demo-api :8000) — **39 faults, 33 caught, 6 escaped (15%)**, same
  six known-uncatchable escapes (5 cross-field temporal + `token_type_bearer`). Identical to the
  1a/1b baseline → behavior preserved.

## Deferred to 2b

- Physical Gradle subprojects `:antigen-engine` / `:antigen-junit` / `:antigen-cli` with the
  dependency direction enforced by classpath (the ArchUnit rule becomes the module graph).
- Per-module AspectJ post-compile weaving config (currently one project-wide weave); `aop.xml`
  placement; the `maven-publish` coordinates and JitPack/example resolution of
  `com.github.antigen-labs:antigen:vX.Y`.
- Placement of the `io.antigen.gradle` plugin (bridges `ai.config` + `simulation.FaultSimulationReport`)
  — rides with cli unless the publishing story argues otherwise.

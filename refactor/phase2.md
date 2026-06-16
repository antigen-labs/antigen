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

- Physical Gradle subprojects with the dependency direction enforced by classpath.
- Per-module AspectJ weaving; `aop.xml` placement; publishing coordinates / JitPack.
- Placement of the `io.antigen.gradle` plugin.

---

# Phase 2b — Physical Gradle module split

> Status: **done**. Three subprojects; dependency direction now enforced by the module graph.

## Goal

Turn the 2a logical boundary into real Gradle modules, so the engine compiles with **none** of
AspectJ/JUnit/RestAssured/OkHttp/Apache-HttpClient on its classpath — the GraalVM-native
precondition (architecture.md §3) and the thing that guarantees adapters can't leak into the
engine.

## Modules (and published coordinates)

| Module | artifactId | Contents | Depends on |
|---|---|---|---|
| `antigen-engine` | `antigen-engine` | config, invariant, normalizer, report, analytics, coverage (model), plan, simulation (scoring), `http` (capture contract: `Request`/`Response`/`RequestResponsePair`) | — |
| `antigen-test-runner` | `antigen-test-runner` | interceptor (AspectJ/JUnit), runner (loop), `http.apache` (Apache impls), `Logger` | `api(engine)` |
| `antigen-cli` | `antigen-cli` | `ai.*` + `io.antigen.gradle` plugin | `implementation(engine)` |

Published under JitPack as `com.github.antigen-labs.antigen:<artifactId>:vX.Y`. The adapter is the
artifact users consume; it exposes the engine transitively via `api`.

## What changed

- **Source tree** relocated into `antigen-engine/`, `antigen-test-runner/`, `antigen-cli/`
  (`src/main`, `src/test`, resources each). `git mv` preserved history. The empty `core.injection`
  and `core.api` packages (contract faults already removed, §7) were dropped.
- **Build** split: root `build.gradle.kts` is an aggregator (no sources) that owns group/version
  and declares the freefair AspectJ plugin `apply false`; each module has its own build file. The
  AspectJ post-compile-weaving plugin and the `runWithAntigen` javaagent `doFirst` block apply
  **only** to `antigen-test-runner`. `settings.gradle.kts` includes the three modules.
- **Resources routed to owners:** `META-INF/aop.xml` + the JUnit `TestExecutionListener` service →
  test-runner; the gradle-plugin descriptor + `antigen/generation/prompt.txt` → cli; the committed
  invariants (`antigen/simulation/**`), WireMock `mappings/**`, and `junit-platform.properties` →
  test-runner test resources (invariants load from the test classpath via
  `ClassLoader.getResource`).
- **One more coupling fix the 2a guard missed:** `coverage.Logger` imported Apache HttpClient
  types directly (it's the adapter-side writer that populates the engine coverage model from
  intercepted requests). Moved to `io.antigen.core.interceptor.Logger` (test-runner); it now
  imports the coverage model classes from the engine — a legal adapter→engine edge.
- **Guard repurposed.** The cross-layer `LayerDependencyTest` is gone — the module classpaths now
  enforce direction structurally (engine literally can't see adapter/cli code). Replaced by
  `EngineLayerTest` in the engine module: a **third-party-purity** rule (engine must not depend on
  `org.apache.http`/`org.aspectj`/`org.junit`/`io.restassured`/`okhttp3`). That's the check that
  would have caught `Logger`, and the one the classpath can't make.
- **`FaultPlannerTest` de-Apache'd.** Its fixture used `ApacheHTTPResponse`; replaced with an
  engine-only `Response` double (status + body parsed to a map, mirroring how the protocol
  delivers a capture) so the test lives in the pure engine module.

## Key decisions

1. **Adapter named `antigen-test-runner`, not `antigen-junit`.** Most of the module (HTTP capture
   shim, `Runner` loop, plan consumption) is framework-neutral; only the `@Test` pointcut and the
   listener service are JUnit-specific. TestNG support would live here (or split into a thin
   `antigen-testng` later). Generic name avoids a misleading coordinate.
2. **Clean 3-artifact split, not a compat aggregator.** Three distinct published artifacts; the
   external `antigen-example` repo switches its dependency to the `antigen-test-runner` coordinate
   on the next release (not touched now — no release is being cut). Chosen over keeping a single
   `antigen` artifact because the long-term topology is the point of the split.
3. **`http` interfaces are engine; only `http.apache` impls are adapter.** Same call as 2a, now
   physical: `Request`/`Response`/`RequestResponsePair` ship in the engine jar as the capture
   contract; the Apache realizations ship in the adapter.
4. **Engine purity guarded by ArchUnit, direction by the build.** Belt-and-suspenders: the build
   makes wrong-direction *code* deps impossible; ArchUnit catches wrong *library* deps the build
   can't see.

## Gotcha surfaced (now in the test skills)

A bare `./gradlew test --tests "<tier>"` **fails** post-split on any module whose test source set
has no match (`No tests found for given includes`). Integration/e2e live only in the adapter, so
those tiers must be scoped: `./gradlew :antigen-test-runner:test --tests "…"`. The unit tier spans
engine + test-runner and a global filter is fine. `test-integration`/`test-e2e` skills updated; the
e2e report now lands under `antigen-test-runner/build/antigen/`.

## Verification

- `:antigen-engine:test` (pure, `runWithAntigen=false`) — green, **including `EngineLayerTest`**
  (engine compiles and runs with zero runtime libraries) and the de-Apache'd `FaultPlannerTest`.
- All three modules compile; `antigen-test-runner` shows only the documented benign
  `adviceDidNotMatch`; `antigen-cli` clean.
- Unit tier (`*.unit.*`, all modules) — green.
- Integration tier (`:antigen-test-runner:test`, WireMock :8089, `runWithAntigen=true`) — green;
  LTW agent weaves `CloseableHttpClient.execute`, `aop.xml` found on the test-runner classpath.
- E2e tier (`:antigen-test-runner:test`, live :8000) — **39 faults, 33 caught, 6 escaped (15%)**,
  same six known-uncatchable escapes. Identical to the 1a/1b/2a baseline → behavior preserved
  across the module split.

## Follow-ups (not in scope)

- Update the `release` skill + `antigen-example` for the 3-artifact coordinates (on the next
  actual release).
- GraalVM native-image build of the engine (Phase 4) — `EngineLayerTest` is the standing
  precondition check.

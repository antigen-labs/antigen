# Antigen Engine Architecture

> Status: **planned**. This document describes the extraction of Antigen's simulation logic
> into a language-neutral engine, and the adapter model that scales Antigen beyond the JVM.
> Audience: any agent/developer picking up this work cold.

---

## 1. Why

Antigen today is a single Java artifact where simulation logic (what to mutate, how to score)
is entangled with execution mechanics (AspectJ weaving, JUnit interception, Apache HttpClient
hooks). Working POCs exist for Python and TypeScript, each with its own runtime mechanism for
re-running tests. Without a shared engine, each language port reimplements violation
generation, scoring, and reporting — they will drift, and a "73% detection rate" will not mean
the same thing across ecosystems.

Goal: **one engine, one implementation of the math; per-language adapters that are dumb glue.**
Supporting a new ecosystem should cost one adapter (~hundreds of lines), not a port.

---

## 2. Current implementation (as of this writing)

Single Gradle project, all under `src/main/java/io/antigen/`:

### Engine-shaped code (pure computation — extraction candidates)
| Concern | Classes |
|---|---|
| Config/DSL parsing & merging | `core.config.*` — `SimulatorConfig`, `ConfigResolver`, `FeatureConfigScanner`, `TestScopedConfigLoader`, `InvariantConfig`, `ConditionConfig` |
| Violation generation (PBT-style mutations from invariants) | `core.invariant.ViolationGenerator`, `ConditionEvaluator`, `FieldExtractor`, `Mutation` |
| Contract fault strategies (being phased out, see §7) | `core.injection.*` — `NullFieldStrategy`, `MissingFieldStrategy`, `EmptyListStrategy`, `EmptyStringStrategy` |
| Scoring & report aggregation | `core.simulation.FaultSimulationReport`, `EndpointFaultResults`, `TestLevelSimulationResults` |
| Report rendering | `core.report.HtmlReportGenerator` |
| Spec gap analysis | `core.analytics.GapAnalyzer`, `OpenAPISpecLoader` |
| Endpoint normalization | `core.normalizer.EndpointPatternNormalizer` |

### Runtime-shaped code (JVM-specific — stays in the Java adapter)
| Concern | Classes |
|---|---|
| Test lifecycle control (wrap `@Test`, re-run via `joinPoint.proceed()`) | `core.interceptor.AspectExecutor` (pointcut on `@org.junit.jupiter.api.Test`) |
| HTTP interception (capture baseline, substitute mutated bodies) | `AspectExecutor` (pointcut on `CloseableHttpClient.execute`; OkHttp/HttpURLConnection are placeholders) |
| Simulation orchestration (currently mixes plan + execution) | `core.simulation.Runner`, `core.invariant.InvariantSimulator` |
| Per-test context | `core.interceptor.TestContext`, `TestContextManager` |

### Known behavior to fix during extraction
- **Re-runs currently hit the real server.** `AspectExecutor.interceptApacheHttpClient` always
  calls `joinPoint.proceed(args)` and swaps the response body afterwards. Every simulation
  re-run issues real HTTP calls (side effects, state pollution, slowness). The target model is
  **cache replay**: during re-runs, no network — all responses served from the captured
  baseline, with the mutated body for the target request.
- `Runner.executeTestWithSimulatedFaults` both *decides* faults and *executes* re-runs. The
  decision half moves to the engine; the execution half becomes the adapter loop.

### AI generation loop (`io.antigen.ai.*`)
`Orchestrator` drives Claude CLI → build → test → simulate, using the fault report as the
quality gate. It consumes engine *outputs* (reports) and is unaffected by this refactor except
for reading the report from the engine instead of the in-process singleton.

---

## 3. Target architecture

```
┌──────────── test process (Java / Python / TS / ...) ───────────┐
│                                                                │
│  Runner adapter                    HTTP shim                   │
│  JUnit5 ext or AspectJ /           AspectJ weave /             │
│  pytest plugin /                   requests-httpx monkeypatch /│
│  Playwright fixture                fetch-axios patch (msw-like)│
│  (runs baseline, then N re-runs)   (serves bodies it is handed │
│                                     — contains NO logic)       │
└───────────────┬────────────────────────────────────────────────┘
                │  JSON over localhost HTTP (or stdio)
┌───────────────▼────────────────────────────────────────────────┐
│            Antigen Engine — single native binary               │
│                                                                │
│  DSL/config parsing · spec-derived invariants ·                │
│  violation generation · fault planning · scoring ·             │
│  control-run accounting · triage/baseline diff ·               │
│  JSON + HTML reports                                           │
└────────────────────────────────────────────────────────────────┘
```

**Strict division of knowledge:**
- The engine knows nothing about test frameworks, HTTP clients, or languages.
- Adapters know nothing about mutations or scoring. An adapter can do exactly three things:
  re-run a test, serve response bytes it was handed, report pass/fail.
- The engine computes mutated response bodies and ships them fully-formed in the fault plan.
  Adapters never construct or modify payloads.

**Engine language: Java, compiled native.** The engine logic already exists and is tested in
Java. Do not rewrite. Ship per-platform static binaries via **GraalVM native-image** so
Python/TS users never need a JVM. Distribution model: the pytest/npm plugin downloads the
pinned engine binary on first run and spawns it (esbuild/Playwright model). The Java adapter
bypasses the protocol and calls the engine in-process (same code, zero overhead).

Validate GraalVM compatibility early: Jackson and SnakeYAML need reflection config;
`HtmlReportGenerator` should stay template-based, no runtime classloading.

---

## 4. Protocol (v1)

Transport: JSON-RPC-style messages over localhost HTTP (stdio acceptable for CI). Adapter
spawns the engine, engine prints its port, adapter connects. Protocol is versioned; engine
rejects mismatched `protocolVersion` at `session/start`.

### 4.1 `session/start`  (adapter → engine)
```json
{
  "protocolVersion": "1",
  "configDir": "src/test/resources/antigen",
  "specPath": "api-specs.yaml",
  "adapter": { "name": "antigen-pytest", "version": "0.1.0" }
}
```
→ `{ "sessionId": "..." }`. Engine loads contract.yml, invariants/*.yml, spec.

### 4.2 `test/baseline`  (adapter → engine, once per test after baseline run)
```json
{
  "sessionId": "...",
  "testId": "com.example.OrdersApiTest#testGetOrder",
  "captures": [
    {
      "index": 0,
      "request":  { "method": "POST", "url": "http://localhost:8080/api/v1/auth/login", "body": "..." },
      "response": { "status": 200, "headers": {"Content-Type": "application/json"}, "body": "..." }
    },
    {
      "index": 1,
      "request":  { "method": "GET", "url": "http://localhost:8080/api/v1/orders/42", "body": null },
      "response": { "status": 200, "headers": {"Content-Type": "application/json"}, "body": "{\"status\":\"FILLED\",\"filled_at\":\"...\"}" }
    }
  ]
}
```
→ **fault plan**. Engine normalizes endpoints (`/orders/42` → `/orders/{id}`), matches
invariants, generates violations, applies exclusions and `stop_on_first_catch` state, and
returns one entry per required re-run:
```json
{
  "runs": [
    { "runId": "r0", "kind": "control",
      "targetIndex": 1, "responseBody": "<unmutated baseline body>" },
    { "runId": "r1", "kind": "invariant_violation",
      "targetIndex": 1,
      "responseBody": "{\"status\":\"FILLED\",\"filled_at\":null}",
      "fault": { "invariant": "filled_order_has_timestamp", "field": "filled_at", "mutation": "set_null" } }
  ]
}
```
Notes:
- `kind: "control"` is a re-run with the *unmutated* body. If the test fails the control run,
  it is flaky/state-dependent; the engine excludes its verdicts from the score and flags it.
- For each re-run, the shim serves cached baseline bodies for all requests and
  `responseBody` for `targetIndex`. **No real network calls during re-runs.**

### 4.3 `test/verdicts`  (adapter → engine, after executing the plan)
```json
{
  "sessionId": "...",
  "testId": "com.example.OrdersApiTest#testGetOrder",
  "verdicts": [
    { "runId": "r0", "passed": true },
    { "runId": "r1", "passed": true }
  ]
}
```
Semantics: for fault runs, `passed: true` = fault **escaped**; `passed: false` = fault
**caught** (include `"error"` string when failed). For control runs, `passed: false` = flaky.

### 4.4 `session/end`  (adapter → engine)
→ Engine writes `fault_simulation_report.json` + `antigen_report.html`, returns
`{ "summary": { "faults": 15, "caught": 11, "escaped": 4, "flakyTests": [...] }, "exitCode": 0|1 }`.

### Request matching during replay
The shim matches an outgoing request to a baseline capture by **sequence index within the
test** (the current `TestContext` counter model). This assumes a test issues the same requests
in the same order on re-run — acceptable v1 constraint; document it. Fallback matching by
(method, normalized path) is a v2 concern.

---

## 5. Migration plan

Each phase leaves the repo green and shippable.

**Phase 0 — Cache replay (prerequisite, independent of extraction).**
In `AspectExecutor.interceptApacheHttpClient`: when `context.getCurrentSimulationIndex() != -1`,
do **not** call `joinPoint.proceed()`; synthesize an `HttpResponse` from the captured pair
(mutated body for the target index, cached bodies otherwise). Verify against
`src/test/java/io/antigen/core/integration/mockrestapi/` (WireMock) — assert via WireMock
request journal that re-runs issue zero requests.

**Phase 1 — Internal seam.** Split `Runner` into:
- `FaultPlanner` (engine side): captures in → list of planned runs out (pure, returns the §4.2
  shape as Java objects). Absorbs the decision logic from `Runner` and `InvariantSimulator`.
- Adapter loop (runtime side): iterate plan, set context, `joinPoint.proceed()`, collect
  verdicts, submit to `FaultSimulationReport`.
Add control runs here. No process boundary yet; protocol shapes exist as Java DTOs with
Jackson serialization (these DTOs *are* the protocol schema source of truth).

**Phase 2 — Module split.** Gradle modules: `antigen-engine` (pure: config, invariant,
injection, simulation-scoring, report, analytics, normalizer — no AspectJ/JUnit/HttpClient
deps), `antigen-junit` (adapter: interceptor, http, runner loop), `antigen-cli` (the `ai.*`
generation loop). Enforce direction: adapters depend on engine, never the reverse.

**Phase 3 — Protocol server.** Thin server in `antigen-engine` exposing §4 messages over
localhost HTTP + stdio. Java adapter keeps the in-process path; the server exists for foreign
adapters. Write conformance vectors now (see §6).

**Phase 4 — Native binary.** GraalVM native-image build of `antigen-engine` server, CI matrix
for linux-x64/macos-arm64/windows-x64, versioned GitHub releases.

**Phase 5 — Foreign adapters.** Rebase the existing Python and TS POCs onto the protocol:
keep their runner-control mechanisms (pytest plugin hooks; the TS runner mechanism from the
POC), replace their local mutation/scoring logic with engine calls. Each adapter implements:
spawn/discover engine, baseline capture, replay shim, verdict reporting.

**Phase 6 — Conformance certification.** Adapters run the shared vector suite in their CI.

---

## 6. Conformance vectors

A versioned directory (`conformance/v1/`) of golden files:
- `*.baseline.json` → expected `*.plan.json` (exercises DSL operators, conditionals,
  cross-field refs, exclusions, endpoint normalization)
- `*.verdicts.json` → expected `*.report.json` (exercises scoring, control-run exclusion,
  stop_on_first_catch)

Engine CI replays them against the binary. Adapter CIs replay an end-to-end subset against a
local mock API. This is what guarantees detection rates are comparable across languages.

---

## 7. Related decisions (context for implementers)

- **Unified fault model.** Blanket contract faults (null/missing/empty on *every* response
  field — `core.injection.*`, the `contract:` config block) are being phased out: they cause
  the combinatorial blowup and unrealizable-fault noise. Everything becomes an **invariant
  violation**; what differs is the source: (a) user-declared YAML (current DSL), (b)
  spec-derived (generated from OpenAPI `required`/`enum`/`nullable`/`min`/`max` via an
  `antigen invariants --from-spec` scaffolding command, human-reviewed, committed), (c) future:
  agent-suggested, human-approved. The protocol already reflects this: plans carry
  `invariant_violation` runs; do not add a parallel contract-fault run kind.
- **Invariants are always committed YAML.** Derived/suggested invariants go through human
  review into `invariants/*.yml`. The engine never trusts runtime-generated rules; this keeps
  simulation deterministic and the score auditable.
- **Proxy interception is out of scope.** A mutating proxy cannot control test re-execution
  and forces routing/TLS changes. In-process shims per language are the model; a proxy is a
  possible future adapter for black-box runners (Postman/Newman) only.

- **Future authoring DSL.** A logic-based syntax may replace YAML as the invariant authoring
  format (see `dsl.md`). It compiles to the model described here and its parser lives only in
  the engine — sequenced after Phases 1–3. Nothing in this document changes; the protocol/JSON
  remains the interchange format.

## 8. Open questions

- JUnit 5 `InvocationInterceptor` as a replacement for the AspectJ `@Test` pointcut (would
  drop the weaver requirement for test control; HTTP capture still needs a shim — evaluate
  in Phase 2).
- Parallel test execution: protocol is per-test and safe, but `stop_on_first_catch` state in
  the engine becomes order-dependent. v1: document serial execution; revisit later.
- Non-JSON response bodies (XML, binary): engine currently assumes JSON maps. v1: pass
  through unmutated, mark unsupported in the plan.
- Header/status mutations (protocol-level faults — 5xx, timeouts) extend the plan schema
  later; reserve `kind` values, do not bake body-only assumptions into shims.


# Q&As
so anyway next phas would be to migrate simulation engine into a new repo, and them have 3 repos (java, python, ts) that install this, and call it at runtime, right?

● Directionally yes — that's the end state — but two corrections to the picture, one about how each language "installs" it and one about sequencing.

Correction 1: Java doesn't install or call the engine at runtime — it links it. The engine stays a plain Java library (jar). The Java adapter depends on it like any dependency and calls it in-process — no binary download, no
protocol round-trip, no spawned process. Only Python and TS install the engine as an artifact: their plugins (PyPI/npm packages) fetch the pinned native binary on first run — or ship it as platform-specific optional
dependencies, the esbuild model — spawn it, and speak the JSON protocol over localhost/stdio. So it's:

- antigen-engine → jar (for Java) and native binaries per platform (for everyone else), released together under one version
- antigen-junit → depends on the jar, in-process
- antigen-pytest / antigen-ts → fetch + spawn the binary, protocol clients

Correction 2: don't create the new repo yet. The repo split is the last mechanical step, not the next phase. Per the plan in engine_architecture.md, the next phases happen inside the current repo: Phase 0 (cache replay), Phase 1
(the Runner → FaultPlanner seam), Phase 2 (Gradle module split: antigen-engine / antigen-junit / antigen-cli with enforced dependency direction). That module boundary is the repo boundary, proven cheaply — moving code between   
modules in one repo is a refactor; moving it between repos is a release-coordination problem. Extract to a separate repo only once the engine module builds standalone, the protocol server exists (Phase 3), and the native build  
works (Phase 4). If you split first, you'll be debugging an entangled boundary across two repos with version pinning in the way.

On the final topology, a recommendation: three repos, not four. Keep engine + Java adapter + CLI + conformance vectors together in the current repo (the Java adapter consumes the engine in-process, so they naturally build and   
version together; the conformance vectors live next to the code they certify). Then antigen-python and antigen-ts as separate repos — they have foreign toolchains (PyPI, npm), foreign CI, and their own release cadence, so       
separation earns its cost there. Each pins an engine version and runs the conformance subset against the downloaded binary in its CI.

One thing to add when you get there: the engine's release artifact set becomes the contract — engine-{version}-{platform} binaries + the jar + protocol-version.json, published as GitHub Releases. Adapters resolve the binary by  
pinned version, and the engine rejects protocol mismatches at session/start (already in the doc, §4). That's what keeps three repos from drifting.

So the sequence is: finish Phases 0–2 in this repo → protocol server + native build (3–4) → then split Python/TS out as the two new repos (Phase 5), with the engine staying where it is.

=====================================================================================

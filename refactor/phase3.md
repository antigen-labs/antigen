# Phase 3a — Engine-side protocol core (in-process, no transport yet)

> Status: **done**. Phase 3b (the stdio/HTTP transport server + filesystem config loading) is now
> also done — see the Phase 3b section below.

## Goal

Make the protocol of architecture.md §4 real **as engine code**, before wrapping it in a socket.
The substantive, error-prone part of "Phase 3 — Protocol server" is not the transport; it is
having the engine own the full session lifecycle as pure, testable operations: captures → fault
plan, verdicts → score, summary. Build and prove that first (single JVM, golden-vector replay),
and 3b becomes a thin JSON shell over an already-correct façade — the same de-risking sequence as
2a→2b.

## What landed

### 1. Protocol message types — `io.antigen.core.protocol` (engine)

The §4 wire shapes as Jackson-friendly DTOs, the source of truth for the eventual schema:

| Message | Type |
|---|---|
| `session/start` ↔ result | `SessionStartRequest` (+ `AdapterInfo`) / `SessionStartResult` |
| `test/baseline` → plan | `BaselineRequest` (`List<Capture>`) → existing `FaultPlan` |
| `test/verdicts` | `VerdictsRequest` (`List<Verdict>`) |
| `session/end` | `SessionEndResult` (+ `Summary`) |
| errors | `ProtocolException` |

`Capture`/`CapturedRequest`/`CapturedResponse` **implement the engine's capture contract**
(`http.Request`/`http.Response`), so a deserialized `test/baseline` message feeds `FaultPlanner`
with zero conversion. This is the transport-shaped `Capture` DTO deferred from Phase 1/2a (phase1.md
decision #3, phase2a decision #2): it was correctly *not* invented earlier because it had no
consumer; the protocol is that consumer.

### 2. `EngineSession` — the transport-agnostic façade (engine)

One instance per session; owns a per-session `FaultSimulationReport` and the plans it issued.
Implements the four operations purely — **no sockets, no test execution, no payload construction**
(architecture §3 knowledge boundary):

- `start(SessionStartRequest)` — rejects a mismatched `protocolVersion` (engine speaks `"1"`),
  opens a session.
- `plan(BaselineRequest)` — resolves per-test config, calls `FaultPlanner`, remembers the plan so
  verdicts can resolve `runId`s.
- `submitVerdicts(VerdictsRequest)` — folds outcomes into the report (see scoring below).
- `end()` — writes the JSON report, returns the `Summary` + `exitCode`.

### 3. Verdict scoring unified in the engine — `simulation.VerdictScorer`

The protocol model puts **verdict interpretation in the engine**: adapters report raw pass/fail,
the engine decides caught/escaped/flaky (architecture §4.3). That interpretation already existed,
inline, in the adapter's `Runner`. Extracted it into `VerdictScorer` (one definition of
`passed → caught/escaped`, and note → not-caught) and made **both** paths call it:

- `EngineSession.submitVerdicts` — control gate (failed control ⇒ flaky, exclude all its
  verdicts), then notes, then per-run verdicts by `runId`.
- `Runner` — now delegates its result-building to `VerdictScorer.scoreFaultRun`/`scoreNote`; the
  live console prints and `stop_on_first_catch` bookkeeping stay in the adapter.

Single source of truth: a detection rate now means the same thing whether the test ran in-process
(Java/AspectJ) or over the wire (a foreign adapter) — which is the entire point of the engine
(architecture §1).

### 4. Conformance vectors v1 — `conformance/v1/` + `ConformanceVectorTest` (engine)

Four golden scenarios replayed through `EngineSession` (architecture §6): `status-enum-caught`
(value invariant, caught), `quantity-escaped` (two boundary mutations, both escape),
`original-violation-note` (baseline already violates), `conditional-not-applicable-note`
(conditional with unmet precondition). Each pins `baseline → expected-plan` and
`verdicts → expected-report`. These goldens are the cross-language contract every adapter must
reproduce.

The replay test is a golden-master harness: `-Dantigen.conformance.regenerate=true` rewrites the
goldens (review the diff — it is a contract change), default mode asserts. Comparison is
**order-insensitive** (parsed JSON trees) so map iteration order never flakes the suite.

## Key decisions

1. **Façade before transport (3a/3b split).** The transport is plumbing; the session semantics are
   the risk. Proving them with in-JVM golden replay means 3b is a JSON shell over correct code, not
   a debugging exercise across a socket. Mirrors the 2a→2b sequencing the user approved.
2. **Engine owns verdict scoring; `Runner` delegates.** Rather than duplicate the caught/escaped/
   flaky rules in the protocol path, both paths share `VerdictScorer`. Prevents the in-process and
   wire scores from drifting.
3. **Capture DTOs *are* the capture contract.** `CapturedRequest`/`CapturedResponse` implement
   `Request`/`Response` instead of being converted, so the planner is indifferent to whether a
   capture arrived in-process or off the wire.
4. **`stop_on_first_catch` is not applied in the plan.** The engine returns the full plan; the
   adapter may skip runs live (as `Runner` does). `submitVerdicts` tolerates missing verdicts for
   skipped runs. The score is identical either way (`caught_by_any_test` aggregates), so this stays
   a re-run *optimization*, not a scoring input — consistent with phase1.md.
5. **`exitCode` is health, not a gate.** `session/end` returns `exitCode = 0`; escapes are a
   reported, expected outcome (~15%, see gotchas.md), and gating policy belongs to the
   adapter/build, not the engine.
6. **Deterministic plan serialization.** `FaultPlanner` now builds mutated bodies with a
   `LinkedHashMap` (was `HashMap`), preserving baseline field order so a planned `responseBody`
   serializes identically every run. Required for byte-stable golden vectors; harmless to the
   in-process path. (Jackson already parses bodies into `LinkedHashMap`s, so round-trips are
   order-stable.)
7. **`FaultSimulationReport` made per-session-constructible.** Constructor is now public and a
   `counts()` summary added; the `getInstance()` singleton remains for the in-process adapter.
   `EngineSession` uses its own instance so sessions (and conformance scenarios) score in isolation.

## The config-loading gap (why 3b is non-trivial, not just sockets)

`session/start` is supposed to load config from a filesystem `configDir`/`specPath` and resolve
per-test config by `testId` **string**. The current config layer is JVM-coupled: `ConfigResolver`
resolves against a `Class<?>`, and `InvariantConfigCache` loads invariants from the **classpath**.
A foreign adapter has neither a `Class` nor the engine's classpath. So `EngineSession.start`
currently opens a session with a global-config fallback (resolver returns `null` → `FaultPlanner`
uses `SimulatorConfig`), and the conformance harness injects the resolved config directly from each
scenario's `invariants.json`. Closing this — filesystem config loading + `testId`-keyed resolution
— is the real work of 3b, alongside the transport.

## Verification

- `:antigen-engine:test` — green, incl. `EngineLayerTest` purity (the protocol package adds only
  Jackson/`java.util`/`java.util.function` to the engine — no forbidden runtime libs) and all four
  `ConformanceVectorTest` scenarios against committed goldens.
- Unit tier (`*.unit.*`, all modules) — green.
- Integration tier (`:antigen-test-runner:test`, WireMock :8089, `runWithAntigen=true`) — green;
  LTW weaving intact; `Runner`'s `VerdictScorer` delegation behaves identically.
- E2e tier (`:antigen-test-runner:test`, live :8000) — **39 faults, 33 caught, 6 escaped (15%),
  0 infra-errors**, the same six known-uncatchable escapes as the 1a/1b/2a/2b baseline → behavior
  preserved across the scoring refactor.

## Deferred to 3b

- The transport server in `antigen-engine`: stdio first (CI-friendly, no port discovery), then
  localhost HTTP; decode JSON onto the `EngineSession` methods; `protocolVersion` negotiation on
  the wire.
- Filesystem config loading from `configDir`/`specPath` and `testId`-keyed resolution (the gap
  above) — decoupling `ConfigResolver`/`InvariantConfigCache` from `Class`/classpath.
- End-to-end conformance subset run by a foreign adapter against a local mock API (architecture §6)
  — lands with the first foreign adapter (Phase 5).

---

# Phase 3b — Transport server + filesystem config loading

> Status: **done**. The engine now runs as a protocol server (stdio + localhost HTTP) and loads
> config from a `configDir` keyed by `testId` — a foreign adapter has everything it needs to drive
> the engine over the wire.

## Goal

Close the two things 3a deferred: (1) decouple config resolution from the JVM `Class`/classpath so
`session/start`'s `configDir`/`testId` actually drive it, and (2) put a thin JSON transport over the
already-correct `EngineSession` façade. With 3a's golden-vector-proven semantics underneath, this is
plumbing — exactly the de-risking the 3a/3b split bought.

## What landed

### 1. Filesystem config loading by `testId` — `config` (engine)

The `Class`/classpath coupling turned out shallow, so the decoupling is small and behavior-preserving:

- **`ConfigResolver.resolve(List<FeatureConfig>, String className, …)`** — a language-neutral
  overload taking features + class name as plain data. The original `resolve(Class, …)` now
  delegates to it (`features = InvariantConfigCache.getInstance().getAllFeatures()`,
  `className = testClass.getName()`), so the in-process path is byte-for-byte unchanged.
- **`InvariantConfigScanner.scanDirectory(Path)`** and **`TestScopedConfigLoader.loadFromFile(Path)`**
  — filesystem siblings of the classpath loaders, reusing the same per-file parsing.
- **`ConfigDirLoader`** — ties them together: scans `<configDir>/simulation/invariants/*.yml` once,
  then resolves a `testId` (`fully.qualified.ClassName#method`) to a `ResolvedTestConfig`, reading
  the optional `<configDir>/simulation/<ClassName>.antigen.yml` lazily. This *is* the
  `Function<String,ResolvedTestConfig>` `EngineSession` wanted.
- **`EngineSession.start`** now builds that resolver from `request.getConfigDir()` (falling back to
  the global-config resolver when no dir is given), closing the gap 3a documented.

Global `SimulatorConfig` settings stay at defaults in this path (they are all constant defaults now
anyway, §3a) — every invariant arrives through the resolved config.

### 2. Transport — `protocol.transport` (engine)

- **`ProtocolDispatcher`** — transport-agnostic router. Envelope (v1): request
  `{"method","params"}` → response `{"result"}` or `{"error":{"message"}}`. Methods map 1:1 to the
  façade (`session/start` → `EngineSession.start` + store; `test/baseline` → `plan`;
  `test/verdicts` → `submitVerdicts`; `session/end` → `end` + evict). Holds open sessions by id, so
  one engine process serves many. `ProtocolException` and any other throw become an error envelope,
  never a crash.
- **`StdioServer`** — JSON-Lines over stdin/stdout (one request per line). The spawn-and-pipe model;
  "stdio acceptable for CI" (architecture §4).
- **`HttpProtocolServer`** — JDK `com.sun.net.httpserver` bound to an ephemeral **loopback** port;
  POST the envelope to `/`. No third-party HTTP dep, so the `EngineLayerTest` purity guard stays
  green and the engine remains GraalVM-native-buildable (Phase 4).
- **`EngineServer`** (`main`) — `stdio` (default) or `http`. Launches the dispatcher and serves.

### 3. The stdout-pollution fix

The engine logs diagnostics with `System.out.println` throughout. On a stdio transport that would
corrupt the JSON response channel. `EngineServer` captures the real stdout, redirects
`System.out → System.err`, and writes protocol bytes only to the captured stream — engine chatter
goes to stderr, the protocol channel stays clean. In HTTP mode the only stdout line is the
`ANTIGEN_PORT=<port>` banner the adapter parses.

## Key decisions

1. **Overload, don't rewrite, `ConfigResolver`.** The `Class`-based path delegates to the
   data-based one, so in-process resolution is provably unchanged (the e2e baseline confirms it),
   and the protocol path is a peer entry point rather than a fork.
2. **`ConfigDirLoader` owns the directory convention**, not `EngineSession`. The session stays a
   pure orchestrator over a `Function<String,ResolvedTestConfig>`; where config *comes from* is a
   config-layer concern. Swapping in a different source later touches one class.
3. **One dispatcher, two transports.** stdio and HTTP are thin shells over the same
   `ProtocolDispatcher`, so wire behavior can't diverge by transport — same reasoning as
   `VerdictScorer` unifying the score.
4. **JDK httpserver, ephemeral loopback port.** No new dependency (purity/native-build safe),
   loopback-only (the engine is a local subprocess, not a network service), ephemeral port printed
   on stdout (no port-collision config).
5. **Redirect `System.out`, don't rewrite every log call.** Re-routing the existing logging to a
   proper logger is a separate cleanup; capturing stdout at the entry point is the one-line,
   zero-risk fix that makes stdio framing reliable today.

## Verification

- `:antigen-engine:test` — green, incl.:
  - `EngineLayerTest` purity — the transport adds only `com.sun.net.httpserver` (JDK, not a
    forbidden runtime lib) to the engine; guard still passes.
  - `ConfigDirLoaderTest` — invariants load from a `@TempDir` configDir and resolve by `testId`;
    per-class `.antigen.yml` merges; missing dir yields no features.
  - `ProtocolDispatcherTest` — full `start → baseline → verdicts → end` round-trip as JSON
    envelopes reproduces the façade's plan/summary; bad version / unknown session / unknown method
    surface as error envelopes.
  - `HttpProtocolServerTest` — real loopback-socket round-trip (start + end) over the HTTP transport.
  - all four `ConformanceVectorTest` scenarios still pass.
- Unit tier (`*.unit.*`, all modules) — green (the new config/protocol tests live under `*.unit.*`).
- Integration tier (`:antigen-test-runner:test`, WireMock :8089, `runWithAntigen=true`) — green;
  LTW weaving intact.
- E2e tier (`:antigen-test-runner:test`, live :8000) — **39 / 33 / 6 (15%), 0 infra-errors**, the
  same six escapes as every prior phase → the `ConfigResolver` delegation preserved in-process
  behavior exactly.

## Follow-ups (not in scope)

- **Phase 4 — GraalVM native-image** build of `EngineServer`: reflection config for Jackson/
  SnakeYAML/Swagger, then a per-platform binary. `EngineLayerTest` is the standing precondition.
- Route the engine's `System.out.println` diagnostics through SLF4J (then the `EngineServer` stdout
  redirect becomes belt-and-suspenders rather than load-bearing).
- `specPath` loading (OpenAPI-derived invariants over the protocol) — `session/start` accepts the
  field but the engine does not yet act on it.
- Multi-line / streaming request framing for stdio if a single capture body ever exceeds a sane line
  length; v1 assumes one-line envelopes.

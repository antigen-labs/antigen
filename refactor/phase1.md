# Phase 1a — FaultPlanner seam (decision / execution split)

> Status: **done** (behavior-preserving). Phase 1b (control runs + flaky handling) deferred —
> see "Deferred to 1b" below.

## Goal

Split fault simulation's two interleaved concerns into a pure **decision** half and a thin
**execution** half (architecture.md §3, Phase 1):

- **Decision** (engine, pure): captures in → a fault plan out. No test execution, no report state.
- **Execution** (adapter/runtime): iterate the plan, re-run the test, collect verdicts, record.

This is the internal seam that later becomes the process boundary. The plan DTOs are the
in-process form of the protocol fault plan (architecture.md §4.2) and are intended as the source of
truth for the future wire schema.

## What changed

**New package `io.antigen.core.plan` (pure):**

| Type | Role |
|---|---|
| `FaultPlan` | output: `List<PlannedRun> runs` + `List<PlannedNote> notes` |
| `PlannedRun` | one re-run: `runId, kind, endpoint, invariant, field, mutation, targetIndex, responseBody` |
| `RunKind` | `CONTROL` (reserved for 1b), `INVARIANT_VIOLATION` |
| `PlannedNote` | a pre-determined "not caught" outcome needing no re-run (`reason`, composed `message`) |
| `FaultPlanner` | `plan(capturedRequests, resolvedConfig) → FaultPlan` |

`FaultPlanner` absorbs the decision logic formerly spread across `Runner` and `InvariantSimulator`:
request-selection strategy (`filterRequestsByStrategy`), `shouldSimulateResponse` gating, endpoint
normalization, invariant resolution, baseline-satisfaction check, `ViolationGenerator` +
`applyMutation`, and serialization of each **fully-formed** mutated body. It emits the two
non-executed outcomes as `PlannedNote`s instead of writing to the report:
- `ORIGINAL_VIOLATION` — baseline response already fails the invariant (assertion gap).
- `NOT_APPLICABLE` — conditional invariant whose precondition is unmet (no mutation generated).

**`Runner` is now the adapter loop.** It asks `FaultPlanner` for the plan, records notes as
not-caught, then for each run: `setSimulatedResponse(baseline.withBody(responseBody))`,
`setCurrentSimulationIndex(targetIndex)`, `resetRequestCounter()`, `joinPoint.proceed()`, maps
pass→escaped / throw→caught, records the verdict. It holds the only JUnit/AspectJ dependency.

**`InvariantSimulator` deleted.** Its decision logic moved to `FaultPlanner`; its execution logic
moved to `Runner`. It had no callers other than `Runner` (`hasInvariants` was dead).

## Key decisions

1. **Planner stays free of the report singleton.** Non-executed outcomes are returned as data
   (`PlannedNote`) rather than recorded inside the planner. This is what makes `plan()` a pure
   function and keeps it movable to the engine module in Phase 2.

2. **`stop_on_first_catch` is applied live in the adapter, not in the planner.** It depends on
   cross-test outcome state (has another test already caught this invariant?), which is inherently
   execution-time and order-dependent (architecture.md §8). The planner therefore emits *all* runs;
   `Runner` skips a run whose invariant is already caught and marks caught after a catch — the exact
   previous behavior, now expressed as a scheduling concern in the adapter. Moving this into engine
   plan-state is a later concern, once the protocol server holds session state.

3. **Input type is `TestContext.RequestResponsePair` for now.** The planner reads the existing
   capture type rather than a new `Capture` DTO. Introducing a transport-shaped input DTO (and the
   `session/baseline` request body) is deferred to Phase 2/3 when the module boundary and protocol
   server land; doing it now would be churn without a consumer.

4. **Micro-deviation in note/run record ordering.** The old code recorded notes and runs
   interleaved per invariant; the adapter now records all notes first, then all runs. Per
   `(endpoint, invariant)` key the *content* is identical (a key yields either notes or runs, never
   both), and the report map is a `ConcurrentHashMap` with no stable key order anyway. The only
   observable difference is list order when the same invariant recurs across multiple requests in
   one test — verdicts and `caught_by_any_test` are unaffected.

## Verification

- `./gradlew compileJava` — clean (only the documented benign `adviceDidNotMatch` warning).
- Unit tier (`io.antigen.core.unit.*`, `runWithAntigen=false`) — green.
- Integration tier (`io.antigen.core.integration.*`, WireMock :8089, `runWithAntigen=true`) —
  green. Report is empty **as expected**: the only committed invariants are `trading-*` (demoapi
  endpoints); none match the mock endpoints (`/users`, `/payments`), so the plan is empty.
- E2e tier (`*demoapi.*`, live oms-demo-api :8000, `runWithAntigen=true`) — the real validation of
  the planner with live invariants: **39 faults, 33 caught, 6 escaped (15%), 0 infra-errors**. In
  the documented healthy 15–20% band; the 6 escapes are the known-uncatchable set (5 cross-field
  temporal `*_updated_after_created` / `created_before_filled` + the intentional `token_type_bearer`
  `include_only` DEMO invariant). Matches the pre-refactor baseline → behavior preserved.

## Unit coverage (added after 1a)

`src/test/java/io/antigen/core/unit/plan/FaultPlannerTest.java` — direct coverage of the pure
planner: satisfied invariant → fully-formed violation run (mutated field, other fields preserved);
baseline-already-violates → `ORIGINAL_VIOLATION` note, no run; conditional precondition unmet →
`NOT_APPLICABLE` note; non-2xx / no-invariant / empty captures → empty plan; sequential unique run
ids; plus the control-run cases below. Precursor to the Phase 6 conformance vectors.

---

# Phase 1b — Control runs + flaky exclusion

> Status: **done**. Builds on the 1a seam; adds new behavior (roadmap #4, architecture.md §4.2/§4.4).

## Goal

Before scoring, re-run each test once with the **unmutated** cached baseline. If the test fails
that replay it is flaky/state-dependent — flag it and exclude all its verdicts from the score. This
makes the detection rate trustworthy and gives flakiness detection for free.

## What changed

- **`RunKind.CONTROL` is now emitted.** `FaultPlanner` attaches **one** control run per test (new
  `FaultPlan.control` field), not one per request — a control validates that the whole test replays
  deterministically. It targets the first request that contributed invariant activity and carries
  that request's **unmutated** baseline body. Emitted whenever there is any activity to gate
  (runs *or* notes); `null` otherwise.
- **`Runner` gates on it.** The control runs first. On failure: `REPORT.markFlaky(testName)`,
  log, and **return** — skipping all notes and invariant runs for that test (excluded from score).
  On pass: proceed as in 1a.
- **Execution unified.** Extracted `runOnce(...)` (install body at target index → `proceed()` →
  return throwable-or-null). Control and invariant runs share it; only verdict *interpretation*
  differs (control: failure = flaky; violation: failure = caught).
- **Report surface.** `FaultSimulationReport` gains a `flakyTests` set (`markFlaky` /
  `getFlakyTests`) and prints a "Flaky/excluded (failed control run)" section in the console
  summary (now printed even when every result was excluded).

## Key decisions

1. **One control per test, not per request.** The flaky signal is about the test's determinism
   under replay; a single whole-test re-run is the faithful check and matches the protocol's single
   control entry (architecture.md §4.2).
2. **Control must keep `currentSimulationIndex >= 0`.** `AspectExecutor` treats index `-1` as the
   *baseline* branch and would issue **real** HTTP calls. So the control run sets a valid target
   index with `responseBody == the unmutated baseline body`: the replay path is used, every request
   is served from cache, nothing is mutated. (Setting `simulatedResponse` to the same bytes as the
   cache is a deliberate no-op mutation.)
3. **Flaky ⇒ exclude everything, including notes.** Notes are not-caught verdicts; a flaky test's
   notes are noise, so they are skipped too. Non-flaky tests on the same invariant still record, so
   `caught_by_any_test` is unaffected.
4. **Flaky list is console + in-memory only for now.** Not yet written to
   `fault_simulation_report.json` (would be a schema addition); the protocol `session/end` summary
   (architecture.md §4.4) is the place to surface it when the server lands.

## Verification

- Unit tier — green (control-run cases added: control present for run-bearing and note-only plans;
  absent when nothing to simulate; replays the unmutated baseline body).
- Integration tier — green (empty plan as before; no invariants for mock endpoints).
- E2e tier (live demo-api) — **39 faults, 33 caught, 6 escaped (15%), 0 infra-errors, 0 flaky**.
  Identical to the 1a baseline: the demoapi suite is deterministic under cache replay, so no test is
  excluded and scoring is unchanged. (A non-zero flaky count here would have signalled a replay
  fidelity bug, not test flakiness — useful canary.)

## Follow-ups surfaced (not in scope)

- Surface `flakyTests` in the JSON/HTML report and the future `session/end` summary.
- Array-path invariants (`$[*].field`) still generate zero mutations (`ViolationGenerator` skips
  them) despite being documented — pre-existing, unchanged here.

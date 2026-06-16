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

## Deferred to 1b

- **`CONTROL` runs.** `RunKind.CONTROL` is reserved but not yet emitted. 1b: planner emits a control
  run (unmutated baseline body) per simulated request; adapter runs it first and, if it **fails**,
  flags the test flaky/state-dependent and excludes its verdicts from the score. Needs new report
  surface for flaky flagging (architecture.md §4.4 `flakyTests`).
- **A `FaultPlanner` unit test.** The planner is now a pure function and should get direct
  unit coverage (captures + config → expected plan), independent of the live e2e run. Natural
  precursor to the Phase 6 conformance vectors.

## Follow-ups surfaced (not in scope)

- Array-path invariants (`$[*].field`) still generate zero mutations (`ViolationGenerator` skips
  them) despite being documented — pre-existing, unchanged here.

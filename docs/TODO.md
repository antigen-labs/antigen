# Antigen — Engine Architecture TODO

Working backlog for the engine extraction (`docs/knowledge/architecture.md`). One section per
item; keep context inline so any agent can pick it up cold. Mark items done in place rather than
deleting them, so the history of what was verified stays visible.

---

## Phase 0 verification — prove cache replay issues zero network calls

**Status:** open.

**Context.** Phase 0 (cache replay) is *already implemented in code* — `architecture.md` still
lists it as planned, but the work is done:
- `AspectExecutor.interceptApacheHttpClient` (`src/main/java/io/antigen/core/interceptor/AspectExecutor.java`)
  has two branches keyed on `TestContext.currentSimulationIndex`. Baseline (`== -1`) makes the
  real call and captures every request/response pair. Re-run (`!= -1`) serves **cached** baseline
  bodies by sequence index and swaps the mutated body in only at the target index — it does **not**
  call `joinPoint.proceed()` on the HTTP client.
- Replay matching is by sequence index within the test (`TestContext.currentRequestCounter`), the
  documented v1 constraint (`architecture.md` §4 "Request matching during replay").

**The gap.** The behavior is implemented but **unproven by test**. `architecture.md` Phase 0
calls for verifying against the WireMock integration suite "that re-runs issue zero requests" — no
such assertion exists today. The integration tests
(`src/test/java/io/antigen/core/integration/mockrestapi/` — `UsersTest`, `PaymentTest`,
`ECommerceApiTest`) run against WireMock on `localhost:8089` but never assert request counts, so a
regression that reintroduced real network calls during re-runs would pass silently. This is exactly
the class of bug the gotchas file warns about (the "every fault caught/escaped" uniform-report
trap) — a green build does not mean a valid simulation.

**What to do.**
- Add an integration test that, with `-DrunWithAntigen=true`, runs a test exercising a WireMock
  endpoint and then asserts via the **WireMock request journal** that the endpoint received exactly
  **one** request (the baseline) regardless of how many invariant mutations were simulated.
- The journal check is the real assertion. Use WireMock `verify(exactly(1), getRequestedFor(...))`
  (or `findAll` + count) after the test method completes. Re-runs must add **zero** journal
  entries.
- Cover both a single-request test and a multi-request test (chained calls), since replay matching
  is index-based — a multi-request test proves the counter resets correctly per re-run
  (`TestContext.resetRequestCounter`) and that non-target indices are also served from cache.
- Prerequisite: the test must actually trigger simulation — the endpoint needs at least one
  invariant configured so `InvariantSimulator` generates mutations and forces re-runs (otherwise
  there is nothing to verify). Confirm the invariant fixtures under
  `src/test/resources/antigen/simulation/invariants/` cover the mock endpoint, or add one scoped via
  `include_only`.

**Done when.** A WireMock-journal assertion fails if any simulation re-run issues a real HTTP call,
and it passes on the current `AspectExecutor`. Run via the `test-integration` skill. Once green,
update `architecture.md` to mark Phase 0 complete.

# Phase 5 — Foreign adapters: TypeScript, then Python

> Status: **done (TypeScript + Python).** Rebases the existing TS and Python POCs
> (`antigen-typescript`, `antigen-python`, sibling repos) off their local mutation/scoring engines
> and onto the §4 protocol, each spawning the Phase 4 fat jar over **localhost HTTP**. TypeScript
> was rebased first (replay-shim risk lowest there); the proven protocol-client + replay shape was
> then ported to the pytest adapter.
>
> Note on repos: this plan doc lives in the engine repo (`refactor/`, next to phases 0–4); the
> **implementations land in `antigen-typescript` and `antigen-python`**. Per architecture §5 the
> foreign adapters become their own repos with their own toolchains/CI.

## Goal

Make the TS adapter "dumb glue" (architecture §3): it spawns/discovers the engine, captures the
baseline exchanges, executes the engine's plan (replay shim), and reports verdicts. **All**
mutation generation and caught/escaped/flaky scoring move to the engine. The adapter ships no
fault strategies and no report math.

## Why TypeScript before Python

1. **Re-execution is trivial here, gnarly in Python.** The TS POC already controls re-runs by
   calling the test function again (`FaultSimulator.interceptTest` → `runWithContext`). That *is*
   the replay shim. The pytest POC re-runs the test fn from `pytest_runtest_teardown` with
   `item.instance` + re-resolved fixtures — the single riskiest mechanism in the phase. Prove the
   protocol-client + replay pattern in the easy harness first, then port the proven shape.
2. **Structure already matches the target.** `adapters/{base,registry,http-clients/axios,
   test-frameworks/jest}` are the seams the architecture wants; the replay loop in
   `executeFaultSimulations` is structurally the adapter loop.
3. **Static typing pays off against the protocol** — the §4 DTOs are Jackson types; mirroring them
   as TS interfaces and validating against the conformance vectors is cleaner than untyped dicts.

## Protocol binding (engine contract this adapter speaks)

Transport: **localhost HTTP** (architecture's primary transport — `HttpProtocolServer`). The
adapter spawns `java -jar antigen-engine-<v>-all.jar http`, reads the `ANTIGEN_PORT=<n>` banner
from stdout, and POSTs one request envelope per call to `http://127.0.0.1:<n>/`. Envelope:
`{"method","params"}` → `{"result"}` | `{"error":{"message"}}`.

> **Why HTTP, not stdio.** stdio (JSON Lines) is simpler to spawn, but Jest sandboxes each test
> file's module registry, so a per-file simulator would spawn its own engine and its own report
> (last write wins). One engine + **one shared session for the whole run** is required, so the
> engine is spawned once in Jest `globalSetup` and reached over HTTP (a shared port) by every
> worker; `globalTeardown` ends the session and kills the process. stdio remains the right choice
> for a single-process adapter (e.g. the future Python/pytest path may differ).

| Message | params (adapter→engine) | result |
|---|---|---|
| `session/start` | `{protocolVersion:"1", configDir, specPath?, adapter:{name,version}}` | `{sessionId}` |
| `test/baseline` | `{sessionId, testId, captures:[{index,request:{method,url,headers,body},response:{status,headers,body}}]}` | plan: `{control, runs:[{runId,kind,endpoint,invariant,field,mutation,targetIndex,responseBody}], notes, empty}` |
| `test/verdicts` | `{sessionId, testId, verdicts:[{runId,passed,error?}]}` | `{ok:true}` |
| `session/end` | `{sessionId}` | `{summary:{faults,caught,escaped,flakyTests}, exitCode}` |

Verdict semantics (§4.3): for fault runs `passed:true` = **escaped**, `passed:false` = **caught**
(carry `error`). For the `control` run `passed:false` = **flaky** (engine drops all that test's
verdicts). The engine writes `build/antigen/fault_simulation_report.json` (relative to the
**spawned process CWD**) at `session/end`.

Config: the engine loads invariants from `configDir/simulation/invariants/*.yml` (filesystem,
`ConfigDirLoader` — Phase 3b). The TS repo ships an `antigen/` config dir with invariants for the
mock endpoints; `testId` carries no class, so invariants match by **endpoint+method** (no
`include_only`).

## What lands in `antigen-typescript`

- `src/engine/protocol.ts` — TS mirror of the §4 envelopes, plan, verdicts, summary.
- `src/engine/client.ts` — `EngineClient(port)`: stateless HTTP RPC shim
  (`sessionStart / baseline / verdicts / sessionEnd`).
- `src/engine/process.ts` — `EngineProcess`: spawns the fat jar in `http` mode, resolves the
  `ANTIGEN_PORT` banner, `unref`s the child so it can't keep Jest alive, kills on stop. Jar +
  `java` resolved via env (`ANTIGEN_ENGINE_JAR`, `ANTIGEN_JAVA`) with a sibling-repo default.
- `src/engine/session-file.ts` — on-disk handoff (`build/antigen/engine-session.json`: port +
  sessionId + pid) from globalSetup to the per-worker simulators (Jest module isolation again).
- `src/core/interceptor.ts` — rewritten `FaultSimulator`: connects to the shared session; per
  test, run baseline (capture ordered exchanges) → `test/baseline` → execute `[control, ...runs]`
  injecting `responseBody` by `targetIndex` → `test/verdicts`. **No** StrategyFactory / report math.
- `src/core/context.ts` — ordered `captures[]` (baseline) + a `replay` cursor (targetIndex, body,
  per-run counter) for the shim.
- `tests/global-setup.ts` / `tests/global-teardown.ts` — own the one engine process + session;
  teardown ends the session (engine writes the report) and `taskkill`/`kill`s the engine.
- `jest.config.js` — `globalSetup`/`globalTeardown`, `maxWorkers: 1` (serial scoring), `forceExit`.
- `antigen/simulation/invariants/{users,payments}.yml` — engine config for the mock suite.
- `package.json` — `test:antigen` / `test:faults` = `cross-env ANTIGEN_ACTIVE=true jest`.

### Orphaned (replaced by the engine)
`src/strategies/*` (mutation generation), `src/report/generator.ts` (scoring), and
`src/config/loader.ts` (local fault toggles/exclusions) are no longer on the active path —
exclusions are an engine concern (an unmatched endpoint simply plans nothing). Left on disk
(unreferenced) rather than deleted; a follow-up cleanup commit can remove them.

## v1 deviations (documented, not bugs)

- **Replay still lets the local mock call happen** and overrides the response body in the axios
  response interceptor, rather than short-circuiting the network like the JVM cache shim
  (architecture §4 "no real network calls during re-runs"). Acceptable against a local WireMock;
  a true request-adapter short-circuit is a follow-up. Bodies served on replay are the engine's
  `responseBody` for `targetIndex` and the cached baseline body otherwise.
- **Serial execution.** One engine process, serialized RPC; matches the §8 open question
  (parallel `stop_on_first_catch` state) — document serial, revisit later.
- `GET /api/v1/payments/{id}` is **out of scope**: the `pay_…` id has an underscore and doesn't
  normalize to `{id}` (same trap the integration tier documents). Only `POST /api/v1/payments`.

## Verification (this machine)

- Engine fat jar built (`antigen-engine-1.0.0-SNAPSHOT-all.jar`, 16M), spawned in `http` mode by
  the adapter against local WireMock (TS `mocks/mappings`, :8089).
- **Active** (`ANTIGEN_ACTIVE=true jest`): 3 suites / 18 tests pass; engine session summary
  **faults 12, caught 9, escaped 3, flaky 0**; clean process exit, no leaked `java`. The report
  (`build/antigen/fault_simulation_report.json`) is non-empty with correct cross-test attribution
  — e.g. `user_valid_theme` caught by "should handle user preferences", `user_positive_id` by
  "should get user details" — and **0 infra-errors** (all `caught_by[].error` are real
  `toBe`/`toBeGreaterThan` assertion failures, no `cannot be cast` / `no content-type`). The 3
  escapes are the intended `is_not_empty` rules (`username`, `email`, payment `id`) against
  presence-only `toBeDefined` assertions.
- **Inactive** (`jest`): 3 suites / 18 tests pass, engine not spawned — the default path is
  undisturbed.
- TS strict type-check (`tsc -p tsconfig.json --noEmit`) green.

## Python adapter (`antigen-python`)

Same protocol, same "dumb glue" contract; the proven TS shape ported to pytest. Key differences,
all stemming from pytest's **single-process** model (no Jest-style module-registry sandboxing):

- **No on-disk session handoff.** The engine + session live on the pytest `config` object. The
  plugin spawns the fat jar once in `pytest_configure`, opens one session, and ends it + kills the
  engine in `pytest_unconfigure`. (Jest needed `engine-session.json` only because each test file
  got a fresh module registry.) `pytest-xdist` is therefore unsupported — documented, not fixed.
- **Replay shim = re-run the pytest item.** The plugin wraps `pytest_runtest_call` as a
  hookwrapper: the real call (the `yield`) is the baseline run (real HTTP, captured); then
  `FaultSimulator.simulate` re-runs `item.runtest()` once per planned run (`[control, ...runs]`),
  with `requests.Session.request` monkeypatched to serve the engine's `responseBody` for
  `targetIndex` and cached bodies otherwise — **no network on replay** (a real `requests.Response`
  is constructed). The baseline outcome is what pytest reports; replay failures are swallowed into
  verdicts. A failing baseline skips simulation (genuine failure).
- **Engine RPC bypasses the shim.** The protocol client uses `http.client` (stdlib), not
  `requests`, so the monkeypatch never intercepts engine calls.
- **Files** (package `antigen`, project dir `antigen-pytest/`): `antigen/engine/{protocol,client,
  process}.py`, `antigen/core/runner/{context,fault_simulator}.py`,
  `antigen/core/interceptors/http_interceptor.py` (rewritten), `antigen/pytest_plugin.py`
  (rewritten). Activation: `--antigen` flag or `ANTIGEN_ACTIVE=true`
  (`--metatest` kept as a deprecated alias). Config dir `antigen/simulation/invariants/{users,
  payments,orders}.yml`; `pytest.ini` anchors rootdir so `pytest --antigen` "just works".
- **Removed (replaced by the engine):** the old `core/faults/*`, `core/reporter/*`, `config/*`,
  `cli/*`, `utils/*`, and `core/runner/fault_runner.py` (local mutation/scoring/CLI) were deleted in
  the rename to package `antigen`; the root `config.yml` is left as an orphan, off the active path.

### Verification (this machine)

- **Active** (`pytest --antigen`): 18 tests pass; session summary **faults 15, caught 12,
  escaped 3, flaky 0**; clean process exit, **no leaked `java`** (after switching the Windows
  teardown to `taskkill /PID <pid> /T /F` — `Popen.terminate()` is unreliable against the JVM).
  Report has correct cross-test attribution — e.g. `user_valid_theme` caught by
  `test_user_preferences_with_null_values`, `user_positive_id` by `test_get_user`/`test_get_user_details`,
  nested fields caught by the *right* invariant (deep-copy holds) — and **0 infra-errors** (all
  `caught_by[].error` are real `assert` failures). The 3 escapes are the intended `is_not_empty`
  rules (`username`, `email`, payment `id`) against `is not None` assertions.
- **Inactive** (`pytest`): 18 tests pass, engine not spawned — default path undisturbed.

## Follow-ups

- True replay short-circuit (request-adapter, zero network) to match engine semantics exactly.
  (Python already constructs a synthetic `requests.Response` with no network on replay; TS still
  lets the local mock call happen.)
- Distribution: npm package / pip package fetches the pinned engine binary on first run
  (esbuild/Playwright model, architecture §3) — depends on the Phase 4 native binary CI matrix.
- Phase 6: run the shared conformance subset in both adapters' CI.
- pytest-xdist support for `antigen-python` (would need the HTTP + shared-session-file model the
  Jest adapter already uses).

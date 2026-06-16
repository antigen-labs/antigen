# Phase 0 — Cache replay

> Status: **implemented** (predates this refactor log). Formal verification test still open —
> tracked in `docs/TODO.md` ("Phase 0 verification").

## Goal

During simulation re-runs, serve all responses from the captured baseline (mutated body for the
target request) instead of issuing real HTTP calls. Eliminates server-side state pollution,
flakiness, and network cost.

## Decision

`AspectExecutor.interceptApacheHttpClient` branches on `TestContext.currentSimulationIndex`:

- **Baseline** (`== -1`): make the real call, capture every request/response pair
  (`TestContext.addCapturedRequest`).
- **Re-run** (`!= -1`): do **not** call `joinPoint.proceed()`. Serve the cached baseline body for
  every request by sequence index, swapping in the mutated body only at the target index. The
  synthetic response must be a `CloseableHttpResponse` and carry the original `Content-Type`
  (default `application/json`) — otherwise it surfaces as a false "caught" for every fault (see
  `docs/knowledge/gotchas.md`).

Request→capture matching during replay is by **sequence index within the test**
(`TestContext.currentRequestCounter`, reset per re-run). This assumes a test issues the same
requests in the same order on re-run — the documented v1 constraint (`architecture.md` §4).

## Verification gap

Behavior is implemented but not asserted by a test. The intended check — a WireMock request-journal
assertion that re-runs issue **zero** real calls — does not exist yet. Until it does, a regression
reintroducing real calls during re-runs would pass silently. See `docs/TODO.md`.

# 0001 — Invariant scoping via the `include_only` cascade

Status: accepted

## Context

An invariant is defined against an endpoint (`POST /api/v1/accounts`), but many tests may
exercise that endpoint — some responsible for asserting its correctness, some only using it
incidentally (e.g. `OrdersApiTest.createAccount()` POSTs `/accounts` just to get an id, asserting
nothing on the body). We needed a way to control which tests an invariant is measured against,
without forcing every invariant to enumerate its tests.

## Decision

Resolve an invariant's test scope with a three-level cascade (in `ConfigResolver`):

1. **per-invariant `include_only`** (if present) — wins.
2. **feature-level `include_only`** (if present) — fallback for invariants in that file.
3. **neither → auto-detection** — the invariant applies to any test whose captured HTTP calls
   hit the matching endpoint (`endpointPattern + "::" + HTTP_METHOD`).

`include_only` entries map a test class (+ optional method globs) — see `FeatureTestMapping`.
Per-test gating lives in per-invariant resolution (not in `FeatureConfigCache`), so a
per-invariant override can pull in a test the feature-level scope would exclude.

## Consequences

- Default behavior needs zero config: write an invariant, it's checked against whatever tests
  touch that endpoint.
- Scoping is opt-in and overridable at the granularity that matters.
- The naming is `name:` (invariant file) and `include_only:` — deliberately not BDD-flavored
  `feature:`/`tests:`. The YAML dir is `simulation/invariants/`.
- Internal Java types still use `FeatureConfig`/`FeatureTestMapping` names (the cleaner
  `InvariantConfig` name was already taken), so code and YAML vocabulary differ slightly.

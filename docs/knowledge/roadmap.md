1. True cache replay — During simulation re-runs, skip the real HTTP call entirely and serve all responses from the captured baseline (mutated for the target). Eliminates state pollution, flakiness, and ~all simulation cost.
2. Invariant scaffolding from OpenAPI — antigen invariants --from-spec generates a draft features/ YAML from required, enum, nullable, min/max. Review, prune, commit. Kills the cold-start authoring problem.
3. Agentic invariant discovery — Claude reads the spec + captured baseline responses and proposes semantic invariants (cross-field, conditional, temporal) as YAML for human approval. Your moat feature.
4. Control runs — Before scoring, re-run each test once with the unmutated cached response. Fails → flaky/state-dependent, excluded from the score. Makes the detection rate trustworthy and gives flakiness detection for free.
5. Baseline + triage workflow — Mark escapes as accepted in a committed baseline file; CI gates on regressions vs. baseline, not absolute score. The feature that keeps teams from abandoning the tool after week two.
6. Protocol-level faults — Inject 5xx, timeouts, malformed/truncated JSON, type mutations (string↔number). Expands the claim from "contract checked" to "degraded API noticed."
7. Incremental simulation — Only re-simulate endpoints whose invariants, spec, or tests changed since the last run (PIT-style). Makes per-PR CI runs viable.
8. Proxy-based interception — Replace/augment AspectJ with a mutating HTTP proxy + thin framework adapters. Unlocks pytest, Playwright, Postman, .NET — the whole non-JVM world.
9. Suite analytics from the test×fault matrix — Redundant-test detection, minimal covering set, per-test value score. You already have the data; teams pay for suite reduction.
10. Detection-rate trends (cloud) — Persist results per service over time via the existing API seam; dashboard of score history, new escapes per release. The natural SaaS layer and the org-level selling point.

What I'd elevate to the centerpiece instead — and this matters now that you have three POCs: a language-neutral simulation protocol. Define as specs, independent of any runtime:

- the invariants DSL (already done, it's YAML),
- the baseline capture format (request/response pairs, indexed),
- the fault plan (which mutation, which request, which run),
- the verdict/report schema (caught/escaped, the test×fault matrix).

Then each language SDK is just: runner adapter (re-run control) + HTTP interception shim + protocol client. The brain — violation generation, scheduling, scoring, reporting — lives once, either as a shared local engine
(sidecar/CLI the adapters talk to) or as a rigorously specified format each SDK implements. Without that, your Python and TS POCs will each reimplement scoring and reporting, drift, and produce numbers that don't mean the same  
thing — which undermines the cross-team comparability that makes the metric valuable.

So revised feature #8: not "proxy-based interception," but "portable simulation protocol + per-runner adapters" — your POCs already prove the adapter half works; the protocol is what turns them into one product instead of three.
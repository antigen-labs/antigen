# Conformance vectors

Versioned golden vectors that pin the engine's observable behavior (architecture.md §6). Every
adapter — the in-process Java one and the future foreign (Python/TS) ones — must reproduce these
exact outputs, which is what makes a detection rate mean the same thing across ecosystems.

## Layout

```
conformance/v<N>/<scenario>/
  invariants.json        # input: { "<endpoint>::<METHOD>": [ InvariantConfig, ... ] }
  baseline.json          # input: a test/baseline message (protocol §4.2)
  expected-plan.json     # golden: the fault plan the engine returns
  verdicts.json          # input: a test/verdicts message (protocol §4.3)
  expected-report.json   # golden: the report after scoring those verdicts
```

`invariants.json` stands in for the per-test resolved config until filesystem config loading lands
(Phase 3b); the replay harness wires it directly into the session.

## Replaying

`io.antigen.core.conformance.ConformanceVectorTest` (engine module) replays every scenario through
`EngineSession` and compares against the goldens as **order-insensitive JSON trees**.

Regenerate the goldens after an intentional behavior change:

```bash
./gradlew :antigen-engine:test --tests '*ConformanceVectorTest' -Dantigen.conformance.regenerate=true
```

Review the diff — a change here is a change to the cross-language contract.

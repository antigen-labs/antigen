---
name: test-unit
description: Run Antigen's unit test tier (io.antigen.core.unit.*). Use when asked to run unit tests, verify the core logic in isolation, or as the fast first gate before integration/e2e or a release. No external services required.
---

# Run unit tests

The unit tier covers `io.antigen.core.unit.*` (config, invariant, report). Pure logic,
no network, no AspectJ weaving. It is the fastest gate and has no external prerequisites.

## Command

```bash
./gradlew --stop          # Windows: release the daemon's lock on build/ first
./gradlew clean test --tests "*.unit.*" -DrunWithAntigen=false
```

- `-DrunWithAntigen=false` — unit tests must NOT weave the AspectJ agent; the simulation
  path is not under test here.
- Add `--info` when you need the per-test log (this is what CI uses).

## Gotchas

- **Always `./gradlew --stop` before `clean` on Windows.** A live daemon holds
  `build/.../output.bin` open and `clean` fails with "Unable to delete directory ... a
  process has files open". This recurs; do not skip it.
- A compile error in *any* test source (all tiers compile together) fails this run even
  if no unit test is broken. If `compileTestJava` fails, fix the reported file first.

## Reporting back

State pass/fail plainly with the `BUILD SUCCESSFUL`/`FAILED` line. On failure, surface the
failing test name and the assertion/compile message — do not just say "tests failed".

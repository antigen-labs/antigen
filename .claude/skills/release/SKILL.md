---
name: release
description: Cut a new Antigen version — run the unit/integration/e2e gates, tag and push the version, bump antigen-example to it, and verify resolution via JitPack. Use when asked to publish, release, or tag a new version (e.g. "publish v0.7", "cut a release").
---

# Release a new version

Publishes Antigen via a git tag (JitPack builds from the tag) and points antigen-example at it.
Tagging and pushing are outward-facing and hard to undo — **confirm the version with the user
before tagging**, and never tag on a failing gate.

Tags follow `vX.Y` (latest published: `v0.6`). The GitHub org is **`antigen-labs`**; the
JitPack coordinate is `com.github.antigen-labs:antigen:<tag>`.

## 1. Pre-flight

- Confirm the target version with the user (e.g. `v0.7`).
- `git status` — working tree should be clean and on `main`. If there are uncommitted changes
  that belong in the release, commit them first (ask before committing if unsure).
- `git tag --sort=-creatordate | head` — make sure the version isn't already taken.

## 2. Gates — all three must pass before tagging

Run in order; stop on the first failure and report it.

1. **Unit** — see the `test-unit` skill: `./gradlew --stop && ./gradlew clean test --tests "*.unit.*" -DrunWithAntigen=false`
2. **Integration** — see the `test-integration` skill (needs WireMock on :8089).
3. **E2E** — see the `test-e2e` skill (needs oms-demo-api on :8000). Run its **report
   health-check** — a green build with `infra-errors > 0` or a 0%/100% escape rate means the
   release is NOT good even though tests "passed".

(`./gradlew --stop` before each `clean` on Windows.)

## 3. Tag and push

**Do NOT use `scripts/publish-tag.sh` — it is broken.** Tag and push directly:

```bash
git tag v0.7
git push origin main      # if there are release commits to publish
git push origin v0.7
```

## 4. Bump antigen-example

Update **both** coordinates in `../antigen-example/build.gradle.kts` (buildscript classpath
~line 7 and `testImplementation` ~line 25):

```
com.github.antigen-labs:antigen:v0.7
```

Commit and push antigen-example (its remote is `git@github.com:antigen-labs/antigen-example.git`).

## 5. Verify against JitPack

The first resolution of a new tag triggers a JitPack build (~2 min). Verify end-to-end:

```bash
cd ../antigen-example
./gradlew --stop
./gradlew clean test --tests "*OrdersApiTest*" -DrunWithAntigen=true --refresh-dependencies
```

Then health-check `antigen-example/build/antigen/fault_simulation_report.json` (same script as
the `test-e2e` skill): confirm it resolved the new tag and shows a real caught/escaped spread
(not all-caught / all-escaped, no infra errors).

## Reporting back

Summarize: version tagged, gate results, example bumped + pushed, and the verified JitPack
caught/escaped spread. If any gate or the health-check failed, stop before tagging and say so.

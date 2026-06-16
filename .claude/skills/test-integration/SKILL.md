---
name: test-integration
description: Run Antigen's integration test tier (io.antigen.core.integration.*) against a local WireMock server with fault simulation enabled. Use when asked to run integration tests or to exercise the AspectJ interception/fault-injection path against mocked HTTP endpoints (no live backend).
---

# Run integration tests

The integration tier (`io.antigen.core.integration.mockrestapi.*` — PaymentTest, UsersTest,
ECommerceApiTest) runs **with the AspectJ agent** (`-DrunWithAntigen=true`) against a
**WireMock** server on port **8089**. It exercises the interception + fault-injection path
without needing a real backend.

## Prerequisite: WireMock on :8089

The tests set `RestAssured.baseURI = "http://localhost:8089"` and rely on the stub mappings
in `src/test/resources/mappings/`. Start WireMock with those mappings mounted (mirrors CI):

```bash
docker run -d --name wiremock -p 8089:8089 \
  -v "$PWD/src/test/resources/mappings:/home/wiremock/mappings" \
  wiremock/wiremock:latest --port 8089 --verbose

# wait until ready
until curl -sf http://localhost:8089/__admin/health; do sleep 1; done
```

Check first whether it's already up (`curl -sf http://localhost:8089/__admin/health`) before
starting a new container. Clean up with `docker rm -f wiremock` when done if you started it.

## Command

```bash
./gradlew --stop
./gradlew clean test --tests "io.antigen.core.integration.*" -DrunWithAntigen=true
```

## Gotchas

- **`./gradlew --stop` before `clean`** (Windows daemon file-lock). Always.
- If every test fails with a connection refused to :8089, WireMock isn't running — start it.
- Because `runWithAntigen=true`, the simulation runs after the baseline. If you see a fault
  report, sanity-check it the same way `test-e2e` does (watch for infrastructure-error
  "false caught": `cannot be cast` / `no content-type` in `caught_by[].error`).

## Reporting back

Report pass/fail and, if a `build/antigen/fault_simulation_report.json` was produced, the
caught/escaped counts. Flag any infrastructure errors rather than treating them as real catches.

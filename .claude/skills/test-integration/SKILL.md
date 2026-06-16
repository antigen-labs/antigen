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
./gradlew :antigen-test-runner:clean :antigen-test-runner:test --tests "io.antigen.core.integration.*" -DrunWithAntigen=true
```

## Gotchas

- **Scope to `:antigen-test-runner`.** Since the Phase 2b module split, integration tests live in
  the `antigen-test-runner` module. A bare `test --tests "io.antigen.core.integration.*"` fails
  because the `antigen-engine` module has a test source set with no matching tests
  ("No tests found for given includes"). Always target the module's task.
- **`./gradlew --stop` before `clean`** (Windows daemon file-lock). Always.
- If every test fails with a connection refused to :8089, WireMock isn't running — start it.
- Because `runWithAntigen=true`, the simulation runs after the baseline and produces a real
  report. The integration invariants (`mock-users.yml`, `mock-payments.yml`, `include_only`-scoped
  to `UsersTest`/`PaymentTest`) yield a deterministic **13 faults — 9 caught, 4 escaped
  (~31%), 0 infra-errors**. The 4 escapes are intentional: three `is_not_empty` rules a bare
  `notNull` assertion can't catch, plus one relational/temporal rule. Sanity-check the report the
  same way `test-e2e` does (watch for `cannot be cast` / `no content-type` in `caught_by[].error`).
- Only `UsersTest`/`PaymentTest` are scoped, and only on normalization-safe endpoints
  (`GET /users/{id}` with a numeric id, `GET /users`, `POST /payments`). `GET /payments/{id}` is
  avoided: the alphanumeric id (`pay_…`) doesn't normalize to `{id}`, so it wouldn't match.

## Reporting back

Report pass/fail and, if a `build/antigen/fault_simulation_report.json` was produced, the
caught/escaped counts. Flag any infrastructure errors rather than treating them as real catches.

# Gotchas

Recurrent traps in Antigen development and their fixes. Format: **Symptom → Cause → Fix**.

## `./gradlew clean` fails: "Unable to delete directory ... output.bin ... a process has files open"

- **Symptom:** `clean` (or `clean test`) fails on Windows complaining a file under `build/` is locked.
- **Cause:** a live Gradle daemon holds test-result files open.
- **Fix:** run `./gradlew --stop` before any `clean`. Always, on Windows.

## Every simulated fault is "caught" (or escaped) — the report is uniform

- **Symptom:** the fault report shows ~100% caught (or ~100% escaped); `caught_by[].error`
  contains `cannot be cast` (`BasicHttpResponse`→`CloseableHttpResponse`) or `no content-type`.
- **Cause:** the simulation re-run in `AspectExecutor.interceptApacheHttpClient` returned a
  response that callers (RestAssured) couldn't use — wrong type, or no `Content-Type` so there
  was no parser. The exception surfaced as a false "caught" for *every* fault.
- **Fix:** the synthetic re-run response must be a `CloseableHttpResponse` **and** carry the
  original `Content-Type` (default `application/json`). See
  `src/main/java/io/antigen/core/interceptor/AspectExecutor.java`. Always health-check the
  report (see the `test-e2e` skill) — a green build does not mean a valid report.

## A value invariant escapes even though the test asserts the field

- **Symptom:** an invariant like `price >= 0` escapes although the test checks `price`.
- **Cause:** monetary fields (`price`, `cash_balance`, `total_value`, `current_price`) are JSON
  **strings** (`"150.00"`), but `ViolationGenerator` injects a **number** (`0.0`/`-1.0`) to
  violate `>0`/`>=0`. A `notNullValue()` / `not(emptyString())` assertion passes on a number.
- **Fix:** assert the field stays a string — `body("price", instanceOf(String.class))` fails
  the moment the value becomes a number, catching both the negative and zero-boundary mutation.

## Cross-field / temporal invariants always escape

- **Symptom:** invariants like `created_at <= updated_at` or `created_at <= $.filled_at` never
  get caught.
- **Cause:** RestAssured (and most assertion libraries) can't express a *relational* assertion
  between two response fields, so a typical suite genuinely can't catch these.
- **Fix:** this is expected, not a bug. These belong in the "uncatchable" bucket when reasoning
  about a target escape rate (~15–20% for the demoapi suite is healthy).

## JitPack doesn't pick up a new tag / example won't resolve

- **Symptom:** the example fails to resolve `com.github.antigen-labs:antigen:vX.Y`.
- **Cause:** the first resolution of a new tag triggers a JitPack build (~2 min); a cached
  failed resolution can also stick.
- **Fix:** wait for the first build; use `--refresh-dependencies`. The org is `antigen-labs`.

## `scripts/publish-tag.sh` is broken

- **Symptom:** releasing via the script misbehaves.
- **Cause:** the script is not maintained/working.
- **Fix:** never use it. Tag and push directly with `git tag vX.Y && git push origin vX.Y`
  (see the `release` skill).

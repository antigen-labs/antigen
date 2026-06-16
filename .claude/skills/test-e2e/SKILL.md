---
name: test-e2e
description: Run Antigen's end-to-end test tier (io.antigen.core.demoapi.*) against the live oms-demo-api on localhost:8000 with fault simulation, then summarize and health-check the fault report. Use when asked to run e2e/demoapi tests or to validate the full simulation against the real trading API.
---

# Run e2e (demoapi) tests

The e2e tier (`io.antigen.core.demoapi.*`) runs **with the AspectJ agent** against the **live
oms-demo-api on `http://localhost:8000/api/v1`**. This tier is **not run in CI** (it needs the
external server). It produces the real fault report under `antigen-test-runner/build/antigen/`.

## Prerequisite: live oms-demo-api on :8000

```bash
# is it up?
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8000/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"test","password":"test123"}'
```

- A `200` means it's up and the `test`/`test123` user exists — proceed.
- Not reachable → start it: `cd oms-demo-api && docker-compose up --build` (repo:
  `github.com/antigen-labs/oms-demo-api`).
- `200` but login fails → register the user once:
  ```bash
  curl -X POST http://localhost:8000/api/v1/auth/register -H "Content-Type: application/json" \
    -d '{"email":"test@example.com","username":"test","password":"test123","full_name":"Test User"}'
  ```

## Command

```bash
./gradlew --stop
./gradlew :antigen-test-runner:clean :antigen-test-runner:test -i --tests "*demoapi.*" -DrunWithAntigen=true
```

**Scope to `:antigen-test-runner`** (Phase 2b module split): the demoapi tests live in that
module, and a bare `clean test --tests "*demoapi.*"` fails on `antigen-engine` with
"No tests found for given includes". Same trap as `test-integration`.

## Health-check the report (critical)

A green build does NOT mean the report is valid. Twice the report was meaningless because the
fault-injection path errored and every fault was recorded as a false "caught". Always inspect
`antigen-test-runner/build/antigen/fault_simulation_report.json`:

```bash
python -c "
import json
d=json.load(open('antigen-test-runner/build/antigen/fault_simulation_report.json'))
tot=c=e=bad=0; esc=[]
for ep,data in d.items():
    for n,f in data.get('invariant_faults',{}).items():
        tot+=1
        if f.get('caught_by_any_test'):
            c+=1
            for x in f.get('caught_by',[]):
                err=(x.get('error') or '')
                if 'cannot be cast' in err or 'no content-type' in err: bad+=1; break
        else: e+=1; esc.append(ep+'::'+n)
print(f'total {tot}  caught {c}  escaped {e}  ({100*e/tot:.0f}%)  infra-errors {bad}')
for n in esc: print('  escaped:',n)
"
```

Interpret:
- **`infra-errors > 0`** → the report is INVALID. The injection threw before reaching the
  assertions (e.g. `BasicHttpResponse` not a `CloseableHttpResponse`, or a missing
  `Content-Type` so RestAssured had no parser). Fix `AspectExecutor` before trusting anything.
- **escaped 0% or ~100%** → almost always a bug, not a real result.
- A healthy demoapi run sits around **15–20% escaped**; the remaining escapes are typically
  cross-field temporal invariants (RestAssured can't assert `a <= b` relationally) plus any
  intentional `include_only`-scoped DEMO invariants.

## Gotchas

- **`./gradlew --stop` before `clean`** (Windows daemon file-lock). Always.
- Monetary fields (`price`, `cash_balance`, ...) are JSON **strings**; the mutator injects a
  **number** to violate `>0`/`>=0`. A `notNull` assertion won't catch that — `instanceOf(String.class)`
  does. Keep this in mind when interpreting why a value invariant escaped.

## Reporting back

Report build pass/fail AND the caught/escaped summary. If `infra-errors > 0`, lead with that —
the run is not trustworthy until it's fixed.

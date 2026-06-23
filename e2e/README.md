# antigen-example — Example Project

A working example of [Antigen](https://github.com/antigen-labs/antigen) running fault simulation against a real stock trading API.

The example covers authentication, accounts, orders, positions, stocks, and trades — with invariants intentionally ranging from well-covered to completely blind, so the Test Matrix shows a realistic spread of caught and escaped faults.

---

## Prerequisites

- Docker and Docker Compose
- Java 17+
- Gradle 7.3+

---

## Step 1 — Start the demo API

The tests run against [oms-demo-api](https://github.com/antigen-labs/oms-demo-api), a Python/FastAPI trading simulator.

```bash
cd oms-demo-api
docker-compose up --build
```

Wait until you see `Application startup complete`. The API is then available at `http://localhost:8000` (tests use the base URI `http://localhost:8000/api/v1`).

Migrations run automatically on startup. They seed the stock prices (AAPL, GOOGL, MSFT, TSLA, AMZN) and create a default admin user.

---

## Step 2 — Register the test user

The example tests authenticate as `test` / `test123`. Register this user once:

```bash
curl -X POST http://localhost:8000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "username": "test", "password": "test123", "full_name": "Test User"}'
```

You only need to do this once — the database persists across restarts unless you run `docker-compose down -v`.

---

## Step 3 — Run the tests

```bash
cd antigen-example

./gradlew test                          # normal run — no simulation
./gradlew test -DrunWithAntigen=true    # run with fault simulation
```

The `-DrunWithAntigen=true` flag enables simulation: the `tasks.test` block in `build.gradle.kts`
attaches the AspectJ weaver agent for that run. You can scope it to specific classes:

```bash
./gradlew test --tests "demoapi.OrdersApiTest" -DrunWithAntigen=true
```

Reports are generated in the project root after the simulation run:
- `antigen_report.html` — open in a browser; start with the Test Matrix tab
- `fault_simulation_report.json` — machine-readable, useful for CI
- `schema_coverage.json` — per-endpoint response field coverage

---

## What's in this project

### Tests (`src/test/java/demoapi/`)

Six test classes covering the full API surface:

| Class | Endpoints covered |
|---|---|
| `AuthApiTest` | POST /auth/login, GET /auth/me |
| `AccountsApiTest` | GET/POST /accounts, deposit, withdraw |
| `OrdersApiTest` | POST/GET /orders |
| `PositionsApiTest` | GET /positions, GET /positions/{id} |
| `StocksApiTest` | GET/PUT /stocks, GET /stocks/{symbol} |
| `TradesApiTest` | GET /trades, GET /trades/{id} |

The tests deliberately vary in assertion depth — some validate every response field, others only check the status code and array size. This is intentional: it produces a realistic report where some faults are caught and others escape.

### Invariant files (`src/test/resources/antigen/simulation/invariants/`)

Four files defining business invariants grouped by domain:

- `trading-auth.yml` — login token rules, current user integrity
- `trading-accounts.yml` — account balances, deposit/withdraw postconditions, position integrity
- `trading-orders.yml` — order lifecycle, status transitions, price constraints
- `trading-market.yml` — stock price validity, trade data integrity

Invariants marked `# DEMO: will not be caught` are valid business rules that the tests don't assert on. They show up as escaped faults in the report — the point being that the tests exist but aren't actually enforcing those rules.

### Antigen config (`src/test/resources/antigen/`)

```
antigen/
├── antigen.properties      # antigen.config.source=local
├── simulation/
│   ├── coverage_config.yml # endpoint coverage + gap analysis
│   └── invariants/         # invariant files above
└── generation/             # AI test-generation config + OpenAPI spec
```

---

## What to expect in the output

After `./gradlew test -DrunWithAntigen=true`, the console prints a summary:

```
============================================================
  Antigen — Fault Simulation Summary
============================================================
  Test                                  Caught  Total  Escaped
--------------------------------------------------------------
  AccountsApiTest.testCreateAccount       11      12      1
  OrdersApiTest.testCreateBuyOrder         8      11      3
  AuthApiTest.testLogin                    1       2      1
  OrdersApiTest.testListMyOrders           0       4      4
  TradesApiTest.testListMyTrades           0       4      4
  ...
--------------------------------------------------------------
```

Tests like `testCreateAccount` (which asserts on every response field) will catch most faults. Tests like `testListMyOrders` (which only checks `size() >= 0`) will catch none — all their invariants escape. This contrast is the core demonstration.

Open `antigen_report.html` and go to the **Test Matrix** tab for the full picture.

---

## Resetting the environment

If tests fail due to stale data or auth issues:

```bash
# Wipe the database and start fresh
cd oms-demo-api
docker-compose down -v
docker-compose up --build

# Re-register the test user
curl -X POST http://localhost:8000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "username": "test", "password": "test123", "full_name": "Test User"}'
```

---

## Further reading

- [Antigen README](https://github.com/antigen-labs/antigen) — invariants DSL reference, full configuration options, how fault simulation and AI test generation work
- [oms-demo-api README](https://github.com/antigen-labs/oms-demo-api) — full API documentation, endpoint reference, Postman collection
</content>

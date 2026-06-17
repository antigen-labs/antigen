# Invariant derivation strategy

The long-term direction for where invariants come from, and the principle that keeps the
caught/escaped metric meaningful as both tests and invariants get AI-generated. This is the *why*
behind roadmap items [#2 (OpenAPI scaffolding)](roadmap.md), [#3 (agentic discovery)](roadmap.md),
and [#5 (baseline + triage)](roadmap.md); read those for the feature mechanics.

## Goal

Humans should not author invariants by hand at scale. Invariants get **auto-derived** (from specs,
traffic, types, or an AI agent) and humans **approve at a high level**. This collapses the
cold-start authoring cost — the main thing throttling adoption — from "write N rules" to "approve N
candidates."

## The load-bearing principle: independence of derivation

Antigen's value is the decoupling of **what to check** (the invariant) from **how to check it** (the
test assertion). That decoupling only buys anything if the two come from **different sources of
truth**.

- An AI that writes a test and the assertions inside it shares a single failure mode: it encodes the
  same misunderstanding in both places. The test passes, the assertion is wrong, nobody notices —
  self-graded homework. AI-generated suites make this *more* common, not less.
- Invariants break that loop **only if derived independently of the tests**. Derived from the
  OpenAPI/GraphQL schema, observed production traffic, the type system, or a *different* model with
  *different* context → they are an **oracle**. Derived from "read the test code and tell me the
  rules" → they are a **mirror**, and they will happily agree with a buggy suite.
- **Rule:** the derivation source must always be external to the suite under test — a separate
  agent, human, schema, or recorded traffic. Same-context co-generation of suite + invariants is
  prohibited; it relocates the circular-trust problem up one level instead of solving it.

A neat consequence: the escape rate itself signals whether independence held. If invariants merely
echo the tests, nearly everything is "caught" and the score is theater — a suspiciously high catch
rate is evidence the oracle wasn't independent.

## Approval is the product, not the derivation

Auto-derivation produces noise: invariants that are too strict (false faults), trivially true, or
duplicative. The bottleneck moves from **authoring** to **triage**, and that's where the product
and the trust live.

- The winning UX is a ranked **approval queue**: "here are N candidate invariants, ordered by
  confidence and by how much suite-quality signal each would add — approve / reject / edit." A
  tractable human task; the step where trust is actually manufactured.
- The ratified, versioned invariant corpus — *"a human approved these business rules and the suite
  is measured against them"* — is the durable asset and audit trail. Raw auto-derivation is a
  commodity feature; the approved corpus is the moat. This is the compliance/security selling point.

## Bounded claim (do not over-promise)

Antigen measures one thing well: **does the suite's assertions encode these declared invariants.**
The honest claim is *"trust that your suite verifies the business rules you've declared"* — not
*"trust the suite."* It does **not** measure:

- whether the declared invariant set is **complete** (see below);
- correctness of behavior **outside** any declared invariant;
- timing / concurrency;
- relational or temporal rules — these are structurally uncatchable by typical assertion libraries
  (see [gotchas.md](gotchas.md), "Cross-field / temporal invariants always escape").

Let the pitch inflate to "trust any test suite" and the first user who finds an escaped real bug
dismisses the whole metric.

## Open risks to de-risk early

1. **Completeness is unsolved.** Like mutation testing, a high score means "the suite catches the
   mutations we thought to make," not "the suite is good." AI derivation widens the set (proposes
   rules a human wouldn't) but cannot prove it complete. The metric is "of *declared* rules, X% are
   guarded" — the denominator is a judgment call. State this precisely or the number gets gamed.
2. **The oracle problem relocates, it doesn't vanish.** The whole bet is that AI is better at
   *deriving invariants* (a constrained, verifiable task — rule out of a schema) than at *writing
   correct assertions*. Defensible, but it is the bet. **Validation test:** take a suite with
   known-wrong assertions; confirm independently-derived invariants catch them.

## Why now (timing)

AI test generation is creating a "verify-the-verifier" trust vacuum. An independently-derived,
human-ratified semantic oracle that scores *any* suite — human- or AI-written — is a well-positioned
answer. This is the category bet, and it matters more now than when mutation testing was invented.

## See also

- [roadmap.md](roadmap.md) — #2 OpenAPI scaffolding, #3 agentic discovery, #5 baseline/triage.
- [dsl.md](dsl.md) — the invariant grammar and the invertibility law that bounds what can be derived
  *and* mechanically violated.

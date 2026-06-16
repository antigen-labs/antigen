# Antigen

Antigen runs fault simulation against an API test suite: it mutates HTTP responses to violate
declared business **invariants** and reports whether the tests catch the faults (caught) or not
(escaped). Core engine under `io.antigen.core.*`; AI test-generation loop under `io.antigen.ai.*`.

## Knowledge base

Durable project knowledge lives in [`docs/knowledge/`](docs/knowledge/README.md):

- `architecture.md` — planned language-neutral engine + adapter model, protocol, migration phases.
- `dsl.md` — future invariant authoring DSL and the invertibility law.
- `roadmap.md` — prioritized feature ideas.
- `gotchas.md` — recurrent traps and fixes (imported below).
- `decisions/` — ADRs.

Read the relevant entry before working in an area; capture new findings with the
`capture-knowledge` skill.

## Always-on gotchas

@docs/knowledge/gotchas.md

## Workflows (skills)

- `test-unit` / `test-integration` / `test-e2e` — run a test tier with its prerequisites.
- `release` — cut a version (gates → tag → bump example → verify JitPack).
- `capture-knowledge` — record a finding/decision into the knowledge base.

## Conventions

- Invariants are committed YAML under `src/test/resources/antigen/simulation/invariants/`
  (`name:` per file, `include_only:` for scoping). Framework's own invariants are JUnit 5 + RestAssured.
- The GitHub org is `antigen-labs`; releases are git tags `vX.Y` resolved via JitPack.
- Only commit/push when asked.

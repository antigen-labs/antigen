# Antigen knowledge base

Durable, committed project knowledge — the *why* and the non-obvious *how* that the code and
git history don't make clear. (Distinct from Claude's personal memory, which is private and
machine-local; this travels with the repo.)

To add or update an entry, use the `capture-knowledge` skill, or edit directly following the
conventions below.

## Index

### Reference
- [architecture.md](architecture.md) — the planned language-neutral engine + adapter model: current
  code map, target architecture, the JSON protocol, migration phases, conformance vectors.
- [dsl.md](dsl.md) — the (future) logic-based invariant authoring DSL and the invertibility law
  that bounds what the grammar may admit.
- [invariant-derivation.md](invariant-derivation.md) — where invariants come from long-term:
  AI/auto-derivation with high-level human approval, and the independence-of-derivation principle
  that keeps the caught/escaped metric honest.
- [roadmap.md](roadmap.md) — prioritized feature ideas; the language-neutral protocol as centerpiece.

### Operational
- [gotchas.md](gotchas.md) — recurrent traps and fixes (daemon lock, fault-injection health,
  string-vs-number mutations, JitPack, the broken publish script). Imported into `CLAUDE.md`.

### Decisions (ADRs)
- [0001-include-only-cascade.md](decisions/0001-include-only-cascade.md) — invariant test scoping.

## Conventions

- **Markdown, present tense, code-anchored.** Reference real symbols/paths as `file_path:line`.
- **Cross-link** related entries with relative links.
- **Placement:** how-it-works → `architecture.md`; rule syntax → `dsl.md`; a trap that bit us →
  `gotchas.md` (*Symptom → Cause → Fix*); a choice with alternatives → a new ADR in `decisions/`.
- **ADRs** are `decisions/NNNN-slug.md`, one decision each, *Context → Decision → Consequences*,
  numbered sequentially. Record *why*, not just *what*.
- Capture what's **non-obvious and durable**. Don't restate code, git history, or CLAUDE.md.
- Keep one fact in one place — update an existing entry rather than duplicating, and refresh the
  pointer in this index.

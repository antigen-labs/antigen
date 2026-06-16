---
name: capture-knowledge
description: Record a finding, gotcha, or design decision into the repo knowledge base at docs/knowledge/. Use when something non-obvious was learned and should be durable — a recurrent trap, how a subsystem works, or a decision with alternatives ("document this", "add an ADR", "capture that gotcha", "write this down").
---

# Capture knowledge

Write a durable finding into [`docs/knowledge/`](../../../docs/knowledge/README.md). This is the
*action* of curating the KB; the KB content itself is plain markdown read on demand. Keep it
committed and shared — distinct from Claude's private personal memory.

## Decide where it goes

| Kind of knowledge | Destination |
|---|---|
| A trap that bit us / surprising failure mode | `gotchas.md` — *Symptom → Cause → Fix* |
| How a subsystem works | `architecture.md` |
| Invariant/DSL syntax or semantics | `dsl.md` |
| A choice made over alternatives, with rationale | new ADR `decisions/NNNN-slug.md` |
| Forward-looking feature idea | `roadmap.md` |

## Procedure

1. **Check for an existing entry** covering this. Update it in place rather than duplicating —
   one fact lives in one place.
2. **Write the entry** following `docs/knowledge/README.md` conventions: present tense,
   code-anchored (`file_path:line`), cross-linked. For ADRs use the next sequential number and
   *Context → Decision → Consequences*.
3. **Update the index** — add or refresh the one-line pointer in `docs/knowledge/README.md`.
4. If the finding is an always-relevant gotcha, it's already surfaced via the `@docs/knowledge/gotchas.md`
   import in `CLAUDE.md` — no extra wiring needed.

## What to capture

Only what's **non-obvious and durable**: the reasoning, the trap, the decision. Do **not**
restate what the code, git history, or `CLAUDE.md` already make clear. If asked to "remember"
something trivial, capture instead the non-obvious *why* behind it.

## Reporting back

Say which file you wrote/updated and summarize the entry in one line. Don't commit unless asked.

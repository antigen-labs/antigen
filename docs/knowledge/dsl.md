# Antigen Property DSL

> Status: **future idea — not scheduled**. A lightweight, logic-based syntax to replace YAML
> as the *authoring* format for invariants. Sequenced after the engine extraction
> (see `architecture.md`, Phases 1–3): the DSL parser must live only in the engine,
> so it costs one implementation forever instead of one per language adapter.

---

## 1. Motivation

Invariants are logic; the current YAML encodes logic as nested data, which is verbose to
write and poor to review:

```yaml
- name: filled_order_has_timestamp
  if:
    field: status
    equals: FILLED
  then:
    field: filled_at
    is_not_null: true
```

vs.

```
status == FILLED => filled_at != null
```

Why it matters beyond aesthetics:

- **Review ergonomics.** The agentic flow (engine/LLM proposes invariants, human approves and
  commits) lives or dies on diff readability. One line per property beats an 8-line YAML block.
- **Expressiveness ceiling.** Operators-as-YAML-keys cannot compose: no `or` / `not`, no
  arithmetic (`sum(items.amount) == total`), no conditional quantifiers. These compound
  properties are the highest-value business invariants.
- **Product surface.** For a test-quality tool, an elegant property language is
  differentiation in itself.

---

## 2. The invertibility law (core design constraint)

Antigen does not just *evaluate* invariants — it **negates** them to generate concrete
violating mutations (`ViolationGenerator`). Therefore:

> Every construct admitted into the grammar must be mechanically invertible into one or more
> concrete response mutations. The grammar grows exactly as fast as violation generation can
> negate it — never faster.

| Construct | Negation strategy |
|---|---|
| `a == v` / `a != v` | inject different value / inject the disallowed value |
| `a > v`, `a >= v`, `<`, `<=` | boundary crossing (inject `v`, `v±1`) |
| `a in [..]` / `not_in` | inject value outside set / first disallowed value |
| `a != null`, `nonempty(a)` | inject null / empty |
| `p => q` | precondition `p` holds in baseline → violate `q` |
| `p and q` | violate `p`; violate `q` (two mutations) |
| `p or q` | violate both simultaneously (one mutation set) |
| `all(xs, pred)` | violate `pred` on one element |
| `any(xs, pred)` | violate `pred` on every element |
| cross-field `a <= b` | inject `a` past `b` (type-aware: number, timestamp) |
| arithmetic `sum(xs.f) == t` | perturb `t` or one `f` |

**Excluded by the law:** arbitrary/user-defined functions, regex beyond known formats,
anything whose negation cannot produce a concrete mutation.

---

## 3. Syntax draft (illustrative, not final)

```
feature "Order Lifecycle"
description "Status transitions, price constraints, temporal ordering."

on GET /api/v1/orders/{id} {
  quantity > 0
  price > 0
  status in [PENDING, FILLED, REJECTED, CANCELLED]
  status == FILLED  => filled_at != null
  filled_at != null => created_at <= filled_at
}

on POST /api/v1/orders {
  status in [PENDING, FILLED, REJECTED]
}

on GET /api/v1/orders {
  all(items, it.quantity > 0)
  sum(items.amount) == total          # future: arithmetic tier
}

tests {
  com.example.OrdersApiTest: [testCreateBuyOrder, testGetOrder, testListMyOrders]
}
```

Conventions:
- One property per line; a property's name is auto-derived (`status_in_pending_filled_...`)
  with optional explicit label: `filled_has_ts: status == FILLED => filled_at != null`.
- Bare identifiers are response fields (current `$.field`); `it` binds the element inside
  quantifiers (current `$[*].field`).
- File extension: `.ant` (or similar), living alongside / replacing `invariants/*.yml`.

---

## 4. Architecture position

- **DSL is sugar; the existing model is the interchange format.** The DSL compiles to the
  current config model / protocol JSON (`architecture.md` §4). Existing YAML keeps
  working indefinitely; conformance vectors stay JSON. Parser bugs cannot change semantics —
  the compiled form is the source of truth the engine executes.
- **Parser lives only in the engine.** Adapters never see DSL text; they receive computed
  fault plans. This is why the work is sequenced after engine extraction.
- **Negation operates on the parsed AST**, replacing today's per-operator ad-hoc handling in
  `ViolationGenerator` with systematic AST negation — this is also the path to composition
  (`and`/`or`/`not`) regardless of surface syntax.

---

## 5. Evaluate CEL before inventing syntax

[CEL](https://cel.dev) (Common Expression Language) is the closest prior art:
non-Turing-complete, designed for field-level constraints, C-like familiar syntax, with strong
precedent in **protovalidate** (field invariants in CEL) and Kubernetes admission policies.
`cel-java` exists; the parsed AST is walkable for negation.

Decision to make when this work starts:
- **Adopt a CEL subset** → free familiarity, editor tooling, spec; must restrict to the
  invertible subset and reject the rest at parse time.
- **Custom grammar** → exact fit for the invertibility law and the `on METHOD /path { }`
  structure; cost: parser, *good* error messages, syntax highlighting, eventually an LSP.
  Custom DSLs die from bad error messages and zero tooling — budget for these or don't start.

A hybrid is plausible: custom file structure (`feature` / `on` blocks), CEL-subset for the
expressions inside.

---

## 6. Non-goals

- Turing completeness, user-defined functions, side effects.
- Replacing the protocol/JSON interchange format — the DSL is an authoring layer only.
- Requiring migration: YAML and DSL coexist; a `antigen dsl migrate` converter can mechanically
  translate existing `invariants/*.yml`.

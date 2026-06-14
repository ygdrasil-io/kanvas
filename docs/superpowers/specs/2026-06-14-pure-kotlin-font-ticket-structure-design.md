# Pure Kotlin Font Ticket Structure Design

## Goal

Define the markdown structure used to write pure Kotlin font system tickets by
milestone, before creating the actual ticket catalog.

The ticket catalog will live with the target specs under
`.upstream/specs/pure-kotlin-text/tickets/` so it remains close to the roadmap,
the durable gates, and the target acceptance rules.

## Directory Layout

```text
.upstream/specs/pure-kotlin-text/tickets/
  README.md
  STATUS.md
  templates/
    milestone-template.md
    ticket-template.md
  M0-claims-ci-diagnostics/
    README.md
    KFONT-M0-001-wire-pure-kotlin-font-modules-into-ci.md
  M1-font-identity-sources/
    README.md
  M2-sfnt-opentype-parser/
    README.md
  M3-truetype-glyf/
    README.md
  M4-cff-cff2-scalers/
    README.md
  M5-unicode-segmentation-bidi/
    README.md
  M6-opentype-layout-shaping/
    README.md
  M7-fallback-system-fonts/
    README.md
  M8-paragraph-engine/
    README.md
  M9-glyph-artifacts/
    README.md
  M10-color-fonts-emoji/
    README.md
  M11-gpu-handoff/
    README.md
  M12-performance-telemetry/
    README.md
  M13-skia-facade-migration/
    README.md
```

Each milestone gets a directory with a `README.md` that acts as the human
navigation entry point for that milestone. Tickets are plain markdown files
inside their milestone directory.

## Ticket IDs

Ticket IDs use milestone-scoped numbering:

```text
KFONT-M<milestone-number>-<three-digit-sequence>
```

Examples:

- `KFONT-M0-001`
- `KFONT-M6-004`
- `KFONT-M13-002`

This keeps the sequence readable and avoids renumbering unrelated milestones
when new tickets are inserted.

## Ticket Status Model

Each ticket must carry a YAML front matter block with a status.

Allowed statuses:

| Status | Meaning |
|---|---|
| `proposed` | Written but not yet accepted for execution. |
| `ready` | Accepted and ready to implement. |
| `in-progress` | Currently being implemented. |
| `blocked` | Cannot progress until a named blocker is resolved. |
| `review` | Implementation exists and is under review. |
| `done` | Completed with linked evidence. |
| `superseded` | Replaced by another ticket. |
| `deferred` | Intentionally postponed. |

The top-level `STATUS.md` summarizes status across milestones. Milestone
`README.md` files summarize status for tickets in that milestone.

## Ticket Front Matter

Each ticket starts with:

```yaml
---
id: KFONT-M0-001
status: proposed
milestone: M0
priority: P0
owner_area: ci
claim_impact: tracked-gap
depends_on: []
legacy_gate: null
---
```

`claim_impact` must use the target taxonomy from
`.upstream/specs/pure-kotlin-text/ROADMAP.md`: `target-supported`,
`current-supported`, `tracked-gap`, `DependencyGated`, `fixture-gated`,
`GPU-gated`, `expected-unsupported`, or `drift-only`.

## Ticket Body

Each ticket uses this section order:

1. `PM Note`
2. `Problem`
3. `Scope`
4. `Non-Goals`
5. `Spec Sources`
6. `Design Sketch`
7. `Acceptance Criteria`
8. `Required Evidence`
9. `Fallback / Refusal Behavior`
10. `Dashboard Impact`
11. `Validation`
12. `Status Notes`
13. `Linear Labels`

The `PM Note` is written in simple French. It explains why the ticket matters
to product delivery without deep implementation language.

The rest of the ticket may be in English. Technical terms can stay in English
when they are the clearest names for the API, spec, or implementation concept.

`Fallback / Refusal Behavior` describes what happens when the target route is
not supported, dependency-gated, fixture-gated, or intentionally unsupported.
It must name stable diagnostics and must keep legacy gates visible until the
ticket evidence allows retirement.

`Dashboard Impact` names the expected dashboard or PM bundle row, the expected
claim classification, and whether claim promotion is allowed. Claim promotion
is normally refused until all required evidence is attached and validation has
passed.

## Design Sketch

`Design Sketch` should include pseudo-code close to Kotlin when useful. It is
not binding implementation code; it exists to make the intended shape concrete
for future implementers.

Example:

```kotlin
data class FontSourceIdentity(
    val sourceId: FontSourceID,
    val provenance: FontProvenance,
    val contentHash: Sha256,
    val faceCount: Int,
    val parserGeneration: Int,
)
```

## Milestone README Shape

Each milestone `README.md` uses:

```md
# M0 - Claims, CI et diagnostics

## Goal

## Dependencies

## Exit Criteria

## Tickets

| Ticket | Status | Priority | Summary |
|---|---|---|---|

## Validation Bundle

## Non-Claims
```

Milestone pages should be navigable without reading every ticket.

## Top-Level README Shape

The top-level `tickets/README.md` links to every milestone and explains:

- the purpose of the catalog;
- the status model;
- the ticket ID convention;
- the relation to `ROADMAP.md`;
- the rule that tickets are markdown source of truth before optional Linear
  import.

## Top-Level STATUS Shape

`tickets/STATUS.md` tracks execution state across milestones:

```md
# Pure Kotlin Font Tickets Status

| Milestone | Proposed | Ready | In Progress | Blocked | Review | Done |
|---|---:|---:|---:|---:|---:|---:|
```

The table may be updated manually at first. Automation can be added later if it
becomes useful.

## Linear Import Rule

Markdown remains the source of truth. Linear tickets can be created later from
the markdown catalog, but they must preserve:

- ticket ID;
- milestone;
- status;
- PM note;
- spec sources;
- acceptance criteria;
- required evidence;
- fallback/refusal behavior;
- dashboard impact;
- validation command;
- legacy gate and claim impact when present.

## Acceptance Criteria

- The ticket catalog uses folders by milestone for human navigation.
- Every ticket has explicit status metadata.
- Every ticket includes a French PM note.
- Every technical ticket can include Kotlin-like pseudo-code as reference.
- Every ticket includes explicit fallback/refusal behavior before validation.
- Every ticket includes dashboard impact before validation.
- The structure keeps markdown as source of truth before any Linear import.

# Pure Kotlin Font Ticket Catalog

This catalog is the markdown source of truth for pure Kotlin font system tickets. It follows `../ROADMAP.md` and the ticket structure defined in `docs/superpowers/specs/2026-06-14-pure-kotlin-font-ticket-structure-design.md`.

Tickets are grouped by milestone for human navigation. Each ticket includes a French PM note, target spec sources, Kotlin-like pseudo-code, acceptance criteria, required evidence, validation commands, status metadata, fallback/refusal behavior, dashboard impact, and optional Linear labels.

## Status Model

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

## Milestones

| Milestone | Directory | Tickets | Purpose |
|---|---|---:|---|
| M0 | [M0-claims-ci-diagnostics](M0-claims-ci-diagnostics/README.md) | 5 | Establish CI, module boundaries, diagnostic namespaces, and claim classification before feature work starts. |
| M1 | [M1-font-identity-sources](M1-font-identity-sources/README.md) | 4 | Make font source and typeface identity deterministic, dumpable, and independent from host state unless explicitly marked. |
| M2 | [M2-sfnt-opentype-parser](M2-sfnt-opentype-parser/README.md) | 5 | Harden SFNT, TTC, cmap, and table fact parsing with bounded reads and stable diagnostics. |
| M3 | [M3-truetype-glyf](M3-truetype-glyf/README.md) | 5 | Complete deterministic TrueType outline scaling, composite glyph behavior, variation interpolation, and metrics evidence. |
| M4 | [M4-cff-cff2-scalers](M4-cff-cff2-scalers/README.md) | 5 | Add bounded CFF/CFF2 parsing, Type 2 execution, subroutine safety, and deterministic path output. |
| M5 | [M5-unicode-segmentation-bidi](M5-unicode-segmentation-bidi/README.md) | 5 | Pin Unicode data and provide deterministic grapheme, bidi, script, and cluster safety foundations. |
| M6 | [M6-opentype-layout-shaping](M6-opentype-layout-shaping/README.md) | 10 | Implement the target OpenType Layout shaping contract, basic and advanced lookups, script policy, and required script evidence. |
| M7 | [M7-fallback-system-fonts](M7-fallback-system-fonts/README.md) | 5 | Make fallback deterministic, traceable, variation-aware, cluster-safe, and explicit about host-dependent system scans. |
| M8 | [M8-paragraph-engine](M8-paragraph-engine/README.md) | 6 | Build the paragraph style, segmentation, line breaking, ellipsis, hit testing, and placeholder contracts over the shaping stack. |
| M9 | [M9-glyph-artifacts](M9-glyph-artifacts/README.md) | 6 | Create deterministic glyph artifact planning, strike keys, A8/SDF masks, atlas invalidation, and cache telemetry. |
| M10 | [M10-color-fonts-emoji](M10-color-fonts-emoji/README.md) | 10 | Implement typed color glyph, bitmap PNG, bounded SVG, and emoji route plans with fixtures and refusal classes. |
| M11 | [M11-gpu-handoff](M11-gpu-handoff/README.md) | 10 | Expose text artifacts to the GPU renderer through typed contracts, route diagnostics, WGSL validation, and no Skia-like leakage. |
| M12 | [M12-performance-telemetry](M12-performance-telemetry/README.md) | 5 | Make font/text costs visible with deterministic telemetry before turning budgets into release gates. |
| M13 | [M13-skia-facade-migration](M13-skia-facade-migration/README.md) | 5 | Move Skia-like facade APIs onto the pure Kotlin core while preserving explicit boundaries, diagnostics, and legacy gates. |

## Source Of Truth

Markdown remains the source of truth before optional Linear import. Linear tickets must preserve ticket ID, milestone, status, PM note, spec sources, acceptance criteria, required evidence, validation command, fallback/refusal behavior, legacy gate, and claim impact.

Ticket IDs use milestone-scoped numbering: `KFONT-M<milestone>-<sequence>`, for example `KFONT-M12-001`.

Claim impact must use the `../ROADMAP.md` taxonomy: `target-supported`, `current-supported`, `tracked-gap`, `DependencyGated`, `fixture-gated`, `GPU-gated`, `expected-unsupported`, or `drift-only`.

A ticket is ready only when it has one primary capability, explicit non-goals, spec sources, dependencies, fallback/refusal behavior, expected evidence, and validation commands.

## Templates

- [Milestone template](templates/milestone-template.md)
- [Ticket template](templates/ticket-template.md)

## Status

See [STATUS.md](STATUS.md) for the cross-milestone status summary.

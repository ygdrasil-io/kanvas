# M0 - Claims, CI, and Diagnostics

## Goal

Establish CI, module boundaries, diagnostic namespaces, and claim classification before feature work starts.

## Dependencies

None.

## Exit Criteria

- [x] The pure Kotlin font CI lane names all candidate `:font:*` validation tasks.
- [x] Pure Kotlin text spec and ticket edits trigger claim/dashboard validation.
- [x] Package boundaries prevent `Sk*` facade leakage and GPU renderer back edges.
- [ ] Diagnostic taxonomy and dashboard rows reject generic text/font claims.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M0-001 - Wire pure Kotlin font modules into CI](KFONT-M0-001-wire-pure-kotlin-font-modules-into-ci.md) | `review` | `P0` | `tracked-gap` | `ci` | - | - |
| [KFONT-M0-002 - Add pure-kotlin-text specs to CI trigger paths](KFONT-M0-002-add-pure-kotlin-text-specs-to-ci-trigger-paths.md) | `review` | `P0` | `tracked-gap` | `ci` | `KFONT-M0-001` | - |
| [KFONT-M0-003 - Freeze module/package layout for the pure Kotlin font core](KFONT-M0-003-freeze-module-package-layout-for-the-pure-kotlin-font-core.md) | `review` | `P0` | `tracked-gap` | `font-architecture` | `KFONT-M0-001` | - |
| [KFONT-M0-004 - Introduce stable diagnostic taxonomy](KFONT-M0-004-introduce-stable-diagnostic-taxonomy.md) | `review` | `P0` | `tracked-gap` | `diagnostics` | `KFONT-M0-003` | `font.native-engine-unavailable`, `font.bitmap-strike-unavailable`, `font.emoji-sequence-shaping-unsupported` |
| [KFONT-M0-005 - Harden dashboard claim classification](KFONT-M0-005-harden-dashboard-claim-classification.md) | `proposed` | `P0` | `tracked-gap` | `validation-dashboard` | `KFONT-M0-004` | `coloremoji_blendmodes`, `scaledemoji`, `scaledemoji_rendering`, `dftext`, `fontations`, `fontations_ft_compare`, `pdf_never_embed` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test :font:sfnt:test :font:scaler:test :font:text:test :font:glyph:test :font:gpu-api:test
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePerformanceTrendWarnings pipelinePmBundle
```

## Non-Claims

- No new font rendering, shaping, fallback, SDF, color, emoji, LCD, or GPU text support is claimed.
- CI and dashboard evidence only prove that future support claims will be checked.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.

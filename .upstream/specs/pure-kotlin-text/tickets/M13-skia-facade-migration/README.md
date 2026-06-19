# M13 - Skia-like Facade Migration

## Goal

Move Skia-like facade APIs onto the pure Kotlin core while preserving explicit boundaries, diagnostics, and legacy gates.

## Dependencies

Evidence-backed contracts from M1 through M12.

## Exit Criteria

- [ ] Facade adapter inventory maps every route to target contracts and remaining gates.
- [ ] SkTypeface, explicit SkShaper, and SkTextBlob routes delegate to pure Kotlin contracts where supported.
- [ ] Stale docs and stubs retire only after linked implementation and validation evidence exist.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M13-001 - Add facade adapter inventory](KFONT-M13-001-add-facade-adapter-inventory.md) | `proposed` | `P0` | `tracked-gap` | `skia-facade` | `KFONT-M1-004`, `KFONT-M2-005`, `KFONT-M6-010`, `KFONT-M8-006`, `KFONT-M9-006`, `KFONT-M11-010`, `KFONT-M12-005` | `coloremoji_blendmodes`, `scaledemoji`, `scaledemoji_rendering`, `dftext`, `fontations`, `fontations_ft_compare`, `pdf_never_embed` |
| [KFONT-M13-002 - Route `SkTypeface` OpenType facts through pure Kotlin core](KFONT-M13-002-route-sktypeface-opentype-facts-through-pure-kotlin-core.md) | `blocked` | `P1` | `tracked-gap` | `skia-facade` | `KFONT-M13-001`, `KFONT-M1-003`, `KFONT-M2-004` | `typeface` |
| [KFONT-M13-003 - Route explicit `SkShaper` APIs through pure Kotlin shaping](KFONT-M13-003-route-explicit-skshaper-apis-through-pure-kotlin-shaping.md) | `blocked` | `P1` | `tracked-gap` | `skia-facade` | `KFONT-M13-001`, `KFONT-M5-005`, `KFONT-M6-010`, `KFONT-M7-004` | `scaledemoji`, `scaledemoji_rendering` |
| [KFONT-M13-004 - Route `SkTextBlob` glyph runs through typed descriptors](KFONT-M13-004-route-sktextblob-glyph-runs-through-typed-descriptors.md) | `blocked` | `P1` | `tracked-gap` | `skia-facade` | `KFONT-M13-001`, `KFONT-M9-002`, `KFONT-M11-003` | `dftext` |
| [KFONT-M13-005 - Retire stale font docs and stubs after evidence promotion](KFONT-M13-005-retire-stale-font-docs-and-stubs-after-evidence-promotion.md) | `blocked` | `P1` | `tracked-gap` | `docs-validation` | `KFONT-M13-001`, `KFONT-M13-002`, `KFONT-M13-003`, `KFONT-M13-004`, `KFONT-M12-005` | `coloremoji_blendmodes`, `scaledemoji`, `scaledemoji_rendering`, `dftext`, `fontations`, `fontations_ft_compare`, `pdf_never_embed` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :kanvas-skia:test --tests 'org.skia.foundation.opentype.*' --tests 'org.skia.foundation.SkFont*' --tests 'org.skia.foundation.SkTypeface*' --tests 'org.skia.foundation.SkTextBlob*' --tests 'org.skia.foundation.SkShaper*'
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle
```

## Current Blockers

- 2026-06-19 readiness audit: `KFONT-M13-001` remains the next deblocking
  coordination slice. Its job is to inventory still-open shaping and GPU
  dependency gates such as `KFONT-M6-010` and `KFONT-M11-010`, rather than to
  wait for those gates to disappear before any facade inventory exists.
- 2026-06-19 readiness audit: `KFONT-M13-002`, `KFONT-M13-003`, and
  `KFONT-M13-004` are blocked on `KFONT-M13-001` because the facade inventory
  must define the route owner, diagnostics, legacy gate mapping, and PM row
  before facade parity dumps or typed descriptor routes are promoted.
- 2026-06-19 readiness audit: `KFONT-M13-003` also remains blocked on the
  still-open `KFONT-M6-010` advanced lookup fixture family, so explicit
  `SkShaper` routing must not imply broader complex-shaping support yet.
- 2026-06-19 readiness audit: `KFONT-M13-005` remains blocked on
  `KFONT-M13-001` through `KFONT-M13-004`; stale docs and stub labels must not
  retire before replacement routes, evidence, diagnostics, and dashboard rows
  exist.

## Non-Claims

- Legacy gates are not retired by documentation-only changes.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.

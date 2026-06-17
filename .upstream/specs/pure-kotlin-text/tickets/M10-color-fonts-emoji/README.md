# M10 - Color Fonts, Bitmap Glyphs, SVG, and Emoji

## Goal

Promote non-outline glyph representations into typed, pure Kotlin plans: COLRv0 layers, COLRv1 paint-operation groups, PNG bitmap glyphs, bounded SVG glyphs, emoji route traces, and fixture manifests with precise refusals.

## Dependencies

M2 supplies color, bitmap, and SVG table facts. M5 supplies Unicode emoji data and cluster boundaries. M6 supplies shaped runs and sequence facts. M7 supplies fallback decisions. M9 supplies glyph representation taxonomy and artifact keys.

## Exit Criteria

- [ ] COLRv0 and COLRv1 plans expose palette, paint graph, bounds, recursion, cycle, budget, and fallback facts.
- [ ] PNG bitmap glyph plans expose strike selection, alpha policy, source/decoded hashes, and non-PNG refusals.
- [ ] SVG glyph plans are pure Kotlin, glyph-scoped, bounded, and backed by supported/refused fixture classes.
- [ ] Emoji route traces show sequence kind, fallback attempts, selected representation, cluster safety, and diagnostics.
- [ ] The color/emoji fixture manifest maps every promoted route and legacy gate to provenance, expected dumps, diagnostics, and remaining GPU evidence.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M10-001 - Complete COLRv0 plan to artifact path](KFONT-M10-001-complete-colrv0-plan-to-artifact-path.md) | `done` | `P0` | `tracked-gap` | `color` | `KFONT-M2-001`, `KFONT-M6-001`, `KFONT-M9-002` | - |
| [KFONT-M10-002 - Implement COLRv1 solid/glyph/colr-glyph operation group](KFONT-M10-002-implement-colrv1-solid-glyph-colr-glyph-operation-group.md) | `done` | `P0` | `tracked-gap` | `color` | `KFONT-M10-001` | - |
| [KFONT-M10-003 - Implement COLRv1 gradient and variable-gradient operation group](KFONT-M10-003-implement-colrv1-gradient-and-variable-gradient-operation-group.md) | `done` | `P1` | `tracked-gap` | `color` | `KFONT-M10-002` | - |
| [KFONT-M10-004 - Implement COLRv1 transform/composite/clip operation group](KFONT-M10-004-implement-colrv1-transform-composite-clip-operation-group.md) | `proposed` | `P0` | `tracked-gap` | `color` | `KFONT-M10-002`, `KFONT-M10-003` | `coloremoji_blendmodes` |
| [KFONT-M10-005 - Add COLRv1 recursion, cycle and bounds fixtures](KFONT-M10-005-add-colrv1-recursion-cycle-and-bounds-fixtures.md) | `proposed` | `P1` | `fixture-gated` | `color` | `KFONT-M10-002`, `KFONT-M10-004` | `coloremoji_blendmodes` |
| [KFONT-M10-006 - Promote PNG bitmap glyph artifacts](KFONT-M10-006-promote-png-bitmap-glyph-artifacts.md) | `proposed` | `P1` | `tracked-gap` | `color` | `KFONT-M2-001`, `KFONT-M9-002` | `scaledemoji_rendering` |
| [KFONT-M10-007 - Implement bounded SVG glyph renderer primitives](KFONT-M10-007-implement-bounded-svg-glyph-renderer-primitives.md) | `proposed` | `P1` | `tracked-gap` | `color` | `KFONT-M2-001`, `KFONT-M9-002` | - |
| [KFONT-M10-008 - Implement SVG glyph refusal classes and bounds fixtures](KFONT-M10-008-implement-svg-glyph-refusal-classes-and-bounds-fixtures.md) | `proposed` | `P1` | `fixture-gated` | `color` | `KFONT-M10-007` | - |
| [KFONT-M10-009 - Implement emoji sequence planner](KFONT-M10-009-implement-emoji-sequence-planner.md) | `proposed` | `P0` | `tracked-gap` | `color` | `KFONT-M5-001`, `KFONT-M6-001`, `KFONT-M7-001`, `KFONT-M10-001`, `KFONT-M10-006` | `scaledemoji` |
| [KFONT-M10-010 - Add color/emoji fixture manifest](KFONT-M10-010-add-color-emoji-fixture-manifest.md) | `proposed` | `P0` | `fixture-gated` | `color` | `KFONT-M10-001`, `KFONT-M10-002`, `KFONT-M10-003`, `KFONT-M10-004`, `KFONT-M10-005`, `KFONT-M10-006`, `KFONT-M10-007`, `KFONT-M10-008`, `KFONT-M10-009` | `scaledemoji`, `scaledemoji_rendering`, `coloremoji_blendmodes` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*COLR*' --tests '*BitmapGlyph*' --tests '*SVGGlyph*' --tests '*Emoji*'
```

Required evidence for this milestone includes `color-glyph-plan.json`, `colrv1-paint-graph.json`, `color-glyph-composite-plan.json`, `colrv1-fixture-manifest.json`, `bitmap-glyph-plan.json`, `svg-glyph-plan.json`, `svg-glyph-fixture-manifest.json`, `emoji-route-trace.json`, and `color-emoji-fixture-manifest.json`.

## Non-Claims

- Metadata-only parsing is not color glyph rendering support.
- CPU/text color plans do not claim GPU composite, texture, SVG vector, or emoji rendering; M11 owns those route proofs.
- `scaledemoji`, `scaledemoji_rendering`, and `coloremoji_blendmodes` remain open until fixture, implementation, CPU oracle, GPU evidence, diagnostics, and dashboard updates are all linked.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.

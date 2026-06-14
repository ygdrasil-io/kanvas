# M10 - Color Fonts, Bitmap Glyphs, SVG, and Emoji

## Goal

Implement typed color glyph, bitmap PNG, bounded SVG, and emoji route plans with fixtures and refusal classes.

## Dependencies

M2 parser facts, M5 clusters, M6 shaping, M7 fallback, and M9 artifact planning.

## Exit Criteria

- [ ] COLRv0/COLRv1, PNG bitmap, SVG, and emoji route decisions have fixture evidence.
- [ ] Unsupported color/SVG/emoji behavior refuses with stable diagnostics.
- [ ] Legacy color/emoji gates remain visible until rendering and GPU evidence exist.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M10-001 - Complete COLRv0 plan to artifact path](KFONT-M10-001-complete-colrv0-plan-to-artifact-path.md) | `proposed` | `P0` | `tracked-gap` | `color` | `KFONT-M2-001`, `KFONT-M6-001`, `KFONT-M9-002` | - |
| [KFONT-M10-002 - Implement COLRv1 solid/glyph/colr-glyph operation group](KFONT-M10-002-implement-colrv1-solid-glyph-colr-glyph-operation-group.md) | `proposed` | `P0` | `tracked-gap` | `color` | `KFONT-M10-001` | - |
| [KFONT-M10-003 - Implement COLRv1 gradient and variable-gradient operation group](KFONT-M10-003-implement-colrv1-gradient-and-variable-gradient-operation-group.md) | `proposed` | `P1` | `tracked-gap` | `color` | `KFONT-M10-002` | - |
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
rtk ./gradlew --no-daemon :font:glyph:test
```

## Non-Claims

- Color metadata parsing is not a GPU rendering support claim.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.

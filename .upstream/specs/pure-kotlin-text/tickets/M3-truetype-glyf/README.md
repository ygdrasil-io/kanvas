# M3 - TrueType glyf Scaler

## Goal

Complete deterministic TrueType outline scaling, composite glyph behavior, variation interpolation, metrics evidence, and malformed-glyph isolation.

## Dependencies

M2 parser facts, `cmap` coverage, OpenType table fact dumps, and malformed SFNT diagnostics.

## Exit Criteria

- [ ] Simple and composite `glyf` outlines produce stable path, bounds, component trace, and metrics dumps.
- [ ] `gvar` IUP interpolation, phantom points, and advance deltas are covered by min/default/max variation fixtures.
- [ ] Vertical metric facts from `vhea`, `vmtx`, and `VVAR` are dumpable without claiming vertical shaping.
- [ ] Malformed glyphs are isolated or refused with precise diagnostics and fixture-backed policy.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M3-001 - Complete composite glyph transform coverage](KFONT-M3-001-complete-composite-glyph-transform-coverage.md) | `proposed` | `P0` | `tracked-gap` | `font-scaler` | `KFONT-M2-004` | - |
| [KFONT-M3-002 - Implement TrueType IUP interpolation tests](KFONT-M3-002-implement-truetype-iup-interpolation-tests.md) | `proposed` | `P0` | `tracked-gap` | `font-scaler` | `KFONT-M3-001` | - |
| [KFONT-M3-003 - Add phantom point and advance delta support](KFONT-M3-003-add-phantom-point-and-advance-delta-support.md) | `proposed` | `P0` | `tracked-gap` | `font-scaler` | `KFONT-M3-002` | - |
| [KFONT-M3-004 - Add vertical metric coverage](KFONT-M3-004-add-vertical-metric-coverage.md) | `proposed` | `P1` | `tracked-gap` | `font-scaler` | `KFONT-M2-004`, `KFONT-M3-003` | - |
| [KFONT-M3-005 - Add glyf malformed isolation suite](KFONT-M3-005-add-glyf-malformed-isolation-suite.md) | `proposed` | `P0` | `fixture-gated` | `fixtures` | `KFONT-M3-001`, `KFONT-M3-003` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test --tests '*Glyf*' --tests '*CompositeGlyph*' --tests '*IUP*' --tests '*Gvar*' --tests '*PhantomPoint*' --tests '*AdvanceDelta*' --tests '*VerticalMetric*' --tests '*MalformedGlyf*'
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*TableFactDump*'
```

## Non-Claims

- Pixel-perfect FreeType hinting, CFF/CFF2 outlines, shaping, paragraph layout, A8/SDF artifacts, and GPU glyph routes remain out of scope.
- Vertical metric extraction does not claim vertical text layout.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.

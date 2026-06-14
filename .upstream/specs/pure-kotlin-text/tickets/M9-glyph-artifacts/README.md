# M9 - Glyph Artifacts, A8, SDF, Outline, and Cache

## Goal

Create deterministic glyph artifact planning, strike keys, A8/SDF masks, atlas invalidation, and cache telemetry.

## Dependencies

M3 scalers and M6 shaped run contract.

## Exit Criteria

- [ ] Glyph artifact route decisions are typed and dumpable.
- [ ] A8 and SDF CPU artifacts have hashes, bounds, and refusal evidence.
- [ ] Atlas cache invalidation and telemetry are deterministic.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M9-001 - Complete `GlyphStrikeKey`](KFONT-M9-001-complete-glyphstrikekey.md) | `proposed` | `P0` | `tracked-gap` | `glyph` | `KFONT-M3-001`, `KFONT-M6-001` | - |
| [KFONT-M9-002 - Promote `GlyphArtifactPlan` route taxonomy](KFONT-M9-002-promote-glyphartifactplan-route-taxonomy.md) | `proposed` | `P0` | `tracked-gap` | `glyph` | `KFONT-M9-001` | - |
| [KFONT-M9-003 - Implement quadratic/cubic outline rasterization for A8](KFONT-M9-003-implement-quadratic-cubic-outline-rasterization-for-a8.md) | `proposed` | `P1` | `tracked-gap` | `glyph` | `KFONT-M3-001`, `KFONT-M9-001`, `KFONT-M9-002` | - |
| [KFONT-M9-004 - Implement production SDF generator boundaries](KFONT-M9-004-implement-production-sdf-generator-boundaries.md) | `proposed` | `P0` | `tracked-gap` | `glyph` | `KFONT-M9-001`, `KFONT-M9-002`, `KFONT-M9-003` | `dftext` |
| [KFONT-M9-005 - Add atlas eviction and invalidation tests](KFONT-M9-005-add-atlas-eviction-and-invalidation-tests.md) | `proposed` | `P1` | `tracked-gap` | `glyph` | `KFONT-M9-001`, `KFONT-M9-003`, `KFONT-M9-004` | `dftext` |
| [KFONT-M9-006 - Add glyph cache telemetry](KFONT-M9-006-add-glyph-cache-telemetry.md) | `proposed` | `P2` | `tracked-gap` | `glyph` | `KFONT-M9-005` | `dftext` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test
```

## Non-Claims

- GPU sampling routes remain gated by M11 evidence.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.

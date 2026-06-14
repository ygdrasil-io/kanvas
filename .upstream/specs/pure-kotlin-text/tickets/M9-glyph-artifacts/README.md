# M9 - Glyph Artifacts, A8, SDF, Outline, and Cache

## Goal

Turn shaped glyph runs into deterministic renderer-neutral artifacts: complete strike keys, route plans, A8 masks, SDF masks, atlas artifacts, invalidation evidence, and cache telemetry.

## Dependencies

M3 supplies stable TrueType outlines and metrics. M6 supplies shaped glyph runs. M8 paragraph output may feed this milestone later, but M9 must also work from explicit `ShapedGlyphRun` fixtures.

## Exit Criteria

- [ ] `GlyphStrikeKey` includes every rendering-affecting fact and excludes live handles, atlas coordinates, GPU resources, and object identity.
- [ ] `GlyphArtifactPlan` records selected route, rejected alternatives, fallback policy, and diagnostics for outline, A8, SDF, color, bitmap, SVG, and unsupported routes.
- [ ] A8 and SDF CPU artifacts have deterministic bounds, hashes, key preimages, and refusal evidence.
- [ ] Atlas artifacts expose entry refs, generation, invalidation tokens, eviction traces, source hashes, and budget diagnostics.
- [ ] Cache inventory and telemetry dumps separate generation, packing, eviction, invalidation, and upload-preparation costs.

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
rtk ./gradlew --no-daemon :font:glyph:test --tests '*Glyph*'
```

Required evidence for this milestone includes `glyph-strike-key.json`, `glyph-artifact-plan.json`, `a8-glyph-mask.json`, `sdf-glyph-artifact.json`, `glyph-atlas.json`, `glyph-atlas-eviction-trace.json`, `glyph-cache-inventory.json`, and `glyph-cache-telemetry.json`.

## Non-Claims

- M9 artifacts do not claim GPU sampling, WGSL validation, upload ordering, or `DrawTextRun` integration; M11 owns those claims.
- LCD subpixel text remains future research and must refuse with a stable diagnostic.
- `dftext` remains open until CPU SDF artifacts, atlas/cache evidence, GPU route evidence, and dashboard updates are all linked.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.

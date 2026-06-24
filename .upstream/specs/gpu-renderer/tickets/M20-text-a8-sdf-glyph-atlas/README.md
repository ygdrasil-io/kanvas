# M20 - Text A8 + SDF Glyph Atlas

## Goal

Deliver A8 and SDF text atlas rendering, DrawTextRun execution, and text shaper integration with controlled product activation.

## Dependencies

Depends on M12 text dependencies (KGPU-M12-001 through KGPU-M12-004). Wave 3 milestone.

## Exit Criteria

- [ ] A8 glyph atlas renders Latin text correctly on GPU
- [ ] SDF glyph atlas renders scale-independent text
- [ ] DrawTextRun batches glyph subruns efficiently
- [ ] Text shaper produces correct glyph positions
- [ ] All text routes are product-activated with rollback

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M20-001 - Add text A8 atlas execution: glyph mask upload -> atlas texture -> WGSL sample](KGPU-M20-001-text-a8-atlas.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `text-rendering` | [KGPU-M12-003, KGPU-M12-004] | null |
| [KGPU-M20-002 - Add SDF glyph atlas: signed distance field generation + WGSL smoothstep](KGPU-M20-002-text-sdf-atlas.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `text-rendering` | [KGPU-M12-003] | null |
| [KGPU-M20-003 - Add DrawTextRun execution: subrun batch -> atlas bind -> draw fullscreen pass](KGPU-M20-003-draw-text-run.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `text-rendering` | [KGPU-M20-001, KGPU-M20-002] | null |
| [KGPU-M20-004 - Add text shaper integration: SkShaper -> GlyphRunDescriptor -> GPU route](KGPU-M20-004-text-shaper-integration.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `text-shaper` | [KGPU-M12-001, KGPU-M12-004] | null |
| [KGPU-M20-005 - Activate M20 routes: A8 + SDF text default ON with rollback](KGPU-M20-005-route-activation.md) | `proposed` | `P0` | `PolicyGated` | `GPUNative` | `false` | `true` | `product-validation` | [KGPU-M20-001, KGPU-M20-002, KGPU-M20-003, KGPU-M20-004] | legacy drawText |
| [KGPU-M20-006 - Add gpu-renderer-scenes evidence: glyph-atlas-strip, sdf-glyph-scale](KGPU-M20-006-scenes-evidence.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `scenes-evidence` | [KGPU-M20-001, KGPU-M20-002, KGPU-M20-003, KGPU-M20-004] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*TextA8*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*TextSDF*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*DrawTextRun*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*TextShaper*'
```

## Non-Claims

- Latin text only
- No bidi, complex scripts, color fonts, COLRv1, SVG glyphs
- No emoji
- No performance readiness claims

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.

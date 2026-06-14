---
id: KGPU-M6-002
title: "Add A8 glyph atlas sampling route"
status: proposed
milestone: M6
priority: P0
owner_area: text-atlas
claim_impact: TargetPrepared
route_kind: CPUPreparedGPU
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M6-003, KFONT-M11-004, KFONT-M11-008, KFONT-M11-009]
legacy_gate: dftext
---

# KGPU-M6-002 - Add A8 glyph atlas sampling route

## PM Note

Ce ticket prouve le premier rendu texte GPU borné via atlas A8.

## Problem

Atlas text needs upload, generation, instance layout, binding, WGSL, and
readback evidence before support can be claimed.

## Scope

- Add A8 atlas sampling route for typed glyph artifacts.
- Add stale/missing atlas and upload refusals.

## Non-Goals

- No broad shaping, fallback fonts, SDF, LCD, emoji, or color fonts.

## Spec Sources

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`

## Graphite Algorithm References

- [`GFX-BITMAP-TEXT-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-bitmap-text-step) - source [BitmapTextRenderStep.cpp:59](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/BitmapTextRenderStep.cpp:59); Reference A8/LCD/color variants and indexed atlas sampling.
- [`GFX-TEXT-ATLAS-GLYPH-UPLOAD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-glyph-upload) - source [TextAtlasManager.cpp:237](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:237); Study glyph mask normalization, padding, and atlas upload.
- [`GFX-DRAW-ATLAS-PLOTS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-atlas-plots) - source [DrawAtlas.cpp:149](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawAtlas.cpp:149); Use plot allocation/retry behavior for atlas overflow diagnostics.
- [`GFX-SUBRUN-DATA`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-subrun-data) - source [SubRunData.h:24](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/geom/SubRunData.h:24); Keep glyph range, mask bounds, and recorder ownership typed.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class A8TextRouteEvidence(val atlasPage: String, val instanceLayout: String)
```

## Acceptance Criteria

- [ ] Atlas generation and upload-before-sample ordering are dumpable.
- [ ] WGSL reflection and binding evidence are linked.
- [ ] Unsupported text routes refuse.

## Required Evidence

- Atlas, upload, instance, binding, WGSL, route, and readback evidence.

## Fallback / Refusal Behavior

Unsupported or stale atlas facts refuse; no full text texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.text.a8-atlas`
- Expected classification: `TargetPrepared`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*Text*'
rtk git diff --check
```

## Status Notes

- `proposed`: First bounded text route after text resource/upload/binding plans.

## Linear Labels

- `gpu-renderer`
- `milestone:M6`
- `area:text-atlas`

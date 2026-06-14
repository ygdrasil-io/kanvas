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
depends_on: [KGPU-M6-001, KFONT-M11-004]
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

- `proposed`: First bounded text route.

## Linear Labels

- `gpu-renderer`
- `milestone:M6`
- `area:text-atlas`

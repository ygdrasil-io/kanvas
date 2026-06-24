---
id: KGPU-M26-004
title: "Replace bitmap/tile-mode scene PNGs with real-image renders"
status: done
milestone: M26
priority: P0
owner_area: scenes-evidence
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M26-001, KGPU-M26-002]
legacy_gate: null
---

# KGPU-M26-004 - Replace bitmap/tile-mode scene PNGs with real-image renders

## PM Note

Les PNGs des scenes bitmap montrent encore un damier procedural. Ce ticket
regenere et committe les PNGs avec de vraies images pour que le PM voie la
preuve d'un rendu d'image reel.

## Problem

The committed scene PNGs for `bitmap-sampler-matrix` and `tile-mode-strip` still
show the procedural checkerboard. Once M26-001/002 sample a real decoded image,
the evidence PNGs must be regenerated and committed so the scene catalog proves
real images render, not checkerboards. Support cannot be promoted while the
committed evidence shows procedural content.

## Scope

- Regenerate the `bitmap-sampler-matrix` and `tile-mode-strip` scene PNGs with the real decoded image
- Commit the new PNGs (and `run.json`) under `reports/gpu-renderer-scenes/`
- Confirm the PNGs show a real image with distinct clamp/repeat/mirror/decal regions
- Keep `RectOnlyOffscreenRenderer` diagnostic PNGs separate from real-image evidence

## Non-Goals

- No renderer wiring changes (KGPU-M26-001/002 own the texture path)
- No text atlas evidence (KGPU-M26-003)
- No new scenes beyond the bitmap/tile-mode scenes
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M17-image-shader-codec-upload/README.md`
- `reports/gpu-renderer-scenes/offscreen/`

## Design Sketch

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=bitmap-sampler-matrix
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=tile-mode-strip
rtk git add reports/gpu-renderer-scenes/offscreen/bitmap-sampler-matrix/ \
            reports/gpu-renderer-scenes/offscreen/tile-mode-strip/
```

## Acceptance Criteria

- [ ] `bitmap-sampler-matrix` PNG shows a real decoded image (not a checkerboard)
- [ ] `tile-mode-strip` PNG shows clamp/repeat/mirror/decal of the real image
- [ ] New PNGs and `run.json` are committed under `reports/gpu-renderer-scenes/`
- [ ] Each `run.json` reports `status: rendered`

## Required Evidence

- Committed `bitmap-sampler-matrix` PNG + `run.json`
- Committed `tile-mode-strip` PNG + `run.json`
- Before/after note confirming the checkerboard was replaced by a real image

## Fallback / Refusal Behavior

If the GPU is unavailable, the render task emits a `gpu-unavailable` diagnostic
and scenes remain not-yet-rendered. PNGs are not committed from procedural
fallback output.

## Dashboard Impact

- Expected row: `gpu-renderer.m26.scene-real-image-pngs`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=bitmap-sampler-matrix
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=tile-mode-strip
rtk cat reports/gpu-renderer-scenes/offscreen/bitmap-sampler-matrix/run.json | rtk jq .status
```

## Status Notes

- `done`: M26-004 completed: bitmap-sampler-matrix, tile-mode-strip, glyph-atlas-strip, sdf-glyph-scale PNGs regenerated with real decoded textures/atlases. All 4 scenes render and produce committed real-image PNGs.

## Linear Labels

- `gpu-renderer`
- `milestone:M26`
- `area:scenes-evidence`

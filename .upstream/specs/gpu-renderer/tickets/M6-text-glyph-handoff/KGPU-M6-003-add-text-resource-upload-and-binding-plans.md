---
id: KGPU-M6-003
title: "Add text resource upload and binding plans"
status: blocked
milestone: M6
priority: P0
owner_area: text-resources
claim_impact: TargetPrepared
route_kind: CPUPreparedGPU
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M6-001, KFONT-M11-007]
legacy_gate: dftext
---

# KGPU-M6-003 - Add text resource upload and binding plans

## PM Note

Ce ticket rend les ressources texte vérifiables avant le draw.

## Problem

Text routes need upload plans, resource descriptors, binding order, and
generation checks before atlas sampling.

## Scope

- Add text upload, resource, instance, and binding plan evidence.
- Add missing/stale/budget refusal fixtures.

## Non-Goals

- Do not add new glyph generation.
- Do not support SDF/color glyphs.

## Spec Sources

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/17-payload-gathering-and-slots.md`

## Graphite Algorithm References

- [`GFX-TEXT-ATLAS-CONFIG`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-config) - source [TextAtlasManager.cpp:47](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:47); Use atlas dimensions and multitexturing as resource planning references.
- [`GFX-TEXT-ATLAS-GLYPH-UPLOAD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-glyph-upload) - source [TextAtlasManager.cpp:237](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:237); Study glyph upload layout and pending atlas upload recording.
- [`GFX-UPLOAD-TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-upload-task) - source [UploadTask.cpp:309](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/UploadTask.cpp:309); Reference upload preparation/replay for text atlas binding evidence.
- [`GFX-BITMAP-TEXT-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-bitmap-text-step) - source [BitmapTextRenderStep.cpp:59](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/BitmapTextRenderStep.cpp:59); Use atlas texture binding behavior for WebGPU binding plans.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class TextBindingEvidence(val uploadPlan: String, val bindingPlan: String)
```

## Acceptance Criteria

- [ ] Upload-before-sample ordering is explicit.
- [ ] Binding layout matches WGSL reflection.
- [ ] Stale generation refuses.

## Required Evidence

- Upload, binding, payload, resource, and refusal dumps.

## Fallback / Refusal Behavior

Text draw refuses until required artifacts and uploads exist.

## Dashboard Impact

- Expected row: `gpu-renderer.text-resource-binding`
- Expected classification: `TargetPrepared`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `blocked`: KGPU-M6-001 is now done, but this ticket still requires accepted
  KFONT-M11-007 resource/upload contracts plus adapter-backed resource, upload,
  instance, binding, stale generation, budget, and no-`MaterialKey`-leak
  evidence before the text resource plan can support KGPU-M6-002.

## Linear Labels

- `gpu-renderer`
- `milestone:M6`
- `area:text-resources`

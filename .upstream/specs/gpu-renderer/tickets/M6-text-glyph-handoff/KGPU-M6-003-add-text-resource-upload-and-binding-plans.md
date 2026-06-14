---
id: KGPU-M6-003
title: "Add text resource upload and binding plans"
status: proposed
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

- `proposed`: Required by A8 text route.

## Linear Labels

- `gpu-renderer`
- `milestone:M6`
- `area:text-resources`

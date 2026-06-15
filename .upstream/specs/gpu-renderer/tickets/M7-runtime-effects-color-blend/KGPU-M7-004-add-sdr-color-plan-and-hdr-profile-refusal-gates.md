---
id: KGPU-M7-004
title: "Add SDR color plan and HDR profile refusal gates"
status: done
milestone: M7
priority: P1
owner_area: color
claim_impact: DependencyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M2-002]
legacy_gate: "color legacy"
---

# KGPU-M7-004 - Add SDR color plan and HDR profile refusal gates

## PM Note

Ce ticket sépare le comportement SDR borné des futurs profils/HDR.

## Problem

Color behavior affects material, image, layer, blend, and store semantics and
must not be silently reinterpreted.

## Scope

- Add SDR color value/spec and store plan evidence.
- Add refusals for HDR, gainmap, ICC/CICP/profile-dependent behavior.

## Non-Goals

- Do not add broad color management.
- Do not add HDR support.

## Spec Sources

- `.upstream/specs/gpu-renderer/29-color-management-pipeline.md`

## Graphite Algorithm References

- [`GFX-RUNTIME-EFFECT-PREAMBLE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-runtime-effect-preamble) - source [ShaderCodeDictionary.cpp:638](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ShaderCodeDictionary.cpp:638); Reference color-transform callback handling for SDR/HDR refusal boundaries.
- [`GFX-PAINTPARAMS-TO-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paintparams-to-key) - source [PaintParams.cpp:222](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParams.cpp:222); Study paint color/color-filter/final-blend lowering into key and destination metadata.
- [`GFX-GRADIENT-STOPS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-gradient-stops) - source [KeyHelpers.cpp:166](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:166); Use color-space interpolation metadata as a color-plan comparison point.
- [`GFX-BLEND-KEYING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-blend-keying) - source [KeyHelpers.cpp:2593](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:2593); Keep advanced blend and color-plan refusals separated.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class ColorPlanEvidence(val valueSpec: String, val storePlan: String)
```

## Acceptance Criteria

- [ ] SDR color facts are dumpable.
- [ ] Unsupported profile/HDR cases refuse.
- [ ] Material and pipeline keys include only behavior-affecting color facts.

## Required Evidence

- Color plan and refusal dumps.

## Fallback / Refusal Behavior

Unsupported color/profile cases refuse; no silent reinterpretation.

## Dashboard Impact

- Expected row: `gpu-renderer.color-sdr-boundary`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Added bounded `GPUSDRColorBoundaryPlanner` evidence for finite sRGB
  value/store planning and terminal refusals for HDR, gainmap, ICC/CICP,
  untagged policy, and extended range. Independent review
  `019ec850-9390-7240-9313-1f9af4b9a77d` accepted the evidence with no
  findings. Evidence remains contract/refusal-only: no product activation,
  GPU-native color route, broad color management, profile conversion, HDR,
  gainmap, untagged policy, or platform color conversion is implied.

## Linear Labels

- `gpu-renderer`
- `milestone:M7`
- `area:color`

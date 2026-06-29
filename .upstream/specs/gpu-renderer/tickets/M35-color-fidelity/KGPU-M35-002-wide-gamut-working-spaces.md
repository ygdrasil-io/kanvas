---
id: KGPU-M35-002
title: "Wide-gamut working spaces — Display P3, Adobe RGB, Rec.2020"
<<<<<<< HEAD
status: done
=======
status: proposed
>>>>>>> master
milestone: M35
priority: P0
owner_area: color
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: legacy color transform
---

# KGPU-M35-002 - Wide-gamut working spaces — Display P3, Adobe RGB, Rec.2020

## PM Note

Les espaces wide-gamut (Display P3, Adobe RGB, Rec.2020) dépassent le sRGB pour le calcul couleur et le stockage intermédiaire.

## Problem

Wide-gamut working spaces (Display P3, Adobe RGB, Rec.2020) are spec'd as TargetNative
in 29-color-management-pipeline.md, but no GPU-native route exists. Without wide-gamut
intermediate formats and conversion plans, content tagged with non-sRGB primaries
degrades to sRGB with visible clipping.

## Scope

- Implement `GPUWideGamutWorkingSpacePlan` with primaries for Display P3, Adobe RGB, Rec.2020.
- Implement `GPUWideGamutConversionPlan` for matrix + transfer function pairs between working spaces.
- Implement `GPUWideGamutIntermediateFormat` (rgba16float or rgba32float) for saveLayer and intermediate surfaces.
- Route: tagged image → wide-gamut decode → intermediate format → target-space conversion.
- WGSL color transforms validated via wgsl4k.
- CPU oracle parity for wide-gamut to sRGB conversion and gradient interpolation.

## Non-Goals

- No ACEScg or ACEScct working spaces in this ticket.
- No gamut mapping (gamut compression/clipping) beyond simple conversion.
- No ICC-based gamut boundary description.

## Spec Sources

- `.upstream/specs/gpu-renderer/29-color-management-pipeline.md` (Wide-Gamut Working Spaces)
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`

## Graphite Algorithm References

- [`GFX-GRADIENT-STOPS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-gradient-stops) - source [KeyHelpers.cpp:166](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:166); Pack gradient stops into uniforms and carry tile mode/color-space interpolation metadata through the paint key.
- [`GFX-PAINTPARAMS-TO-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paintparams-to-key) - source [PaintParams.cpp:222](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParams.cpp:222); Lower paint color, image shader, primitive color, color filters, and final blend into key blocks.
- [`GFX-IMAGE-SAMPLER-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-image-sampler-key) - source [KeyHelpers.cpp:530](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:530); Select image shader variants for HW/cubic/clamp/manual tiling, add texture/sampler bindings, and embed immutable sampler data.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
enum class GPUWideGamutPrimaries { DisplayP3, AdobeRGB, Rec2020 }

data class GPUWideGamutWorkingSpacePlan(
    val primaries: GPUWideGamutPrimaries,
    val transferFunction: GPUHDRTransferFunction?,
    val intermediateFormat: GPUWideGamutIntermediateFormat,
)

data class GPUWideGamutConversionPlan(
    val source: GPUWideGamutWorkingSpacePlan,
    val destination: GPUWideGamutWorkingSpacePlan,
    val matrix: FloatArray, // 3x3 or 4x4 conversion matrix
    val transferConversion: GPUHDRTransferFunctionPlan?,
    val wgslModule: WGSLFragmentModule,
)

enum class GPUWideGamutIntermediateFormat { rgba16float, rgba32float }

sealed interface GPUWideGamutRoute {
    data class Accepted(
        val workingSpace: GPUWideGamutWorkingSpacePlan,
        val conversion: GPUWideGamutConversionPlan,
        val intermediateFormat: GPUWideGamutIntermediateFormat,
    ) : GPUWideGamutRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : GPUWideGamutRoute
}
```

## Acceptance Criteria

- [ ] Display P3 tagged image decoded and rendered with GPU evidence vs sRGB fallback.
- [ ] saveLayer wide-gamut intermediate preserves color fidelity within DeltaE tolerance vs CPU oracle.
- [ ] Gradient interpolation in wide-gamut uses transfer-function-aware interpolation (not naive linear).
- [ ] Adobe RGB and Rec.2020 conversion matrices validated via wgsl4k.
- [ ] Unsupported gamut produces stable `unsupported.color.wide_gamut_working_space` diagnostic.

## Required Evidence

- GPUWideGamutWorkingSpacePlan deterministic dump (P3, AdobeRGB, Rec.2020).
- GPUWideGamutConversionPlan deterministic dump with matrix + transfer pairs.
- CPU oracle comparison: P3 → sRGB, AdobeRGB → sRGB, Rec.2020 → sRGB.
- saveLayer wide-gamut fidelity test with DeltaE measurement.
- Transfer-function-aware gradient interpolation evidence.
- Refusal fixtures: unsupported gamut.

## Fallback / Refusal Behavior

- Unsupported gamut → `unsupported.color.wide_gamut_working_space`.
- WGSL validation failure → `unsupported.color.wide_gamut_wgsl_unvalidated`.
- No intermediate format available → `unsupported.color.wide_gamut_intermediate_format`.
- No CPU-rendered texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.color.wide-gamut`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless accepted GPU evidence and CPU oracle parity.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*WideGamut*'
```

## Status Notes

- `proposed`: Initial ticket. Awaiting M35 milestone acceptance.
<<<<<<< HEAD
- `proposed → ready` (2026-06-28): milestone activated, autonomous implementation starting.
- `ready → review` (2026-06-28): implemented. Pending independent review.
- `review → done` (2026-06-28): reviewed and fixed.
=======
>>>>>>> master

## Linear Labels

- `gpu-renderer`
- `milestone:M35`
- `area:color`

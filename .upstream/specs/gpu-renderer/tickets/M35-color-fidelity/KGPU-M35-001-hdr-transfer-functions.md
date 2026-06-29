---
id: KGPU-M35-001
title: "HDR transfer functions — PQ, HLG, scRGB with EOTF and tone map"
status: done
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

# KGPU-M35-001 - HDR transfer functions — PQ, HLG, scRGB with EOTF and tone map

## PM Note

Les fonctions de transfert HDR (PQ, HLG, scRGB) permettent le rendu HDR natif sur cibles compatibles.

## Problem

HDR transfer functions (PQ, HLG, scRGB) are spec'd as TargetNative in
29-color-management-pipeline.md, but no GPU-native route exists. Without GPU-side
EOTF and tone mapping, HDR images cannot be rendered correctly on HDR targets or
tone-mapped to SDR fallback surfaces.

## Scope

- Implement `GPUHDRTransferFunctionPlan` with transfer function enum (PQ, HLG, scRGBLinear).
- Implement `GPUHDREOTFPlan` for GPU-side display mapping (per-frame EOTF application).
- Implement `GPUHDRToneMapPlan` with strategy enum (Reinhard, ACES, Hable, Custom) for SDR fallback.
- Route: HDR image → transfer function → EOTF → tone map → HDR target or SDR fallback.
- WGSL color transforms validated via wgsl4k.
- CPU oracle parity for PQ and HLG transfer function application.
- Refusal fixtures for unsupported transfer functions and missing HDR target format.

## Non-Goals

- No HDR on SDR-only targets beyond tone-mapped fallback.
- No dynamic HDR metadata processing (HDR10+, Dolby Vision).
- No display-side EDID or HDR capability negotiation in this ticket.

## Spec Sources

- `.upstream/specs/gpu-renderer/29-color-management-pipeline.md` (HDR Transfer Functions)
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`

## Graphite Algorithm References

- [`GFX-GRADIENT-STOPS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-gradient-stops) - source [KeyHelpers.cpp:166](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:166); Pack gradient stops into uniforms, move larger stop tables into storage buffers or textures, and carry tile mode/color-space interpolation metadata.
- [`GFX-PAINTPARAMS-TO-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paintparams-to-key) - source [PaintParams.cpp:222](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParams.cpp:222); Lower paint color, image shader, primitive color, color filters, and final blend into key blocks while producing destination-usage metadata.
- [`GFX-BLEND-KEYING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-blend-keying) - source [KeyHelpers.cpp:2593](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:2593); Reduce coefficient blends to constants, group HSL advanced blends into a shared snippet.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
enum class GPUHDRTransferFunction { PQ, HLG, scRGBLinear }

data class GPUHDRTransferFunctionPlan(
    val transferFunction: GPUHDRTransferFunction,
    val wgslModule: WGSLFragmentModule,
    val inputFormat: GPUColorFormat,
)

data class GPUHDREOTFPlan(
    val eotf: GPUHDRTransferFunction,
    val displayPeakLuminance: Float,
    val wgslModule: WGSLFragmentModule,
)

enum class GPUHDRToneMapStrategy { Reinhard, ACES, Hable, Custom }

data class GPUHDRToneMapPlan(
    val strategy: GPUHDRToneMapStrategy,
    val displayPeakLuminance: Float,
    val wgslModule: WGSLFragmentModule,
)

sealed interface GPUHDRTransferRoute {
    data class Accepted(
        val transferPlan: GPUHDRTransferFunctionPlan,
        val eotfPlan: GPUHDREOTFPlan,
        val toneMapPlan: GPUHDRToneMapPlan?,
    ) : GPUHDRTransferRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : GPUHDRTransferRoute
}
```

## Acceptance Criteria

- [ ] PQ-encoded image decoded with correct EOTF and GPU evidence (CPU oracle parity).
- [ ] HLG scene-referred content mapped to display with GPU evidence.
- [ ] scRGB linear float rendered without quantization artifacts.
- [ ] HDR-to-SDR tone mapping produces output within CPU oracle DeltaE tolerance.
- [ ] WGSL color transforms validated via wgsl4k.
- [ ] Unsupported transfer function produces stable `unsupported.color.hdr_transfer_function` diagnostic.
- [ ] No HDR target format available produces stable `unsupported.color.hdr_target_format` diagnostic.

## Required Evidence

- GPUHDRTransferFunctionPlan deterministic dump (PQ, HLG, scRGB).
- GPUHDREOTFPlan deterministic dump.
- GPUHDRToneMapPlan deterministic dump (Reinhard, ACES, Hable).
- CPU oracle comparison for PQ and HLG transfer function application.
- Refusal fixtures: unsupported transfer function, no HDR target format.
- WGSL fragment module validation reports via wgsl4k.

## Fallback / Refusal Behavior

- Unsupported transfer function → `unsupported.color.hdr_transfer_function`.
- No HDR target format → `unsupported.color.hdr_target_format`.
- WGSL validation failure → `unsupported.color.hdr_wgsl_unvalidated`.
- No CPU-rendered texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.color.hdr-transfer`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless accepted GPU evidence and CPU oracle parity.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*HDR*'
```

## Status Notes

- `proposed`: Initial ticket. Awaiting M35 milestone acceptance.
- `proposed → ready` (2026-06-28): milestone activated, autonomous implementation starting.
- `ready → review` (2026-06-28): implemented. Pending independent review.
- `review → done` (2026-06-28): reviewed and fixed.
- `done → review` (2026-06-29): reopened — needs real wgsl4k AST reflection/walker instead of workaround.
- `review → done` (2026-06-29): wgsl4k reflection wired — compute entry point, WGSL validation, entry point stage, assembled module validation.

## Linear Labels

- `gpu-renderer`
- `milestone:M35`
- `area:color`

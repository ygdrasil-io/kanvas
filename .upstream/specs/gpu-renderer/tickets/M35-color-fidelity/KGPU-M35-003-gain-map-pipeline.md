---
id: KGPU-M35-003
title: "Gain map pipeline — Ultra HDR decode, apply, and display adaptation"
status: proposed
milestone: M35
priority: P1
owner_area: color
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: legacy color transform
---

# KGPU-M35-003 - Gain map pipeline — Ultra HDR decode, apply, and display adaptation

## PM Note

Les gain maps (Ultra HDR / Android) permettent le rendu adaptatif HDR/SDR à partir d'une seule image.

## Problem

Gain map pipelines (Ultra HDR JPEG, Android gain map) are spec'd as TargetNative
in 29-color-management-pipeline.md, but no GPU-native route exists. Without GPU-side
gain map application, HDR images with gain map metadata render as SDR-only, losing
the adaptive HDR/SDR rendering capability.

## Scope

- Implement `GPUGainmapDecodePlan` for base image + gain map image + metadata extraction.
- Implement `GPUGainmapApplyPlan` for per-pixel WGSL gain map application (HDR reconstruction).
- Implement `GPUGainmapDisplayAdaptationPlan` for adaptive rendering based on current display headroom.
- Route: Ultra HDR JPEG → decode + metadata → gain map apply → display adaptation → HDR or tone-mapped SDR.
- WGSL gain map application validated via wgsl4k.
- CPU oracle parity for gain map application output.

## Non-Goals

- No gain map generation or encoding.
- No multi-layer gain maps beyond single gain map + base image.
- No gain map for non-JPEG codec formats unless codec support is available.

## Spec Sources

- `.upstream/specs/gpu-renderer/29-color-management-pipeline.md` (Gain Map Pipeline)
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`

## Graphite Algorithm References

- `GFX-GAINMAP` from `GRAPHITE-ALGORITHM-REFERENCES.md` — GPU-side gain map application patterns. Algorithm reference only.

## Design Sketch

```kotlin
data class GPUGainmapDecodePlan(
    val baseImage: GPUTextureDescriptor,
    val gainMapImage: GPUTextureDescriptor,
    val metadata: GPUGainmapMetadata,
)

data class GPUGainmapMetadata(
    val gainMapMin: FloatArray?,
    val gainMapMax: FloatArray?,
    val gamma: FloatArray?,
    val offsetSDR: FloatArray?,
    val offsetHDR: FloatArray?,
    val hdrCapacityMin: Float?,
    val hdrCapacityMax: Float?,
)

data class GPUGainmapApplyPlan(
    val decodePlan: GPUGainmapDecodePlan,
    val wgslModule: WGSLFragmentModule,
    val outputFormat: GPUColorFormat,
)

data class GPUGainmapDisplayAdaptationPlan(
    val applyPlan: GPUGainmapApplyPlan,
    val displayHeadroom: Float,
    val targetIsHDR: Boolean,
)

sealed interface GPUGainmapRoute {
    data class Accepted(
        val decode: GPUGainmapDecodePlan,
        val apply: GPUGainmapApplyPlan,
        val adaptation: GPUGainmapDisplayAdaptationPlan,
    ) : GPUGainmapRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : GPUGainmapRoute
}
```

## Acceptance Criteria

- [ ] Ultra HDR JPEG decoded with gain map metadata preserved (codec integration gate).
- [ ] GPU-side gain map application produces HDR output within CPU oracle DeltaE tolerance.
- [ ] Adaptive rendering: HDR output on HDR target, tone-mapped SDR fallback on SDR target.
- [ ] Missing metadata produces stable `unsupported.color.gainmap_metadata_missing` diagnostic.
- [ ] WGSL gain map application validated via wgsl4k.

## Required Evidence

- GPUGainmapDecodePlan deterministic dump with metadata preserved.
- GPUGainmapApplyPlan deterministic dump with WGSL module.
- GPUGainmapDisplayAdaptationPlan deterministic dump with headroom parameter.
- CPU oracle comparison for gain map application output.
- Refusal fixtures: missing metadata, unvalidated WGSL.
- WGSL fragment module validation reports via wgsl4k.

## Fallback / Refusal Behavior

- Missing metadata → `unsupported.color.gainmap_metadata_missing`.
- Unvalidated WGSL → `unsupported.color.gainmap_apply_wgsl_unvalidated`.
- No codec gain map metadata extraction → `unsupported.color.gainmap_codec_unavailable`.
- No CPU-rendered texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.color.gain-map`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless accepted GPU evidence, codec integration, and CPU oracle parity.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Gainmap*'
```

## Status Notes

- `proposed`: Initial ticket. Awaiting M35 milestone acceptance and codec Ultra HDR JPEG gain map metadata support.

## Linear Labels

- `gpu-renderer`
- `milestone:M35`
- `area:color`

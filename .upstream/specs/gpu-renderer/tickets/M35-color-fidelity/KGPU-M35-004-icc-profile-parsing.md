---
id: KGPU-M35-004
title: "ICC profile parsing — v2/v4 matrix/TRC with transform and cache"
<<<<<<< HEAD
status: done
=======
status: proposed
>>>>>>> master
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

# KGPU-M35-004 - ICC profile parsing — v2/v4 matrix/TRC with transform and cache

## PM Note

Le parsing ICC profile passe de RefuseDiagnostic à un pipeline accepté pour les profiles matrix/TRC.

## Problem

ICC profile parsing is currently refused with a blanket diagnostic. Matrix/TRC
profiles (the most common embedded profile type) are spec'd as TargetNative in
29-color-management-pipeline.md but no parsing, transform, or cache route exists.
Without this, tagged images with embedded ICC profiles cannot be color-managed.

## Scope

- Implement `GPUICCProfileParsePlan` for v2 and v4 ICC profile header + tag parsing.
- Implement `GPUICCProfileTransformPlan` for matrix + TRC extracted transform application.
- Implement `GPUICCProfileCachePlan` with cache keyed by profile bytes hash.
- Route: embedded ICC profile → parse → transform → cache → apply.
- WGSL matrix/TRC transform validated via wgsl4k.
- CPU oracle parity for matrix/TRC profile application within DeltaE tolerance.
- Cache hit/miss telemetry.

## Non-Goals

- No ICC v5 profile parsing (v5 produces stable refusal).
- No LUT-based profiles (A2B0/B2A0 produce stable refusal).
- No named color profiles (ncl2 tag).
- No profile embedding or generation.

## Spec Sources

- `.upstream/specs/gpu-renderer/29-color-management-pipeline.md` (ICC Profile Parsing)
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`

## Graphite Algorithm References

- [`GFX-PAINTPARAMS-TO-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paintparams-to-key) - source [PaintParams.cpp:222](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParams.cpp:222); Lower paint color, image shader, primitive color, color filters, and final blend into key blocks while producing destination-usage metadata.
- [`GFX-RENDERSTEP-MODEL`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderstep-model) - source [Renderer.h:83](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Renderer.h:83); Decompose one high-level renderer into ordered RenderSteps that can be batched across draws.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUICCProfileParsePlan(
    val version: GPUICCVersion, // v2, v4
    val header: GPUICCHeader,
    val tagTable: List<GPUICCTag>,
    val matrixTRC: GPUICCMatrixTRC?,
)

enum class GPUICCVersion { v2, v4 }

data class GPUICCHeader(
    val profileSize: UInt,
    val preferredCMM: String,
    val profileVersion: String,
    val deviceClass: String,
    val colorSpace: String,
    val pcs: String,
)

data class GPUICCTag(
    val signature: String,
    val offset: UInt,
    val size: UInt,
)

data class GPUICCMatrixTRC(
    val matrix: FloatArray, // 3x3
    val trc: List<FloatArray>, // per-channel TRC curves
)

data class GPUICCProfileTransformPlan(
    val parsePlan: GPUICCProfileParsePlan,
    val matrixTRC: GPUICCMatrixTRC,
    val wgslModule: WGSLFragmentModule,
)

data class GPUICCProfileCachePlan(
    val cacheKey: String, // SHA-256 of profile bytes
    val parsedPlan: GPUICCProfileParsePlan?,
    val transformPlan: GPUICCProfileTransformPlan?,
)

sealed interface GPUICCProfileRoute {
    data class Accepted(
        val parse: GPUICCProfileParsePlan,
        val transform: GPUICCProfileTransformPlan,
        val cache: GPUICCProfileCachePlan,
    ) : GPUICCProfileRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : GPUICCProfileRoute
}
```

## Acceptance Criteria

- [ ] v2 ICC profile with matrix/TRC tags parsed and transformed with CPU oracle DeltaE tolerance.
- [ ] v4 ICC profile with matrix/TRC tags parsed and transformed correctly.
- [ ] Cache hit/miss telemetry observable in route diagnostics.
- [ ] ICC v5 profile produces stable `unsupported.color.icc_profile_version` refusal.
- [ ] LUT-based profile (A2B0/B2A0) produces stable `unsupported.color.icc_lut_profile` refusal.
- [ ] Unparseable/malformed profile produces stable `unsupported.color.icc_parse_failure` refusal.
- [ ] WGSL matrix/TRC transform validated via wgsl4k.

## Required Evidence

- GPUICCProfileParsePlan deterministic dump (v2, v4 with matrix/TRC).
- GPUICCProfileTransformPlan deterministic dump with matrix + TRC curves.
- GPUICCProfileCachePlan deterministic dump with cache key and hit/miss.
- CPU oracle comparison for v2 and v4 matrix/TRC profile application.
- Cache hit/miss telemetry evidence.
- Refusal fixtures: v5 profile, LUT profile, malformed profile.
- WGSL fragment module validation reports via wgsl4k.

## Fallback / Refusal Behavior

- ICC v5 → `unsupported.color.icc_profile_version`.
- LUT profile (A2B0/B2A0) → `unsupported.color.icc_lut_profile`.
- Parse failure → `unsupported.color.icc_parse_failure`.
- WGSL validation failure → `unsupported.color.icc_transform_wgsl_unvalidated`.
- No CPU-rendered texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.color.icc-parsing`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless accepted GPU evidence and CPU oracle parity.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ICCProfile*'
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

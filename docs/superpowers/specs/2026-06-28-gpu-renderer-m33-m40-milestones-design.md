# GPU Renderer M33-M40 Feature Expansion Milestones

Status: Draft
Date: 2026-06-28

## Purpose

Define eight new milestones (M33-M40) extending the GPU renderer ticket catalog
with feature contracts covering 34 previously missing capability areas. These
milestones continue the existing M0-M32 series in
`.upstream/specs/gpu-renderer/tickets/`.

Each milestone maps to one independent implementation lane from the expanded
`36-implementation-roadmap.md`. Lanes are ordered by natural dependency: lanes
that depend only on R0-R3 (command, route, material, WGSL ABI) come first;
lanes that need full R0-R6 completion or architecture specs (38-40) come last.

## Source Of Truth

- Ticket catalog: `.upstream/specs/gpu-renderer/tickets/`
- Ticket template: `.upstream/specs/gpu-renderer/tickets/templates/ticket-template.md`
- Milestone template: `.upstream/specs/gpu-renderer/tickets/templates/milestone-template.md`
- Status summary: `.upstream/specs/gpu-renderer/tickets/STATUS.md`
- Parent roadmap: `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- Target specs: `.upstream/specs/gpu-renderer/02`, `09`, `12`, `21`, `22`, `23`, `25`, `27`, `29`, `30`, `37`, `38`, `39`, `40`

## Prerequisites

M33-M40 require M0 and M1 as minimum evidence baseline:

- M0: R0-R6 boundary review (7 tickets done) — contract shapes, route taxonomy, diagnostics
- M1: First route product activation (4 tickets done) — solid FillRect with GPUNative evidence

Lanes may begin as soon as their required spec sections are accepted and the
baseline R0-R3 contracts (NormalizedDrawCommand, route decision, MaterialKey,
WGSL module + ABI) are proven through M0/M1 evidence.

## Milestones

### M33: Geometry Hardening

**Directory:** `tickets/M33-geometry-hardening/`
**Source specs:** `25-path-stroke-geometry-pipeline.md`, `30-coordinate-transform-bounds-policy.md`
**Claim impact:** `TargetNative`

| Ticket | Scope |
|---|---|
| `KGPU-M33-001` | GPU compute tessellation — `GPUComputeTessellationPlan`, compute shader WGSL, indirect dispatch, `GPUComputeTessellationArtifact` |
| `KGPU-M33-002` | Advanced stroke expansion — `GPUPathEffectChainPlan`, `GPUComplexDashPlan`, `GPUStrokeStyleCompositionPlan` |
| `KGPU-M33-003` | Perspective transform acceptance — `GPUPerspectiveTransformPlan`, homogeneous divide, conservative bounds proof for rect/rrect |

**Acceptance gates per feature:**
- 001: At least one path fill and one path stroke through compute tessellation with CPU oracle parity
- 002: At least one complex dash fixture (4-element with phase offsets) accepted or refused with stable reason
- 003: Rect + solid color with perspective matrix, projected bounds proof, and GPU evidence

### M34: Text Breadth

**Directory:** `tickets/M34-text-breadth/`
**Source spec:** `21-text-glyph-pipeline.md`
**Claim impact:** `TargetNative` (except color fonts/emoji: `DependencyGated`)

| Ticket | Scope |
|---|---|
| `KGPU-M34-001` | Subpixel LCD rendering — `GPUSubpixelLCDPlan`, per-component coverage mask, RGB/BGR stripe geometry |
| `KGPU-M34-002` | Color font pipeline — `GPUColorGlyphLayerPlan` (COLRv0/v1), `GPUCBDTCBLCGlyphPlan`, `GPUSVGOpenTypeGlyphPlan`, `GPUEmojiFallbackPlan` |
| `KGPU-M34-003` | Variable font support — `GPUVariableFontInstancePlan`, axis-tag/value pairs, resolved glyph artifacts |
| `KGPU-M34-004` | Complex shaping integration — `GPUShapingIntegrationContract`, `GPUBiDiRunPlan`, `GPUScriptComplexityClass` |
| `KGPU-M34-005` | Font fallback chain — `GPUFallbackGlyphPlan`, `GPUFallbackBatchPolicy`, exhausted-chain diagnostics |

**Dependencies:** M34-002 through M34-005 require pure Kotlin text stack output artifacts. M34-001 requires adapter pixel geometry query.

### M35: Color Fidelity

**Directory:** `tickets/M35-color-fidelity/`
**Source spec:** `29-color-management-pipeline.md`
**Claim impact:** `TargetNative`

| Ticket | Scope |
|---|---|
| `KGPU-M35-001` | HDR transfer functions — `GPUHDRTransferFunctionPlan` (PQ, HLG, scRGB), `GPUHDREOTFPlan`, `GPUHDRToneMapPlan` |
| `KGPU-M35-002` | Wide-gamut working spaces — `GPUWideGamutWorkingSpacePlan` (P3, AdobeRGB, Rec.2020), `GPUWideGamutConversionPlan` |
| `KGPU-M35-003` | Gain map pipeline — `GPUGainmapDecodePlan`, `GPUGainmapApplyPlan`, `GPUGainmapDisplayAdaptationPlan` |
| `KGPU-M35-004` | ICC profile parsing — `GPUICCProfileParsePlan`, `GPUICCProfileTransformPlan`, `GPUICCProfileCachePlan` |

**Dependencies:** M35-003 depends on codec support for Ultra HDR JPEG gain map metadata.

### M36: Image Pipeline Extension

**Directory:** `tickets/M36-image-pipeline/`
**Source spec:** `22-image-bitmap-codec-pipeline.md`
**Claim impact:** `DependencyGated` (HEIF/AVIF, hardware codecs), `TargetNative` (YUV, mipmap)

| Ticket | Scope |
|---|---|
| `KGPU-M36-001` | HEIF/AVIF gate promotion — `GPUHEIFCodecDescriptor`, `GPUAVIFCodecDescriptor`, `GPUISOBMFFParsePlan` |
| `KGPU-M36-002` | YUV multi-plan texture route — `GPUYUVMultiPlanDescriptor`, `GPUYUVPlaneUploadPlan`, `GPUYUVToRGBCoverterPlan` |
| `KGPU-M36-003` | Mipmap auto-generation — `GPUImageMipmapGenerationPlan`, blit and compute paths, `GPUImageMipmapCachePlan` |
| `KGPU-M36-004` | Hardware codec descriptor — `GPUHardwareCodecDescriptor`, `GPUHardwareCodecNondeterminismPolicy`, `GPUHardwareCodecFallbackPlan` |

**Dependencies:** M36-001 and M36-004 depend on accepted KanvasImageCodec registry entries.

### M37: Filter Breadth

**Directory:** `tickets/M37-filter-breadth/`
**Source spec:** `23-filter-effect-pipeline.md`
**Claim impact:** `TargetNative`

| Ticket | Scope |
|---|---|
| `KGPU-M37-001` | Multi-pass separable blur — `GPUSeparableBlurPlan`, horizontal + vertical passes, `GPUBlurQualityLevel` tiers |
| `KGPU-M37-002` | Morphology filter — `GPUMorphologyPlan`, dilate/erode with rectangular and circular kernels |
| `KGPU-M37-003` | Drop shadow filter — `GPUDropShadowPlan`, mask extraction, blur reuse, composite |
| `KGPU-M37-004` | Lighting filters — `GPULightingPlan`, directional + specular, bump map and normal map sources |
| `KGPU-M37-005` | Displacement map filter — `GPUDisplacementMapPlan`, channel-select offset sampling |
| `KGPU-M37-006` | Filter tile-based evaluation — `GPUFilterTilePlan`, tiled sub-renders with overlap |

**Dependencies:** M37-003 reuses M37-001 blur contracts. M37-006 depends on filter intermediate texture budgets.

### M38: Runtime Effects V2

**Directory:** `tickets/M38-runtime-effects-v2/`
**Source spec:** `27-registered-runtime-effects-registry.md`
**Claim impact:** `TargetNative`

| Ticket | Scope |
|---|---|
| `KGPU-M38-001` | Live parameter editing V2 — `GPURuntimeEffectLiveParameterSchema`, per-parameter binding, dirty-tracking, preset round-trip |
| `KGPU-M38-002` | Extended effect kinds — `Blender`, `ClipShader`, `Compute` effect kinds with kind-specific WGSL and route placement |
| `KGPU-M38-003` | Dynamic shader graph assembly — `GPURuntimeEffectShaderGraph`, topological sort, combined WGSL module, cycle detection |

**Dependencies:** M38-003 depends on `wgsl4k` supporting multi-fragment module assembly.

### M39: Rendering Architecture

**Directory:** `tickets/M39-rendering-architecture/`
**Source specs:** `12-blend-color-target-state.md`, `37-draw-packet-command-stream.md`, `02-gpu-recording-task-graph.md`
**Claim impact:** `TargetNative`

| Ticket | Scope |
|---|---|
| `KGPU-M39-001` | MSAA resolve — `GPUMultisamplePlan`, multisample render target, resolve to single-sample, alpha-to-coverage |
| `KGPU-M39-002` | Instanced draw batching — `GPUInstancedPacketGroup`, per-instance uniform/vertex strategies, grouping rules |
| `KGPU-M39-003` | Subpass merging — `GPUSubpassMergePlan`, input attachment merging, producer-consumer pass fusion |
| `KGPU-M39-004` | Deferred display list — `GPUDeferredDisplayList`, compatibility key, replay plan with CTM/clip substitution |

### M40: New Architecture Capabilities

**Directory:** `tickets/M40-architecture-capabilities/`
**Source specs:** `38-tile-deferred-rendering.md`, `39-multithreaded-recording.md`, `40-hi-z-occlusion-culling.md`
**Claim impact:** `TargetNative`
**Prerequisite:** R0-R6 completion (full recording → submission chain proven)

| Ticket | Scope |
|---|---|
| `KGPU-M40-001` | Tile-deferred rendering — `GPUTileGridPlan`, tile binning, `GPUTilePass`, `GPUTileCompositePass`, tile memory budget |
| `KGPU-M40-002` | Multi-threaded recording — `GPURecordingFragment`, `GPURecordingFragmentMerger`, thread-bound arenas, determinism contract |
| `KGPU-M40-003` | Hi-Z occlusion culling — `GPUHiZPyramid`, depth pyramid build, per-draw occlusion test, Z-prepass integration |

**Dependencies:** M40-001 is the most complex milestone. It requires the full R0-R6 command→submission chain and affects draw-layer planning, resource allocation, and pass construction. M40-002 depends on M40-001 for tile-parallel recording strategy. M40-003 depends on M40-001 for per-tile pyramid interaction.

## Ticket Conventions

All tickets use the existing template from `tickets/templates/ticket-template.md`:

1. PM Note (French)
2. Problem
3. Scope
4. Non-Goals
5. Spec Sources
6. Graphite Algorithm References
7. Design Sketch (Kotlin-like)
8. Acceptance Criteria
9. Required Evidence
10. Fallback / Refusal Behavior
11. Dashboard Impact
12. Validation
13. Status Notes
14. Linear Labels

Ticket IDs: `KGPU-M<milestone>-<sequence>` (e.g., `KGPU-M33-001`).

## Route Kind Policy

All M33-M40 features target `GPUNative` routes. `CPUPreparedGPU` is accepted
only where the spec explicitly authorizes a typed artifact (e.g.,
`GPUComputeTessellationArtifact` when compute shader capabilities are unavailable).
`RefuseDiagnostic` with stable reason codes is the valid outcome when a route
cannot be accepted.

No feature may CPU-render a complete draw, layer, filter, or scene into a texture
for GPU composition.

## Readiness Model

M33-M40 milestones extend the existing status model from `tickets/STATUS.md`.
Each milestone starts with all tickets `proposed`. Promotion to `ready` requires
the milestone README to be accepted and source specs to be `Accepted` (not just
`Draft`).

A ticket moves to `done` only when:
- Implementation evidence exists (contract tests, WGSL validation, CPU/GPU parity)
- Refusal fixtures cover unsupported variants
- Diagnostics are stable and dumpable
- PM dashboard impact is recorded
- Independent review accepts the evidence

## Non-Goals

- Do not change existing M0-M32 ticket status or scope.
- Do not activate product routes without M1 authorization.
- Do not add new spec files beyond those already in `.upstream/specs/gpu-renderer/`.
- Do not claim support for features still `DependencyGated` in their source spec.
- Do not skip the R0-R6 vertical slice for lanes that depend on full infrastructure.

## Open Decisions

- Whether M40 should be split into M40 (tile-deferred alone) and M41 (multi-threaded + hi-z) due to complexity.
- Exact ticket count per milestone (current counts are estimates; may split or merge during ticket authoring).
- Whether M34 (Text Breadth) tickets should be deferred until pure Kotlin text stack reaches accepted artifact maturity.

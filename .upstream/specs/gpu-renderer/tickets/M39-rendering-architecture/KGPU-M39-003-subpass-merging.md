---
id: KGPU-M39-003
title: "Subpass merging"
status: proposed
milestone: M39
priority: P1
owner_area: passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M39-003 - Subpass merging

## PM Note

Le subpass merging fusionne des render passes producteur/consommateur avec
input attachments pour éviter les copies intermédiaires et réduire la
pression mémoire sur les tuiles GPU.

## Problem

When a render pass consumes the output of a preceding pass (e.g., horizontal
blur feeding vertical blur), the current architecture issues two separate
render passes with an intermediate texture copy or barrier. This wastes
bandwidth and tile memory. Subpass merging must detect producer-consumer
pairs and fuse them into a single render pass with input attachments, where
the adapter supports this capability.

## Scope

- `GPUSubpassMergePlan` — describes a merged producer-consumer pass where
  the producer's color attachment becomes the consumer's input attachment.
- Merge conditions:
  - Producer and consumer are within the same render pass scope.
  - No copy, upload, barrier, or readback between the two passes.
  - Adapter supports `inputAttachment` feature.
  - Compatible color attachment formats.
  - Same sample count on both passes.
- Merge analysis that inspects the pass sequence and identifies eligible
  producer-consumer pairs.
- The merged subpass must not regress pixel output vs. the unmerged
  two-pass execution.

## Non-Goals

- Do not merge passes across different render targets or framebuffer
  configurations.
- Do not attempt subpass merging when the adapter does not support
  `inputAttachment`.
- Do not merge passes with intervening GPU operations (copies, barriers,
  dispatches).

## Spec Sources

- `.upstream/specs/gpu-renderer/02-gpu-recording-task-graph.md`
- `.upstream/specs/gpu-renderer/README.md`

## Graphite Algorithm References

- `GFX-RENDERPASS-SUBPASS` from `../GRAPHITE-ALGORITHM-REFERENCES.md` —
  study Graphite's subpass planning and input attachment usage for algorithm
  reference; do not port Graphite or Ganesh.
- Boundary: references are for algorithm study only; do not port Graphite or
  Ganesh and do not treat them as Kanvas acceptance criteria.

## Design Sketch

```kotlin
data class GPUSubpassMergePlan(
    val producerPass: GPURenderPassHandle,
    val consumerPass: GPURenderPassHandle,
    val inputAttachmentIndex: Int,
    val colorAttachmentIndex: Int,
)

data class GPUSubpassMergeAnalysis(
    val eligiblePairs: List<GPUSubpassMergePlan>,
    val refusedPairs: List<GPUSubpassMergeRefusal>,
)
```

## Acceptance Criteria

- [ ] Blur horizontal pass feeding blur vertical pass is merged into a
      single render pass with input attachments, verified via WebGPU trace.
- [ ] Non-mergeable pairs (incompatible format, intervening barrier) produce
      stable refusal diagnostics.
- [ ] Merged subpass pixel output matches unmerged two-pass pixel output
      (zero-diff or within PSNR threshold).
- [ ] Adapter without `inputAttachment` support produces refusal diagnostic
      and falls back to unmerged passes.

## Required Evidence

- WebGPU capture showing merged subpass with input attachment.
- Pixel diff report: merged vs. unmerged blur (horizontal → vertical).
- Refusal diagnostic dump for incompatible format and intervening barrier
  cases.
- Adapter capability report confirming `inputAttachment` support.

## Fallback / Refusal Behavior

- Incompatible pair → `unsupported.recording.subpass_merge_incompatible`
  diagnostic.
- Unmerged two-pass execution remains the fallback when merge conditions are
  not met.

## Dashboard Impact

- Expected row: `gpu-renderer.rendering.subpass-merge`
- Expected classification: `TargetNative`
- Claim promotion allowed: only after Required Evidence is linked and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*SubpassMerge*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M39`
- `area:passes`

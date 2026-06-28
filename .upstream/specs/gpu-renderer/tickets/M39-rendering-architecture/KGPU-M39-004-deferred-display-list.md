---
id: KGPU-M39-004
title: "Deferred display list"
status: proposed
milestone: M39
priority: P1
owner_area: recording
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M39-004 - Deferred display list

## PM Note

La deferred display list permet d'enregistrer une séquence une fois et de la
rejouer sur plusieurs frames avec des transformations et clips différents,
réduisant le coût d'enregistrement par frame.

## Problem

Re-recording identical command sequences every frame wastes CPU time when
only the composed CTM, intersection clip, or target surface changes. A
deferred display list must capture an immutable recorded command sequence
with its analysis and layer plans, validate replay compatibility, and apply
lightweight substitutions at replay time.

## Scope

- `GPUDeferredDisplayList` — immutable container holding recorded commands,
  precomputed analysis, and layer plans.
- `GPUDeferredDisplayListCompatibilityKey` — computed from recording ID,
  command hash, and replay-compatible fields; used to detect incompatible
  replays.
- `GPUDeferredDisplayListReplayPlan` — applies composed CTM substitution,
  intersection clip substitution, target surface substitution, and a
  lightweight analysis pass at replay time.
- `GPUDeferredDisplayListCachePlan` — manages cache eviction, lifetime, and
  invalidation for deferred display lists.

## Non-Goals

- No public API surface; deferred display list is an internal optimization.
- No cross-device or cross-adapter replay.
- No performance claim without profiling evidence.

## Spec Sources

- `.upstream/specs/gpu-renderer/02-gpu-recording-task-graph.md`
- `.upstream/specs/gpu-renderer/README.md`

## Graphite Algorithm References

- `GFX-DRAWCONTEXT-FLUSH` from `../GRAPHITE-ALGORITHM-REFERENCES.md` — study
  pass extraction and task insertion for deferred replay for algorithm
  reference; do not port Graphite or Ganesh.
- Boundary: references are for algorithm study only; do not port Graphite or
  Ganesh and do not treat them as Kanvas acceptance criteria.

## Design Sketch

```kotlin
data class GPUDeferredDisplayList(
    val recordedCommands: List<GPURecordedCommand>,
    val analysis: GPUDrawAnalysis,
    val layerPlans: List<GPULayerPlan>,
)

data class GPUDeferredDisplayListCompatibilityKey(
    val recordingId: String,
    val commandHash: Long,
    val replayCompatibleFields: Set<String>,
)

data class GPUDeferredDisplayListReplayPlan(
    val composedCtm: GPUMatrix,
    val intersectionClip: GPUClip,
    val targetSurface: GPUTargetSurface,
)

data class GPUDeferredDisplayListCachePlan(
    val maxEntries: Int,
    val evictionPolicy: GPUCacheEvictionPolicy,
)
```

## Acceptance Criteria

- [ ] A recorded sequence replayed with a different composed CTM produces
      correct transformed output.
- [ ] A recorded sequence replayed with a different intersection clip
      produces correct clipped output.
- [ ] Incompatible replay (format change, capability change, device change)
      produces refusal diagnostic and falls back to re-recording.
- [ ] Cache hit/miss telemetry is reported.

## Required Evidence

- Pixel diff report: deferred replay with rotated CTM vs. fresh recording
  (must match).
- Pixel diff report: deferred replay with clipped region vs. fresh recording
  (must match).
- Refusal diagnostic dump for incompatible replay scenarios.
- Cache telemetry artifact from a multi-frame test sequence.

## Fallback / Refusal Behavior

- Incompatible replay → `unsupported.recording.deferred_incompatible_replay`
  diagnostic.
- Fresh re-recording remains the fallback path when replay compatibility
  checks fail.

## Dashboard Impact

- Expected row: `gpu-renderer.rendering.deferred-dl`
- Expected classification: `TargetNative`
- Claim promotion allowed: only after Required Evidence is linked and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*DeferredDL*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M39`
- `area:recording`

---
id: KGPU-M40-002
title: "Multi-threaded recording"
status: done
milestone: M40
priority: P1
owner_area: recording
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001, KGPU-M40-001]
legacy_gate: null
---

# KGPU-M40-002 - Multi-threaded recording

## PM Note

L'enregistrement multi-threadé permet le parallélisme de recording avec fragments mergés déterministiquement pour accélérer la construction de passes.

## Problem

Single-threaded recording serializes command construction for large draw batches, becoming a CPU bottleneck that cannot saturate multi-core systems. Splitting recording across threads introduces ordering and dependency hazards that must be resolved deterministically without degrading pipeline cache quality.

## Scope

- `GPURecordingFragment` — immutable partial recording produced by one thread containing a contiguous sub-range of commands.
- `GPURecordingFragmentMerger` — validates ordering tokens, resolves cross-fragment dependencies, and produces a single merged `GPURecording`.
- `GPUThreadBoundArena` — thread-local temporary allocations released when the fragment is produced.
- `GPUMergeOrderingToken` — per-command ordering value that survives fragment boundaries and enables deterministic merge.
- `GPUConcurrencyTelemetry` — fragment count, merge duration, contention events.
- Thread model: `GPURecorder` not shared across threads; `GPUResourceProvider` shared and thread-safe; `GPUExecutionContext` thread-safe read-only; `GPUMaterialDictionary` thread-safe read-only; diagnostics and telemetry use thread-safe atomics.
- Determinism contract: merged output must be identical to single-threaded recording (same `MaterialKey`, `SortKey`, payload slot, merge order).
- Three partitioning strategies: Disjoint Command Ranges, Layer-Scope Partitioning, Tile-Parallel (from KGPU-M40-001).

## Non-Goals

- Do not promote support without accepted evidence.
- Do not activate product routing unless this ticket explicitly owns that decision and validation.
- Do not add hidden CPU-rendered texture compatibility.

## Spec Sources

- `.upstream/specs/gpu-renderer/39-multithreaded-recording.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- [`GFX-RECORDER-SNAP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-recorder-snap) - source [Recorder.cpp:198](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Recorder.cpp:198); Flush tracked devices, finalize draw/upload buffers, put root uploads before dependent render tasks, run prepareResources, then reset transient dictionaries and proxy read counts.
- [`GFX-TASKLIST`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tasklist) - source [TaskList.cpp:19](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/TaskList.cpp:19); Walk tasks in order, honor success/discard/fail statuses, scope scratch resources during preparation, and replay only prepared tasks.
- [`GFX-DRAWLIST-RECORD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-record) - source [DrawList.cpp:21](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:21); One high-level draw expands to one sort key per RenderStep; each key combines render step ID, paint ID, uniform data, and texture binding data.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPURecordingFragment(
    val threadId: Int,
    val commands: List<GPUDrawCommand>,
    val orderingTokens: List<GPUMergeOrderingToken>,
    val fragmentIndex: Int,
)

data class GPURecordingFragmentMerger(
    val fragments: List<GPURecordingFragment>,
    val validatesOrdering: Boolean = true,
    val resolvesCrossFragmentDeps: Boolean = true,
)

data class GPUThreadBoundArena(
    val threadId: Int,
    val allocationBytes: Long,
    val released: Boolean,
)

data class GPUMergeOrderingToken(
    val commandIndex: Long,
    val fragmentIndex: Int,
)

data class GPUConcurrencyTelemetry(
    val fragmentCount: Int,
    val mergeDurationMs: Double,
    val contentionEvents: Int,
)
```

## Acceptance Criteria

- [ ] Two fragments merged correctly with identical output to single-threaded recording.
- [ ] Cross-thread dependency correctly blocked (fragment split inside atomic group refused).
- [ ] Merge deterministic across runs: same output ordering regardless of thread scheduling.
- [ ] Pipeline cache not degraded by multi-threaded recording (cache hit rate unchanged).
- [ ] Arena memory fully released after fragment production.
- [ ] Refusal emitted for split inside atomic group, layer, or dst-read scope with stable diagnostic.

## Required Evidence

- Two-fragment merge output hash parity against single-threaded reference.
- Determinism verification across N runs (N >= 5) with resource dumps.
- Pipeline cache hit-rate comparison (single-threaded vs multi-threaded).
- Arena memory release report showing zero leak.
- Refusal diagnostic for split inside atomic group / layer / dst-read scope.
- Telemetry dump: `reports/gpu-renderer/telemetry/multithreaded-recording/`.

## Fallback / Refusal Behavior

- Unsafe split: `unsupported.recording.fragment_split_unsafe` diagnostic, merge refused.
- Merge cycle detected: `unsupported.recording.fragment_merge_cycle` diagnostic, recording aborted.
- Silent fallback to CPU-rendered complete draw/layer/filter/text texture compatibility is not allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.architecture.multithreaded-recording`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*MultiThreaded*'
```

## Status Notes

- `proposed`: Initial ticket.
- `proposed → ready (2026-06-28)`: M40-001 tile-deferred implemented, unblocking.
- `ready → review` (2026-06-28): implemented. Pending independent review.
- `review → done` (2026-06-29): promoted — independent review accepted.

## Linear Labels

- `gpu-renderer`
- `milestone:M40`
- `area:recording`

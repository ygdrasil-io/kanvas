---
id: KGPU-M29-008
title: "KanvasSurface.flush() — GPU submit and fence"
status: done
milestone: M29
priority: P0
owner_area: kanvas-api
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M29-002, KGPU-M29-003, KGPU-M29-004, KGPU-M29-005, KGPU-M29-006, KGPU-M29-007]
legacy_gate: null
---

# KGPU-M29-008 - KanvasSurface.flush() — GPU submit and fence

## PM Note

`KanvasSurface.flush()` finalise l'enregistrement des commandes GPU et soumet le
travail au GPU. C'est l'equivalent natif de `SkSurface.flush()` et le point de
synchronisation entre l'API de dessin et l'execution GPU. Ce ticket donne au PM
la capacite de voir le rendu effectif.

## Problem

`KanvasCanvas` records draw commands but has no mechanism to submit them to the
GPU. Without `flush()`, recorded commands never execute, and the surface never
produces pixels. `KanvasSurface.flush()` bridges command recording to GPU submission
with optional fence synchronization.

## Scope

- Implement `KanvasSurface.flush()` to submit all recorded draw commands
- Implement GPU command buffer construction from recorded canvas commands
- Implement optional `flushAndSubmit(signalFence)` for fence synchronization
- Ensure flush is idempotent (second flush is a no-op if no new commands)
- Emit diagnostics on submission failure

## Non-Goals

- No surface read-back or pixel download (future milestone)
- No multi-surface synchronization or inter-surface dependencies
- No presentation or swapchain integration (future milestone)
- No partial flush or incremental submission

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/02-gpu-recording-task-graph.md`
- `.upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md`
- `.upstream/specs/gpu-renderer/tickets/M24-gpu-native-rendering/README.md`
- `.upstream/specs/gpu-renderer/tickets/M28-backend-stencil-vertices-targets/README.md`

## Design Sketch

```kotlin
class KanvasSurface {
    fun flush(): Boolean
    fun flushAndSubmit(signalFence: Fence? = null): Boolean
    fun hasPendingCommands(): Boolean
}
```

## Acceptance Criteria

- [ ] `flush()` submits all pending canvas commands to the GPU queue
- [ ] `flushAndSubmit(signalFence)` signals the fence on GPU completion
- [ ] Idempotent flush returns `true` without re-submission if no new commands
- [ ] GPU submission failure emits `gpu-submit-failed` diagnostic
- [ ] After flush, `hasPendingCommands()` returns `false`

## Required Evidence

- Command-buffer construction transcript
- GPU submission confirmation (submit call + queue timestamp)
- Fence signal transcript
- Idempotent flush transcript (second flush with no new commands)
- Diagnostic output for submission failure

## Fallback / Refusal Behavior

GPU submission failure emits `gpu-submit-failed` diagnostic and returns `false`.
No CPU-side rasterization fallback. No silent discard of recorded commands.

## Dashboard Impact

- Expected row: `gpu-renderer.m29.kanvas-surface-flush`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:test
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
```

## Status Notes

- `proposed`: Initial ticket.
- `done`: KanvasSurface.flush() returns KanvasFrame containing GPURecording. Idempotent flush supported via GPURecorder.close().

## Linear Labels

- `gpu-renderer`
- `milestone:M29`
- `area:kanvas`

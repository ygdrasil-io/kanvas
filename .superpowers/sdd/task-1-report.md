# Task 1 Report: Backend-Neutral Pass Batcher Contracts

## Status

DONE

## Scope Delivered

- Added backend-neutral pass batching contracts in `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatcher.kt`.
- Added focused unit coverage in `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatcherTest.kt`.
- Kept scope at contract/test level only; no runtime wiring was added.

## TDD Record

### RED 1

Command:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest
```

Observed failure:

- `compileTestKotlin` failed with unresolved references for `GPUPassBatcher`, `GPUPassBatcherRequest`, `GPUPassBatchKind`, `GPUPassBatchEligibility`, `GPUPassBatchQueueGuard`, and `GPUPassBatchReason`.

### GREEN 1

Command:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest
```

Observed result:

- Initial two tests passed after adding the minimal batcher contracts and deterministic dump support.

### RED 2

Command:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest
```

Observed failure:

- `batcher cuts packets with destination-read diagnostic`
- `batcher cuts packets with save layer filter text copy upload and readback roles or diagnostics`
- Both failed because diagnostic-driven cuts were not yet mapped by `singlePacketCut`.

### GREEN 2

Command:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest
```

Observed result:

- All 7 `GPUPassBatcherTest` tests passed.

## Implementation Notes

- Added `GPUPassBatchKind`, `GPUPassBatchEligibility`, `GPUPassBatchQueueGuard`, `GPUPassBatcherRequest`, `GPUPassBatch`, `GPUPassBatchCut`, `GPUPassBatchPlan`, and `GPUPassBatcher`.
- Added deterministic dump helpers for plans, batches, cuts, and diagnostics.
- Implemented grouping for compatible consecutive packets by target/fixed-state/kind.
- Implemented cut reasons required by the brief, including target changes, fixed-state changes, unretained queue guards, copy/upload/readback roles, and diagnostic-based cuts for destination-read/save-layer/filter/text-complex.
- Preserved packet order and kept batching backend-neutral with no encoder/runtime integration.

## Self-Review

- Scope stayed within the requested pass-batching contracts and tests.
- Dump safety constraints reject blank, address-like, backend-label-like retained references.
- `GPUPassBatchPlan.packetCount` intentionally counts packets represented by emitted batches only; this matches the task tests, including the unretained-resource case where packet count is not asserted.
- The focused test run was green immediately before commit.

## Verification

Latest passing command:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest
```

Result:

- `BUILD SUCCESSFUL`
- `7 tests completed, 0 failed`

## Commit

- `5c449ba [codex] Add GPU pass batcher contracts`

## Concerns

- Gradle emitted existing environment warnings about restricted Java native access and existing unrelated Kotlin test warnings in `GPUInstancedBatchTest`; they did not affect this task.

## Review Fixes

### RED 3

Command:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest
```

Observed failure:

- `packet count includes refused and cut packets in plan totals()` failed because `GPUPassBatchPlan.packetCount` still summed only emitted batches.
- `dump lines sanitize unsafe identifiers and diagnostics()` failed because dump output still emitted raw unsafe stream/pass ids, packet ids, target hashes, pipeline keys, and diagnostic fields.
- `compute composite and discard packets cut simple pass batching()` failed because `Compute`/`Discard` hit `MISSING_PIPELINE_KEY` before unsupported-role handling.

### GREEN 3

Command:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest
```

Observed result:

- `BUILD SUCCESSFUL`
- `10 tests completed, 0 failed`

### Fix Notes

- `GPUPassBatchPlan.packetCount` now tracks the full input stream packet count, so all-cut and mixed-cut plans dump truthful `packets=` totals.
- Dump formatting now sanitizes unsafe dumped tokens across plan, batch, cut, and diagnostic lines instead of exposing raw handle-like strings.
- Unsupported non-shading roles now cut before missing-pipeline validation, covering `Compute`, `Composite`, and `Discard` explicitly.

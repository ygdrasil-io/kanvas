# Task 2 Report: Batch Plan To Pass Command Stream

## Scope

- Added `GPUPassCommandStream.fromBatchPlan(...)` in `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatchCommandStream.kt`
- Added focused coverage in `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatchCommandStreamTest.kt`
- Kept native runtime wiring out of scope, per brief

## TDD Record

### RED

Added the test file from the brief, then ran:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchCommandStreamTest
```

Observed the expected failure on unresolved reference `fromBatchPlan`.

Note: the compiler also reported a secondary unresolved `it` inside the new test because `commandStream` could not be typed while `fromBatchPlan` was still missing. The RED condition still centered on the missing API required by the brief.

### GREEN

Implemented the batch-plan lowering extension and reran:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchCommandStreamTest
```

Result: both focused tests passed.

## Implementation Notes

- Lowering iterates accepted `batchPlan.batches` in order and emits:
  - `beginRenderPass`
  - per packet: `setRenderPipeline`, `setBindGroup`, optional `setScissor`, `draw`
  - `endRenderPass`
- Added stream/pass consistency guards between `GPUDrawPacketStream` and `GPUPassBatchPlan`
- Reused the existing operand-bridge materialization behavior from `fromDrawPacketStream`
- Appended batch-plan dump evidence as synthetic `GPUPassDiagnostic` entries with codes `batch-plan-line-N`

## Verification Evidence

Fresh verification command:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchCommandStreamTest
```

Fresh result:

- `GPUPassBatchCommandStreamTest > single accepted batch lowers to one render pass() PASSED`
- `GPUPassBatchCommandStreamTest > cut batches lower to separate render pass scopes in order() PASSED`
- Gradle exit code: `0`

## Self-Review

- Scope stayed limited to the new lowering extension and focused tests
- No native runtime behavior was wired
- No unrelated files were changed
- The diagnostics list intentionally includes batch-plan evidence lines so `dumpLines()` exposes the plan provenance required by the brief

## Files Changed

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatchCommandStream.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatchCommandStreamTest.kt`
- `.superpowers/sdd/task-2-report.md`

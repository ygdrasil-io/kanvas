# Task 2 Report — Pass Commands and Command Stream Lowering

## Scope
- Added pass-command contracts for generic intermediate preparation, explicit MSAA resolve, and intermediate refusal evidence.
- Added intermediate-plan to pass-command-stream lowering only.
- Kept implementation bounded to command evidence; no planner, resource, or runtime behavior from later tasks.

## Files Modified
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateCommandStream.kt` (new)
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateCommandStreamTest.kt` (new)
- `.superpowers/sdd/task-2-report.md` (new)

## Steps Executed
1. Read `superpowers:test-driven-development` skill instructions.
2. Read `.superpowers/sdd/task-2-brief.md`.
3. Wrote `GPUIntermediateCommandStreamTest.kt` from the brief before touching production code.
4. Ran the required RED command:
   - `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateCommandStreamTest"`
   - Observed expected compilation failures for missing `GPUPassCommandStream.fromIntermediatePlan`, `GPUPassCommand.PrepareIntermediateTexture`, and `GPUPassCommand.ResolveMSAA`.
5. Implemented the minimal production changes:
   - Added `GPUPassCommand.PrepareIntermediateTexture`
   - Added `GPUPassCommand.ResolveMSAA`
   - Added `GPUPassCommand.RefuseIntermediate`
   - Added deterministic dump and operand-kind coverage for the new command labels
   - Added `GPUPassCommandStream.fromIntermediatePlan(...)`
   - Added private `GPUIntermediateTextureDescriptor.prepareCommand()`
6. Ran the required GREEN command:
   - `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateCommandStreamTest" --tests "org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchCommandStreamTest"`
   - Verified all targeted tests passed.
7. Staged only Task 2 implementation files for commit:
   - `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt`
   - `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateCommandStream.kt`
   - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateCommandStreamTest.kt`
8. Committed with the required message:
   - `Lower GPU intermediate plans to pass commands`

## Validation
- Tests run:
  - `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateCommandStreamTest" --tests "org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchCommandStreamTest"`
- Result: `BUILD SUCCESSFUL`
- Passing tests:
  - `GPUIntermediateCommandStreamTest` (2 tests)
  - `GPUPassBatchCommandStreamTest` (6 tests)

## Self-review
- Scope stayed inside the Task 2 files requested by the brief plus the required report file.
- Lowering intentionally ignores `BindIntermediate` because Task 2 is limited to pass-command evidence and the brief does not require bind lowering behavior yet.
- Durable command evidence does not introduce backend handles, addresses, texture contents, uniform values, CPU readback fallback, or active attachment sampling acceptance.

## Review Fixes — 2026-07-08
- Fixed `GPUPassCommandStream.fromIntermediatePlan(...)` to use lazy render-pass opening instead of unconditional `BeginRenderPass`.
- Lowered `CreateIntermediate` / `ReuseIntermediate`, `CopyDestination`, `ResolveMSAA`, and `Refuse` outside active render-pass scope.
- Fixed pass splitting so destination copies close any active render pass before `copyTexture` and reopen only when later render work exists.
- Fixed refusal-only plans to emit refusal evidence without synthetic `BeginRenderPass` / `EndRenderPass`.
- Propagated `plan.diagnostics` into stable `GPUPassDiagnostic` entries on the produced `GPUPassCommandStream`.
- Expanded `GPUIntermediateCommandStreamTest` coverage for:
  - lazy copy-before-sample ordering,
  - split-pass reopen behavior,
  - refusal-only lowering,
  - diagnostics propagation,
  - MSAA resolve staying outside render-pass work.

## Review Fix Validation
- Tests run:
  - `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateCommandStreamTest" --tests "org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchCommandStreamTest"`
- Result: `BUILD SUCCESSFUL`
- Passing tests:
  - `GPUIntermediateCommandStreamTest` (5 tests)
  - `GPUPassBatchCommandStreamTest` (6 tests)

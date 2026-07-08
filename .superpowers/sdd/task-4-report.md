# Task 4 Report: Runtime Integration For Fullscreen Simple Passes

## Scope
- Recorded backend-neutral `GPUPassBatchPlan` decisions from the existing fullscreen simple-pass runtime path.
- Derived queue-guard retained labels from `WgpuPayloadSlabMaterialization.leases` using stable lease labels only.
- Kept fullscreen draw encoding and pixel behavior unchanged.

## Files Changed
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`

## Implementation Summary
- Added pass-batch contract imports and a local `sanitizeBatchLabel()` helper for dump-safe synthetic packet evidence.
- Added `recordFullscreenPassBatchPlan(...)` inside `WgpuRenderRecorder`.
- Built synthetic `GPUDrawPacket` / `GPUDrawPacketStream` evidence for fullscreen uniform draws and planned it through `GPUPassBatcher`.
- Recorded the resulting plan with existing runtime telemetry via `telemetryRecorder.recordPassBatchPlan(plan)`.
- Wired plan recording into `recordFullscreenUniformPass(...)` immediately after fullscreen slab materialization.
- Derived queue-guard retained refs from slab leases as `lease:<leaseId>`; no backend handles or raw WGPU labels were exposed.
- Added the required backend-available batch-plan smoke and the submission-comparison smoke.

## TDD Evidence
### RED
Command:
`rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.backend\ runtime\ records\ pass\ batch\ plan\ for\ fullscreen\ rect\ draws\ when\ backend\ is\ available`

Result:
- Backend was available.
- Test failed at `assertTrue(dump.contains("passBatchPlans=1"), dump)`.
- Failure dump showed:
  - `passBatchPlans=0`
  - `passBatchesAccepted=0`
  - payload slab planning evidence was present, proving the failure was specifically missing runtime batch-plan recording.

### GREEN
After implementing runtime recording, reran the same command:
- `GPUBackendRuntimeNativeSmokeTest > backend runtime records pass batch plan for fullscreen rect draws when backend is available() PASSED`

## Verification
### Required targeted backend-available smoke for batch plan dumps
Command:
`rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.backend\ runtime\ records\ pass\ batch\ plan\ for\ fullscreen\ rect\ draws\ when\ backend\ is\ available --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest`

Result:
- Batch-plan smoke passed.
- All targeted `GPUPassBatcherTest` tests passed.
- Gradle exited `BUILD SUCCESSFUL`.

### Required submission comparison smoke
Command:
`rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.batched\ rectangle\ scene\ uses\ fewer\ submissions\ than\ explicit\ unbatched\ baseline\ when\ backend\ is\ available`

Result:
- Smoke passed.
- Test assertions validated `baselineSubmissions == 4` and `batchedSubmissions == 1`.
- Gradle exited `BUILD SUCCESSFUL`.

## Diff Stat
- `GPUBackendRuntimeNative.kt`: `+98`
- `GPUBackendRuntimeNativeSmokeTest.kt`: `+89`
- Total: `187 insertions`

## Deviation Check
- No deviation from the brief's synthetic packet builder was required for the current Task 1-3 codebase.

## Fix Review Findings
### RED
Command:
`rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.backend runtime does not record pass batch plan for unmarked raw uniform fullscreen passes when backend is available' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.backend runtime records pass batch plan for explicitly marked simple gradient raw uniform fullscreen passes when backend is available' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.batched rectangle scene uses fewer submissions than explicit unbatched baseline when backend is available'`

Result:
- Initial RED failed at compile time because `drawFullscreenRawUniformPass(..., passBatchKind = ...)` and `GPUBackendSimplePassBatchKind` did not exist yet.
- After adding only the minimal opt-in API surface, rerunning the same command produced behavioral RED:
  - unmarked raw-uniform smoke failed because `passBatchPlans` incremented to `1` and dumped `passes.batch-plan stream=fullscreen-uniform-pass`
  - explicit simple-gradient raw-uniform smoke failed because the single-packet plan recorded `passBatchesAccepted=0` while still dumping `kind=simple-gradient`
  - explicit-unbatched baseline smoke failed because four single-draw encodes recorded `passBatchesAccepted=0`, proving accepted batches only occur for `packetCount >= 2`

### GREEN
Command:
`rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.backend runtime records pass batch plan for fullscreen rect draws when backend is available' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.backend runtime does not record pass batch plan for unmarked raw uniform fullscreen passes when backend is available' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.backend runtime records pass batch plan for explicitly marked simple gradient raw uniform fullscreen passes when backend is available' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.batched rectangle scene uses fewer submissions than explicit unbatched baseline when backend is available'`

Result:
- All four targeted backend-available smoke tests passed.
- The rect batch-plan smoke now asserts dump safety for `@`, `0x`, `WGPU`, and `wgpu`.
- Unmarked raw-uniform passes no longer increment `passBatchPlans` or emit accepted simple batch-plan dumps.
- Explicitly marked simple-gradient raw-uniform passes record `kind=simple-gradient`.
- The explicit-unbatched comparison now proves `baselineSubmissions == 4`, `batchedSubmissions == 1`, `baselinePassBatchPlans == 4`, `baselinePassBatchesAccepted == 0`, `batchedPassBatchPlans == 1`, `batchedPassBatchesAccepted == 1`, and `batchedPassBatchPackets == 4`.

Command:
`rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest`

Result:
- All `GPUPassBatcherTest` tests passed.
- Gradle exited `BUILD SUCCESSFUL`.

## Fix Review Findings 2
### RED
Command:
`rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.backend runtime does not record pass batch plan for unmarked generic fullscreen pass draws when backend is available'`

Result:
- Backend was available.
- The new generic/unmarked `drawFullscreenPass(...)` smoke failed as expected.
- Failure dump showed the current bug clearly:
  - `passBatchPlans=1`
  - `passBatchesAccepted=1`
  - `passBatchPackets=2`
  - `passes.batch-plan stream=fullscreen-uniform-pass`
  - `passes.batch id=batch-1 kind=solid-fill`
- This confirmed `drawFullscreenPass(...)` was still implicitly opting every fullscreen rect pass into Phase 4 solid-fill batching, including a non-simple fullscreen shader.

### GREEN
Command:
`rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.backend runtime does not record pass batch plan for unmarked generic fullscreen pass draws when backend is available' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.backend runtime records pass batch plan for fullscreen rect draws when backend is available' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.backend runtime does not record pass batch plan for unmarked raw uniform fullscreen passes when backend is available' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.backend runtime records pass batch plan for explicitly marked simple gradient raw uniform fullscreen passes when backend is available' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.batched rectangle scene uses fewer submissions than explicit unbatched baseline when backend is available' --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest`

Result:
- All five targeted runtime smokes passed.
- The new generic/unmarked `drawFullscreenPass(...)` smoke now leaves `passBatchPlans`, `passBatchesAccepted`, and `passBatchPackets` unchanged and emits no accepted `kind=solid-fill` evidence.
- The explicit solid-fill fullscreen rect smoke still records `passes.batch id=batch-1 kind=solid-fill`.
- The unmarked raw-uniform smoke still records nothing.
- The explicit simple-gradient raw-uniform smoke still records `kind=simple-gradient`.
- The explicit-unbatched-baseline smoke still proves solid-fill batching only when callers opt in intentionally.
- All `GPUPassBatcherTest` tests passed.
- Gradle exited `BUILD SUCCESSFUL`.

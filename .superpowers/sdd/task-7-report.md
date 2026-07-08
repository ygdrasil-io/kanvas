# Task 7 Report: MSAA Plan Integration

## What changed

- Extended `GPUIntermediatePlannerRequest` with:
  - `requestedSampleCount`
  - `msaaAdapter`
- Updated `GPUIntermediatePlanner.plan(...)` to:
  - validate MSAA requests through `GPUMsaa.resolve(...)`,
  - refuse with stable diagnostic codes when the MSAA route is refused,
  - append MSAA intermediate/resolve planning steps when `requestedSampleCount > 1`,
  - increment intermediate-plan telemetry for `msaaTargets` and `msaaResolves`.
- Added runtime telemetry counters to `GPUBackendRuntimeTelemetry`:
  - `intermediateTexturesCreated`
  - `destinationCopies`
  - `msaaTargets`
  - `msaaResolves`
- Extended `WgpuBackendRuntimeTelemetryRecorder` with matching counters and snapshot propagation.
- Wired runtime counter increments for:
  - intermediate texture creation in both offscreen-target and recorder allocation paths,
  - destination-copy consumption in `drawBlendPass(...)`.
- Added focused runtime-contract coverage for the new telemetry defaults, dump line, and negative-value guards.
- Added a dedicated planner RED/GREEN test for the MSAA plan path.
- Left `GPUMsaaTest` and `GPUPipelineKeyDerivationTest` unchanged; existing assertions already covered the required invariants, and both were rerun as GREEN verification.

## TDD RED/GREEN evidence

### RED

Command:

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateMsaaPlanTest"
```

Observed failure:

- `No parameter with name 'requestedSampleCount' found.`
- `No parameter with name 'msaaAdapter' found.`
- `:gpu-renderer:compileTestKotlin FAILED`

### GREEN

Command:

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateMsaaPlanTest" --tests "org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaTest" --tests "org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyDerivationTest" --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest"
```

Observed result:

- `BUILD SUCCESSFUL`
- `GPUIntermediateMsaaPlanTest` passed
- `GPUMsaaTest` passed
- `GPUPipelineKeyDerivationTest` passed
- `GPUBackendRuntimeContractsTest` passed

## Tests and results

- `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateMsaaPlanTest"`  
  RED: failed as expected before implementation because the new planner request fields did not exist
- `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateMsaaPlanTest" --tests "org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaTest" --tests "org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyDerivationTest" --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest"`  
  GREEN: passed
- `rtk git diff --check`  
  Passed; no whitespace or patch-format issues

## Files changed

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateMsaaPlanTest.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt`

## Self-review

- Kept the change scoped to the planner and runtime telemetry surfaces named in the task brief.
- Preserved stable dump behavior: no backend handles, raw addresses, or uniform values were added to durable telemetry or plan dumps.
- Preserved the existing pipeline-key contract; `sampleStateHash` remains the MSAA-relevant executable axis and no concrete resource identity fields were introduced.
- Kept the runtime-side MSAA counters passive unless a real backend event is observable; no CPU/readback fallback was introduced.
- Verified the RED came from the intended missing planner-request fields, not from fixture mistakes.
- Verified the new runtime telemetry fields are validated and serialized deterministically through `GPUBackendRuntimeContractsTest`.

## Concerns

- The scoped files for Task 7 do not yet expose a real native multisample offscreen allocation / resolve encode path, so `recordMsaaTarget()` and `recordMsaaResolve()` are present in the recorder but are not yet exercised by a concrete backend resolve command in this change. Planner-side MSAA telemetry is in place, and runtime counters remain stable rather than faking a resolve.
- Non-blocking JVM/Gradle warnings still appear during test execution (`System::load` native-access warning), but they are pre-existing environment/runtime warnings and did not affect task results.

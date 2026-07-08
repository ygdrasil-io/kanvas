# Task 3 Report: Runtime Batch Telemetry

## Scope
- Added passive runtime telemetry counters for pass batch evidence only.
- Added deterministic dump storage for `GPUPassBatchPlan` evidence.
- Kept runtime pass batching inactive; no encoding path was switched over.

## Changes
- `GPUBackendRuntimeTelemetry` now carries:
  - `passBatchPlans`
  - `passBatchesAccepted`
  - `passBatchCuts`
  - `passBatchPackets`
- Validation now rejects negative values for each new counter.
- Telemetry dump lines now include the new counters.
- `WgpuBackendRuntimeTelemetryRecorder` now stores:
  - pass batch counters
  - capped `passBatchDumpLines` evidence
- Added `recordPassBatchPlan(plan: GPUPassBatchPlan)` for backend-neutral evidence capture.

## TDD Evidence
- Red step: added `runtime telemetry dump includes pass batch counters`.
- Verified failure: Kotlin compilation failed because `GPUBackendRuntimeTelemetry` did not yet have the new constructor parameters.
- Green step: added the minimal contract and recorder changes.

## Verification
- `rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.runtime\\ telemetry\\ dump\\ includes\\ pass\\ batch\\ counters`
- `rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest`

## Notes
- The new recorder method is intentionally passive. It is available for later wiring, but Task 3 does not activate pass batching.

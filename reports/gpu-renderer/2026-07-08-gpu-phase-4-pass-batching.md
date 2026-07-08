# GPU Phase 4 Pass Batching Evidence

Date: 2026-07-08

## Scope

Implemented conservative `GPUPassBatcher` support for solid fills and simple
linear two-stop gradients. Destination-read, saveLayer, filters, text complex,
copy, upload, readback, compute, and unretained materialized resources remain
explicit cuts.

## Commands

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest --tests org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchCommandStreamTest --tests org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketCommandStreamTest --tests org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepScaffoldTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
rtk ./gradlew :gpu-renderer-scenes:test --tests org.graphiks.kanvas.gpu.renderer.scenes.offscreen.M25ExecutorWiringTest
rtk git status --short
rtk git diff --check
```

## Results

- `:gpu-renderer:test` targeted bundle: PASS.
- `:gpu-renderer-scenes:test --tests org.graphiks.kanvas.gpu.renderer.scenes.offscreen.M25ExecutorWiringTest`: BLOCKED in `:gpu-renderer-scenes:compileKotlin`.
- `rtk git status --short`: clean when checked before writing this report.
- `rtk git diff --check`: clean.
- Follow-up review check after closeout commit `6ed88f510`: `rtk git status --short`
  and `rtk git diff --check` both produced no output.

## Review Fix Follow-Up

- `GPUPassCommandStream.fromBatchPlan(...)` now refuses plans unless every
  input packet appears in exactly one lowered batch; cut/refused packets can no
  longer disappear silently during grouped command emission.
- Phase 4 `SimpleGradient` opt-in is constrained to explicit linear two-stop
  routes. Radial and sweep fallback paths no longer mark themselves as
  `SimpleGradient`.
- Runtime pass-batch telemetry is recorded only when
  `materializeFullscreenUniformSlab(...)` returns a materialized slab with
  retained leases. Slab fallback keeps the runtime path functional but does not
  emit accepted batch-plan evidence.
- Static scene-fixture diagnostics now carry the
  `passes.batching.wiring-fixture` prefix so they cannot be confused with live
  runtime telemetry dumps.

## Runtime Evidence

Representative dump lines derived from the deterministic dump formats and the
assertions exercised by the executed test bundle. They are not pasted live
stdout because Gradle's default test logging only exposed the PASS summaries in
this run.

```text
passes.batch-plan stream=fullscreen-uniform-pass pass=pass:fullscreen-uniform-pass batches=1 accepted=1 cuts=0 packets=2 diagnostics=none
passes.batch id=batch-1 kind=solid-fill target=rgba8unorm packets=packet:fullscreen-uniform-pass:1,packet:fullscreen-uniform-pass:2 pipelines=render:solid-fill queueRetained=true
passes.batch-queue-guard batch=batch-1 retained=true required=lease:uniform-slab:fullscreen:phase4 retainedRefs=lease:uniform-slab:fullscreen:phase4
gpu-runtime.telemetry renderPasses=1 offscreenPasses=1 windowPasses=0 submissions=1 commandBuffers=1 buffersCreated=3 texturesCreated=2 bindGroupsCreated=2 samplersCreated=0 queueWrites=2 uniformSlabsCreated=1 uniformSlabBytesAllocated=512 uniformSlabFallbacks=0 passBatchPlans=1 passBatchesAccepted=1 passBatchCuts=0 passBatchPackets=2
gpu-queue.telemetry submitted=1 completed=1 released=1 pending=0 waits=1 unknownCompletions=0
```

## Non-Claims

- No destination-read batching.
- No saveLayer batching.
- No filter/intermediate batching.
- No text complex batching.
- No radial/sweep simple-gradient pass-batching opt-in in Phase 4.
- No Graphite or Ganesh port.
- No dynamic SkSL compilation.

## GM Impact

No GM reference images were regenerated for this phase. Pixel output is
expected to remain unchanged because runtime encoding still emits per-packet
state setup inside accepted simple batches and cuts complex cases.

## Known Blockers

- `:gpu-renderer-scenes:test --tests org.graphiks.kanvas.gpu.renderer.scenes.offscreen.M25ExecutorWiringTest`
  fails before test execution in `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/CompareKanvasSurfaceOffscreenMain.kt`
  and `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/RenderKanvasSurfaceOffscreenMain.kt`.
- The observed unresolved references are `Canvas`, `Paint`, `Path`, `RRect`,
  `Rect`, `Surface`, `SurfaceRenderResult`, `rgba`, `nonTransparentPixels`,
  `dispatchedCount`, `refusedCount`, and `diagnostics`.

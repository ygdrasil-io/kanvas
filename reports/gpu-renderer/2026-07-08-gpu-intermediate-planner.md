# GPU Intermediate Planner Evidence

Date: 2026-07-08

## Summary

- Default activation: `GPUIntermediatePlanner` is active by default for the
  phase-5 routes covered here.
- Product flags required: none for the normal phase-5 path; product flags are
  not required to activate the validated intermediate planner routes in this
  phase.
- Crash/exception status: no crash, no undocumented exception, and no
  `CrashOrException` diagnostic was observed in the validation-scene test or
  offscreen regeneration commands executed for this task.
- Visual-delta policy: visual deltas are acceptable in this phase when they are
  documented. This report keeps unchanged PNGs as-is, records two newly
  materialized gate-board PNGs, and treats diagnostics-only deltas as accepted.

## Verification Commands

| Command | Exit | Classification | Evidence |
| --- | ---: | --- | --- |
| `rtk ./gradlew :gpu-renderer-scenes:test --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest.phase five validation scenes expose intermediate diagnostics"` | 0 | PASS | `gpu-renderer-scenes/build/test-results/test/TEST-org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest.xml` |
| `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.*" --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUIntermediateResourceProviderTest" --tests "org.graphiks.kanvas.gpu.renderer.state.BlendAllowlistGateTest"` | 0 | PASS | `gpu-renderer/build/test-results/test/TEST-org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateCommandStreamTest.xml`, `gpu-renderer/build/test-results/test/TEST-org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateMsaaPlanTest.xml`, `gpu-renderer/build/test-results/test/TEST-org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanContractsTest.xml`, `gpu-renderer/build/test-results/test/TEST-org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlannerTest.xml`, `gpu-renderer/build/test-results/test/TEST-org.graphiks.kanvas.gpu.renderer.resources.GPUIntermediateResourceProviderTest.xml`, `gpu-renderer/build/test-results/test/TEST-org.graphiks.kanvas.gpu.renderer.state.BlendAllowlistGateTest.xml` |
| `rtk ./gradlew :gpu-renderer-scenes:test --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.*"` | 0 | PASS | `gpu-renderer-scenes/build/reports/tests/test/index.html` |
| `rtk ./gradlew :integration-tests:skia:regenerateSkiaGmRenders` | 1 | TASK_UNAVAILABLE | stdout/stderr only; task missing in `:integration-tests:skia` |
| `rtk ./gradlew :integration-tests:skia:tasks --all` | 0 | DISCOVERY_PASS | stdout/stderr listed the locally available `:integration-tests:skia` tasks, including `generateSkiaRenders` and `generateSkiaDashboard` |
| `rtk ./gradlew :integration-tests:skia:generateSkiaDashboard` | 0 | PASS_WITH_SKIPS | `integration-tests/skia/build/reports/skia-gm-dashboard/index.html` |
| `rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test` | 1 | FAILED_ON_EXISTING_PACKAGE_BOUNDARY_TEST | `gpu-renderer/build/reports/tests/test/index.html` and `gpu-renderer-scenes/build/reports/tests/test/index.html` |
| `rtk ./gradlew :gpu-renderer:test --rerun-tasks --tests "org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest"` | 0 | PASS_AFTER_PACKAGE_BOUNDARY_FIX | `gpu-renderer/build/test-results/test/TEST-org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest.xml` |
| `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUIntermediateResourceProviderTest" --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProviderTest" --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanContractsTest" --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlannerTest" --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateCommandStreamTest" --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateMsaaPlanTest"` | 0 | PASS_AFTER_PACKAGE_BOUNDARY_FIX | `gpu-renderer/build/reports/tests/test/index.html` |
| `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.paintblend.PaintBlendExecutionBoundaryTest"` | 0 | PASS_AFTER_SHADER_BLEND_TEST_ALIGNMENT | `gpu-renderer/build/test-results/test/TEST-org.graphiks.kanvas.gpu.renderer.paintblend.PaintBlendExecutionBoundaryTest.xml` |
| `rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test` | 0 | PASS | `gpu-renderer/build/reports/tests/test/index.html` and `gpu-renderer-scenes/build/reports/tests/test/index.html` |
| `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUIntermediateResourceProviderTest"` | 1 | EXPECTED_RED_INVALID_DESCRIPTOR_BOUNDARY | `gpu-renderer/build/reports/tests/test/index.html` |
| `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUIntermediateResourceProviderTest"` | 0 | PASS_AFTER_REQUEST_BOUNDARY_VALIDATION_FIX | `gpu-renderer/build/test-results/test/TEST-org.graphiks.kanvas.gpu.renderer.resources.GPUIntermediateResourceProviderTest.xml` |
| `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest"` | 0 | PASS_AFTER_CONTROLLER_REVIEW_FIXES | `gpu-renderer/build/test-results/test/TEST-org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest.xml` |
| `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.paintblend.PaintBlendExecutionBoundaryTest"` | 0 | PASS_AFTER_CONTROLLER_REVIEW_FIXES | `gpu-renderer/build/test-results/test/TEST-org.graphiks.kanvas.gpu.renderer.paintblend.PaintBlendExecutionBoundaryTest.xml` |
| `rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test` | 0 | PASS_FINAL_SMOKE_AFTER_CONTROLLER_REVIEW_FIXES | `gpu-renderer/build/reports/tests/test/index.html` and `gpu-renderer-scenes/build/reports/tests/test/index.html` |
| `rtk git diff --check` | 0 | PASS | stdout empty |
| `rtk git status --short` | 0 | PASS_INTENTIONAL_CHANGES_ONLY | stdout listed only intentional task-owned source, test, report, and artifact changes at the time of execution |

## Skia Availability Note

The exact brief command `:integration-tests:skia:regenerateSkiaGmRenders` does
not exist in this checkout. `rtk ./gradlew :integration-tests:skia:tasks --all`
shows the locally available equivalents `generateSkiaRenders`,
`generateSkiaRendersFor`, `generateSkiaScan`, and `generateSkiaDashboard`.

`generateSkiaDashboard` succeeded and, via its task dependency on
`generateSkiaRenders`, regenerated local GM outputs and the dashboard bundle.
No tracked changes were produced under
`integration-tests/skia/src/test/resources/generated-renders/**` or
`integration-tests/skia/src/test/resources/test-similarity-scores.properties`
in this workspace.

## Runtime Artifacts

| Scene | Command Exit | Output Path | Crash/Exception Classification | Visual Delta Classification | `intermediate.plan` Evidence |
| --- | ---: | --- | --- | --- | --- |
| `savelayer-isolated` | 0 | `reports/gpu-renderer-scenes/offscreen/savelayer-isolated/render.png` | rendered-no-crash-no-exception | render unchanged; diagnostics/run metadata updated | `intermediate.plan id=scene-intermediate:savelayer-isolated target=target:savelayer-isolated steps=3 diagnostics=none` |
| `savelayer-group-alpha` | 0 | `reports/gpu-renderer-scenes/offscreen/savelayer-group-alpha/render.png` | rendered-no-crash-no-exception | render unchanged; diagnostics/run metadata updated | `intermediate.plan id=scene-intermediate:savelayer-group-alpha target=target:savelayer-group-alpha steps=3 diagnostics=none` |
| `dst-read-strategy` | 0 | `reports/gpu-renderer-scenes/offscreen/dst-read-strategy/render.png` | rendered-no-crash-no-exception | render unchanged; diagnostics/run metadata updated | `intermediate.plan id=scene-intermediate:dst-read-strategy target=target:dst-read-strategy steps=1 diagnostics=none` |
| `destination-read-strategy-gate-board` | 0 | `reports/gpu-renderer-scenes/offscreen/destination-read-strategy-gate-board/render.png` | rendered-no-crash-no-exception | new committed PNG plus `not-yet-rendered` -> `rendered` transition | `intermediate.plan id=scene-intermediate:destination-read-strategy-gate-board target=target:destination-read-strategy-gate-board steps=1 diagnostics=none` |
| `savelayer-isolation-gate-board` | 0 | `reports/gpu-renderer-scenes/offscreen/savelayer-isolation-gate-board/render.png` | rendered-no-crash-no-exception | new committed PNG plus `not-yet-rendered` -> `rendered` transition | `intermediate.plan id=scene-intermediate:savelayer-isolation-gate-board target=target:savelayer-isolation-gate-board steps=1 diagnostics=none` |

## Telemetry

Short diagnostic lines captured from the regenerated offscreen reports:

```text
gpu-runtime.telemetry renderPasses=0 offscreenPasses=0 windowPasses=0 submissions=0 commandBuffers=0 buffersCreated=1 texturesCreated=2 intermediateTexturesCreated=0 destinationCopies=0 msaaTargets=0 msaaResolves=0 bindGroupsCreated=0 samplersCreated=0 queueWrites=0 uniformSlabsCreated=0 uniformSlabBytesAllocated=0 uniformSlabFallbacks=0 passBatchPlans=0 passBatchesAccepted=0 passBatchCuts=0 passBatchPackets=0
intermediate.telemetry destinationReadCopies=0 destinationReadIntermediateBinds=0 copiedBytes=0 passSplits=0 intermediatesCreated=1 intermediatesReused=0 intermediatesRefused=0 liveIntermediateBytes=256000 layerTargets=1 layerComposites=1 msaaTargets=0 msaaResolves=0
intermediate.telemetry destinationReadCopies=0 destinationReadIntermediateBinds=0 copiedBytes=0 passSplits=0 intermediatesCreated=0 intermediatesReused=0 intermediatesRefused=0 liveIntermediateBytes=0 layerTargets=0 layerComposites=0 msaaTargets=0 msaaResolves=0
```

Deterministic intermediate resource-provider dump format covered by
`GPUIntermediateResourceProviderTest`:

```text
resource-provider.cache lane=intermediate-texture result=create key=target=target:main;descriptor=sha256:layer-a;bounds=bounds:layer-a;format=rgba8unorm;usage=render_attachment+texture_binding;sampleCount=1;generation=5;lifetime=layer-local;owner=scope:layer-a subject=intermediate:layer-a
resource-provider.cache lane=intermediate-texture result=reuse key=target=target:main;descriptor=sha256:layer-a;bounds=bounds:layer-a;format=rgba8unorm;usage=render_attachment+texture_binding;sampleCount=1;generation=5;lifetime=layer-local;owner=scope:layer-a subject=intermediate:layer-a
```

## Known Limitations

- Visual correctness is not globally complete in this phase.
- Remaining unsupported routes keep stable reason codes.
- No CPU-rendered texture product fallback was added.

## Final Verification Notes

- Controller follow-up resolved the package-boundary cycle by removing the
  production `resources -> intermediates` import edge from intermediate texture
  materialization.
- Controller follow-up also aligned `PaintBlendExecutionBoundaryTest` with the
  Phase 5 shader-blend acceptance contract: shader blend can now be accepted by
  the blend allowlist while the fixed paint-blend execution boundary still
  refuses it with `unsupported.paint_blend.shader_blend_unvalidated`.
- Controller review follow-up then hardened the intermediate texture request
  boundary so interface-backed descriptors are snapshotted and validated before
  they can influence cache keys or durable dumps.
- The historical first full smoke failure was preserved as evidence, then fixed
  in follow-up; the final smoke pass is green on
  `rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test`.
- `rtk git diff --check` was clean.
- `rtk git status --short` showed only intentional task-owned source, test,
  report, and artifact changes.

## Controller Review Follow-Up

- Package-boundary gate fix:
  criteria = no production `resources -> intermediates` import edge;
  evidence = `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest"` passed and the production boundary remains interface-based.
- Paint-blend gate alignment fix:
  criteria = shader blends may be accepted by the allowlist while the execution boundary still refuses unvalidated shader blends with the stable reason code;
  evidence = `rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.paintblend.PaintBlendExecutionBoundaryTest"` passed.
- Request-boundary invariant fix:
  criteria = arbitrary `GPUIntermediateTextureMaterializationDescriptor` implementations are rejected when labels, sizes, usages, generations, or sample counts are invalid, while stable keys/dumps stay dump-safe and backend-handle-free;
  evidence = the red/green `GPUIntermediateResourceProviderTest` runs above and the production changes in `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/IntermediateResourceProvider.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceDumpSafety.kt`, and `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`.

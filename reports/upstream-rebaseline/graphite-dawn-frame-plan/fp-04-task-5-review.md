# FP-04 Task 5 review

Date: 2026-07-27

Verdict: **accepted**

This review consolidates the current implementation, the Task 5.4 repair, the
initial independent findings, and the clean independent re-review. It accepts
Task 5. Task 6 is ready to start under a separate implementation scope; this
review does not implement Task 6 or open product routing.

## Review matrix

| Gate | Current evidence | Review state |
|---|---|---|
| Reflected ABI/layout identity | Prepared-image builder, structural pipeline identity, shader reflection, and native bind-group materialization share the ABI112/layout authority. | implemented; independently reviewed |
| Command-exact source mapping | Prepared bindings are keyed by packet/command identity and repeated draws may share one artifact without losing per-command mapping. | implemented; focused tests green |
| Task-4 native sharing | The session cache retains texture/view/sampler/bind-group sharing and immutable device generation. | implemented; focused cache/materializer tests green |
| Stable refusals | Preparation, preflight, cache, and native materialization preserve typed refusal codes before side effects. | implemented; mutation tests green |
| Pipeline-key normalization | Uniform-only payload axes stay outside structural pipeline identity; scene target format is a real structural axis. | implemented; legacy hash locked |
| Native translucent sRGB oracle | Straight sRGB source, shader premultiplication, and native sRGB store are checked against an independent oracle. | implemented; native probe included in final manifest |
| Animation | Existing animation refusal remains unchanged. | unchanged; no support claim |

## Task 5.4 findings resolved

The review found that a prepared image could carry an sRGB source contract
while scene-target and CorePrimitive authorities still defaulted structurally
to `rgba8unorm`. It also found a runtime validator that accepted only the
legacy target pair and a prepared-surface diagnostic that described the
superseded encoded-premul/unorm path.

The repair:

1. gives `RGBA8UnormSrgb` a distinct CorePrimitive structural identity;
2. propagates the exact target format through builders, target-state hashes,
   preflight, materializers, descriptors, and cache keys;
3. keeps coverage-mask producers/intermediates explicitly unorm;
4. accepts only the two exact prepared-scene format/interpretation pairs;
5. maps each accepted pair to its exact native WebGPU format;
6. refuses cross-pairs before capability lookup and allocation;
7. updates prepared-image diagnostic evidence to straight sRGB upload and
   native sRGB store;
8. declares native `RGBA8UnormSrgb` single-sample attachment support only,
   with no x4 resolve-source support;
9. permits byte-exact readback from only `RGBA8Unorm` and
   `RGBA8UnormSrgb` storage when the CPU output contract is canonical
   `Rgba8Unorm` + `EncodedPremulSrgb`;
10. names `EncodedPremulSrgb` as the bounded SDR readback interpretation and
    refuses forged sRGB x4 frame plans centrally, before encoder validation,
    cache/pool mutation, or native acquisition.

A subsequent independent read-only review found two important inconsistencies:
the bounded SDR contract still named `LinearPremul` for readback, and the
native capability inventory advertised sRGB x4 even though the frame pool owns
only an unorm MSAA attachment. Both are repaired above without remapping the
format or promoting the pool. The focused RED run failed 3/3 assertions; the
corresponding GREEN run passed 3/3.

The independent re-review of the repaired working tree reported no significant
finding and a ready-to-merge verdict for the bounded Task 5.4 scope. Together
with the complete consolidation evidence, that closes Task 5.

## Focused evidence

The Task 5.4 focused matrix is green:

- 176/176 structural, descriptor, preflight, and materializer tests;
- 84/84 runtime-native and prepared-surface builder tests;
- legacy target-state and pipeline-hash byte identities remain locked by
  tests;
- no changed file belongs to an animation implementation path.

The Surface integration run also supplied a direct RED/GREEN sequence:

- RED: 81 tests, 79 passed, 2 failed while `SurfaceTest` exposed the missing
  native sRGB capability and then the unorm-only native readback validation;
- GREEN: the same 9 suites completed 81/81 after the capability and
  byte-exact readback contracts were closed.

The readback regression itself was observed RED (1 failed) and GREEN
(1 passed). The GREEN case asserts unchanged stored bytes, while incoherent
`LinearPremul` output and unsupported `BGRA8Unorm` storage are refused before
mapping.

The sRGB-MSAA defense-in-depth test starts from a recordable sRGB x1 fixture,
forges only its render step to `MultisampleFrame(4)`, and supplies an empty,
therefore invalid, encoder plan. The stable
`unsupported.native-core-primitive.srgb-msaa` refusal wins before route
dispatch and leaves native events, session-cache counters, and frame-pool slot
count unchanged.

All commands used `--dependency-verification=off --no-daemon
--console=plain --rerun-tasks --max-workers=1`.

## Fresh Task 5.4 manifests

These are fresh current-working-tree manifests for Task 5.4. They are not a
historical certified manifest and do not by themselves accept Task 5.
In particular, the historical 2026-07-26 289-test audit remains background
evidence only; it is not substituted for either fresh manifest below.

The exact 24-suite GPU list was:

1. `org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest`
2. `org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest`
3. `org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighterTest`
4. `org.graphiks.kanvas.gpu.renderer.execution.GPUFrameReadbackCompletionTest`
5. `org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageNativeResourcesTest`
6. `org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImagePipelineSpecializationTest`
7. `org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageSessionCacheTest`
8. `org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageShaderTest`
9. `org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageSrgbNativeProbeTest`
10. `org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageUploadScopeTest`
11. `org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeResourceAdapterTest`
12. `org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest`
13. `org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitivePipelineDescriptorTest`
14. `org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kFrameEncodingBackendOwnershipTest`
15. `org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest`
16. `org.graphiks.kanvas.gpu.renderer.images.PreparedImageContractsTest`
17. `org.graphiks.kanvas.gpu.renderer.passes.GPUBlendAllowlistPlannerTest`
18. `org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskStructuralKeyTest`
19. `org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePathStencilStructuralKeyTest`
20. `org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadTest`
21. `org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilderTest`
22. `org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTest`
23. `org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageFrameResourcePlanTest`
24. `org.graphiks.kanvas.gpu.renderer.resources.GPUTextureSamplerMaterializationProviderTest`

Exact command:

```text
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighterTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFrameReadbackCompletionTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageNativeResourcesTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImagePipelineSpecializationTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageSessionCacheTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageShaderTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageSrgbNativeProbeTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageUploadScopeTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeResourceAdapterTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitivePipelineDescriptorTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kFrameEncodingBackendOwnershipTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest' --tests 'org.graphiks.kanvas.gpu.renderer.images.PreparedImageContractsTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUBlendAllowlistPlannerTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskStructuralKeyTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePathStencilStructuralKeyTest' --tests 'org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadTest' --tests 'org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilderTest' --tests 'org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTest' --tests 'org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageFrameResourcePlanTest' --tests 'org.graphiks.kanvas.gpu.renderer.resources.GPUTextureSamplerMaterializationProviderTest' --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Result: 24 suites, 531 tests, 531 passed, 0 failed, 0 skipped;
`BUILD SUCCESSFUL in 1m 7s`.

The exact 9-suite Kanvas list was:

1. `org.graphiks.kanvas.image.BitmapTest`
2. `org.graphiks.kanvas.image.ImageTest`
3. `org.graphiks.kanvas.picture.PictureTest`
4. `org.graphiks.kanvas.surface.ImageEncoderTest`
5. `org.graphiks.kanvas.surface.SurfaceTest`
6. `org.graphiks.kanvas.surface.gpu.GPUPreparedImageSourceTest`
7. `org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceColorMappingTest`
8. `org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameBuilderTest`
9. `org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceSemanticBuilderTest`

Exact command:

```text
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.image.BitmapTest' --tests 'org.graphiks.kanvas.image.ImageTest' --tests 'org.graphiks.kanvas.picture.PictureTest' --tests 'org.graphiks.kanvas.surface.ImageEncoderTest' --tests 'org.graphiks.kanvas.surface.SurfaceTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedImageSourceTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceColorMappingTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameBuilderTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceSemanticBuilderTest' --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Result: 9 suites, 81 tests, 81 passed, 0 failed, 0 skipped;
`BUILD SUCCESSFUL in 1m 2s`.

## Task 5 acceptance and Task 6 readiness

Task 5 is accepted and completed. The independent review reconciled the
complete focused manifests, native probe output, sharing counters, stable
refusal inventory, pipeline-key measurement, and the two repaired review
findings as one coherent evidence set.

Task 6 is ready to start but is not implemented by this change. Product
routing and the image legacy allowlist remain unchanged.

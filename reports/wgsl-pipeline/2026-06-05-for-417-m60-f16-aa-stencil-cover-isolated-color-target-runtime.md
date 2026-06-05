# FOR-417 M60 F16 AA stencil-cover isolated color-target runtime

Date: 2026-06-05

## Result

Global classification: `isolated-color-target-output-nonzero-matches-mutation`.

FOR-417 adds an opt-in runtime diagnostic guarded by `kanvas.webgpu.m60F16AaStencilCoverIsolatedColorTarget.enabled`. The guard is disabled by default. The default render path, route, score, thresholds, fallback policy, and promotion state are unchanged.

## Evidence

- Source draft memory: `global/kanvas/tickets/drafts/brouillon-ticket-for-417-m60-f16-ajouter-scratch-no-blend-pour-sortie-color-target-aa-stencil-cover`
- Source finding: `global/kanvas/findings/for-416-refuse-de-synthetiser-la-sortie-color-target-isolee-et-confirme-le-besoin-dun-scratch-no-blend`
- Raw runtime snapshot: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-isolated-color-target-runtime-for417/raw-runtime-snapshot-for417.json`
- Final artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-isolated-color-target-runtime-for417/m60-f16-aa-stencil-cover-isolated-color-target-runtime-for417.json`
- Runtime owner: `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`
- Test sink: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt`
- Capture test: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt`

## Scope

- Selected pixels: 16
- Zero-return mutating transitions covered: 16
- Mutating draw counts: {'1': 6, '3': 10}
- Pipeline family: `StencilCoverAaPolygonDraw`
- Blend mode: `kSrcOver`
- Scratch format: `RGBA16Float`

## Observation

The scratch pass replays the bounded M60 F16 `StencilCoverAaPolygonDraw` cover draw into a separate `RGBA16Float` color target. The color target disables blend with `blend = null`, uses a separate depth/stencil texture, and is read through the existing 16-point compute readback shader.

All 16 FOR-413 mutating transitions have available scratch samples. The scratch output is non-zero for all 16 records, diverges from the zero FOR-412 storage side-channel, and `SrcOver(scratchOutput, dstBefore)` matches the FOR-414 immediate post-draw mutation for all 16.

## Summary

- Runtime events observed: 3
- Scratch samples available for mutation records: 16
- Non-zero scratch outputs: 16
- Scratch SrcOver matches mutation: 16
- Source-vs-scratch divergences: 16

## Interpretation

FOR-415 already excluded an obvious blend/render-pass descriptor mismatch. FOR-417 now shows the actual no-blend color-target output is non-zero and reconstructs the mutation. The remaining suspect is therefore the FOR-412 shader-return storage side-channel or its diagnostic capture path, not fixed-function blend/load/store.

## Non-goals preserved

- No rendering correction.
- No default behavior, threshold, score, route, fallback, or promotion change.
- No extension outside M60 F16 / `StencilCoverAaPolygonDraw` / `kSrcOver`.
- No synthetic zero source.
- No Ganesh, Graphite, or SkSL compiler work.

## Validation

Expected local commands:

```text
rtk python3 scripts/validate_for417_m60_f16_aa_stencil_cover_isolated_color_target_runtime.py
rtk python3 scripts/validate_for416_m60_f16_aa_stencil_cover_isolated_color_target.py
rtk python3 scripts/validate_for415_m60_f16_aa_stencil_cover_blend_render_pass_state.py
rtk python3 scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py
rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py
rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for417-pycache python3 -m py_compile scripts/validate_for417_m60_f16_aa_stencil_cover_isolated_color_target_runtime.py
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
```

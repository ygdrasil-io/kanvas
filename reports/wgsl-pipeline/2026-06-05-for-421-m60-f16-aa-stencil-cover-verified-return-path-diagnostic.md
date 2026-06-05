# FOR-421 - M60 F16 verified return-path diagnostic

Date: 2026-06-05

## Result

Classification: `verified-return-path-storage-nonzero`.

FOR-421 replaces the fragile diagnostic WGSL return-path substitution with a
checked transformation. The diagnostic now requires exactly two bounded return
blocks, replaces them with `m60_f16_application_point_output(...)`, then
requires exactly two instrumented returns before creating the shader module.

## Evidence

- Final WGSL diagnostics verify that `fs_inside` and `fs_outside` return
  `m60_f16_application_point_output` for FOR-412, FOR-418, and FOR-419.
- FOR-419 still disables the entry storage write and keeps the `output_nonzero`
  gate.
- FOR-419 now observes 32 nonzero storage source writes on the verified return
  path.
- The same run observes 16 nonzero scratch color-target samples.
- FOR-418 continues to share the FOR-412 shader source.
- No default rendering, scoring, threshold, fallback, support claim, or
  promotion changed.

## Artifact

- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-verified-return-path-diagnostic-for421/m60-f16-aa-stencil-cover-verified-return-path-diagnostic-for421.json`

## Interpretation

FOR-420 correctly identified that the previous diagnostic helper was not on the
rendered return path. FOR-421 fixes that diagnostic path and proves the storage
side-channel can capture the true returned source values when the final WGSL is
instrumented correctly.

The remaining M60 F16 work should compare those verified source values against
the scratch color-target and the final blended output. This ticket does not
claim rendering support or change the scene threshold.

## Validation

- `rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for421_m60_f16_aa_stencil_cover_verified_return_path_diagnostic.py`

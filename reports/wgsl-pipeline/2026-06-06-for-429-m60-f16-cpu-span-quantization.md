# FOR-429 - M60 F16 CPU addSpanCoverage span quantization

Date: 2026-06-06

## Scope

Linear: FOR-429

Source draft memory:
`global/kanvas/tickets/drafts/brouillon-ticket-for-429-m60-f16-diagnostiquer-la-quantification-cpu-add-span-coverage`

Source finding:
`global/kanvas/findings/for-428-cpu-scan-fill-path-span-count-exceeds-center-mask`

Previous finding:
`global/kanvas/findings/for-427-subsample-mask-stage-incomplete-because-cpu-scan-fill-path-identity-is-not-exported`

This slice keeps rendering unchanged and adds opt-in diagnostic evidence for
the six M60 F16 partial pixels isolated by FOR-426/FOR-427/FOR-428. The trace
records the real `SkBitmapDevice.scanFillPath.addSpanCoverage` intersections
per subrow and exports the CPU span-width quantization facts.

## Artifact

JSON:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-span-quantization-for429/m60-f16-cpu-span-quantization-for429.json`

Prerequisite artifacts:

- FOR-428:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-scanfill-subsample-mask-for428/m60-f16-cpu-scanfill-subsample-mask-for428.json`
- FOR-427:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-subsample-mask-for427/m60-f16-aa-stencil-cover-subsample-mask-for427.json`

## Result

Classification: `scanfill-rounded-width-exceeds-center-samples`

Across the six partial pixels `(92,75)`, `(91,76)`, `(90,77)`, `(89,78)`,
`(88,79)`, `(87,80)`:

- CPU `scanFillPath.addSpanCoverage` span sample count: `10/16` per pixel,
  `60/96` total.
- CPU 4x4 center mask: `0x0137`, `6/16` covered subsamples per pixel,
  `36/96` total.
- WGSL `sample_covered` mask: `0x0137`, `6/16` covered subsamples per pixel,
  `36/96` total.
- The `24/96` total delta is explained by per-subrow width quantization:
  `roundedSamples = (intersectionWidth * supers + 0.5f).toInt().coerceIn(0, supers)`.

The finding does not reintroduce `wgsl-misses-cpu-covered-subsamples`: CPU
center samples and WGSL samples still match cell-for-cell. The remaining
difference is that the CPU span path counts fractional width as rounded area
coverage, while the center-mask diagnostic counts only sample centers.

## Non-goals preserved

- No default rendering change.
- No threshold or scoring change.
- No scene promotion.
- No fallback policy change.
- No `PipelineKey` change.
- No production WGSL change.
- No wgsl4k change.
- No renderer correction is applied in this slice.

## Validation

Artifact generation:

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverSubsampleMaskFor427.enabled=true -Dkanvas.cpu.m60F16ScanFillSubsampleMaskFor428.enabled=true -Dkanvas.cpu.m60F16CpuSpanQuantizationFor429.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
```

Required validation commands:

```bash
rtk ./gradlew :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for429_m60_f16_cpu_span_quantization.py
rtk python3 scripts/validate_for428_m60_f16_cpu_scanfill_subsample_mask.py
rtk python3 scripts/validate_for427_m60_f16_aa_stencil_cover_subsample_mask.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for429-pycache python3 -m py_compile scripts/validate_for429_m60_f16_cpu_span_quantization.py
```

The draft command `:gpu-raster:jvmTest` is unavailable in this Gradle module;
the module test task is `:gpu-raster:test`.

## Next step

Decide whether the WebGPU M60 F16 path should align with CPU span/area-width
quantization, or whether the center-sample diagnostic remains the GPU rule with
an explicit CPU-reference exception for this stroke-cap/join case.

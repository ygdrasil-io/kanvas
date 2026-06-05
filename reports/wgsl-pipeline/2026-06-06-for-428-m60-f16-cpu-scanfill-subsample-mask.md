# FOR-428 - M60 F16 CPU scanFillPath subsample mask

Date: 2026-06-06

## Scope

Linear: FOR-428

Source draft memory:
`global/kanvas/tickets/drafts/brouillon-ticket-for-428-m60-f16-exporter-le-masque-4x4-cpu-scan-fill-path-pour-comparaison-subsample`

Source finding:
`global/kanvas/findings/for-427-subsample-mask-stage-incomplete-because-cpu-scan-fill-path-identity-is-not-exported`

This slice adds opt-in, test-only CPU instrumentation for the six M60 F16
partial pixels isolated by FOR-426. The instrumentation records
`SkBitmapDevice.scanFillPath.addSpanCoverage` while
`BoundedStrokeCapJoinCoverageMaskGM` renders, then compares the CPU 4x4
center mask against the FOR-427 WGSL `sample_covered` mask and separately
reports the CPU span sample count.

## Artifact

JSON:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-scanfill-subsample-mask-for428/m60-f16-cpu-scanfill-subsample-mask-for428.json`

Prerequisite artifacts:

- FOR-427:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-subsample-mask-for427/m60-f16-aa-stencil-cover-subsample-mask-for427.json`
- FOR-426:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-coverage-input-stage-for426/m60-f16-aa-stencil-cover-coverage-input-stage-for426.json`

## Result

Classification: `scanfill-span-count-exceeds-center-mask`

Across the six partial pixels `(92,75)`, `(91,76)`, `(90,77)`, `(89,78)`,
`(88,79)`, `(87,80)`:

- CPU `scanFillPath` span sample count: `10/16` per pixel, `60/96` total.
- CPU 4x4 center mask: `0x0137`, `6/16` covered subsamples per pixel.
- WGSL `sample_covered` mask: `0x0137`, `6/16` covered subsamples per pixel.
- Matching cells: `16/16` per pixel, `96/96` total.
- CPU-only cells: `0/16` per pixel, `0/96` total.
- WGSL-only cells: `0/16` per pixel, `0/96` total.

The CPU mask is not synthesized from alpha. It is captured during the real
`scanFillPath` execution through `SkScanFillPathSubsampleTrace`, which is
enabled only by `kanvas.cpu.m60F16ScanFillSubsampleMaskFor428.enabled`.
The important result is that the CPU span counter reports `10/16`, while the
4x4 center mask matches WGSL at `6/16`. The next fix should inspect
`addSpanCoverage` span quantization before changing `sample_covered`.

## Non-goals preserved

- No default rendering change.
- No threshold or scoring change.
- No scene promotion.
- No fallback policy change.
- No `PipelineKey` change.
- No wgsl4k change.
- No renderer correction is applied in this slice.

## Validation

Artifact generation:

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverSubsampleMaskFor427.enabled=true -Dkanvas.cpu.m60F16ScanFillSubsampleMaskFor428.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
```

Validation commands executed successfully:

```bash
rtk python3 scripts/validate_for428_m60_f16_cpu_scanfill_subsample_mask.py
rtk python3 scripts/validate_for427_m60_f16_aa_stencil_cover_subsample_mask.py
rtk python3 scripts/validate_for426_m60_f16_aa_stencil_cover_coverage_input_stage.py
rtk python3 scripts/validate_for425_m60_f16_aa_stencil_cover_alpha_conversion_stage.py
rtk python3 scripts/validate_for424_m60_f16_aa_stencil_cover_partial_coverage_alpha.py
rtk python3 scripts/validate_for423_m60_f16_aa_stencil_cover_reference_source_coverage.py
rtk python3 scripts/validate_for422_m60_f16_aa_stencil_cover_verified_source_comparison.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for428-pycache python3 -m py_compile scripts/validate_for428_m60_f16_cpu_scanfill_subsample_mask.py
rtk git diff --check
rtk ./gradlew --no-daemon :kanvas-skia:compileKotlin :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin
```

## Next step

Use the real CPU/WGSL cell comparison to inspect why CPU `addSpanCoverage`
counts four additional span samples per pixel even though the center-mask cells
match WGSL. The likely follow-up is a span quantization diagnostic, not a
threshold change.

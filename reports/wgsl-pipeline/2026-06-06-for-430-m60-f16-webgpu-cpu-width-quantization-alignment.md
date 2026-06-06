# FOR-430 - M60 F16 WebGPU CPU width quantization alignment

Date: 2026-06-06

## Scope

Linear: FOR-430

Source draft memory:
`global/kanvas/tickets/drafts/brouillon-ticket-for-430-m60-f16-evaluer-lalignement-web-gpu-sur-la-quantification-cpu-par-largeur`

Source finding:
`global/kanvas/findings/for-429-cpu-add-span-coverage-span-quantization-explains-m60-f16-10-of-16-vs-6-of-16`

This slice evaluates a bounded WebGPU alignment model for the six M60 F16
partial pixels isolated by FOR-426 through FOR-429. It intentionally uses a
test-side simulation from the FOR-429 CPU span-width facts. No render path is
enabled by this ticket.

## Artifact

JSON:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-cpu-width-quantization-alignment-for430/m60-f16-webgpu-cpu-width-quantization-alignment-for430.json`

Prerequisite artifact:

- FOR-429:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-span-quantization-for429/m60-f16-cpu-span-quantization-for429.json`

## Result

Classification:
`webgpu-cpu-width-quantization-diagnostic-matches-cpu`

Decision:
`diagnostic-only-ready-for-render-fix-ticket`

Across the six partial pixels `(92,75)`, `(91,76)`, `(90,77)`, `(89,78)`,
`(88,79)`, `(87,80)`:

- Current WGSL center-mask model: `0x0137`, `6/16` per pixel, `36/96` total.
- CPU `scanFillPath.addSpanCoverage` width model: `10/16` per pixel, `60/96`
  total.
- Simulated WebGPU width-quantized model: `10/16` per pixel, `60/96` total.
- Current delta to CPU width model: `24/96`.
- Simulated aligned delta to CPU width model: `0/96`.

This proves that an alignment model using the FOR-429 width quantization facts
can match the CPU coverage count for these six pixels. It does not prove a
production implementation, full-scene improvement, or broader path coverage
policy.

## Non-goals preserved

- aucun changement de rendu par défaut.
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
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverSubsampleMaskFor427.enabled=true -Dkanvas.cpu.m60F16ScanFillSubsampleMaskFor428.enabled=true -Dkanvas.cpu.m60F16CpuSpanQuantizationFor429.enabled=true -Dkanvas.webgpu.m60F16CpuWidthQuantizationAlignmentFor430.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
```

Required validation commands:

```bash
rtk ./gradlew :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for430_m60_f16_webgpu_cpu_width_quantization_alignment.py
rtk python3 scripts/validate_for429_m60_f16_cpu_span_quantization.py
rtk python3 scripts/validate_for428_m60_f16_cpu_scanfill_subsample_mask.py
rtk python3 scripts/validate_for427_m60_f16_aa_stencil_cover_subsample_mask.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for430-pycache python3 -m py_compile scripts/validate_for430_m60_f16_webgpu_cpu_width_quantization_alignment.py
rtk git diff --check
```

## Next step

Open a separate render-fix ticket if the project chooses to make WebGPU AA
stencil-cover consume CPU-width quantized coverage instead of center-mask
coverage. That follow-up must implement the rendering strategy and prove image
improvement without weakening thresholds or promotion policy.

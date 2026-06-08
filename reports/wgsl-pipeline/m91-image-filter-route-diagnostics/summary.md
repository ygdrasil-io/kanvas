# M91 Image Filter Route Diagnostics

Status: generated evidence

This report adds CPU/WebGPU route diagnostics for the ImageMakeWithFilterGM and OffsetImageFilterGM policy-only refusal rows selected by `M91-IF-1`. It does not promote support, change thresholds, add a generic image-filter DAG compiler, or add a CPU/readback fallback.

## Counters

- Diagnostic rows: `2`
- CPU route diagnostics: `2`
- GPU route diagnostics: `2`
- New support claims: `0`
- Readiness delta: `0.0`
- Dashboard promotions: `0`
- Threshold changes: `0`

## Diagnostics

### skia-gm-imagemakewithfilter

- Source GM: `ImageMakeWithFilterGM`
- Fallback: `image-filter.imagemakewithfilter.row-specific-artifacts-required`
- CPU route: `cpu.image-filter.image-make-with-filter.expected-unsupported`
- GPU route: `webgpu.image-filter.image-make-with-filter.expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-imagemakewithfilter/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-imagemakewithfilter/route-gpu.json`
- Support claim: `False`
- Policy-only: `True`

### skia-gm-offsetimagefilter

- Source GM: `OffsetImageFilterGM`
- Fallback: `image-filter.offset.row-specific-artifacts-required`
- CPU route: `cpu.image-filter.offset-image-filter.expected-unsupported`
- GPU route: `webgpu.image-filter.offset-image-filter.expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-offsetimagefilter/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-offsetimagefilter/route-gpu.json`
- Support claim: `False`
- Policy-only: `True`

## Support Guard

- supportClaimAdded: `False`
- policyOnlyPromoted: `False`
- thresholdChanged: `False`
- belowThresholdCountedAsProductionGap: `False`
- broadImageFilterDAGSupport: `False`
- genericImageFilterDagCompiler: `False`
- cpuReadbackFallbackAdded: `False`
- arbitraryLayerPrepass: `False`
- ganeshPort: `False`
- graphitePort: `False`
- dynamicSkSLCompiler: `False`
- dynamicSkSLIR: `False`
- dynamicSkSLVM: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_route_diagnostics.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterRouteDiagnostics`
- `rtk git diff --check`

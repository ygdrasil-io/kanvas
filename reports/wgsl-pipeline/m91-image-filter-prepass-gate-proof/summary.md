# M91 Image Filter Prepass Gate Proof

Status: generated evidence

This report exposes the `M91-IF-2` dependency gate for `Crop(input=nonNull)` image-filter graphs. It keeps the out-of-scope row expected-unsupported while pointing to the bounded M38 sibling that is already supported.

## Counters

- Proof rows: `1`
- Prepass gate rows: `1`
- GPU expected-unsupported rows: `1`
- CPU oracle pass rows: `1`
- Required smoke candidates added: `0`
- New support claims: `0`
- Readiness delta: `0.0`
- Dashboard promotions: `0`
- Threshold changes: `0`

## Gate Proof

- Row: `image-filter-crop-nonnull-prepass-required`
- Status: `expected-unsupported`
- Fallback: `image-filter.crop-input-nonnull-prepass-required`
- Source shape: `Crop(input=nonNull)`
- Supported sibling: `crop-image-filter-nonnull-prepass`
- CPU route: `cpu.image-filter.crop-nonnull-reference` (`pass`)
- GPU route: `webgpu.image-filter.refuse.prepass-required` (`expected-unsupported`)
- GPU pipeline key: `imageFilter=Crop(input=nonNull),prePass=required,selectedM38Shape=false`
- Required smoke candidate allowed: `False`
- General DAG compiler added: `False`
- CPU/readback fallback added: `False`
- Support claim: `False`
- Refusal CPU diagnostic: `reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/route-cpu.json`
- Refusal GPU diagnostic: `reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/route-gpu.json`
- Refusal inventory stats: `reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/stats.json`

## Supported Sibling Boundary

- Sibling scene: `crop-image-filter-nonnull-prepass`
- GPU diagnostic: `reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/route-gpu.json`
- Prepass diagnostic: `reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/stats.json`
- Boundary: M38 bounded Crop(kDecal, input=Offset(null)) prepass only; does not generalize to Crop(input=nonNull) graph shapes.

## Support Guard

- supportClaimAdded: `False`
- readinessMoved: `False`
- thresholdChanged: `False`
- dashboardPromoted: `False`
- belowThresholdCountedAsProductionGap: `False`
- requiredSmokeCandidateAllowed: `False`
- generalImageFilterDagCompiler: `False`
- cpuReadbackFallbackAdded: `False`
- arbitraryLayerPrepass: `False`
- recursiveCropPrepass: `False`
- ganeshPort: `False`
- graphitePort: `False`
- dynamicSkSLCompiler: `False`
- dynamicSkSLIR: `False`
- dynamicSkSLVM: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_prepass_gate_proof.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterPrepassGateProof`
- `rtk git diff --check`

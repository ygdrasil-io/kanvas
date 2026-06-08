# M91 ImageMakeWithFilterGM Reference Package Plan

Status: reference-package-plan-written-no-new-rendering-support

This report records the reference/provenance package contract for `M91-IF-3B-REF`. It is intentionally non-promotional: the packaged Skia/reference artifact, CPU/WebGPU `fallbackReason=none` routes, render artifacts, diff/stat artifacts, and performance evidence remain missing.

## Row

- Row ID: `skia-gm-imagemakewithfilter`
- Source GM: `ImageMakeWithFilterGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `image-filter.imagemakewithfilter.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Reference Package Contract

- Selected capture: one bounded ImageMakeWithFilterGM candidate cell before any full-grid support evaluation
- Required reference kind: `skia-upstream`
- Packaged reference path: `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/skia.png`
- Provenance path: `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/reference-provenance.json`
- Historical fixtures can stand in: `False`
- Reason: Historical original-888 fixtures preserve provenance, but they are not the row-specific M91 packaged reference/provenance artifact.

## Historical Reference Boundary

- `skia-integration-tests/src/test/resources/original-888/imagemakewithfilter.png`: `1840x860`, bitDepth=`16`, colorType=`6`, promotable=`False`
- `skia-integration-tests/src/test/resources/original-888/imagemakewithfilter_ref.png`: `1840x860`, bitDepth=`16`, colorType=`6`, promotable=`False`
- `skia-integration-tests/src/test/resources/original-888/imagemakewithfilter_crop.png`: `1840x860`, bitDepth=`16`, colorType=`6`, promotable=`False`
- `skia-integration-tests/src/test/resources/original-888/imagemakewithfilter_crop_ref.png`: `1840x860`, bitDepth=`16`, colorType=`6`, promotable=`False`

## Counters

- dashboardPromotions: `0`
- diffStatsAdded: `0`
- fallbackReasonNoneRoutesAdded: `0`
- historicalReferencePromoted: `0`
- historicalReferencesInventoried: `4`
- missingEvidenceItemsStill: `9`
- newSupportClaims: `0`
- presentEvidenceItemsStill: `2`
- readinessDelta: `0.0`
- referenceArtifactsAdded: `0`
- referencePlans: `1`
- renderArtifactsAdded: `0`
- thresholdChanges: `0`

## Required Future Outputs

- `row-specific Skia/reference artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/skia.png`
- `reference provenance`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/reference-provenance.json`
- `CPU route evidence with fallbackReason=none`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/route-cpu.json`
- `WebGPU route evidence with fallbackReason=none`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/route-gpu.json`
- `CPU render artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/cpu.png`
- `WebGPU render artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/gpu.png`
- `CPU diff artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/cpu-diff.png`
- `WebGPU diff artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/gpu-diff.png`
- `CPU/GPU diff/stat artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/stats.json`
- `performance impact evidence`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/performance.json`

## Non-Claims

- belowThresholdCountedAsProductionGap: `False`
- boundedM61M89EvidenceInherited: `False`
- broadImageFilterDAGSupport: `False`
- cpuReadbackFallbackAdded: `False`
- cropPrepassSupport: `False`
- dashboardPromoted: `False`
- diffStatsAdded: `False`
- dynamicSkSLCompiler: `False`
- dynamicSkSLIR: `False`
- dynamicSkSLVM: `False`
- fallbackReasonNoneClaimed: `False`
- ganeshPort: `False`
- genericImageFilterDagCompiler: `False`
- graphitePort: `False`
- historicalReferencePromoted: `False`
- imagemakewithfilterEvidenceInherited: `False`
- layerPrepassSupport: `False`
- makeWithFilterExecuted: `False`
- performanceGatePromoted: `False`
- picturePrepassSupport: `False`
- policyOnlyPromoted: `False`
- readinessMoved: `False`
- referenceArtifactAdded: `False`
- renderArtifactsAdded: `False`
- supportClaimAdded: `False`
- thresholdChanged: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_imagemakewithfilter_reference_package_plan.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-imagemakewithfilter-reference-plan-pycache python3 -m py_compile scripts/m91_image_filter_imagemakewithfilter_reference_package_plan.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterImageMakeWithFilterReferencePackagePlan`
- `rtk git diff --check`

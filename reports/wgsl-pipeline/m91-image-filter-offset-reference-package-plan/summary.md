# M91 OffsetImageFilterGM Reference Package Plan

Status: reference-package-plan-written-no-new-rendering-support

This report records the reference-package contract for `M91-IF-3A-REF`. It is intentionally non-promotional: the packaged Skia/reference artifact, CPU/WebGPU `fallbackReason=none` routes, render artifacts, diff/stat artifacts, and performance evidence remain missing.

## Row

- Row ID: `skia-gm-offsetimagefilter`
- Source GM: `OffsetImageFilterGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `image-filter.offset.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Historical Reference Boundary

- Historical fixture: `skia-integration-tests/src/test/resources/original-888/offsetimagefilter.png`
- Dimensions: `600x100`
- Bit depth: `16`
- Color type: `6`
- Promotable as dashboard reference: `False`
- Reason: Historical fixture only; M91 still requires a row-specific packaged Skia/reference artifact with provenance before support evaluation.

## Counters

- dashboardPromotions: `0`
- diffStatsAdded: `0`
- fallbackReasonNoneRoutesAdded: `0`
- historicalReferencePromoted: `0`
- missingEvidenceItemsStill: `9`
- newSupportClaims: `0`
- presentEvidenceItemsStill: `2`
- readinessDelta: `0.0`
- referenceArtifactsAdded: `0`
- referencePlans: `1`
- renderArtifactsAdded: `0`
- thresholdChanges: `0`

## Required Future Outputs

- `row-specific Skia/reference artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/skia.png`
- `reference provenance`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/reference-provenance.json`
- `CPU route evidence with fallbackReason=none`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/route-cpu.json`
- `WebGPU route evidence with fallbackReason=none`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/route-gpu.json`
- `CPU render artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu.png`
- `WebGPU render artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu.png`
- `CPU diff artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu-diff.png`
- `WebGPU diff artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu-diff.png`
- `CPU/GPU diff/stat artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/stats.json`
- `performance impact evidence`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/performance.json`

## Non-Claims

- adjacentSimpleOffsetEvidenceInherited: `False`
- arbitraryLayerPrepass: `False`
- belowThresholdCountedAsProductionGap: `False`
- cpuReadbackFallbackAdded: `False`
- cropImageFilterDagSupport: `False`
- dashboardPromoted: `False`
- diffStatsAdded: `False`
- dynamicSkSLCompiler: `False`
- dynamicSkSLIR: `False`
- dynamicSkSLVM: `False`
- fallbackReasonNoneClaimed: `False`
- ganeshPort: `False`
- generalImageFilterDagSupport: `False`
- genericImageFilterDagCompiler: `False`
- graphitePort: `False`
- historicalReferencePromoted: `False`
- performanceGatePromoted: `False`
- picturePrepassSupport: `False`
- policyOnlyPromoted: `False`
- readinessMoved: `False`
- referenceArtifactAdded: `False`
- renderArtifactsAdded: `False`
- requiredSmokeCandidateAllowed: `False`
- supportClaimAdded: `False`
- thresholdChanged: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_offset_reference_package_plan.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-offset-reference-plan-pycache python3 -m py_compile scripts/m91_image_filter_offset_reference_package_plan.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetReferencePackagePlan`
- `rtk git diff --check`

# M91 OffsetImageFilterGM Reference Provenance Gate

Status: blocked-until-row-specific-reference-provenance-exists

This report blocks reference promotion for `M91-IF-3A-PROV` until a row-specific `skia.png` and `reference-provenance.json` exist. It deliberately validates that those files, plus route/render/diff/performance artifacts, are absent in this gate.

## Row

- Row ID: `skia-gm-offsetimagefilter`
- Source GM: `OffsetImageFilterGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `image-filter.offset.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Historical Fixture Boundary

- Fixture: `skia-integration-tests/src/test/resources/original-888/offsetimagefilter.png`
- Dimensions: `600x100`
- Bit depth: `16`
- Color type: `6`
- Allowed use: `provenance-boundary-check-only`
- Can satisfy `skia.png`: `False`
- Can satisfy `reference-provenance.json`: `False`
- Reason: The checked-in fixture is historical test data, not a row-specific M91 reference package with generation provenance.

## Required Absent Outputs

- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/skia.png`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/reference-provenance.json`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/route-cpu.json`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/route-gpu.json`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu.png`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu.png`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu-diff.png`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu-diff.png`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/stats.json`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/performance.json`: present=`False`

## Counters

- dashboardPromotions: `0`
- diffStatsAdded: `0`
- fallbackReasonNoneRoutesAdded: `0`
- historicalFixturePromoted: `0`
- missingEvidenceItemsStill: `9`
- newSupportClaims: `0`
- performanceArtifactsAdded: `0`
- presentEvidenceItemsStill: `2`
- provenanceGates: `1`
- readinessDelta: `0.0`
- referenceArtifactsAdded: `0`
- referenceProvenanceArtifactsAdded: `0`
- renderArtifactsAdded: `0`
- thresholdChanges: `0`

## Non-Claims

- belowThresholdCountedAsProductionGap: `False`
- cpuReadbackFallbackAdded: `False`
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
- performanceGatePromoted: `False`
- policyOnlyPromoted: `False`
- readinessMoved: `False`
- referenceArtifactAdded: `False`
- referenceProvenanceAdded: `False`
- renderArtifactsAdded: `False`
- supportClaimAdded: `False`
- thresholdChanged: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_offset_reference_provenance_gate.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-offset-reference-provenance-gate-pycache python3 -m py_compile scripts/m91_image_filter_offset_reference_provenance_gate.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetReferenceProvenanceGate`
- `rtk git diff --check`

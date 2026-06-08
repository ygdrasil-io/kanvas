# M91 ImageMakeWithFilterGM Reference Provenance Gate

Status: blocked-until-row-specific-reference-provenance-exists

This report blocks reference promotion for `M91-IF-3B-PROVENANCE` until a row-specific `skia.png` and `reference-provenance.json` exist. It deliberately validates that those files, plus route/render/diff/performance artifacts, are absent in this gate.

## Row

- Row ID: `skia-gm-imagemakewithfilter`
- Source GM: `ImageMakeWithFilterGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `image-filter.imagemakewithfilter.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Historical Fixture Boundaries

- `skia-integration-tests/src/test/resources/original-888/imagemakewithfilter.png`: `1840x860`, bitDepth=`16`, colorType=`6`, canSatisfySkiaReference=`False`, canSatisfyReferenceProvenance=`False`
- `skia-integration-tests/src/test/resources/original-888/imagemakewithfilter_ref.png`: `1840x860`, bitDepth=`16`, colorType=`6`, canSatisfySkiaReference=`False`, canSatisfyReferenceProvenance=`False`
- `skia-integration-tests/src/test/resources/original-888/imagemakewithfilter_crop.png`: `1840x860`, bitDepth=`16`, colorType=`6`, canSatisfySkiaReference=`False`, canSatisfyReferenceProvenance=`False`
- `skia-integration-tests/src/test/resources/original-888/imagemakewithfilter_crop_ref.png`: `1840x860`, bitDepth=`16`, colorType=`6`, canSatisfySkiaReference=`False`, canSatisfyReferenceProvenance=`False`

## Required Absent Outputs

- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/skia.png`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/reference-provenance.json`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/route-cpu.json`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/route-gpu.json`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/cpu.png`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/gpu.png`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/cpu-diff.png`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/gpu-diff.png`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/stats.json`: present=`False`
- `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/performance.json`: present=`False`

## Counters

- dashboardPromotions: `0`
- diffStatsAdded: `0`
- fallbackReasonNoneRoutesAdded: `0`
- historicalFixturePromoted: `0`
- historicalFixturesChecked: `4`
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
- referenceProvenanceAdded: `False`
- renderArtifactsAdded: `False`
- supportClaimAdded: `False`
- thresholdChanged: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_imagemakewithfilter_reference_provenance_gate.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-imagemakewithfilter-reference-provenance-gate-pycache python3 -m py_compile scripts/m91_image_filter_imagemakewithfilter_reference_provenance_gate.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterImageMakeWithFilterReferenceProvenanceGate`
- `rtk git diff --check`

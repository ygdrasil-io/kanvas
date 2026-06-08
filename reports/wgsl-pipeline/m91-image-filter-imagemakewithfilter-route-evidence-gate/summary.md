# M91 ImageMakeWithFilterGM Route Evidence Gate

Status: blocked-until-row-specific-route-evidence-can-use-fallback-none

This report keeps `M91-IF-3B-ROUTE` non-promotional. Existing CPU/WebGPU route diagnostics remain policy-only expected-unsupported records and cannot stand in for scene route evidence with `fallbackReason=none`.

## Row

- Row ID: `skia-gm-imagemakewithfilter`
- Source GM: `ImageMakeWithFilterGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `image-filter.imagemakewithfilter.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Route Diagnostics Boundary

- CPU diagnostic: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-imagemakewithfilter/route-cpu.json` status=`expected-unsupported` fallback=`image-filter.imagemakewithfilter.row-specific-artifacts-required` scene-evidence=`False`
- WebGPU diagnostic: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-imagemakewithfilter/route-gpu.json` status=`expected-unsupported` fallback=`image-filter.imagemakewithfilter.row-specific-artifacts-required` scene-evidence=`False`
- Reason: Existing M91 route diagnostics are policy-only refusal records. Scene route evidence requires row-specific route-cpu.json and route-gpu.json with fallbackReason=none plus reference, render, diff/stat, and performance evidence.

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
- missingEvidenceItemsStill: `9`
- newSupportClaims: `0`
- performanceArtifactsAdded: `0`
- presentEvidenceItemsStill: `2`
- readinessDelta: `0.0`
- referenceArtifactsAdded: `0`
- referenceProvenanceArtifactsAdded: `0`
- renderArtifactsAdded: `0`
- routeDiagnosticInputs: `2`
- routeEvidenceGates: `1`
- sceneRouteEvidenceArtifactsAdded: `0`
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
- routeEvidenceArtifactAdded: `False`
- supportClaimAdded: `False`
- thresholdChanged: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_imagemakewithfilter_route_evidence_gate.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-imagemakewithfilter-route-evidence-gate-pycache python3 -m py_compile scripts/m91_image_filter_imagemakewithfilter_route_evidence_gate.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterImageMakeWithFilterRouteEvidenceGate`
- `rtk git diff --check`

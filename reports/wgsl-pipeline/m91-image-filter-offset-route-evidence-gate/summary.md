# M91 OffsetImageFilterGM Route Evidence Gate

Status: blocked-until-row-specific-route-evidence-can-use-fallback-none

This report keeps `M91-IF-3A-ROUTE` non-promotional. Existing CPU/WebGPU route diagnostics remain policy-only expected-unsupported records and cannot stand in for scene route evidence with `fallbackReason=none`.

## Row

- Row ID: `skia-gm-offsetimagefilter`
- Source GM: `OffsetImageFilterGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `image-filter.offset.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Route Diagnostics Boundary

- CPU diagnostic: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-offsetimagefilter/route-cpu.json` status=`expected-unsupported` fallback=`image-filter.offset.row-specific-artifacts-required` scene-evidence=`False`
- WebGPU diagnostic: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-offsetimagefilter/route-gpu.json` status=`expected-unsupported` fallback=`image-filter.offset.row-specific-artifacts-required` scene-evidence=`False`
- Reason: Existing M91 route diagnostics are policy-only refusal records. Scene route evidence requires row-specific route-cpu.json and route-gpu.json with fallbackReason=none plus reference, render, diff/stat, and performance evidence.

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
- performanceGatePromoted: `False`
- policyOnlyPromoted: `False`
- readinessMoved: `False`
- referenceArtifactAdded: `False`
- referenceProvenanceAdded: `False`
- renderArtifactsAdded: `False`
- routeEvidenceArtifactAdded: `False`
- supportClaimAdded: `False`
- thresholdChanged: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_offset_route_evidence_gate.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-offset-route-evidence-gate-pycache python3 -m py_compile scripts/m91_image_filter_offset_route_evidence_gate.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetRouteEvidenceGate`
- `rtk git diff --check`

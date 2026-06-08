# M91 OffsetImageFilterGM Render Diff Performance Gate

Status: blocked-until-row-specific-render-diff-stat-performance-exists

This report keeps `M91-IF-3A-RENDER` non-promotional. `OffsetImageFilterGM` has no row-specific CPU/WebGPU render outputs, diff/stat payloads, or performance evidence yet, so support evaluation remains blocked.

## Row

- Row ID: `skia-gm-offsetimagefilter`
- Source GM: `OffsetImageFilterGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `image-filter.offset.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Render Diff Performance Boundary

- Ready for diff computation: `False`
- Ready for performance measurement: `False`
- Ready for support evaluation: `False`
- Reason: OffsetImageFilterGM has graph and ownership requirements only. Row-specific CPU/WebGPU render outputs, diff/stat payloads, and performance evidence are absent, so no visual support or performance claim can be evaluated.

## Required Render/Diff/Performance Artifacts

- `CPU render artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu.png`
- `WebGPU render artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu.png`
- `CPU diff artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu-diff.png`
- `WebGPU diff artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu-diff.png`
- `CPU/GPU diff/stat artifact`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/stats.json`
- `performance impact evidence`: `not-generated` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/performance.json`

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
- performanceGatePromotions: `0`
- presentEvidenceItemsStill: `2`
- readinessDelta: `0.0`
- referenceArtifactsAdded: `0`
- referenceProvenanceArtifactsAdded: `0`
- renderArtifactsAdded: `0`
- renderDiffPerfGates: `1`
- requiredRenderDiffPerfArtifacts: `6`
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
- historicalReferencePromoted: `False`
- performanceArtifactsAdded: `False`
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

- `rtk python3 scripts/m91_image_filter_offset_render_diff_perf_gate.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-offset-render-diff-perf-gate-pycache python3 -m py_compile scripts/m91_image_filter_offset_render_diff_perf_gate.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetRenderDiffPerfGate`
- `rtk git diff --check`

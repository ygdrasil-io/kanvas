# M91 OffsetImageFilterGM Readiness Recap

Status: not-ready-for-support-evaluation

This recap closes the current `M91-IF-3A` evidence package without promoting support. `OffsetImageFilterGM` remains expected-unsupported until row-specific reference, provenance, route, render, diff/stat, and performance artifacts exist.

## Row

- Row ID: `skia-gm-offsetimagefilter`
- Source GM: `OffsetImageFilterGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `image-filter.offset.row-specific-artifacts-required`

## Evidence State

- Required evidence items: `11`
- Present evidence items: `2`
- Missing evidence items: `9`

## Gates

- `M91-IF-3A-REF`: `reference-package-plan-written-no-new-rendering-support` at `reports/wgsl-pipeline/m91-image-filter-offset-reference-package-plan/summary.json`
- `M91-IF-3A-PROV`: `blocked-until-row-specific-reference-provenance-exists` at `reports/wgsl-pipeline/m91-image-filter-offset-reference-provenance-gate/summary.json`
- `M91-IF-3A-ROUTE`: `blocked-until-row-specific-route-evidence-can-use-fallback-none` at `reports/wgsl-pipeline/m91-image-filter-offset-route-evidence-gate/summary.json`
- `M91-IF-3A-RENDER`: `blocked-until-row-specific-render-diff-stat-performance-exists` at `reports/wgsl-pipeline/m91-image-filter-offset-render-diff-perf-gate/summary.json`

## Readiness Decision

- Ready for support evaluation: `False`
- Ready for promotion: `False`
- Next recommended ticket: `M91-IF-3B`
- Scope: Start the next M91 image-filter policy-only row, or generate real OffsetImageFilterGM reference/provenance/route/render/diff/perf artifacts before any support evaluation.
- Reason: OffsetImageFilterGM has graph and ownership evidence only. Reference, provenance, fallbackReason=none routes, render, diff/stat, and performance artifacts remain absent.

## Missing Support Outputs

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
- gatesSummarized: `4`
- missingEvidenceItemsStill: `9`
- newSupportClaims: `0`
- presentEvidenceItemsStill: `2`
- readinessDelta: `0.0`
- recapReports: `1`
- supportOutputsAdded: `0`
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

- `rtk python3 scripts/m91_image_filter_offset_readiness_recap.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-offset-readiness-recap-pycache python3 -m py_compile scripts/m91_image_filter_offset_readiness_recap.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetReadinessRecap`
- `rtk git diff --check`

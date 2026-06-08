# M91 ImageMakeWithFilterGM Evidence Intake

Status: blocked-by-missing-row-specific-evidence

This report starts `M91-IF-3B` for `skia-gm-imagemakewithfilter`. It aggregates existing refusal and policy-only route evidence, and keeps support evaluation blocked until row-specific graph, ownership, reference, route, render, diff/stat, and performance artifacts exist.

## Row

- Row ID: `skia-gm-imagemakewithfilter`
- Source GM: `ImageMakeWithFilterGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `image-filter.imagemakewithfilter.row-specific-artifacts-required`

## Counters

- dashboardPromotions: `0`
- missingEvidenceItems: `11`
- newSupportClaims: `0`
- nonPromotionalSignals: `6`
- presentEvidenceItems: `0`
- readinessDelta: `0.0`
- requiredEvidenceItems: `11`
- thresholdChanges: `0`
- validatedNonPromotionalEvidenceItems: `0`

## Required Evidence

- `row-specific graph dump`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/graph.json`
- `intermediate texture ownership`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/intermediate-ownership.json`
- `row-specific Skia/reference artifact`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/skia.png`
- `CPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/route-cpu.json`
- `WebGPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/route-gpu.json`
- `CPU/GPU render artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/cpu.png`
- `CPU/GPU render artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/gpu.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/cpu-diff.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/gpu-diff.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/stats.json`
- `performance impact evidence`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/performance.json`

## Non-Promotional Signals

- `FOR-470 row-specific refusal`: `reports/wgsl-pipeline/2026-06-08-for-470-skia-gm-imagemakewithfilter-evidence.md`; promotional=`False`
- `FOR-470 structured evidence`: `reports/wgsl-pipeline/scenes/generated/for470-skia-gm-imagemakewithfilter-evidence.json`; promotional=`False`
- `CPU expected-unsupported route diagnostic`: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-imagemakewithfilter/route-cpu.json`; promotional=`False`
- `WebGPU expected-unsupported route diagnostic`: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-imagemakewithfilter/route-gpu.json`; promotional=`False`
- `ImageMakeWithFilterGM Kotlin source`: `skia-integration-tests/src/main/kotlin/org/skia/tests/ImageMakeWithFilterGM.kt`; promotional=`False`
- `historical skia similarity` (`ImageMakeWithFilterGM=84.35382962588474`): `skia-integration-tests/test-similarity-scores.properties`; promotional=`False`

## Next Recommended Ticket

- ID: `M91-IF-3B-GRAPH`
- Scope: Produce row-specific ImageMakeWithFilterGM graph dump and intermediate ownership requirements before any reference, route, render, diff/stat, performance, or support evaluation.
- Support claim allowed: `False`
- Promotion allowed without evidence: `False`

## Non-Claims

- belowThresholdCountedAsProductionGap: `False`
- boundedM61M89EvidenceInherited: `False`
- broadImageFilterDAGSupport: `False`
- cpuReadbackFallbackAdded: `False`
- cropPrepassSupport: `False`
- dashboardPromoted: `False`
- dynamicSkSLCompiler: `False`
- dynamicSkSLIR: `False`
- dynamicSkSLVM: `False`
- ganeshPort: `False`
- genericImageFilterDagCompiler: `False`
- graphitePort: `False`
- imagemakewithfilterEvidenceInherited: `False`
- layerPrepassSupport: `False`
- picturePrepassSupport: `False`
- policyOnlyPromoted: `False`
- readinessMoved: `False`
- supportClaimAdded: `False`
- thresholdChanged: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_imagemakewithfilter_evidence_intake.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-imagemakewithfilter-intake-pycache python3 -m py_compile scripts/m91_image_filter_imagemakewithfilter_evidence_intake.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterImageMakeWithFilterEvidenceIntake`
- `rtk git diff --check`

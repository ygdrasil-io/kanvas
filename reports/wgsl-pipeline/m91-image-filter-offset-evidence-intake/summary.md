# M91 Image Filter Offset Evidence Intake

Status: blocked-by-missing-row-specific-evidence

This report materializes `M91-IF-3A` for `skia-gm-offsetimagefilter`. It inventories current evidence and keeps support evaluation blocked until row-specific graph, ownership, reference, CPU/WebGPU route, render, diff/stat, and performance artifacts exist.

## Row

- Row ID: `skia-gm-offsetimagefilter`
- Source GM: `OffsetImageFilterGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `image-filter.offset.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Counters

- Required evidence items: `11`
- Present evidence items: `0`
- Missing evidence items: `11`
- Validated non-promotional evidence items: `0`
- Historical signals: `8`
- Promotional historical signals: `0`
- New support claims: `0`
- Readiness delta: `0.0`
- Dashboard promotions: `0`
- Threshold changes: `0`

## Required Evidence

- `row-specific graph dump`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/graph.json`; present=`False` promotional=`False`
- `intermediate texture ownership`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/intermediate-ownership.json`; present=`False` promotional=`False`
- `row-specific Skia/reference artifact`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/skia.png`; present=`False` promotional=`False`
- `CPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/route-cpu.json`; present=`False` promotional=`False`
- `WebGPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/route-gpu.json`; present=`False` promotional=`False`
- `CPU/GPU render artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu.png`; present=`False` promotional=`False`
- `CPU/GPU render artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu.png`; present=`False` promotional=`False`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu-diff.png`; present=`False` promotional=`False`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu-diff.png`; present=`False` promotional=`False`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/stats.json`; present=`False` promotional=`False`
- `performance impact evidence`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/performance.json`; present=`False` promotional=`False`

## Historical Signals

- `FOR-468 row-specific refusal`: `reports/wgsl-pipeline/2026-06-06-for-468-skia-gm-offsetimagefilter-evidence.md`; promotional=`False`. FOR-468 formalizes the expected-unsupported refusal and missing row-specific artifacts.
- `FOR-468 structured evidence`: `reports/wgsl-pipeline/scenes/generated/for468-skia-gm-offsetimagefilter-evidence.json`; promotional=`False`. Structured refusal evidence keeps reference, CPU, GPU, and diff/stat unavailable.
- `D51 row-specific precision evidence`: `reports/wgsl-pipeline/2026-06-06-d51-4-offsetimagefilter-row-specific-evidence.md`; promotional=`False`. D51 narrows the missing artifact contract without promoting OffsetImageFilterGM.
- `OffsetImageFilterGM Kotlin source`: `skia-integration-tests/src/main/kotlin/org/skia/tests/OffsetImageFilterGM.kt`; promotional=`False`. A source port is provenance only; it is not route, render, diff/stat, or performance evidence.
- `OffsetImageFilterGM historical test`: `skia-integration-tests/src/test/kotlin/org/skia/tests/OffsetImageFilterTest.kt`; promotional=`False`. Historical CPU similarity coverage is not M91 row-specific CPU/WebGPU route evidence.
- `historical skia similarity` (`OffsetImageFilterGM=84.515`): `skia-integration-tests/test-similarity-scores.properties`; promotional=`False`. A historical below-threshold similarity signal is not counted as a production feature gap or support proof.
- `historical cpu-raster similarity` (`OffsetImageFilterGM=84.505`): `cpu-raster/test-similarity-scores.properties`; promotional=`False`. Mirrored historical score only preserves provenance.
- `historical kanvas-skia similarity` (`OffsetImageFilterGM=84.505`): `kanvas-skia/test-similarity-scores.properties`; promotional=`False`. Mirrored historical score only preserves provenance.

## Upstream Readiness State

- Active next recommended ticket: `M91-IF-3A`
- Active next recommended row: `skia-gm-offsetimagefilter`
- This intake is out of order: `False`
- Reason: M91-IF-3A is the active candidate-readiness recommendation; this intake inventories evidence and remains non-promotional.

## Next Recommended Ticket

- ID: `M91-IF-3A-REF`
- Scope: Produce row-specific OffsetImageFilterGM graph dump, intermediate ownership, Skia/reference, CPU/WebGPU fallbackReason=none routes, render, diff/stat, and performance artifacts before any support evaluation.
- Support claim allowed: `False`
- Promotion allowed without evidence: `False`

## Support Guard

- adjacentSimpleOffsetEvidenceInherited: `False`
- arbitraryLayerPrepass: `False`
- belowThresholdCountedAsProductionGap: `False`
- cpuReadbackFallbackAdded: `False`
- cropImageFilterDagSupport: `False`
- dashboardPromoted: `False`
- dynamicSkSLCompiler: `False`
- dynamicSkSLIR: `False`
- dynamicSkSLVM: `False`
- ganeshPort: `False`
- generalImageFilterDagSupport: `False`
- genericImageFilterDagCompiler: `False`
- graphitePort: `False`
- picturePrepassSupport: `False`
- policyOnlyPromoted: `False`
- readinessMoved: `False`
- requiredSmokeCandidateAllowed: `False`
- supportClaimAdded: `False`
- thresholdChanged: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_offset_evidence_intake.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetEvidenceIntake`
- `rtk git diff --check`

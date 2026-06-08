# M90 Path AA DashCubics Evidence Intake

Status: blocked by missing row-specific evidence

This report materializes the `M90-PAA-3I` intake for `skia-gm-dashcubics`. It records the active policy-only refusal, inventories direct historical DashCubicsGM signals as non-promotional, and keeps support evaluation blocked until row-specific artifacts exist.

## Row

- Row ID: `skia-gm-dashcubics`
- Source GM: `DashCubicsGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `coverage.dash-cubic.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Counters

- Required evidence items: `10`
- Present evidence items: `0`
- Missing evidence items: `10`
- Historical signals: `7`
- Promotional historical signals: `0`
- New support claims: `0`
- Readiness delta: `0.0`

## Required Evidence

- `row-specific Skia reference`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashcubics/skia.png`
- `CPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashcubics/route-cpu.json`
- `WebGPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashcubics/route-gpu.json`
- `CPU/GPU rendered artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashcubics/cpu.png`
- `CPU/GPU rendered artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashcubics/gpu.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashcubics/cpu-diff.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashcubics/gpu-diff.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashcubics/stats.json`
- `performance impact evidence`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashcubics/cpu-performance.json`
- `performance impact evidence`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashcubics/gpu-performance.json`

## Historical Signals

- `historical-cpu-test`: `skia-integration-tests/src/test/kotlin/org/skia/tests/DashCubicsTest.kt`; promotional=`False`. Historical CPU DashCubicsGM test is not a M90 row-specific CPU/WebGPU artifact bundle and does not update the M89 registry row.
- `historical-gm-source-port`: `skia-integration-tests/src/main/kotlin/org/skia/tests/DashCubicsGM.kt`; promotional=`False`. The existing Kotlin GM source is a historical port signal only; it is not row-specific route, diff/stat, or performance evidence.
- `historical-crossbackend-test`: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/DashCubicsCrossBackendTest.kt`; promotional=`False`. Historical cross-backend floors are provenance only; they do not replace current skia/cpu/gpu/diff/stat/perf evidence.
- `historical-gpu-similarity-floor` (`DashCubicsGM-gpu=92.69; DashCubicsGM-raster=91.07`): `gpu-raster/test-similarity-scores-webgpu.properties`; promotional=`False`. A historical GPU score is not a support claim and does not override the expected-unsupported row policy.
- `historical-cpu-similarity-floor` (`DashCubicsGM=89.86897880539499`): `skia-integration-tests/test-similarity-scores.properties`; promotional=`False`. A historical CPU score is not a M90 support promotion or a replacement for row-specific route artifacts.
- `historical-cpu-raster-score-mirror` (`DashCubicsGM=89.86897880539499`): `cpu-raster/test-similarity-scores.properties`; promotional=`False`. A mirrored historical score file is consistency evidence only, not a CPU/WebGPU promotion artifact.
- `historical-kanvas-skia-score-mirror` (`DashCubicsGM=89.86897880539499`): `kanvas-skia/test-similarity-scores.properties`; promotional=`False`. A mirrored kanvas-skia score is consistency evidence only and does not clear the row-specific M90 evidence contract.

## Upstream Readiness State

- Active next recommended ticket: `M90-PAA-3A`
- Active next recommended row: `skia-gm-hairlines`
- This intake is out of order: `True`
- Reason: M90-PAA-3I is materialized as a conservative evidence intake for the ninth-ranked candidate only; it does not supersede the active M90-PAA-3A recommendation.

## Next Recommended Ticket

- ID: `M90-PAA-3I-REF`
- Scope: Produce row-specific DashCubicsGM Skia reference plus CPU/WebGPU fallbackReason=none route, render, diff/stat, and performance artifacts before any support evaluation.
- Support claim allowed: `False`
- Promotion allowed without evidence: `False`

## Support Guard

- supportClaimAdded: `False`
- readinessMoved: `False`
- policyOnlyPromoted: `False`
- thresholdChanged: `False`
- edgeBudgetChanged: `False`
- belowThresholdCountedAsProductionGap: `False`
- broadPathAASupport: `False`
- broadDashSupport: `False`
- broadHairlineSupport: `False`
- broadStrokeSupport: `False`
- ganeshPort: `False`
- graphitePort: `False`

## Validation Commands

- `rtk python3 scripts/m90_path_aa_dashcubics_evidence_intake.py`
- `rtk ./gradlew --no-daemon pipelineM90PathAaDashCubicsEvidenceIntake`
- `rtk git diff --check`

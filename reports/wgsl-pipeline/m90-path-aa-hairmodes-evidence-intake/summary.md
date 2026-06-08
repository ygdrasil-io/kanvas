# M90 Path AA HairModes Evidence Intake

Status: blocked by missing row-specific evidence

This report materializes the `M90-PAA-3F` intake for `skia-gm-hairmodes`. It records the active policy-only refusal, inventories direct historical HairModesGM signals as non-promotional, and keeps support evaluation blocked until row-specific artifacts exist.

## Row

- Row ID: `skia-gm-hairmodes`
- Source GM: `HairModesGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `coverage.hairmode.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Counters

- Required evidence items: `10`
- Present evidence items: `0`
- Missing evidence items: `10`
- Historical signals: `5`
- Promotional historical signals: `0`
- New support claims: `0`
- Readiness delta: `0.0`

## Required Evidence

- `row-specific Skia reference`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairmodes/skia.png`
- `CPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairmodes/route-cpu.json`
- `WebGPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairmodes/route-gpu.json`
- `CPU/GPU rendered artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairmodes/cpu.png`
- `CPU/GPU rendered artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairmodes/gpu.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairmodes/cpu-diff.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairmodes/gpu-diff.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairmodes/stats.json`
- `performance impact evidence`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairmodes/cpu-performance.json`
- `performance impact evidence`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairmodes/gpu-performance.json`

## Historical Signals

- `historical-cpu-test`: `skia-integration-tests/src/test/kotlin/org/skia/tests/Round13Test.kt`; promotional=`False`. Historical CPU HairModesGM test is not a M90 row-specific CPU/WebGPU artifact bundle and does not update the M89 registry row.
- `historical-gm-source-port`: `skia-integration-tests/src/main/kotlin/org/skia/tests/HairModesGM.kt`; promotional=`False`. The existing Kotlin GM source is a historical port signal only; it is not row-specific route, diff/stat, or performance evidence.
- `historical-cpu-similarity-floor` (`HairModesGM=72.19368489583333`): `skia-integration-tests/test-similarity-scores.properties`; promotional=`False`. A historical CPU score is not a support claim; tolerance-only status is not counted as a production missing feature.
- `historical-cpu-raster-score-mirror` (`HairModesGM=72.19368489583333`): `cpu-raster/test-similarity-scores.properties`; promotional=`False`. A mirrored historical score file is consistency evidence only, not a CPU/WebGPU promotion artifact.
- `historical-kanvas-skia-score-mirror` (`HairModesGM=72.19368489583333`): `kanvas-skia/test-similarity-scores.properties`; promotional=`False`. A mirrored kanvas-skia score is consistency evidence only and does not clear the row-specific M90 evidence contract.

## Upstream Readiness State

- Active next recommended ticket: `M90-PAA-3A`
- Active next recommended row: `skia-gm-hairlines`
- This intake is out of order: `True`
- Reason: M90-PAA-3F is materialized as a conservative evidence intake for the sixth-ranked candidate only; it does not supersede the active M90-PAA-3A recommendation.

## Next Recommended Ticket

- ID: `M90-PAA-3F-REF`
- Scope: Produce row-specific HairModesGM Skia reference plus CPU/WebGPU fallbackReason=none route, render, diff/stat, and performance artifacts before any support evaluation.
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

- `rtk python3 scripts/m90_path_aa_hairmodes_evidence_intake.py`
- `rtk ./gradlew --no-daemon pipelineM90PathAaHairModesEvidenceIntake`
- `rtk git diff --check`

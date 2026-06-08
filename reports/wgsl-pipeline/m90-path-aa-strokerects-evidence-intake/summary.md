# M90 Path AA StrokeRects Evidence Intake

Status: blocked by missing row-specific evidence

This report materializes the `M90-PAA-3E` intake for `skia-gm-strokerects`. It records the active policy-only refusal, inventories direct historical StrokeRectsGM signals as non-promotional, and keeps support evaluation blocked until row-specific artifacts exist.

## Row

- Row ID: `skia-gm-strokerects`
- Source GM: `StrokeRectsGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `coverage.stroke-rects.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Counters

- Required evidence items: `10`
- Present evidence items: `0`
- Missing evidence items: `10`
- Historical signals: `8`
- Promotional historical signals: `0`
- New support claims: `0`
- Readiness delta: `0.0`

## Required Evidence

- `row-specific Skia reference`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/skia.png`
- `CPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/route-cpu.json`
- `WebGPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/route-gpu.json`
- `CPU/GPU rendered artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/cpu.png`
- `CPU/GPU rendered artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/gpu.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/cpu-diff.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/gpu-diff.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/stats.json`
- `performance impact evidence`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/cpu-performance.json`
- `performance impact evidence`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/gpu-performance.json`

## Historical Signals

- `historical-cpu-test`: `skia-integration-tests/src/test/kotlin/org/skia/tests/StrokeRectsTest.kt`; promotional=`False`. Historical CPU StrokeRectsGM test is not a M90 row-specific CPU/WebGPU artifact bundle and does not update the M89 registry row.
- `historical-gm-source-port`: `skia-integration-tests/src/main/kotlin/org/skia/tests/StrokeRectsGM.kt`; promotional=`False`. The existing Kotlin GM source is a historical port signal only; it is not row-specific route, diff/stat, or performance evidence.
- `historical-webgpu-test`: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeRectsWebGpuTest.kt`; promotional=`False`. Historical WebGPU StrokeRectsGM test is not a M90 row-specific artifact bundle and does not update route status.
- `historical-crossbackend-test`: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/StrokeRectsCrossBackendTest.kt`; promotional=`False`. Historical cross-backend floors are provenance only; they do not replace current skia/cpu/gpu/diff/stat/perf evidence.
- `historical-gpu-similarity-floor` (`StrokeRectsGM=99.86`): `gpu-raster/test-similarity-scores-webgpu.properties`; promotional=`False`. A historical GPU score is not a support claim and does not override the expected-unsupported row policy.
- `historical-cpu-similarity-floor` (`StrokeRectsGM=92.0928125`): `skia-integration-tests/test-similarity-scores.properties`; promotional=`False`. A historical CPU score is not a M90 support promotion or a replacement for row-specific route artifacts.
- `historical-cpu-raster-score-mirror` (`StrokeRectsGM=92.0928125`): `cpu-raster/test-similarity-scores.properties`; promotional=`False`. A mirrored historical score file is consistency evidence only, not a CPU/WebGPU promotion artifact.
- `historical-kanvas-skia-score-mirror` (`StrokeRectsGM=92.0928125`): `kanvas-skia/test-similarity-scores.properties`; promotional=`False`. A mirrored kanvas-skia score is consistency evidence only and does not clear the row-specific M90 evidence contract.

## Upstream Readiness State

- Active next recommended ticket: `M90-PAA-3A`
- Active next recommended row: `skia-gm-hairlines`
- This intake is out of order: `True`
- Reason: M90-PAA-3E is materialized as a conservative evidence intake for the fifth-ranked candidate only; it does not supersede the active M90-PAA-3A recommendation.

## Next Recommended Ticket

- ID: `M90-PAA-3E-REF`
- Scope: Produce row-specific StrokeRectsGM Skia reference plus CPU/WebGPU fallbackReason=none route, render, diff/stat, and performance artifacts before any support evaluation.
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

- `rtk python3 scripts/m90_path_aa_strokerects_evidence_intake.py`
- `rtk ./gradlew --no-daemon pipelineM90PathAaStrokeRectsEvidenceIntake`
- `rtk git diff --check`

# M90 Path AA ThinStrokedRects Evidence Intake

Status: blocked by missing row-specific evidence

This report materializes the `M90-PAA-3C` intake for `skia-gm-thinstrokedrects`. It records the active policy-only refusal, inventories direct historical ThinStrokedRectsGM signals as non-promotional, and keeps support evaluation blocked until row-specific artifacts exist.

## Row

- Row ID: `skia-gm-thinstrokedrects`
- Source GM: `ThinStrokedRectsGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `coverage.thin-stroked-rects.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Counters

- Required evidence items: `10`
- Present evidence items: `0`
- Missing evidence items: `10`
- Historical signals: `4`
- Promotional historical signals: `0`
- New support claims: `0`
- Readiness delta: `0.0`

## Required Evidence

- `row-specific Skia reference`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-thinstrokedrects/skia.png`
- `CPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-thinstrokedrects/route-cpu.json`
- `WebGPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-thinstrokedrects/route-gpu.json`
- `CPU/GPU rendered artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-thinstrokedrects/cpu.png`
- `CPU/GPU rendered artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-thinstrokedrects/gpu.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-thinstrokedrects/cpu-diff.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-thinstrokedrects/gpu-diff.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-thinstrokedrects/stats.json`
- `performance impact evidence`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-thinstrokedrects/cpu-performance.json`
- `performance impact evidence`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-thinstrokedrects/gpu-performance.json`

## Historical Signals

- `historical-webgpu-test`: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ThinStrokedRectsWebGpuTest.kt`; promotional=`False`. Historical WebGPU ThinStrokedRectsGM test is not a M90 row-specific artifact bundle and does not update the M89 registry row.
- `historical-crossbackend-test`: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/ThinStrokedRectsCrossBackendTest.kt`; promotional=`False`. Historical cross-backend signal is not the current row-specific skia/cpu/gpu/diff/stat/perf evidence required by M90-PAA-3.
- `historical-gpu-similarity-floor` (`ThinStrokedRectsGM=94.21`): `gpu-raster/test-similarity-scores-webgpu.properties`; promotional=`False`. A historical score is not a support claim; tolerance-only status is not counted as a production missing feature.
- `historical-cpu-similarity-floor` (`ThinStrokedRectsGM=88.86848958333333`): `skia-integration-tests/test-similarity-scores.properties`; promotional=`False`. A historical CPU score is not a M90 support promotion or a replacement for row-specific route artifacts.

## Upstream Readiness State

- Active next recommended ticket: `M90-PAA-3A`
- Active next recommended row: `skia-gm-hairlines`
- This intake is out of order: `True`
- Reason: M90-PAA-3C is materialized as a conservative evidence intake for the third-ranked candidate only; it does not supersede the active M90-PAA-3A recommendation.

## Next Recommended Ticket

- ID: `M90-PAA-3C-REF`
- Scope: Produce row-specific ThinStrokedRectsGM Skia reference plus CPU/WebGPU fallbackReason=none route, render, diff/stat, and performance artifacts before any support evaluation.
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

- `rtk python3 scripts/m90_path_aa_thinstrokedrects_evidence_intake.py`
- `rtk ./gradlew --no-daemon pipelineM90PathAaThinStrokedRectsEvidenceIntake`
- `rtk git diff --check`

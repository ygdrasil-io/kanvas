# M90 Path AA StrokeRect Evidence Intake

Status: blocked by missing row-specific evidence

This report materializes the `M90-PAA-3B` intake for `skia-gm-strokerect`. It records the active policy-only refusal, inventories historical StrokeRectGM and stroke-primitive signals as non-promotional, and keeps support evaluation blocked until row-specific artifacts exist.

## Row

- Row ID: `skia-gm-strokerect`
- Source GM: `StrokeRectGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `coverage.stroke-rect.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Counters

- Required evidence items: `10`
- Present evidence items: `0`
- Missing evidence items: `10`
- Historical signals: `6`
- Promotional historical signals: `0`
- New support claims: `0`
- Readiness delta: `0.0`

## Required Evidence

- `row-specific Skia reference`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerect/skia.png`
- `CPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerect/route-cpu.json`
- `WebGPU route evidence with fallbackReason=none`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerect/route-gpu.json`
- `CPU/GPU rendered artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerect/cpu.png`
- `CPU/GPU rendered artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerect/gpu.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerect/cpu-diff.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerect/gpu-diff.png`
- `CPU/GPU diff/stat artifacts`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerect/stats.json`
- `performance impact evidence`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerect/cpu-performance.json`
- `performance impact evidence`: `missing` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerect/gpu-performance.json`

## Historical Signals

- `historical-webgpu-test`: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeRectWebGpuTest.kt`; promotional=`False`. Historical WebGPU StrokeRectGM test is not a M90 row-specific artifact bundle and does not update the M89 registry row.
- `historical-crossbackend-test`: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/StrokeRectCrossBackendTest.kt`; promotional=`False`. Historical cross-backend signal is not the current row-specific skia/cpu/gpu/diff/stat/perf evidence required by M90-PAA-3.
- `historical-gpu-similarity-floor` (`StrokeRectGM=95.96`): `gpu-raster/test-similarity-scores-webgpu.properties`; promotional=`False`. A historical score is not a support claim; tolerance-only status is not counted as a production missing feature.
- `historical-cpu-similarity-floor` (`StrokeRectGM=93.63899613899615`): `skia-integration-tests/test-similarity-scores.properties`; promotional=`False`. A historical CPU score is not a M90 support promotion or a replacement for row-specific route artifacts.
- `historical-stroke-primitive-gpu-representative-artifact`: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/route-gpu.json`; promotional=`False`. The existing stroke-primitive artifact is keyed to path-aa-stroke-primitive with StrokeCircleGM representative; it is not the skia-gm-strokerect row-specific M90 bundle.
- `historical-stroke-primitive-cpu-representative-artifact`: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/route-cpu.json`; promotional=`False`. The existing CPU stroke-primitive artifact is also keyed to path-aa-stroke-primitive with StrokeCircleGM representative; it is provenance only for this intake.

## Upstream Readiness State

- Active next recommended ticket: `M90-PAA-3A`
- Active next recommended row: `skia-gm-hairlines`
- This intake is out of order: `True`
- Reason: M90-PAA-3B is materialized as a conservative evidence intake for the second-ranked candidate only; it does not supersede the active M90-PAA-3A recommendation.

## Next Recommended Ticket

- ID: `M90-PAA-3B-REF`
- Scope: Produce row-specific StrokeRectGM Skia reference plus CPU/WebGPU fallbackReason=none route, render, diff/stat, and performance artifacts before any support evaluation.
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

- `rtk python3 scripts/m90_path_aa_strokerect_evidence_intake.py`
- `rtk ./gradlew --no-daemon pipelineM90PathAaStrokeRectEvidenceIntake`
- `rtk git diff --check`

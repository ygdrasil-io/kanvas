# M90 Path AA Hairlines Evidence Intake

Status: row-specific-evidence-present-non-promotional

This report materializes the `M90-PAA-3A` intake for `skia-gm-hairlines`. It records the active refusal state, inventories historical HairlinesGM signals as non-promotional, and keeps support evaluation blocked until row-specific artifacts exist.

## Row

- Row ID: `skia-gm-hairlines`
- Source GM: `HairlinesGM`
- Status: `expected-unsupported`
- Support claim: `False`
- Policy-only: `True`
- Fallback: `coverage.hairline.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`

## Counters

- Required evidence items: `10`
- Present evidence items: `10`
- Missing evidence items: `0`
- Validated non-promotional evidence items: `10`
- Historical signals: `7`
- Promotional historical signals: `0`
- New support claims: `0`
- Readiness delta: `0.0`

## Required Evidence

- `row-specific Skia reference`: `present-non-promotional` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/skia.png`; present=`True` promotional=`False`
- `CPU route evidence with fallbackReason=none`: `present-non-promotional` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/route-cpu.json`; present=`True` promotional=`False`
- `WebGPU route evidence with fallbackReason=none`: `present-non-promotional` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/route-gpu.json`; present=`True` promotional=`False`
- `CPU/GPU rendered artifacts`: `present-non-promotional` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/cpu.png`; present=`True` promotional=`False`
- `CPU/GPU rendered artifacts`: `present-non-promotional` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/gpu.png`; present=`True` promotional=`False`
- `CPU/GPU diff/stat artifacts`: `present-non-promotional` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/cpu-diff.png`; present=`True` promotional=`False`
- `CPU/GPU diff/stat artifacts`: `present-non-promotional` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/gpu-diff.png`; present=`True` promotional=`False`
- `CPU/GPU diff/stat artifacts`: `present-non-promotional` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/stats.json`; present=`True` promotional=`False`
- `performance impact evidence`: `present-non-promotional` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/cpu-performance.json`; present=`True` promotional=`False`
- `performance impact evidence`: `present-non-promotional` at `reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/gpu-performance.json`; present=`True` promotional=`False`

## Historical Signals

- `historical-cpu-test`: `skia-integration-tests/src/test/kotlin/org/skia/tests/Round8Test.kt`; promotional=`False`. Historical CPU HairlinesGM test is not a M90 row-specific CPU/WebGPU artifact bundle and does not update the M89 registry row.
- `historical-gm-source-port`: `skia-integration-tests/src/main/kotlin/org/skia/tests/HairlinesGM.kt`; promotional=`False`. The existing Kotlin GM source is a historical port signal only; it is not row-specific route, diff/stat, or performance evidence.
- `historical-crossbackend-test`: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/HairlinesCrossBackendTest.kt`; promotional=`False`. Historical cross-backend floors are provenance only; they do not replace current skia/cpu/gpu/diff/stat/perf evidence.
- `historical-gpu-similarity-floor` (`HairlinesGM=98.97`): `gpu-raster/test-similarity-scores-webgpu.properties`; promotional=`False`. A historical similarity score is not a support claim; below-threshold/tolerance-only status is not counted as a production missing feature.
- `historical-cpu-similarity-floor` (`HairlinesGM=97.678016`): `skia-integration-tests/test-similarity-scores.properties`; promotional=`False`. A historical CPU score is not a M90 support promotion or a replacement for row-specific route artifacts.
- `historical-cpu-raster-score-mirror` (`HairlinesGM=97.678016`): `cpu-raster/test-similarity-scores.properties`; promotional=`False`. A mirrored historical score file is consistency evidence only, not a CPU/WebGPU promotion artifact.
- `historical-kanvas-skia-score-mirror` (`HairlinesGM=97.690624`): `kanvas-skia/test-similarity-scores.properties`; promotional=`False`. A mirrored kanvas-skia score is consistency evidence only and does not clear the row-specific M90 evidence contract.

## Upstream Readiness State

- Active next recommended ticket: `M90-PAA-3A`
- Active next recommended row: `skia-gm-hairlines`
- This intake is out of order: `False`
- Reason: M90-PAA-3A remains the active candidate-readiness recommendation, but this intake is still non-promotional and only inventories missing row-specific evidence.

## Next Recommended Ticket

- ID: `M90-PAA-3A-REF`
- Scope: Produce row-specific HairlinesGM Skia reference plus CPU/WebGPU fallbackReason=none route, render, diff/stat, and performance artifacts before any support evaluation.
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

- `rtk python3 scripts/m90_path_aa_hairlines_evidence_intake.py`
- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.HairlinesSceneCaptureTest`
- `rtk ./gradlew --no-daemon pipelineM90PathAaHairlinesEvidenceIntake`
- `rtk git diff --check`

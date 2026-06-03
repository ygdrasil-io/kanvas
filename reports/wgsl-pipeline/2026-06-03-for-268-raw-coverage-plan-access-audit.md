# FOR-268 Raw Coverage Plan Access Audit

Date: 2026-06-03

Decision: `RAW_COVERAGE_CAPTURE_NOT_AVAILABLE`

FOR-268 audited whether Kanvas can expose raw CPU/WebGPU coverage for bounded
round cap/join cells without changing production rendering. It cannot do that
from the current implementation. Existing FOR-266/FOR-267 evidence reaches
rendered bytes, route diagnostics, path facts, and byte-derived coverage
proxies; it does not expose a raw coverage plane on either backend.

Preserved production decision: `KEEP_DIAGNOSTIC`.

Preserved refusal: `coverage.stroke-cap-join-visual-parity-below-threshold`.

Preserved Crop fallback: `image-filter.crop-input-nonnull-prepass-required`.

## Artifact

- `reports/wgsl-pipeline/scenes/artifacts/raw-coverage-plan-access-audit-for268/raw-coverage-plan-access-audit-for268.json`

## Entry Point Inventory

| Entry point | Finding | Raw coverage access |
|---|---|---|
| `CoveragePlan` | Semantic coverage descriptor only. `PathCoverage` records `fillType`, `aa`, and `inverse`. | Not available |
| `PathCoverage` | Lowers to `CoverageBackendStrategy.CpuSpanPath`, but no span alpha/cell values are exposed. | Not available |
| `GeometryCoverageMigrationHarness` | Can consume a test-supplied `coverageAlpha` array, but does not extract CPU stroke cap/join coverage. | CPU test input only |
| CPU raster | Current FOR evidence reads CPU oracle image bytes; no public raw stroke coverage snapshot exists. | Not available |
| `aa_stencil_cover.wgsl` | Computes `supersampled_path_cov` locally, multiplies clip coverage, then returns premul RGBA. | Shader-local only |
| `SkWebGpuDevice` | Records route diagnostics and final readback bytes. The experimental route only bypasses the refusal for diagnostics. | Not available |
| `WebGpuCoveragePlanSelector` | Records budgets, stroke style facts, route, pipeline axes, and stable refusal. | Not available |
| `kanvas.webgpu.strokeCapJoin.experimentalRender` | Diagnostic render route; no coverage output/readback channel. | Diagnostic render only |

## Why Raw Capture Is Not Available

The CPU side has no test-only extractor for the stroke outline coverage alpha
plane before paint and blend. The migration harness can compare descriptor
pixels when a caller supplies `coverageAlpha`, but it does not capture that
alpha from `SkBitmapDevice`.

The WebGPU side computes coverage inside `aa_stencil_cover.wgsl` fragment
entry points and immediately folds it into the color output. There is no
coverage-only attachment, storage buffer, or readback lane for those floats.
`SkWebGpuDevice.flush()` reads final RGBA bytes after color, coverage,
intermediate storage, blend, present, and RGBA8 readback.

FOR-267 therefore remains a byte-derived proxy audit. Its coverage proxy is
useful for localization, but it is not a raw CPU/GPU coverage-plane proof.

## Next Option

The realistic follow-up is a separate, opt-in test-only raw coverage probe:
capture CPU A8 coverage or bounded cell samples before paint, add a WebGPU
coverage-only output/readback path for the same sample coordinates, and compare
raw coverage before premul, colorspace, blend, quantization, or present. That
should be a dedicated implementation ticket because it touches raster and
shader instrumentation.

## Kotlin Test Decision

No Kotlin test was added for FOR-268. A render test today would only reproduce
FOR-266/FOR-267 byte/proxy evidence. Since the audit decision is that raw
coverage capture is not available, the useful evidence is the stable JSON
artifact plus the dedicated validator.

## Validation

```text
rtk python3 scripts/validate_for268_raw_coverage_plan_access_audit.py
rtk python3 scripts/validate_for267_round_cap_join_coverage_equivalence.py
rtk python3 scripts/validate_for266_stroke_cap_join_aa_residual.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

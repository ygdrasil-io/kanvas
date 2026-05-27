# M31 GPU Smoke Promotion Policy

Date: 2026-05-27  
Linear: GRA-78  
Milestone: M31 — GPU CI Stabilization

## Purpose

Define when a GPU inventory test can be promoted into the required
`GPU tests (macos)` smoke gate. The full inventory remains broader and
non-blocking by design.

## Promotion Checklist (all required)

1. Adapter lane proof exists:
   at least one successful run on the required macOS adapter lane
   (`GPU tests (macos)`), with no adapter skips for the candidate tests.
2. Unsupported-path diagnostics are absent:
   candidate tests must not rely on expected unsupported classifications
   (for example `coverage.edge-count-exceeded` or
   `image-filter.crop-input-nonnull-prepass-required`).
3. Similarity behavior is stable:
   no unresolved floor regression classification for the candidate
   (debug artifacts available when thresholds are close or failing).
4. Generated-shader telemetry is stable when relevant:
   `PipelineKey`/cache behavior is deterministic and validated by existing
   telemetry assertions before promotion.
5. PM-readable evidence exists:
   promotion rationale and validation evidence are present in
   `pipelineConformanceReport` and Linear ticket comments.
6. Rollback path is explicit:
   if a promoted test turns flaky, it is removed from smoke and returned to
   inventory until evidence restores promotion readiness.

## Current Smoke-Eligible Baseline

The current required smoke gate is intentionally minimal and already
promotion-eligible under this policy:

- `org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest`
- `org.skia.gpu.webgpu.PipelineKeyTelemetryTest`

These fixtures run via `:gpu-raster:gpuSmokeTest` and fail closed on skipped
adapter-dependent execution.

## Explicit Promotion Blocks (M31)

Do not promote these classes until implementation evidence lands:

- `coverage.edge-count-exceeded` (complex AA edge-budget refusals);
- `image-filter.crop-input-nonnull-prepass-required`
  (`SkImageFilters.Crop(input = nonNull)` requires render-to-texture pre-pass);
- unresolved `similarity-regression` records from the GPU inventory report.

## Evidence Sources

- CI workflow structure: `.github/workflows/test.yml`
- Smoke fixture selection: `gpu-raster/build.gradle.kts` (`gpuSmokePatterns`)
- Inventory classification artifact:
  `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md`
- PM status report:
  `build/reports/pipeline-conformance/m24-pipeline-conformance-report.md`

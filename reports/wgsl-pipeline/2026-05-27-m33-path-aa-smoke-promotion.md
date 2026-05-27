# M33 Path AA Smoke Promotion

Date: 2026-05-27
Linear: GRA-107
Parent epic: GRA-101
Milestone: M33 -- Path AA MVP Boundary

## Decision

Promote one rendered Path AA fixture to required GPU smoke:

```text
org.skia.gpu.webgpu.AnalyticAntialiasConvexWebGpuTest
```

This is a conservative promotion. It does not promote any fixture that relies
on `coverage.edge-count-exceeded`.

## Rationale

Inputs:

- GRA-105 inventory audit:
  `reports/wgsl-pipeline/2026-05-27-m33-path-aa-inventory-audit.md`
- GRA-106 edge-budget hardening:
  `reports/wgsl-pipeline/2026-05-27-m33-edge-budget-classification-hardening.md`
- M31 smoke promotion policy:
  `reports/wgsl-pipeline/2026-05-27-m31-gpu-smoke-promotion-policy.md`

Promotion checklist:

| Criterion | Result |
|---|---|
| Adapter-backed fixture | Yes. It is a WebGPU rendered GM fixture and required smoke rejects adapter skips. |
| No expected unsupported diagnostic | Yes. The fixture is not one of the 50 `coverage.edge-count-exceeded` rows in GRA-105. |
| Stable similarity behavior | Yes. It passed the post-M32 full inventory with its existing floor. |
| Minimal Path AA scope | Yes. It exercises analytic-AA convex fill without expanding to edge-budget overflow cases. |
| Rollback path | Remove the fixture from `gpuSmokePatternSpecs` and return it to full inventory if it becomes flaky. |

## Validation

```text
rtk git diff --check
```

Result: passed.

```text
rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy
```

Result: passed.

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest
```

Result: passed locally with zero adapter skips.

## Outcome

Required GPU smoke now includes:

- `org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest`
- `org.skia.gpu.webgpu.PipelineKeyTelemetryTest`
- `org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest`
- `org.skia.gpu.webgpu.AnalyticAntialiasConvexWebGpuTest`

The edge-budget refusal set remains inventory-only expected unsupported and is
not promoted.

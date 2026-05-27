# M33 Edge-Budget Classification Hardening

Date: 2026-05-27
Linear: GRA-106
Parent epic: GRA-101
Milestone: M33 -- Path AA MVP Boundary

## Goal

Ensure `coverage.edge-count-exceeded` is consistently reported as expected
unsupported inventory and cannot silently become an unclassified release
blocker or an unsafe smoke candidate.

## Changes

- Hardened `WebGpuCoveragePlanSelectorTest` to assert the exact diagnostic
  code, refusal route, and `RefuseDiagnostic(coverage.edge-count-exceeded)`
  action for AA path edge-budget overflow.
- Added a `GpuInventoryFailureReportTest` case that proves:
  - `reason=coverage.edge-count-exceeded` is classified as
    `expected-unsupported-diagnostic`;
  - diagnostic dumps containing `coverage.edge-count-exceeded` are classified
    as `expected-unsupported-diagnostic`;
  - unknown future `coverage.*` reason codes fail closed as
    `unexpected-exception`;
  - PM markdown states that `coverage.edge-count-exceeded` is not
    smoke-eligible until follow-up implementation evidence exists.
- Updated `.upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md` to
  state that edge-budget paths remain inventory-only unless support evidence
  lands.

## Validation

```text
rtk git diff --check
```

Result: passed.

```text
rtk ./gradlew --no-daemon :gpu-raster:test --tests 'org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest'
```

Result: passed.

```text
rtk ./gradlew --no-daemon :gpu-raster:test --tests 'org.skia.gpu.webgpu.tools.GpuInventoryFailureReportTest'
```

Result: passed.

```text
rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy
```

Result: passed.

## Outcome

The Path AA edge-budget reason is now guarded on both sides:

- selector diagnostics emit the stable `coverage.edge-count-exceeded` code;
- the inventory report maps that exact code to expected unsupported;
- unknown `coverage.*` codes remain release-visible as unexpected exceptions;
- refused edge-budget Path AA remains out of required smoke unless future
  implementation evidence supports promotion.

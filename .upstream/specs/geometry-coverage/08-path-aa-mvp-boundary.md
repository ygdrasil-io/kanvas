# Spec 08: Path AA MVP Boundary

Status: Draft
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Milestone: M33 -- Path AA MVP Boundary

## Purpose

Define the M33 boundary for Path AA work in the WGSL/WebGPU MVP tail.

The goal is not to make every complex AA path render through WebGPU for the
MVP. The goal is to prove that supported AA routes are adapter-backed, that
edge-budget refusals are explicit expected unsupported inventory, and that no
refused AA path can enter required GPU smoke.

## Current Baseline

The post-M32 inventory classifies the dominant Path AA breadth gap under the
stable reason:

```text
coverage.edge-count-exceeded
```

This reason is expected unsupported inventory until implementation evidence
lands for a broader WebGPU strategy. It must not be treated as a similarity
regression, an unclassified exception, or a smoke candidate.

## GRA-106 Classification Hardening

`coverage.edge-count-exceeded` is inventory-only for the MVP unless follow-up
implementation evidence lands for a broader WebGPU Path AA strategy. The
classifier must accept only the stable reason code and must fail closed for
unknown future `coverage.*` codes by leaving them in `unexpected-exception`.

Required smoke must not include a Path AA fixture whose pass condition depends
on this expected unsupported diagnostic. Selector unit tests may assert the
diagnostic itself; that is not a rendered fixture promotion.

## M33 Tickets

| Ticket | Purpose |
|---|---|
| GRA-105 | Produce the fresh post-M32 Path AA inventory audit. |
| GRA-106 | Harden edge-budget expected unsupported classification. |
| GRA-107 | Decide AA smoke promotion or document no-promotion. |
| GRA-108 | Close M33 with PM-readable evidence. |

## Acceptance Rules

M33 can be accepted when:

- Path AA inventory rows are summarized with exact category counts;
- `coverage.edge-count-exceeded` rows remain fail-closed expected unsupported
  diagnostics;
- any rendered-but-below-floor AA rows are separated from refused rows;
- smoke promotion either names one adapter-backed AA fixture or explicitly
  records no promotion for MVP;
- required GPU smoke contains no `coverage.edge-count-exceeded` candidate;
- remaining limitations have Linear follow-up ownership.

## Non-Goals

M33 does not:

- remove the WebGPU edge budget;
- implement persistent coverage atlas policy;
- solve glyph mask or text coverage ownership;
- port Ganesh or Graphite coverage strategies;
- lower similarity floors to hide AA breadth gaps.

## Validation Sources

Expected evidence sources:

- `rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest`
- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md`
- `rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy`
- `rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest`
- M33 reports under `reports/wgsl-pipeline/`

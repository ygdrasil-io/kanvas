# Spec 08: Path AA MVP Boundary

Status: Accepted
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Milestone: M33 -- Path AA MVP Boundary

## Purpose

Define the M33 boundary for Path AA work in the WGSL/WebGPU MVP tail.

The goal is not to make every complex AA path render through WebGPU for the
MVP. The goal is to prove that supported AA routes are adapter-backed, that
edge-budget refusals are explicit expected unsupported inventory, and that no
refused AA path can enter required GPU smoke.

## Acceptance Evidence

Accepted on 2026-05-27 for the M33 MVP boundary.

Evidence:

- GRA-105 / PR #1177 / `ee710a2479741d91e41a0213cd80e4dc2a6449b2`:
  post-M32 Path AA inventory audit.
- GRA-106 / PR #1178 / `d1031ad2020358ba1dd25760ec447dfcfdd7c087`:
  fail-closed edge-budget classifier hardening.
- GRA-107 / PR #1179 / `0fdfbb43553903ece9abdcbbace231ae25f29b67`:
  rendered AA smoke promotion for `AnalyticAntialiasConvexWebGpuTest`.
- GRA-108: M33 PM closeout report.

Acceptance is limited to the M33 MVP decision boundary. Broader Path AA support
for edge-budget overflow remains explicitly out of scope until follow-up
implementation evidence lands.

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

## GRA-107 Smoke Decision

M33 promotes one rendered Path AA fixture to required GPU smoke:

```text
org.skia.gpu.webgpu.AnalyticAntialiasConvexWebGpuTest
```

This fixture is separate from the 50 `coverage.edge-count-exceeded` inventory
refusals. It exercises the analytic-AA convex-fill path, passed the current
full inventory, and is covered by `gpuSmokeTest` after GRA-107.

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

- `rtk ./gradlew --no-daemon :gpu-renderer:gpuInventoryTest`
- `gpu-renderer/build/reports/gpu-inventory/gpu-inventory-failure-classification.md`
- `rtk ./gradlew --no-daemon :gpu-renderer:validateGpuSmokePromotionPolicy`
- `rtk ./gradlew --no-daemon :gpu-renderer:gpuSmokeTest`
- M33 reports under `reports/wgsl-pipeline/`

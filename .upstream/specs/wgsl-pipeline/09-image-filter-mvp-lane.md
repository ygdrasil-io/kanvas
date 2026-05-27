# Spec 09: Image-Filter MVP Lane

Status: Draft
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Milestone: M34 -- Image-filter MVP Lane

## Purpose

Define the M34 image-filter MVP boundary for the WGSL/WebGPU release
candidate.

The M34 decision is whether `SkImageFilters.Crop(input = nonNull)` receives a
minimal render-to-texture child-filter pre-pass for the selected fixture set or
remains an accepted MVP limitation with stable inventory diagnostics.

## Current Baseline

The post-M32 inventory currently classifies the known image-filter pre-pass gap
as:

```text
image-filter.crop-input-nonnull-prepass-required
```

The affected MVP-visible rows are expected to be:

- `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest`
- `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest`

M34 must verify that this list is current before deciding implementation versus
accepted limitation.

## M34 Tickets

| Ticket | Purpose |
|---|---|
| GRA-109 | Reproduce and artifact the current image-filter inventory signal. |
| GRA-110 | Decide pre-pass support versus MVP limitation. |
| GRA-111 | Execute the selected implementation or limitation path. |
| GRA-112 | Update image-filter spec, README, and release-readiness wording. |
| GRA-113 | Close M34 with PM-readable evidence. |

## Acceptance Rules

M34 can be accepted when:

- supported image-filter fixtures are explicitly named;
- retained unsupported filter graphs use stable diagnostics;
- required smoke contains no unsupported image-filter diagnostic;
- full inventory image-filter status is PM-readable;
- the release-readiness spec states whether `Crop(input = nonNull)` is
  supported or intentionally MVP-limited;
- any post-MVP pre-pass work has Linear ownership if not implemented.

## Non-Goals

M34 does not:

- implement a general Skia image-filter graph compiler;
- rebuild SkSL or Skia's image-filter internals;
- add short-lived substitutes for dependency-gated codec or font behavior;
- silently fall back to CPU/readback paths without stable diagnostics.

## Validation Sources

Expected evidence sources:

- `rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest`
- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md`
- focused image-filter WebGPU/cross-backend tests when implementation changes;
- `rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy`
- M34 reports under `reports/wgsl-pipeline/`

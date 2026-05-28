# Spec 09: Image-Filter MVP Lane

Status: Accepted
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Milestone: M34 -- Image-filter MVP Lane

## Purpose

Define the M34 image-filter MVP boundary for the WGSL/WebGPU release
candidate.

The M34 decision is whether `SkImageFilters.Crop(input = nonNull)` receives a
minimal render-to-texture child-filter pre-pass for the selected fixture set or
remains an accepted MVP limitation with stable inventory diagnostics.

## Accepted M34 Direction

GRA-109 reproduced the post-M33 image-filter inventory. GRA-110 selected the
accepted MVP limitation path, and GRA-111 hardened required GPU smoke policy so
the unresolved fixtures cannot be promoted before implementation evidence
lands.

The retained expected-unsupported reason is:

```text
image-filter.crop-input-nonnull-prepass-required
```

The affected MVP-visible rows are:

- `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest`
- `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest`

No required smoke fixture may include either row while this reason remains
inventory-only. A future promotion requires a render-to-texture child pre-pass
and adapter-backed evidence that removes the reason from inventory.

## M38 Policy Update

GRA-181 implemented the selected render-to-texture child pre-pass for the
bounded `Crop(kDecal, input = Offset(null))` SimpleOffset fixture shape. GRA-182
updates the smoke and inventory policy accordingly:

- `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest` is required GPU
  smoke after adapter-backed implementation evidence.
- `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest`
  remains full-inventory parity evidence, not a required smoke expansion.
- `unsupported-image-filter` inventory count for the selected SimpleOffset rows
  is `0`.
- `image-filter.crop-input-nonnull-prepass-required` remains a stable
  inventory-only reason for out-of-scope Crop(input = nonNull) graph shapes that
  are not covered by the selected M38 pre-pass.

The M38 promotion is intentionally not a general image-filter DAG compiler and
does not change the M34 non-goals.

## M34 Tickets

| Ticket | Purpose |
|---|---|
| GRA-109 | Reproduce and artifact the current image-filter inventory signal. |
| GRA-110 | Decided accepted MVP limitation rather than pre-pass implementation. |
| GRA-111 | Hardened the limitation path with required-smoke guards. |
| GRA-112 | Update image-filter spec, README, and release-readiness wording. |
| GRA-113 | Closed M34 with PM-readable evidence. |

## Acceptance Rules

M34 can be accepted when:

- supported image-filter fixtures are explicitly named as none promoted for
  this lane;
- retained unsupported filter graphs use stable diagnostics;
- required smoke contains no unsupported image-filter diagnostic;
- full inventory image-filter status is PM-readable;
- the release-readiness spec states that `Crop(input = nonNull)` is
  intentionally MVP-limited;
- GRA-113 links the final PM evidence and administrative closeout.

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

## Acceptance Evidence

- GRA-109: `reports/wgsl-pipeline/2026-05-27-m34-crop-nonnull-inventory-reproduction.md`
- GRA-110: `reports/wgsl-pipeline/2026-05-27-m34-crop-nonnull-decision.md`
- GRA-111: `reports/wgsl-pipeline/2026-05-27-m34-crop-nonnull-limitation-hardening.md`
- GRA-112: `reports/wgsl-pipeline/2026-05-27-m34-spec-readiness-sync.md`
- GRA-113: `reports/wgsl-pipeline/2026-05-27-m34-image-filter-closeout.md`
- GRA-181: `reports/wgsl-pipeline/2026-05-28-m38-crop-nonnull-prepass-implementation.md`
- GRA-182: `reports/wgsl-pipeline/2026-05-28-m38-image-filter-policy-update.md`

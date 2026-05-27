# M34 Crop(input = nonNull) Inventory Reproduction

Date: 2026-05-27
Linear: GRA-109
Branch: gra-109-crop-nonnull-inventory

## Goal

Reproduce the current GPU inventory signal for `SkImageFilters.Crop(input = nonNull)` after M33, before deciding whether M34 should implement a render-to-texture child pre-pass or retain the behavior as an MVP limitation.

## Command

```bash
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

Result: expected inventory task failure because `:gpu-raster:test` contains classified inventory failures. The task still generated the classification artifacts:

- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md`
- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.json`
- Raw command log captured locally at `/tmp/gra109-gpuInventoryTest.log`

## Category Summary

| Category | Count |
|---|---:|
| `expected-unsupported-diagnostic` | 50 |
| `similarity-regression` | 0 |
| `unsupported-image-filter` | 2 |
| `adapter-skip` | 48 |
| `adapter-missing` | 0 |
| `unexpected-exception` | 4 |

## Crop(input = nonNull) Rows

The reproduced image-filter inventory signal is unchanged from the M34 baseline. Exactly two rows carry the stable reason code `image-filter.crop-input-nonnull-prepass-required`:

| Test | Classification | Reason | Source XML |
|---|---|---|---|
| `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest#SimpleOffsetImageFilterGM renders close to reference PNG on the GPU backend()` | `unsupported-image-filter` | `image-filter.crop-input-nonnull-prepass-required` | `TEST-org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest.xml` |
| `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest#SimpleOffsetImageFilterGM matches reference on raster and GPU backends()` | `unsupported-image-filter` | `image-filter.crop-input-nonnull-prepass-required` | `TEST-org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest.xml` |

No other image-filter rows were classified as unsupported, unexpected, or similarity-regressed in this inventory run.

## Interpretation

`Crop(input = nonNull)` remains the only MVP-relevant image-filter blocker in the current inventory. The failure mode is still a missing child-filter pre-pass, not a similarity rebaseline problem and not an adapter-availability problem.

This keeps the GRA-110 decision narrow:

- implement a minimal render-to-texture child pre-pass for the two `SimpleOffsetImageFilter*` rows; or
- retain `image-filter.crop-input-nonnull-prepass-required` as an accepted MVP limitation, harden the smoke gate, and assign post-MVP ownership for the pre-pass.

## Non-M34 Inventory Signal

This run also surfaced four `unexpected-exception` rows in `org.skia.gpu.webgpu.SaveLayerTest`:

| Test | Failure class |
|---|---|
| `saveLayer with kDifference blendMode subtracts colors()` | `org.opentest4j.AssertionFailedError` |
| `saveLayer with kLighten blendMode picks brighter channel()` | `org.opentest4j.AssertionFailedError` |
| `saveLayer with kMultiply blendMode multiplies layer with background()` | `org.opentest4j.AssertionFailedError` |
| `saveLayer with kScreen blendMode lightens background()` | `org.opentest4j.AssertionFailedError` |

These rows are not image-filter failures and do not change the M34 `Crop(input = nonNull)` decision input. They are nevertheless release-readiness blockers for M35 unless they are fixed or reclassified with explicit evidence before the final inventory audit.

## Decision Input for GRA-110

Recommended input to GRA-110: treat the image-filter signal as stable and limited to two `SimpleOffsetImageFilter*` rows. The support-vs-limitation decision should be based on the cost and risk of the render-to-texture child pre-pass, not on uncertainty about inventory breadth.

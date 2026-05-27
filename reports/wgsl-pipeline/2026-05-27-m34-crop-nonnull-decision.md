# M34 Crop(input = nonNull) Decision

Date: 2026-05-27
Linear: GRA-110
Branch: gra-110-crop-nonnull-decision

## Decision

Retain `SkImageFilters.Crop(input = nonNull)` as an accepted MVP limitation for M34.

Do not implement the render-to-texture child-filter pre-pass in the MVP tail. GRA-111 should harden the limitation path so the two affected inventory rows remain explicitly expected unsupported and cannot enter required GPU smoke.

## Inputs

- GRA-109 report: `reports/wgsl-pipeline/2026-05-27-m34-crop-nonnull-inventory-reproduction.md`
- Active spec: `.upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md`
- Current implementation: `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`
- Current inventory classifier: `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/tools/GpuInventoryFailureReport.kt`

## Current Supported Surface

The WebGPU layer image-filter path already supports these MVP-relevant pieces:

- top-level `Crop(input = null)` through `layer_composite.wgsl` UV remap payload;
- top-level `Tile(input = null)` and `Magnifier(input = null)` through the same UV remap branch;
- top-level `Offset(input = null)` through destination-origin shifting;
- top-level `Blur`, `ColorFilter`, `DropShadow`, and `MatrixTransform` in specific bounded forms;
- selected `Compose` chains through the existing materialise chain for DropShadow/MatrixTransform and Blur/ColorFilter prefixes.

The refused path is narrower: `Crop(input = nonNull)` needs the child filter's output materialised before the crop/tile sampling semantics can be applied.

## Reproduced Failure Breadth

GRA-109 reproduced exactly two image-filter inventory rows:

| Test | Reason |
|---|---|
| `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest#SimpleOffsetImageFilterGM renders close to reference PNG on the GPU backend()` | `image-filter.crop-input-nonnull-prepass-required` |
| `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest#SimpleOffsetImageFilterGM matches reference on raster and GPU backends()` | `image-filter.crop-input-nonnull-prepass-required` |

No image-filter similarity regressions, adapter-missing rows, or unexpected image-filter exceptions were present.

## Why Not Implement the Pre-Pass Now

A minimal child pre-pass for the target fixture still changes structural image-filter execution, not just classification:

- the GM uses `SkImageFilters.Offset(..., cropRect = ...)`, which is represented as a `Crop(kDecal, input = Offset(...))` wrapper;
- the child filter output must be rendered into a layer-local scratch before the Crop shader samples it;
- clip rect and crop rect composition must remain pixel-correct for the `clipRect ∩ cropRect` permutations in `SimpleOffsetImageFilterGM`;
- scratch texture lifetime and source-view substitution must integrate with the existing materialise chain without breaking Blur/ColorFilter/DropShadow/MatrixTransform stages;
- the implementation would need focused WebGPU and cross-backend pixel evidence on adapter-backed CI before promotion.

That work is valid, but it is larger than the MVP tail requirement. The current failure is already stable, named, and limited to two rows. Shipping a rushed pre-pass would create higher release risk than retaining the explicit limitation.

## Required GRA-111 Limitation Path

GRA-111 should execute the limitation path by adding or confirming these gates:

- required GPU smoke must not include `SimpleOffsetImageFilterWebGpuTest` or `SimpleOffsetImageFilterCrossBackendTest` while they emit `image-filter.crop-input-nonnull-prepass-required`;
- `GpuInventoryFailureReportTest` must keep the exact two-row crop inventory assertion;
- `:gpu-raster:validateGpuSmokePromotionPolicy` must fail closed if either crop pre-pass fixture is promoted before implementation evidence lands;
- the limitation must remain PM-readable in the inventory report and M34 closeout report.

## Required GRA-112 Wording

GRA-112 should synchronize docs with this decision:

- keep or move `.upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md` to Accepted only after GRA-111 hardening lands;
- state that `Crop(input = nonNull)` is intentionally MVP-limited and post-MVP owned by a render-to-texture pre-pass follow-up;
- state that no required smoke fixture carries `image-filter.crop-input-nonnull-prepass-required`;
- mention that GRA-109 found four unrelated `SaveLayerTest` unexpected exceptions, which are M35 release-readiness blockers outside the image-filter decision.

## Rollback / Promotion Rule

The limitation can be reversed only when a future pre-pass PR provides:

- adapter-backed WebGPU and cross-backend passing evidence for the two `SimpleOffsetImageFilter*` rows;
- updated inventory with `unsupported-image-filter = 0` for the crop pre-pass reason;
- smoke-policy update proving the fixture is promotable without expected-unsupported diagnostics;
- documentation changing this decision from accepted MVP limitation to implemented support.

## Validation

```bash
rtk git diff --check
```

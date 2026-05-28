# M38 Crop(input = NonNull) Child Pre-pass Implementation

Date: 2026-05-28
Linear: GRA-181
Design: `reports/wgsl-pipeline/2026-05-28-m38-crop-nonnull-prepass-design.md`

## Outcome

Implemented the first bounded WebGPU child pre-pass for `Crop(input = nonNull)` image-filter graphs.

Selected supported graph:

```text
Crop(rect = cropRect, tileMode = kDecal, input = Offset(dx, dy, input = null))
```

This covers `SimpleOffsetImageFilterGM`, where `SkImageFilters.Offset(..., cropRect = ...)` is represented as a `Crop(kDecal, input = Offset(input = null))` wrapper.

The implementation does not claim general image-filter DAG support. Non-selected `Crop(input = nonNull)` graphs remain explicitly unsupported.

## Code Path

Changed file:

- `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`

New route behavior:

1. `resolveCropNonNullOffsetPrePassPlan` detects only the selected graph shape.
2. The child `Offset(input = null)` is materialised into a layer-sized scratch texture via the existing `LayerCompositeDraw.materializeTargetView` path.
3. The final composition samples that scratch through the existing `Crop(kDecal)` UV-remap payload in `layer_composite.wgsl`.
4. Scratch resource ownership follows the existing materialise draw lifecycle and is closed after the command buffer is submitted.

Route diagnostics named in the implementation path:

| Stage | Diagnostic |
|---|---|
| Child render | `webgpu.image-filter.crop-nonnull.prepass.offset-child` |
| Intermediate allocation | `SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch` |
| Final crop composition | existing `Crop(kDecal)` payload in `layer_composite.wgsl` |

## Scope Guards

The pre-pass only routes when all of these are true:

- top-level image filter is `Crop`
- `crop.tileMode == SkTileMode.kDecal`
- `crop.input` is `Offset`
- `offset.input == null`
- no `paint.colorFilter` shares the same layer paint
- layer blend mode remains fixed-function compatible

Out-of-scope graphs keep the existing fallback policy rather than silently dropping a child filter.

## Before / After Inventory

Baseline before GRA-181:

| Category / reason | Rows |
|---|---:|
| `unsupported-image-filter` | 2 |
| `image-filter.crop-input-nonnull-prepass-required` | 2 |
| `unexpected-exception` | 0 |

After GRA-181 full inventory:

| Category / reason | Rows |
|---|---:|
| `unsupported-image-filter` | 0 |
| `image-filter.crop-input-nonnull-prepass-required` | 0 |
| `expected-unsupported-diagnostic` | 50 |
| `adapter-skip` | 48 |
| `unexpected-exception` | 0 |

Remaining expected unsupported Path AA rows are unchanged:

- `coverage.edge-count-exceeded`: 46
- `coverage.stroke-outline-edge-count-exceeded`: 4

## Rows Resolved

These rows are no longer present in the generated failure-classification JSON:

- `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest#SimpleOffsetImageFilterGM renders close to reference PNG on the GPU backend()`
- `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest#SimpleOffsetImageFilterGM matches reference on raster and GPU backends()`

## Validation

Focused validation passed:

```text
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest' --tests '*SimpleOffsetImageFilterCrossBackendTest'
```

Full inventory validation:

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

Expected result: task exits non-zero because Path AA inventory rows remain intentionally classified. It generated:

- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md`
- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.json`

Observed generated JSON summary:

- `expected-unsupported-diagnostic`: 50
- `unsupported-image-filter`: 0
- `adapter-skip`: 48
- `adapter-missing`: 0
- `similarity-regression`: 0
- `unexpected-exception`: 0

Run before merge:

```text
rtk git diff --check
```

## Follow-up Required

GRA-182 should update smoke/inventory policy now that the two `SimpleOffsetImageFilter*` rows pass. GRA-183 should add the dashboard row with CPU/GPU artifacts and route JSON for `crop-image-filter-nonnull-prepass`.

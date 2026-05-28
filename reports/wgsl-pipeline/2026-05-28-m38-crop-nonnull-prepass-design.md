# M38 Crop(input = nonNull) Child Pre-pass Design

Date: 2026-05-28
Linear: GRA-174
Milestone: M38 -- Image-filter Child Pre-pass
Implementation ticket: GRA-181
Policy ticket: GRA-182
Dashboard ticket: GRA-183

## Goal

Define the first bounded WebGPU render-to-texture child pre-pass for image-filter graphs currently refused as:

```text
image-filter.crop-input-nonnull-prepass-required
```

The first target is `SimpleOffsetImageFilterGM`, covered by:

- `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest#SimpleOffsetImageFilterGM renders close to reference PNG on the GPU backend()`
- `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest#SimpleOffsetImageFilterGM matches reference on raster and GPU backends()`

This target is representative because `SkImageFilters.Offset(dx, dy, input = null, cropRect = R)` is encoded by `kanvas-skia` as:

```text
Crop(rect = R, tileMode = kDecal, input = Offset(dx, dy, input = null))
```

That is the smallest real non-null-child crop graph: one structural child, one crop wrapper, no blur, no color-filter, no displacement, no arbitrary DAG traversal.

## Existing Route Boundary

Current WebGPU support in `SkWebGpuDevice` handles pure top-level UV-remap image filters in the layer composite shader:

- `Crop(input = null)`
- `Tile(input = null)`
- `Magnifier(input = null)`

The current refusal is correct because `Crop(input = nonNull)` cannot be represented by only changing the final composite UVs. The child output must be materialised first, then the crop shader must sample that child output using crop/tile semantics.

Current stable fallback reason remains:

```text
image-filter.crop-input-nonnull-prepass-required
```

## Selected First Slice

Supported graph shape for GRA-181:

```text
Crop(rect = cropRect, tileMode = kDecal, input = Offset(dx, dy, input = null))
```

Required constraints:

- `crop.tileMode == SkTileMode.kDecal`
- `crop.input` must be an `Offset` filter
- `offset.input == null`
- `dx` / `dy` must use the existing integer-rounded Offset convention already used by WebGPU composite dispatch
- no `paint.colorFilter` on the same layer paint in the first slice
- no nested `Compose`, `Blur`, `DropShadow`, `MatrixTransform`, `Tile`, `Magnifier`, `Image`, `Blend`, morphology, lighting, or displacement child in this slice

Out-of-scope graphs must continue to emit stable explicit diagnostics instead of silently falling back or ignoring the child.

## Pre-pass Sequence

For a saveLayer composite with layer source texture `sourceTexture` and paint image filter `Crop(kDecal, Offset(dx, dy))`:

1. Resolve the filter graph before `computeImageFilterUvRemapPayload` refuses it.
2. Detect the selected graph shape and create a `CropChildPrePassPlan` with:
   - child kind: `Offset`
   - child offset: integer-rounded `(dx, dy)`
   - crop rect: layer-pixel-space `SkRect`
   - crop tile mode: `kDecal`
   - source bounds: layer source texture size and destination origin
   - output bounds: crop rect intersected with the layer/composite bounds
3. Allocate a scratch texture for the child output.
4. Render the child `Offset(input = null)` into the scratch texture by sampling `sourceTexture` with the existing source texture view and shifting the effective destination origin by `(dx, dy)`.
5. Run the final crop composite by sampling the child scratch texture, not the original layer texture.
6. Apply `kDecal` semantics outside `cropRect`: transparent samples, no clamp/repeat/mirror in the first slice.
7. Release the scratch texture after the command encoder has recorded the final composite using it.

The final draw still writes to the parent render target through the existing layer composite path. The pre-pass only substitutes the sampled source view for the crop stage.

## Bounds And Coordinate Policy

Coordinate domains must stay explicit:

| Field | Domain | Rule |
|---|---|---|
| `cropRect` | layer pixel space after saveLayer origin subtraction | Match current top-level Crop payload translation behavior. |
| `sourceTexture` bounds | scratch/layer texture pixel space | Width/height from the popped layer source. |
| child output bounds | layer pixel space | `sourceBounds` shifted by `(dx, dy)`, clipped to scratch allocation. |
| final output bounds | parent/device composite bounds | Existing `compositeFrom` destination rect and clip handling remain authoritative. |

The first implementation should allocate scratch at the layer source extent for simplicity and evidence stability. Cropping the allocation smaller is allowed only after tests prove the origin math and crop intersections stay bit-stable.

For `kDecal`, samples outside `cropRect` must become transparent even if the child scratch contains pixels there. This prevents the pre-pass from treating Crop as only a scissor.

## Resource Ownership And Lifetime

Ownership stays inside `SkWebGpuDevice.compositeFrom` / layer-composite dispatch:

- scratch texture format: same color format as existing layer composite scratch textures used by blur/materialise stages
- usage: render attachment plus texture binding
- lifetime: allocate for the selected composite, retain until the final crop composite command is encoded, then release with existing per-frame resource cleanup
- adapter/device: same WebGPU device and queue as the parent layer composite
- no CPU readback and no raster fallback path

The pre-pass must reuse the existing materialise/scratch conventions where possible so it does not introduce a second resource lifecycle model.

## Route Diagnostics

Implementation must expose route diagnostics at three points:

| Stage | Required diagnostic |
|---|---|
| CPU oracle | `cpu.image-filter.crop-nonnull.offset-oracle` or equivalent in report/dashboard route JSON |
| GPU pre-pass selected | `webgpu.image-filter.crop-nonnull.prepass.offset-child` |
| GPU final crop composite | `webgpu.image-filter.crop-nonnull.crop-decal-composite` |

Inventory and error policy:

- selected target passing: remove the two `SimpleOffsetImageFilter*` rows from `unsupported-image-filter`
- selected target still refused: keep `image-filter.crop-input-nonnull-prepass-required`
- unsupported graph shape after pre-pass exists: emit a more specific reason only if the implementation can distinguish it deterministically, otherwise keep the existing stable reason
- no case may become `unexpected-exception`

The implementation report must record the before/after inventory count for `unsupported-image-filter`.

## Validation Plan For GRA-181

Required focused validation:

```text
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest' --tests '*SimpleOffsetImageFilterCrossBackendTest'
```

Required policy/inventory validation after implementation:

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy
rtk git diff --check
```

`gpuInventoryTest` may continue to exit non-zero while other expected-unsupported inventory rows remain, but the generated JSON must show whether the two image-filter rows moved out of `unsupported-image-filter`.

## Dashboard Requirements For GRA-183

If GRA-181 produces adapter-backed passing evidence, add scene row:

```text
crop-image-filter-nonnull-prepass
```

The row must include:

- CPU/reference image for `SimpleOffsetImageFilterGM`
- GPU image from the pre-pass route
- diff and similarity stats
- route JSON naming both pre-pass and final crop composite diagnostics
- links to the implementation report and inventory JSON

If GRA-181 keeps the target explicitly unsupported, the dashboard row may remain expected-unsupported, but it must include the stable fallback reason and explain which design constraint blocked support.

## Remaining Unsupported Cases

The first slice deliberately does not support:

- `Crop(input = Blur(...))`
- `Crop(input = DropShadow(...))`
- `Crop(input = MatrixTransform(...))`
- `Crop(input = Tile/Magnifier/Crop(...))`
- `Crop(input = Compose(...))`
- non-`kDecal` crop tile modes with a non-null child
- arbitrary image-filter graphs or Skia image-filter DAG compilation

Those cases require separate tickets because they need either additional materialise stages, different sample-coordinate transforms, or multi-input graph evaluation.

## Acceptance Summary

GRA-174 is complete when this design is merged and linked to the implementation sequence:

- GRA-181 implements the selected pre-pass or records why it remains unsupported.
- GRA-182 updates inventory/smoke policy based on the measured result.
- GRA-183 adds scene dashboard evidence.
- GRA-184 closes the milestone with before/after counts and remaining backlog.

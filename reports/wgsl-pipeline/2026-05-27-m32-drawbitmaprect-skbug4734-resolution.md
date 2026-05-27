# M32 DrawBitmapRectSkbug4734 Resolution Evidence

Date: 2026-05-27
Linear: GRA-96
Milestone: M32 -- Bitmap/ImageRect GPU Similarity Remediation
Base evidence: `reports/wgsl-pipeline/2026-05-27-m32-bitmap-imagerect-reproduction.md`
Runtime fix: GRA-95 / PR #1170 (`dcf38fb3805f29c71638524f85f279043acc0fe2`)

## Decision

`DrawBitmapRectSkbug4734GM` is fixed by the strict-nearest source-rect
sampling change merged for GRA-95. No fixture-specific floor lowering,
rebaseline, or expected-unsupported reclassification is required for GRA-96.

Classification after validation: fixed above the configured `99.95` floor.

## Root Cause

`DrawBitmapRectSkbug4734GM` draws `images/randPixels.png` through a strict
`drawImageRect` path after insetting the source rect by `(0.5, 1.5)` and
mapping it through `SkMatrix.MakeScale(8, 8)`.

The failing WebGPU path treated strict nearest sampling like strict linear
sampling: it clamped the floating source coordinate to texel-center bounds
before sampling. That is correct for keeping linear filter taps inside the
requested source subset, but it is too narrow for nearest. CPU raster chooses
nearest texels on the integer grid with strict min/max rules:

```text
strictSampleMin(edge) = ceil(edge - 0.5)
strictSampleMax(edge) = floor(edge - 0.5)
selected texel = floor(mappedSourceCoord).coerceIn(strictMin, strictMax)
```

For half-pixel subsets this difference can select the wrong border texels.
The GRA-95 runtime change made strict nearest use the CPU integer-grid rule,
which also resolves the larger Skbug4734 delta.

## Code References

- `skia-integration-tests/src/main/kotlin/org/skia/tests/DrawBitmapRectSkbug4734GM.kt`: GM source, strict drawImageRect with source inset `(0.5, 1.5)`.
- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/DrawBitmapRectSkbug4734WebGpuTest.kt`: WebGPU floor remains `99.95`.
- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/DrawBitmapRectSkbug4734CrossBackendTest.kt`: raster and GPU floors remain `99.95`.
- `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`: strict mode is encoded as `0` fast, `1` strict linear taps, `2` strict nearest texels.
- `gpu-raster/src/main/resources/shaders/bitmap_shader.wgsl`: strict nearest uses `textureLoad` after `ceil(src.left - 0.5)`, `floor(src.right - 0.5)`, and `floor(mappedSourceCoord)`.

## Before / After

| Test | Before | After | Floor | Result |
|---|---:|---:|---:|---|
| `DrawBitmapRectSkbug4734WebGpuTest` | `91.02` | `100.00` | `99.95` | fixed |
| `DrawBitmapRectSkbug4734CrossBackendTest` GPU lane | `91.02` | `100.00` | `99.95` | fixed |
| `DrawBitmapRectSkbug4734CrossBackendTest` raster lane | `100.00` | `100.00` | `99.95` | unchanged |

After artifact images:

- `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/draw_bitmap_rect_skbug4734-raster-after-gra96.png`
- `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/draw_bitmap_rect_skbug4734-gpu-after-gra96.png`
- `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/draw_bitmap_rect_skbug4734-diff-after-gra96.png`

## Validation

Focused validation passed:

```text
rtk ./gradlew --no-daemon :gpu-raster:test \
  --tests 'org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest' \
  --tests 'org.skia.gpu.webgpu.crossbackend.DrawBitmapRectSkbug4734CrossBackendTest'
```

Results from JUnit XML:

- `DrawBitmapRectSkbug4734WebGpuTest`: `100.00`, `4096/4096`, max diff `(A=0, R=0, G=0, B=0)`.
- `DrawBitmapRectSkbug4734CrossBackendTest`: GPU `100.00`, raster `100.00`, both `4096/4096`, max diff `(A=0, R=0, G=0, B=0)`.

Full inventory was run and remains non-blocking:

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

Inventory result:

- Expected failure overall: inventory-only unsupported/skipped records remain.
- Total classified records: `100`.
- `similarity-regression`: `0`.
- `expected-unsupported-diagnostic`: `50`.
- `unsupported-image-filter`: `2`.
- `adapter-skip`: `48`.
- `unexpected-exception`: `0`.

## Closeout

GRA-96 resolves by evidence, not by a new runtime patch in this branch. The
runtime fix is the strict-nearest integer texel clamp from GRA-95. This ticket
adds the after-fix Skbug4734 artifacts and the reviewer-readable closeout
record required before image-rect smoke promotion is considered by GRA-97 and
GRA-99.

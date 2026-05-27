# M32 DrawBitmapRect3 Strict-Nearest Fix

Date: 2026-05-27  
Linear: GRA-95  
Milestone: M32 -- Bitmap/ImageRect GPU Similarity Remediation  
Base evidence: `reports/wgsl-pipeline/2026-05-27-m32-bitmap-imagerect-reproduction.md`

## Root Cause

`DrawBitmapRect3` draws a 3x3 image with `srcR = (0.5, 0.5, 2.5, 2.5)` and default nearest sampling through `SkCanvas.drawImageRect`, whose default constraint is `kStrict`.

The CPU raster path applies strict nearest in integer texel space:

- `strictSampleMin(edge) = ceil(edge - 0.5)`
- `strictSampleMax(edge) = floor(edge - 0.5)`
- selected texel = `floor(mappedSourceCoord).coerceIn(strictMin, strictMax)`

For `srcR.x = 0.5..2.5`, this permits texels `0..2`, so the left and right border texels remain visible.

The WebGPU bitmap shader used the strict linear-filter clamp for every filter mode:

```text
sx = clamp(sx_raw, srcRect.left + 0.5, srcRect.right - 0.5)
```

For the same source rect this collapsed the effective nearest lookup to `1.0..2.0`, dropping the texel-0/texel-2 border contribution and producing the previous `97.15 < 99.95` similarity result.

## Code Change

- `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`
  - Encodes strict mode in the existing uniform slot:
    - `0`: fast/unconstrained
    - `1`: strict filter taps for linear sampling
    - `2`: strict nearest texels for nearest sampling
- `gpu-raster/src/main/resources/shaders/bitmap_shader.wgsl`
  - Keeps the existing center-bound clamp for strict linear sampling.
  - Uses `textureLoad` for strict nearest sampling after computing the integer strict min/max bounds, matching `SkBitmapDevice`.
- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SrcRectConstraintWebGpuTest.kt`
  - Adds a half-pixel strict-nearest subset regression test that asserts the border texels remain visible.

## Before / After

| Test | Before | After | Floor | Result |
|---|---:|---:|---:|---|
| `DrawBitmapRect3WebGpuTest` | `97.15` | `100.00` | `99.95` | fixed |
| `DrawBitmapRect3CrossBackendTest` GPU lane | `97.15` | `100.00` | `99.95` | fixed |
| `DrawBitmapRect3CrossBackendTest` raster lane | `100.00` | `100.00` | `95.00` | unchanged |

After artifact images:

- `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/3x3bitmaprect-raster-after-gra95.png`
- `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/3x3bitmaprect-gpu-after-gra95.png`
- `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/3x3bitmaprect-diff-after-gra95.png`

## Validation

Focused validation passed:

```text
rtk ./gradlew --no-daemon :gpu-raster:test \
  --tests org.skia.gpu.webgpu.SrcRectConstraintWebGpuTest \
  --tests org.skia.gpu.webgpu.DrawBitmapRect3WebGpuTest \
  --tests org.skia.gpu.webgpu.crossbackend.DrawBitmapRect3CrossBackendTest
```

Results:

- `DrawBitmapRect3WebGpuTest`: `100.00`, `307200/307200`, max diff `(A=0, R=0, G=0, B=0)`.
- `DrawBitmapRect3CrossBackendTest`: GPU `100.00`, raster `100.00`.
- `SrcRectConstraintWebGpuTest#strict nearest half-pixel subset keeps border texels on webgpu`: passed.
- `SrcRectConstraintWebGpuTest#strict drawImageRect prevents linear filter guard-pixel bleed on webgpu`: passed.

Expanded validation passed:

```text
rtk ./gradlew --no-daemon :gpu-raster:test \
  --tests org.skia.gpu.webgpu.ImageRectTest \
  --tests org.skia.gpu.webgpu.crossbackend.SrcRectConstraintCrossBackendTest \
  --tests org.skia.gpu.webgpu.tools.WgslStrictValidationReportTest
```

Full inventory was run and remains non-blocking:

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

Inventory result:

- Expected failure overall: existing inventory-only failures remain.
- Total classified records: `100`.
- `similarity-regression`: `0`.
- `expected-unsupported-diagnostic`: `50`.
- `unsupported-image-filter`: `2`.
- `adapter-skip`: `48`.
- `unexpected-exception`: `0`.

## Collateral Signal

The same strict-nearest fix also made `DrawBitmapRectSkbug4734GM` pass at `100.00` in both WebGPU and cross-backend lanes during the full inventory run. That resolves the technical root cause for GRA-96, but GRA-96 should still receive its own evidence/Linear closeout entry.

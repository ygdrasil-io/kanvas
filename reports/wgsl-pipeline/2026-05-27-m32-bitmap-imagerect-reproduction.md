# M32 Bitmap/ImageRect Similarity Reproduction

Date: 2026-05-27  
Linear: GRA-94  
Milestone: M32 -- Bitmap/ImageRect GPU Similarity Remediation  
Source commit: `5f330491edd83fb2b009ad5cf0f09b5255cfb524`

## Command

```text
rtk ./gradlew --no-daemon :gpu-raster:test \
  --tests 'org.skia.gpu.webgpu.DrawBitmapRect3WebGpuTest' \
  --tests 'org.skia.gpu.webgpu.crossbackend.DrawBitmapRect3CrossBackendTest' \
  --tests 'org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest' \
  --tests 'org.skia.gpu.webgpu.crossbackend.DrawBitmapRectSkbug4734CrossBackendTest'
```

Expected result for this ticket: the focused run fails with four
`similarity-regression` records and regenerates debug artifacts.

Full inventory was also run:

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

Expected result for this ticket: the full inventory remains non-blocking and
fails while the unresolved similarity regressions stay in inventory-only
coverage.

## Environment

| Field | Value |
|---|---|
| Host | `Omega` |
| OS | `macOS 26.5 (25F71)` |
| JDK | `Temurin 25.0.1+8-LTS` |
| JUnit XML timestamp | `2026-05-27T19:17:51Z` to `2026-05-27T19:17:52Z` |
| Inventory artifact | `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md` |
| Versioned artifact directory | `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/` |

## Artifact Table

| Test | Fixture / GM | Backend path | Observed | Floor | Pixel signal | Artifacts |
|---|---|---|---:|---:|---|---|
| `org.skia.gpu.webgpu.DrawBitmapRect3WebGpuTest#DrawBitmapRect3 renders close to reference PNG on the GPU backend()` | `DrawBitmapRect3` / `3x3bitmaprect` | WebGPU vs upstream PNG | `97.15` | `99.95` | `298450/307200` matching, max diff `(A=0, R=255, G=255, B=255)` | GPU: `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/3x3bitmaprect-gpu.png` |
| `org.skia.gpu.webgpu.crossbackend.DrawBitmapRect3CrossBackendTest#DrawBitmapRect3 matches reference on raster and GPU backends()` | `DrawBitmapRect3` / `3x3bitmaprect` | Raster + WebGPU vs upstream PNG | GPU `97.15`, raster `100.00` | GPU `99.95`, raster `95.00` | GPU `298450/307200` matching; raster `307200/307200` matching | Raster: `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/3x3bitmaprect-raster.png`; GPU: `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/3x3bitmaprect-gpu.png`; Diff: `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/3x3bitmaprect-diff.png` |
| `org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest#DrawBitmapRectSkbug4734GM renders close to reference PNG on the GPU backend()` | `DrawBitmapRectSkbug4734GM` / `draw_bitmap_rect_skbug4734` | WebGPU vs upstream PNG | `91.02` | `99.95` | `3728/4096` matching, max diff `(A=0, R=99, G=210, B=140)` | GPU: `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/draw_bitmap_rect_skbug4734-gpu.png` |
| `org.skia.gpu.webgpu.crossbackend.DrawBitmapRectSkbug4734CrossBackendTest#DrawBitmapRectSkbug4734GM matches reference on raster and GPU backends()` | `DrawBitmapRectSkbug4734GM` / `draw_bitmap_rect_skbug4734` | Raster + WebGPU vs upstream PNG | GPU `91.02`, raster `100.00` | GPU `99.95`, raster `99.95` | GPU `3728/4096` matching; raster `4096/4096` matching | Raster: `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/draw_bitmap_rect_skbug4734-raster.png`; GPU: `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/draw_bitmap_rect_skbug4734-gpu.png`; Diff: `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/draw_bitmap_rect_skbug4734-diff.png` |

## Initial Hypotheses

| Fixture family | Hypothesis | Rationale | Next step |
|---|---|---|---|
| `DrawBitmapRect3` | Sampling coordinate or source-rect boundary issue. | Raster is byte-exact while WebGPU misses 8750 pixels on a nearest-sampled partial `srcR = (0.5, 0.5, 2.5, 2.5)` draw. The full-channel max diff suggests discrete texel selection or boundary classification drift rather than minor color-space noise. | Fix-first in GRA-95; do not rebaseline before inspecting source/destination coordinate mapping. |
| `DrawBitmapRectSkbug4734GM` | Fractional source/destination precision or pixel-snap fast-path issue. | Raster is byte-exact while WebGPU misses 368 of 4096 pixels for the skbug 4734 fractional image-rect case. The larger color-channel max diffs point to sampling the wrong source pixels rather than tolerance drift. | Fix-first in GRA-96; rebaseline only if review accepts the visual delta after source mapping analysis. |

## Decision

- Classification remains `similarity-regression` for all four records.
- These are rendered-output deltas, not adapter skips and not expected
  unsupported diagnostics.
- Full inventory classified `105` records: `50` expected unsupported
  diagnostics, `4` similarity regressions, `2` unsupported image-filter
  records, `48` adapter skips, and `1` unexpected exception.
- The unrelated unexpected exception is
  `org.skia.gpu.webgpu.SaveLayerTest#saveLayer with kExclusion blendMode subtracts double product()`,
  which fails with an exclusion-blend center pixel mismatch. It is outside
  GRA-94 scope and should not be used as bitmap/image-rect evidence.
- Keep both fixture families inventory-only until GRA-95/GRA-96 fix or
  evidence-rebaseline them.
- Do not promote either family into `gpuSmokeTest` while this report is the
  latest unresolved evidence.

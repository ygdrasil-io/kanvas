# M32 Bitmap/ImageRect Closeout

Date: 2026-05-27
Linear: GRA-98
Parent epic: GRA-93
Milestone: M32 -- Bitmap/ImageRect GPU Similarity Remediation

## PM Summary

M32 is complete for the bitmap/image-rect regressions inherited from GRA-76.
All four original failures now pass above floor with byte-exact GPU output. No
similarity floor was lowered, no fixture was reclassified as expected
unsupported, and no bulk rebaseline was used.

Required GPU smoke now includes one minimal image-rect fixture:

- `org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest`

The broader image-rect coverage remains inventory-only:

- `org.skia.gpu.webgpu.DrawBitmapRect3WebGpuTest`
- `org.skia.gpu.webgpu.crossbackend.DrawBitmapRect3CrossBackendTest`
- `org.skia.gpu.webgpu.crossbackend.DrawBitmapRectSkbug4734CrossBackendTest`

## Original Failure Status

| GRA-76 failure | Before | After | Floor | Final classification |
|---|---:|---:|---:|---|
| `DrawBitmapRect3WebGpuTest` | `97.15` | `100.00` | `99.95` | fixed |
| `DrawBitmapRect3CrossBackendTest` GPU lane | `97.15` | `100.00` | `99.95` | fixed |
| `DrawBitmapRectSkbug4734WebGpuTest` | `91.02` | `100.00` | `99.95` | fixed and promoted to required smoke |
| `DrawBitmapRectSkbug4734CrossBackendTest` GPU lane | `91.02` | `100.00` | `99.95` | fixed, inventory-only |

Raster lanes remained `100.00` for the cross-backend tests.

Final local inventory XML on `origin/master` after GRA-99:

- `DrawBitmapRect3WebGpuTest`: `100.00`, `307200/307200`, max diff `(A=0, R=0, G=0, B=0)`.
- `DrawBitmapRect3CrossBackendTest`: GPU `100.00`, raster `100.00`, both `307200/307200`, max diff `0`.
- `DrawBitmapRectSkbug4734WebGpuTest`: `100.00`, `4096/4096`, max diff `(A=0, R=0, G=0, B=0)`.
- `DrawBitmapRectSkbug4734CrossBackendTest`: GPU `100.00`, raster `100.00`, both `4096/4096`, max diff `0`.

## Evidence And Artifacts

| Area | Evidence |
|---|---|
| Reproduction and before artifacts | `reports/wgsl-pipeline/2026-05-27-m32-bitmap-imagerect-reproduction.md` |
| `DrawBitmapRect3` fix and after artifacts | `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect3-strict-nearest-fix.md` |
| `DrawBitmapRectSkbug4734` resolution and after artifacts | `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect-skbug4734-resolution.md` |
| Smoke promotion guard | `reports/wgsl-pipeline/2026-05-27-m32-image-rect-smoke-promotion-guard.md` |
| Smoke promotion | `reports/wgsl-pipeline/2026-05-27-m32-image-rect-smoke-promotion.md` |

Versioned artifact directory:

- `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/`

Before/after image families in that directory:

- `3x3bitmaprect-{raster,gpu,diff}.png`
- `3x3bitmaprect-{raster,gpu,diff}-after-gra95.png`
- `draw_bitmap_rect_skbug4734-{raster,gpu,diff}.png`
- `draw_bitmap_rect_skbug4734-{raster,gpu,diff}-after-gra96.png`

## PR And CI Links

| Ticket | PR | Merge commit | Required CI |
|---|---|---|---|
| GRA-94 | https://github.com/ygdrasil-io/kanvas/pull/1169 | `918643139025da8cbbc4735e45a0691b21598720` | No checks reported for doc/artifact-only PR |
| GRA-95 | https://github.com/ygdrasil-io/kanvas/pull/1170 | `dcf38fb3805f29c71638524f85f279043acc0fe2` | Raster success: https://github.com/ygdrasil-io/kanvas/actions/runs/26533947215/job/78157662527; GPU smoke success: https://github.com/ygdrasil-io/kanvas/actions/runs/26533947215/job/78157662510 |
| GRA-96 | https://github.com/ygdrasil-io/kanvas/pull/1171 | `e1c1f0f08b0f3904ab0ee96bffeed5d440fde1bf` | No checks reported for doc/artifact-only PR |
| GRA-97 | https://github.com/ygdrasil-io/kanvas/pull/1172 | `488d06aacf8e0a94c49f97a43b1c30efef9ce597` | Raster success: https://github.com/ygdrasil-io/kanvas/actions/runs/26534776580/job/78160620899; GPU smoke success: https://github.com/ygdrasil-io/kanvas/actions/runs/26534776580/job/78160620853 |
| GRA-99 | https://github.com/ygdrasil-io/kanvas/pull/1173 | `f795ae11ece749cf84454394960167209192609a` | Raster success: https://github.com/ygdrasil-io/kanvas/actions/runs/26534982487/job/78161338671; GPU smoke success: https://github.com/ygdrasil-io/kanvas/actions/runs/26534982487/job/78161338666 |

Non-blocking inventory CI remains intentionally non-required. The final GRA-99
inventory job failed as expected for inventory-only unsupported/follow-up
records:

- https://github.com/ygdrasil-io/kanvas/actions/runs/26534982487/job/78161478557

## Final Smoke Status

Local command on `origin/master` after GRA-99:

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest
```

Result: passed.

Smoke XML summary:

- `DrawBitmapRectSkbug4734WebGpuTest`: `1` test, `0` skipped, `0` failures, `0` errors; similarity `100.00`.
- `PipelineKeyTelemetryTest`: `8` tests, `0` skipped, `0` failures, `0` errors.
- `WebGpuCoveragePlanSelectorTest`: `24` tests, `0` skipped, `0` failures, `0` errors.

CI required GPU smoke after promotion also passed:

- https://github.com/ygdrasil-io/kanvas/actions/runs/26534982487/job/78161338666

## Final Inventory Status

Local command on `origin/master` after GRA-99:

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

Result: expected non-blocking failure (`GPU_INVENTORY_EXIT:1`).

Final local classification:

| Category | Count |
|---|---:|
| `expected-unsupported-diagnostic` | 50 |
| `similarity-regression` | 0 |
| `unsupported-image-filter` | 2 |
| `adapter-skip` | 48 |
| `adapter-missing` | 0 |
| `unexpected-exception` | 1 |

M32-specific result: no unresolved bitmap/image-rect `similarity-regression`
records remain.

## Remaining Work

No remaining work is required for M32 acceptance.

Out-of-scope follow-up created during closeout:

- GRA-100: `SaveLayerTest#saveLayer with kScreen blendMode lightens background()`
  was classified as the single final `unexpected-exception` in full inventory.
  This is not a bitmap/image-rect regression and is not smoke-promoted by M32.

Existing non-M32 inventory-only categories remain dependency-gated or policy
blocked:

- `coverage.edge-count-exceeded` and related coverage diagnostics remain out of
  smoke until GRA-70/follow-up implementation evidence exists.
- `image-filter.crop-input-nonnull-prepass-required` remains inventory-only
  until render-to-texture pre-pass evidence exists.
- Adapter-skip placeholder families remain excluded from required smoke.

## Closeout Decision

GRA-93 can be marked Done after this GRA-98 closeout merges and Linear receives
this summary. All M32 child issues are Done or represented by this closeout;
GRA-100 is intentionally outside the M32 parent epic.

# D52-4 - DrawMiniBitmapRect drawImageRect provenance boundary

Date: 2026-06-06

## Decision

D52-4 applies the correction at the shared `SkCanvas.drawImageRect` boundary.
Instead of adding a WebGPU heuristic for `SkBitmapShader(kClamp)`, `SkCanvas`
now intersects the requested source rect with the source image bounds and
shrinks the destination rect proportionally before dispatching either the
axis-aligned device route or the rotated shader/path rewrite.

This transports the `drawImageRect` contract directly. User-authored
`SkBitmapShader(kClamp)` keeps normal edge extension because it does not pass
through this pre-dispatch clipping step.

## Measurement

| Metric | Before D52-4 | After D52-4 |
|---|---:|---:|
| WebGPU similarity | 94.9305% | 99.9864% |
| WebGPU threshold | 99.95% | 99.95% |
| WebGPU status | expected-unsupported | pass |
| fallbackReason | `bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required` | `none` |
| matching pixels | 995418 / 1048576 | 1048433 / 1048576 |

Impact: the D52 artifact row now has a local support claim. No global
threshold, `results.json`, `scenes.json`, D50/D51/D52-1/D52-2/D52-3 manifest,
fallback policy, or global score file was changed.

## Files

- `kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt`
- `kanvas-skia/src/test/kotlin/org/skia/core/SkCanvasInternalsTest.kt`
- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/BitmapShaderPaintRectTest.kt`
- `reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/*`
- `reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-draw-image-rect-provenance-boundary.json`
- `scripts/validate_d52_drawminibitmaprect_provenance_boundary.py`

## User kClamp proof

Two targeted tests distinguish the routes:

- Raster: `SkCanvasInternalsTest.drawImageRect clips source outside image without changing user kClamp shader`
- WebGPU: `BitmapShaderPaintRectTest.drawImageRect source outside image clips but user kClamp shader extends edge`

Both use a source rect that extends past the image. `drawImageRect` leaves the
out-of-image destination area untouched, while a user `SkBitmapShader(kClamp)`
still extends the edge texel across the same area.

## Validation

- `rtk ./gradlew --no-daemon --rerun-tasks :kanvas-skia:test --tests org.skia.core.SkCanvasInternalsTest` - pass
- `rtk ./gradlew --no-daemon --rerun-tasks :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderPaintRectTest` - pass
- `rtk ./gradlew --no-daemon --rerun-tasks :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest` - pass
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest` - pass, WebGPU 99.9864%
- `rtk python3 scripts/validate_d52_drawminibitmaprect_provenance_boundary.py` - pass
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-draw-image-rect-provenance-boundary.json` - pass
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d52-4-pycache python3 -m py_compile scripts/validate_d52_drawminibitmaprect_provenance_boundary.py` - pass
- `rtk git diff --check` - pass

## Remaining risks

- The correction is axis-rect proportional clipping. It is intentionally scoped
  to `SkCanvas.drawImageRect`; it does not attempt to reinterpret arbitrary
  user bitmap shaders.
- The D52-3 validator remains a historical expected-unsupported validator and
  intentionally rejects the regenerated D52 artifacts after D52-4 promotion.
  D52-4 uses its own validator for the post-correction evidence.

# KGPU-M32-002 — Bridge ↔ Legacy SkWebGpuDevice Pixel Parity (Rect / RRect / Path)

## Status

**DONE** — All three proven fill families reach parity.

## Command

```
rtk ./gradlew --no-daemon :kanvas-skia-bridge:compareBridgeVsLegacyGpuRaster
```

## Real Output (verbatim)

```
=== Bridge vs Legacy SkWebGpuDevice Comparison ===

Scene: Rect solid fill
  similarity=100.00% matching=40000/40000 maxDiff=0
Scene: RRect solid fill
  similarity=99.77% matching=39908/40000 maxDiff=123
Scene: Path solid triangle fill
  similarity=100.00% matching=40000/40000 maxDiff=0

=== Summary ===
PASS | Rect solid fill | similarity=100.00% matching=40000/40000 maxDiff=0
PASS | RRect solid fill | similarity=99.77% matching=39908/40000 maxDiff=123
PASS | Path solid triangle fill | similarity=100.00% matching=40000/40000 maxDiff=0
All comparisons passed (threshold >= 99.0%)
```

## Per-Family Results

| Family | Similarity | Matching | Total | maxDiff | PASS/FAIL |
|--------|-----------|----------|-------|---------|-----------|
| Rect solid fill | 100.00% | 40,000 | 40,000 | 0 | PASS |
| RRect solid fill | 99.77% | 39,908 | 40,000 | 123 | PASS |
| Path solid triangle fill | 100.00% | 40,000 | 40,000 | 0 | PASS |

## Threshold Rationale

- **Rect (tol=0 / 100% expectation):** Both the Kanvas bridge FillRect dispatch and the legacy `SkWebGpuDevice` analytical-coverage solid rect shader produce byte-identical pixels for non-AA integer-bounded rects. Achieved 100.00% — meets the tol=0 expectation.
- **RRect (threshold ≥99.0%):** The bridge renders rounded rectangles via SDF coverage (`rrect_cov` in a fullscreen triangle pass); the legacy device uses analytical AA over the radii. The 0.23% mismatch (92 pixels out of 40,000, maxDiff=123) is concentrated on anti-aliased edge pixels where SDF and analytical AA diverge in sub-pixel precision. Well above the ≥99.0% threshold.
- **Path (non-AA triangle fill):** Both use polygon tessellation — the bridge via stencil-cover (write + test passes), the legacy via fan triangulation into `solid_polygon.wgsl`. With `isAntiAlias = false`, interior and exterior pixels match exactly. Achieved 100.00%.

## Methodology

### Bridge Path
1. `SkSurface.MakeRasterN32Premul(200, 200)` — creates a raster-backed SkSurface.
2. `SkiaKanvasSurface.wrap(bridgeSurface)` — wraps it for Kanvas GPU dispatch.
3. Draw scene (rect/rrect/path) via `surface.drawRect()` / `surface.drawRRect()` / `surface.drawPath()`.
4. `kanvasSurface.flush()` — triggers GPU render → surface decodes result into the wrapped SkSurface.
5. `bridgeSurface.makeImageSnapshot().pixels` — reads the IntArray of ARGB pixels.

### Legacy `SkWebGpuDevice` Path
1. `WebGpuContext.createOrNull()` — creates a WebGPU context (Dawn/Metal backend on macOS).
2. `SkWebGpuDevice(ctx, 200, 200)` — creates the legacy GPU device with transparent black background (`setBackground(0)`).
3. `SkCanvas(device).drawRect()` / `drawRRect()` / `drawPath()` — draws the identical scene through the legacy SkCanvas API.
4. `device.flush()` — submits GPU command buffer, executes present pass, reads back RGBA `ByteArray` from the target texture.

### Comparison
- Legacy `ByteArray` (RGBA byte order) is converted to `IntArray` (ARGB int order) to match the bridge's `SkImage.pixels` format.
- Per-pixel comparison computes max channel delta (A, R, G, B) across all 40,000 pixels.
- `similarity = matching / total * 100.0`

## Dependency

- Added `implementation(project(":gpu-raster"))` to `:kanvas-skia-bridge` build (temporary scaffolding; removed in Phase 5 with the comparison code).
- No dependency cycle: `:gpu-raster → :kanvas-skia` and `:kanvas-skia-bridge → :kanvas-skia` already exist; `:gpu-raster` does not depend on `:kanvas-skia-bridge`.

## Hardware

- **GPU:** Apple M2 Max
- **Backend:** Dawn/Metal via `libWGPU-v27.0.4.0.dylib`

## Files Changed

- **Created:** `kanvas-skia-bridge/src/main/kotlin/org/skia/kanvas/CompareBridgeVsLegacyGpuRaster.kt` — comparison main
- **Modified:** `kanvas-skia-bridge/build.gradle.kts` — added `implementation(project(":gpu-raster"))` + `compareBridgeVsLegacyGpuRaster` JavaExec task
- **Created:** `reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md` — this report

package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/bitmaptiled.cpp` — covers all four GMs in the file.
 *
 * Exercises Ganesh's drawing of tiled bitmaps — in particular that the
 * offsets and extents of the tiles don't cause gaps between tiles. Ten
 * strips are drawn with varying fractional offsets straddling the
 * `kBmpSmallTileSize == 1024` boundary so that `drawImageRect`'s tiling
 * path is hit (in upstream Ganesh). `:kanvas-skia` is raster-only, so
 * `SkTiledImageUtils::DrawImageRect` collapses to a plain
 * `canvas->drawImageRect` per the upstream fallback in
 * `SkTiledImageUtils.cpp:39-44` ("either the image didn't require
 * tiling or this is a raster-backed canvas. In either case fall back to
 * a default draw").
 *
 * Four variants in the upstream file:
 *
 *  **CPU (manual) — ACTIVE, reference PNGs available:**
 *  - `bitmaptiled_fractional_horizontal_manual` — 1124 × 365, horizontal strips
 *  - `bitmaptiled_fractional_vertical_manual`   — 365 × 1124, vertical strips
 *
 *  **GPU-only (`#if defined(SK_GANESH)`) — INTRACTABLE, no reference PNGs:**
 *  - `bitmaptiled_fractional_horizontal` — 1124 × 365, horizontal strips
 *  - `bitmaptiled_fractional_vertical`   — 365 × 1124, vertical strips
 *
 * The GPU GMs also call `dContext->setResourceCacheLimit` to force tiling;
 * on a raster canvas the GrDirectContext block is skipped and the draws
 * are identical to the manual variants. No reference PNG exists for either
 * GPU variant — tests are `@Disabled(INTRACTABLE.GPU_ONLY)`.
 *
 * C++ original (shared driver):
 * ```cpp
 * static void draw_tile_bitmap_with_fractional_offset(SkCanvas* canvas, bool vertical, bool manual) {
 *     const int kTileSize = 1 << 10;
 *     const int kBitmapLongEdge = 7 * kTileSize;
 *     const int kBitmapShortEdge = 1 * kTileSize;
 * #if defined(SK_GANESH)
 *     if (auto dContext = GrAsDirectContext(canvas->recordingContext())) {
 *         const int kBitmapArea = kBitmapLongEdge * kBitmapShortEdge;
 *         const size_t kBitmapBytes = kBitmapArea * sizeof(SkPMColor);
 *         dContext->setResourceCacheLimit(kBitmapBytes + kBitmapBytes / 2);
 *     }
 * #endif
 *     SkBitmap bmp;
 *     bmp.allocN32Pixels(vertical ? kBitmapShortEdge : kBitmapLongEdge,
 *                        vertical ? kBitmapLongEdge : kBitmapShortEdge, true);
 *     bmp.eraseColor(SK_ColorWHITE);
 *     for (int i = 0; i < 10; ++i) {
 *         float offset = i * 0.1f;
 *         SkRect src = vertical ? SkRect::MakeXYWH(0, (kTileSize - 50) + offset, 32, 1124.0f)
 *                               : SkRect::MakeXYWH((kTileSize - 50) + offset, 0, 1124, 32);
 *         SkRect dst = vertical ? SkRect::MakeXYWH(37.0f * i, 0.0f, 32.0f, 1124.0f)
 *                               : SkRect::MakeXYWH(0.0f, 37.0f * i, 1124.0f, 32.0f);
 *         if (manual) {
 *             SkTiledImageUtils::DrawImageRect(canvas, bmp.asImage(), src, dst, SkSamplingOptions(),
 *                                              nullptr, SkCanvas::kStrict_SrcRectConstraint);
 *         } else {
 *             canvas->drawImageRect(bmp.asImage(), src, dst, SkSamplingOptions(),
 *                                   nullptr, SkCanvas::kStrict_SrcRectConstraint);
 *         }
 *     }
 * }
 *
 * // GPU-only (Ganesh direct-context required):
 * DEF_SIMPLE_GPU_GM_BG(bitmaptiled_fractional_horizontal, rContext, canvas, 1124, 365, SK_ColorBLACK) {
 *     draw_tile_bitmap_with_fractional_offset(canvas, false, false);
 * }
 * DEF_SIMPLE_GPU_GM_BG(bitmaptiled_fractional_vertical, rContext, canvas, 365, 1124, SK_ColorBLACK) {
 *     draw_tile_bitmap_with_fractional_offset(canvas, true, false);
 * }
 *
 * // CPU/manual (all backends):
 * DEF_SIMPLE_GM_BG(bitmaptiled_fractional_horizontal_manual, canvas, 1124, 365, SK_ColorBLACK) {
 *     draw_tile_bitmap_with_fractional_offset(canvas, false, true);
 * }
 * DEF_SIMPLE_GM_BG(bitmaptiled_fractional_vertical_manual, canvas, 365, 1124, SK_ColorBLACK) {
 *     draw_tile_bitmap_with_fractional_offset(canvas, true, true);
 * }
 * ```
 */
private const val K_TILE_SIZE: Int = 1 shl 10
private const val K_BITMAP_LONG_EDGE: Int = 7 * K_TILE_SIZE
private const val K_BITMAP_SHORT_EDGE: Int = 1 * K_TILE_SIZE

private fun drawTileBitmapWithFractionalOffset(canvas: SkCanvas, vertical: Boolean) {
    val w = if (vertical) K_BITMAP_SHORT_EDGE else K_BITMAP_LONG_EDGE
    val h = if (vertical) K_BITMAP_LONG_EDGE else K_BITMAP_SHORT_EDGE
    val bmp = SkBitmap(w, h)
    bmp.eraseColor(SK_ColorWHITE)
    val image = bmp.asImage()

    for (i in 0 until 10) {
        val offset = i * 0.1f
        val src = if (vertical) {
            SkRect.MakeXYWH(0f, (K_TILE_SIZE - 50) + offset, 32f, 1124f)
        } else {
            SkRect.MakeXYWH((K_TILE_SIZE - 50) + offset, 0f, 1124f, 32f)
        }
        val dst = if (vertical) {
            SkRect.MakeXYWH(37f * i, 0f, 32f, 1124f)
        } else {
            SkRect.MakeXYWH(0f, 37f * i, 1124f, 32f)
        }
        // SkTiledImageUtils::DrawImageRect falls back to canvas->drawImageRect
        // for raster-backed canvases (see kdoc).
        canvas.drawImageRect(
            image,
            src,
            dst,
            SkSamplingOptions.Default,
            null,
            SrcRectConstraint.kStrict,
        )
    }
}

public class BitmapTiledFractionalHorizontalManualGM : GM() {
    init { setBGColor(SK_ColorBLACK) }
    override fun getName(): String = "bitmaptiled_fractional_horizontal_manual"
    override fun getISize(): SkISize = SkISize.Make(1124, 365)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawTileBitmapWithFractionalOffset(c, vertical = false)
    }
}

public class BitmapTiledFractionalVerticalManualGM : GM() {
    init { setBGColor(SK_ColorBLACK) }
    override fun getName(): String = "bitmaptiled_fractional_vertical_manual"
    override fun getISize(): SkISize = SkISize.Make(365, 1124)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawTileBitmapWithFractionalOffset(c, vertical = true)
    }
}

/**
 * GPU-only (`DEF_SIMPLE_GPU_GM_BG`) companion to [BitmapTiledFractionalHorizontalManualGM].
 *
 * Upstream: `bitmaptiled_fractional_horizontal` in `gm/bitmaptiled.cpp` (line 76),
 * guarded by `#if defined(SK_GANESH)`. This variant calls `canvas->drawImageRect`
 * directly (no `SkTiledImageUtils`) and relies on `GrDirectContext::setResourceCacheLimit`
 * to force GPU tiling. On a raster canvas the GrDirectContext block is elided — the
 * draw is identical to the manual variant.
 *
 * **Classification: INTRACTABLE.GPU_ONLY** — meaningful tiling behaviour requires
 * Ganesh GPU context; no reference PNG exists for this GM name in the repo.
 * [BitmapTiledFractionalHorizontalTest] is `@Disabled`.
 */
public class BitmapTiledFractionalHorizontalGM : GM() {
    init { setBGColor(SK_ColorBLACK) }
    override fun getName(): String = "bitmaptiled_fractional_horizontal"
    override fun getISize(): SkISize = SkISize.Make(1124, 365)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // The upstream GrDirectContext::setResourceCacheLimit call is GPU-only;
        // on a raster canvas it is a no-op. The draw itself is identical to the
        // manual variant — canvas.drawImageRect (no SkTiledImageUtils wrapper).
        drawTileBitmapWithFractionalOffset(c, vertical = false)
    }
}

/**
 * GPU-only (`DEF_SIMPLE_GPU_GM_BG`) companion to [BitmapTiledFractionalVerticalManualGM].
 *
 * Upstream: `bitmaptiled_fractional_vertical` in `gm/bitmaptiled.cpp` (line 79),
 * guarded by `#if defined(SK_GANESH)`. This variant calls `canvas->drawImageRect`
 * directly (no `SkTiledImageUtils`) and relies on `GrDirectContext::setResourceCacheLimit`
 * to force GPU tiling. On a raster canvas the GrDirectContext block is elided — the
 * draw is identical to the manual variant.
 *
 * **Classification: INTRACTABLE.GPU_ONLY** — meaningful tiling behaviour requires
 * Ganesh GPU context; no reference PNG exists for this GM name in the repo.
 * [BitmapTiledFractionalVerticalTest] is `@Disabled`.
 */
public class BitmapTiledFractionalVerticalGM : GM() {
    init { setBGColor(SK_ColorBLACK) }
    override fun getName(): String = "bitmaptiled_fractional_vertical"
    override fun getISize(): SkISize = SkISize.Make(365, 1124)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // The upstream GrDirectContext::setResourceCacheLimit call is GPU-only;
        // on a raster canvas it is a no-op. The draw itself is identical to the
        // manual variant — canvas.drawImageRect (no SkTiledImageUtils wrapper).
        drawTileBitmapWithFractionalOffset(c, vertical = true)
    }
}

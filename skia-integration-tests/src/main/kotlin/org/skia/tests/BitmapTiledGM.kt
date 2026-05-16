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
 * Port of Skia's `gm/bitmaptiled.cpp` `bitmaptiled_fractional_*_manual` GMs.
 *
 * Exercises Ganesh's drawing of tiled bitmaps — in particular that the
 * offsets and extents of the tiles don't cause gaps between tiles. Ten
 * strips are drawn with varying fractional offsets straddling the
 * `kBmpSmallTileSize == 1024` boundary so that `drawImageRect`'s tiling
 * path is hit (in upstream Ganesh). `:kanvas-skia` is raster-only, so
 * the `manual` variants — which call `SkTiledImageUtils::DrawImageRect`
 * — collapse to a plain `canvas->drawImageRect` per the upstream fallback
 * in `SkTiledImageUtils.cpp:39-44` ("either the image didn't require
 * tiling or this is a raster-backed canvas. In either case fall back to
 * a default draw").
 *
 * Two variants:
 *  - `bitmaptiled_fractional_horizontal_manual` — 1124 × 365, horizontal
 *    strips, bitmap is 7168 × 1024.
 *  - `bitmaptiled_fractional_vertical_manual` — 365 × 1124, vertical
 *    strips, bitmap is 1024 × 7168.
 *
 * C++ original (shared driver):
 * ```cpp
 * static void draw_tile_bitmap_with_fractional_offset(SkCanvas* canvas, bool vertical, bool manual) {
 *     const int kTileSize = 1 << 10;
 *     const int kBitmapLongEdge = 7 * kTileSize;
 *     const int kBitmapShortEdge = 1 * kTileSize;
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

package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of upstream Skia's `gm/tallstretchedbitmaps.cpp::TallStretchedBitmapsGM`
 * (`tall_stretched_bitmaps`, 730 × 690, default white BG).
 *
 * Builds 8 progressively taller bitmaps (`(4 + i) × 1024` rows tall, all
 * `60` columns wide). Each bitmap is filled with a vertical strip of round
 * AA arcs — `(2·radius + margin)` apart. The GM then draws the **last 10**
 * elements of each tall bitmap into a `1.3×` scaled column on the output
 * canvas via `drawImageRect`.
 *
 * `:kanvas-skia` differences from upstream :
 *  - Upstream's "small sub-canvas per item" trick (a workaround for the
 *    SW rasterizer disabling AA on tall canvases) is replaced here by
 *    drawing each arc directly into the parent surface with a per-arc
 *    `save` + `translate` + `restore`. Our rasteriser doesn't share the
 *    upstream "no AA on large canvas" heuristic, so the workaround is
 *    unnecessary ; pixel-level output stays the same modulo our usual
 *    AA edge drift.
 *  - `SkTiledImageUtils::DrawImageRect` collapses to plain
 *    [SkCanvas.drawImageRect] for raster surfaces (the upstream fallback
 *    in `SkTiledImageUtils.cpp:39-44`).
 */
public class TallStretchedBitmapsGM : GM() {

    override fun getName(): String = "tall_stretched_bitmaps"
    override fun getISize(): SkISize = SkISize.Make(730, 690)

    private val tallBmps: Array<TallBmp> = Array(8) { TallBmp() }

    private data class TallBmp(var bmp: SkBitmap? = null, var itemCnt: Int = 0)

    override fun onOnceBeforeDraw() {
        for (i in tallBmps.indices) {
            val h = (4 + i) * 1024
            tallBmps[i] = makeBm(h)
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.scale(1.3f, 1.3f)
        for (i in tallBmps.indices) {
            val entry = tallBmps[i]
            check(entry.itemCnt > 10) { "tallBmps[$i] only has ${entry.itemCnt} items" }
            val bmp = entry.bmp!!
            val startItem = entry.itemCnt - 10
            val itemHeight = bmp.height / entry.itemCnt
            val subRect = SkIRect.MakeLTRB(0, startItem * itemHeight, bmp.width, bmp.height)
            val dstRect = SkRect.MakeWH(bmp.width.toFloat(), 10f * itemHeight.toFloat())
            c.drawImageRect(
                bmp.asImage(),
                SkRect.Make(subRect),
                dstRect,
                SkSamplingOptions(SkFilterMode.kLinear),
                null,
                SrcRectConstraint.kStrict,
            )
            c.translate((bmp.width + 10).toFloat(), 0f)
        }
    }

    /**
     * Mirrors upstream's `make_bm(SkBitmap*, int height)` collapsed onto
     * `:kanvas-skia` — see class kdoc for why the per-cell sub-canvas
     * trick is folded into a single direct-draw loop.
     */
    private fun makeBm(rawHeight: Int): TallBmp {
        val count = rawHeight / (2 * K_RADIUS + K_MARGIN)
        val height = count * (2 * K_RADIUS + K_MARGIN)
        val bmp = SkBitmap(2 * (K_RADIUS + K_MARGIN), height)
        bmp.eraseColor(0)
        val random = SkRandom()

        val surface = SkSurface.MakeRasterDirect(bmp)
        val canvas = surface.canvas

        var angle = K_START_ANGLE
        for (i in 0 until count) {
            canvas.save()
            // Centre of the i-th cell.
            canvas.translate(
                (K_MARGIN + K_RADIUS).toFloat(),
                (i * (K_MARGIN + 2 * K_RADIUS) + K_MARGIN + K_RADIUS).toFloat(),
            )
            val paint = SkPaint().apply {
                isAntiAlias = true
                color = random.nextU() or 0xFF000000.toInt()
                style = SkPaint.Style.kStroke_Style
                strokeWidth = K_THICKNESS
                strokeCap = SkPaint.Cap.kRound_Cap
            }
            val radius = K_RADIUS - K_THICKNESS / 2f
            val bounds = SkRect.MakeLTRB(-radius, -radius, radius, radius)
            canvas.drawArc(bounds, angle, K_SWEEP, useCenter = false, paint = paint)
            canvas.restore()
            angle += K_DANGLE
        }
        return TallBmp(bmp, count)
    }

    private companion object {
        private const val K_RADIUS: Int = 22
        private const val K_MARGIN: Int = 8
        private const val K_START_ANGLE: Float = 0f
        private const val K_DANGLE: Float = 25f
        private const val K_SWEEP: Float = 320f
        private const val K_THICKNESS: Float = 8f
    }
}

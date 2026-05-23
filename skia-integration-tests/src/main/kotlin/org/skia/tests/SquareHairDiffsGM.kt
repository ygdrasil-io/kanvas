package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkColorSetRGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder

/**
 * Port of Skia's `gm/hairlines.cpp::squarehair_diffs` (DEF_SIMPLE_GM_CAN_FAIL,
 * 600 × 720).
 *
 * Overlays Butt / Square / Round cap hairlines in R / G / B channels so
 * the per-cap differences can be magnified and inspected. For each
 * combination of AA mode (aliased / anti-aliased) × stroke width (0, 1,
 * 1.001) the GM :
 *
 *  1. Fills a black backdrop across the right 480 px (120 → 600) to let
 *     the additive colour channels show up clearly.
 *  2. For each of the 3 caps, renders the same `draw_squarehair_tests`
 *     primitives into a 120 × 25 off-screen surface using the cap's
 *     channel colour (R / G / B), then blits the snapshot at `(0, 30·i)`.
 *  3. Scales 4× and additively (kPlus) composites the three channel
 *     images starting at `(30, 0)` in the zoomed view.
 *
 * The 3 width columns are separated by `translate(0, 120)`, and the two
 * AA panes by `translate(0, 20)`.
 *
 * Upstream uses `DEF_SIMPLE_GM_CAN_FAIL` because `canvas->makeSurface`
 * can return null on GPU configs where the surface parameters are
 * incompatible. Here we guard the same way : if [SkCanvas.makeSurface]
 * returns null we skip that width iteration early.
 */
public class SquareHairDiffsGM : GM() {

    override fun getName(): String = "squarehair_diffs"
    override fun getISize(): SkISize = SkISize.Make(600, 720)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val aliases = booleanArrayOf(false, true)
        val widths = floatArrayOf(0f, 1f, 1.001f)
        val caps = arrayOf(SkPaint.Cap.kButt_Cap, SkPaint.Cap.kSquare_Cap, SkPaint.Cap.kRound_Cap)
        val colors = intArrayOf(
            SkColorSetRGB(255, 0, 0),
            SkColorSetRGB(0, 255, 0),
            SkColorSetRGB(0, 0, 255),
        )

        for (alias in aliases) {
            for (width in widths) {
                // Black backdrop for the zoomed overlay column.
                val backdropPaint = SkPaint().apply {
                    color = SK_ColorBLACK
                    style = SkPaint.Style.kFill_Style
                }
                c.drawRect(SkRect.MakeLTRB(120f, 0f, 600f, 100f), backdropPaint)

                for (i in 0..2) {
                    // Off-screen surface for this cap's channel.
                    val info = SkImageInfo.MakeN32Premul(120, 25)
                    val surface: SkSurface = c.makeSurface(info)
                        ?: SkSurface.MakeRaster(info)

                    val paint = SkPaint().apply {
                        isAntiAlias = alias
                        strokeWidth = width
                        strokeCap = caps[i]
                        color = colors[i]
                    }
                    drawSquareHairTests(surface.canvas, paint)

                    val img = surface.makeImageSnapshot()
                    // Draw this cap's strip at its row.
                    c.drawImage(img, 0f, 30f * i)

                    // Overlay the same strip × 4 in additive mode.
                    c.save()
                    c.scale(4f, 4f)
                    val plusPaint = SkPaint().apply { blendMode = SkBlendMode.kPlus }
                    c.drawImage(img, 30f, 0f, paint = plusPaint)
                    c.restore()
                }

                c.translate(0f, 120f)
            }
            c.translate(0f, 20f)
        }
    }

    /**
     * Port of Skia's `draw_squarehair_tests` helper from `gm/hairlines.cpp`.
     *
     * Draws a fixed set of stroke primitives through [paint] onto [canvas]:
     *   - three lines (horizontal, diagonal, near-diagonal),
     *   - a moveTo / quadTo / conicTo compound path,
     *   - a moveTo / cubicTo / lineTo compound path.
     *
     * Paint style is forced to kStroke; all other paint attributes (width,
     * cap, AA, colour) come from the caller's [paint].
     */
    private fun drawSquareHairTests(canvas: SkCanvas, paint: SkPaint) {
        paint.style = SkPaint.Style.kStroke_Style
        canvas.drawLine(10f, 10f, 20f, 10f, paint)
        // Degenerate moveTo / lineTo / close — upstream draws cap on both ends.
        val p = SkPathBuilder().moveTo(10f, 15f).lineTo(20f, 15f).close().detach()
        canvas.drawPath(p, paint)
        canvas.drawLine(10f, 20.5f, 20f, 20.5f, paint)
        canvas.drawLine(30f, 10f, 30f, 20f, paint)
        canvas.drawLine(35.5f, 10f, 35.5f, 20f, paint)
        canvas.drawLine(40f, 10f, 50f, 20f, paint)

        val pathA = SkPathBuilder()
            .moveTo(60f, 10f)
            .quadTo(60f, 20f, 70f, 20f)
            .conicTo(70f, 10f, 80f, 10f, 0.707f)
            .detach()
        canvas.drawPath(pathA, paint)

        val pathB = SkPathBuilder()
            .moveTo(90f, 10f)
            .cubicTo(90f, 20f, 100f, 20f, 100f, 10f)
            .lineTo(110f, 10f)
            .detach()
        canvas.drawPath(pathB, paint)
    }
}

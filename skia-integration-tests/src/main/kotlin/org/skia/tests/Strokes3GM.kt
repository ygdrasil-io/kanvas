package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkStroker
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/strokes.cpp::Strokes3GM` (`strokes3`, 1500 × 1500).
 *
 * 6 rows × 13 columns. Each row uses a different two-contour generator
 * (`make0` … `make5`): nested CW/CW or CW/CCW rects, ovals, or rect+oval
 * pairs (the inset rect for ovals uses an X-inset + Y-outset). Each
 * column draws the path three times :
 *  1. stroked in `565`-quantised blue (`0xFF4444FF`) at width `j*j` ;
 *  2. stroked again in default-AA black (width 0) on top ;
 *  3. the stroker output (`FillPathWithPaint`) drawn filled in red.
 *
 * Stresses the stroker on nested-contour paths under wide strokes —
 * inner contours can disappear or invert their winding depending on
 * stroke width. Per-column translate by `dx + strokeWidth` packs the
 * row tightly.
 */
public class Strokes3GM : GM() {

    override fun getName(): String = "strokes3"
    override fun getISize(): SkISize = SkISize.Make(1500, 1500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val origPaint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }
        val fillPaint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            color = SK_ColorRED
        }
        val strokePaint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            color = ToolUtils.colorTo565(0xFF4444FF.toInt())
        }

        c.translate(20f, 80f)

        val bounds = SkRect.MakeWH(50f, 50f)
        val dx = bounds.width() * 4f / 3f
        val dy = bounds.height() * 5f

        for (i in 0 until 6) {
            val orig = makeProc(i, bounds)

            c.save()
            for (j in 0 until 13) {
                val w = (j * j).toFloat()
                strokePaint.strokeWidth = w
                c.drawPath(orig, strokePaint)
                c.drawPath(orig, origPaint)
                // FillPathWithPaint substitute : invoke the stroker
                // directly and draw the outline as a filled path.
                val outline = SkStroker.fromPaint(strokePaint).stroke(orig)
                c.drawPath(outline, fillPaint)
                c.translate(dx + w, 0f)
            }
            c.restore()
            c.translate(0f, dy)
        }
    }

    private fun makeProc(i: Int, bounds: SkRect): SkPath = when (i) {
        0 -> SkPathBuilder()
            .addRect(bounds, SkPathDirection.kCW)
            .addRect(inset(bounds), SkPathDirection.kCW)
            .detach()
        1 -> SkPathBuilder()
            .addRect(bounds, SkPathDirection.kCW)
            .addRect(inset(bounds), SkPathDirection.kCCW)
            .detach()
        2 -> SkPathBuilder()
            .addOval(bounds, SkPathDirection.kCW)
            .addOval(inset(bounds), SkPathDirection.kCW)
            .detach()
        3 -> SkPathBuilder()
            .addOval(bounds, SkPathDirection.kCW)
            .addOval(inset(bounds), SkPathDirection.kCCW)
            .detach()
        4 -> {
            val r = SkRect.MakeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom)
            r.inset(bounds.width() / 10f, -bounds.height() / 10f)
            SkPathBuilder()
                .addRect(bounds, SkPathDirection.kCW)
                .addOval(r, SkPathDirection.kCW)
                .detach()
        }
        else -> {
            val r = SkRect.MakeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom)
            r.inset(bounds.width() / 10f, -bounds.height() / 10f)
            SkPathBuilder()
                .addRect(bounds, SkPathDirection.kCW)
                .addOval(r, SkPathDirection.kCCW)
                .detach()
        }
    }

    private fun inset(r: SkRect): SkRect {
        val rr = SkRect.MakeLTRB(r.left, r.top, r.right, r.bottom)
        rr.inset(r.width() / 10f, r.height() / 10f)
        return rr
    }
}

package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.math.SkColor
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/hairlines.cpp::hairline_subdiv` (DEF_SIMPLE_GM,
 * 512 × 256).
 *
 * Renders 4 increasingly-large quadratic-Bézier hairlines (coords
 * 334..944) drawn one over the other at progressively-shifted origins.
 * Each draw exercises a different subdivision count in the AA hairline
 * Bézier flattener (no subdivisions / 1 / 2 / 3).
 */
public class HairlineSubdivGM : GM() {

    override fun getName(): String = "hairline_subdiv"
    override fun getISize(): SkISize = SkISize.Make(512, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(45f, -25f)
        drawSubdividedQuad(c, 334, 334, 467, 267, SK_ColorBLACK)

        c.translate(-185f, -150f)
        drawSubdividedQuad(c, 472, 472, 660, 378, SK_ColorRED)

        c.translate(-275f, -200f)
        drawSubdividedQuad(c, 668, 668, 934, 535, SK_ColorGREEN)

        c.translate(-385f, -260f)
        drawSubdividedQuad(c, 944, 944, 1320, 756, SK_ColorBLUE)
    }

    private fun drawSubdividedQuad(canvas: SkCanvas, x0: Int, y0: Int, x1: Int, y1: Int, color: SkColor) {
        val paint = SkPaint().apply {
            strokeWidth = 1f
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            this.color = color
        }
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat())
            .detach()
        canvas.drawPath(path, paint)
    }
}

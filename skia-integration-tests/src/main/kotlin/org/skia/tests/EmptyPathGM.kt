package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/emptypath.cpp::EmptyPathGM` (600 × 280).
 *
 * Draws an empty `SkPath` into a 100 × 30 rect clip, repeated for the
 * full Cartesian product of 4 fill rules
 * (`Winding`, `EvenOdd`, `InverseWinding`, `InverseEvenOdd`) × 3 paint
 * styles (`Fill`, `Stroke`, `StrokeAndFill`). Each cell shows the
 * black-stroked clip outline + two label strings underneath.
 *
 * Empty + `kInverse*` is the meaningful case : the inverse fill paints
 * everything outside the (empty) outline, i.e. the entire clip rect.
 * Empty + non-inverse paints nothing.
 */
public class EmptyPathGM : GM() {

    override fun getName(): String = "emptypath"
    override fun getISize(): SkISize = SkISize.Make(600, 280)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val font = ToolUtils.DefaultPortableFont(15f)
        val title = "Empty Paths Drawn Into Rectangle Clips With Indicated Style and Fill"
        c.drawString(title, 20f, 20f, font, SkPaint())

        val rand = SkRandom()
        val rect = SkRect.MakeWH(100f, 30f)
        var i = 0
        c.save()
        c.translate(10f, 0f)
        c.save()
        for (style in gStyles) {
            for (fill in gFills) {
                if (i % 4 == 0) {
                    c.restore()
                    c.translate(0f, rect.height() + 40f)
                    c.save()
                } else {
                    c.translate(rect.width() + 40f, 0f)
                }
                ++i

                var color = rand.nextU()
                color = color or 0xFF000000.toInt()           // force solid alpha
                color = ToolUtils.colorTo565(color)
                drawEmpty(c, color, rect, style.style, fill.fill)

                val rectPaint = SkPaint().apply {
                    this.color = SK_ColorBLACK
                    this.style = SkPaint.Style.kStroke_Style
                    strokeWidth = -1f                          // hairline
                    isAntiAlias = true
                }
                c.drawRect(rect, rectPaint)

                val labelPaint = SkPaint().apply { this.color = color }
                val labelFont = ToolUtils.DefaultPortableFont(12f)
                c.drawString(style.label, 0f, rect.height() + 15f, labelFont, labelPaint)
                c.drawString(fill.label, 0f, rect.height() + 28f, labelFont, labelPaint)
            }
        }
        c.restore()
        c.restore()
    }

    private fun drawEmpty(
        canvas: SkCanvas,
        color: Int,
        clip: SkRect,
        style: SkPaint.Style,
        fill: SkPathFillType,
    ) {
        val path: SkPath = SkPathBuilder().detach().makeFillType(fill)
        val paint = SkPaint().apply {
            this.color = color
            this.style = style
        }
        canvas.save()
        canvas.clipRect(clip)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private data class FillAndName(val fill: SkPathFillType, val label: String)
    private data class StyleAndName(val style: SkPaint.Style, val label: String)

    private companion object {
        val gFills: List<FillAndName> = listOf(
            FillAndName(SkPathFillType.kWinding, "Winding"),
            FillAndName(SkPathFillType.kEvenOdd, "Even / Odd"),
            FillAndName(SkPathFillType.kInverseWinding, "Inverse Winding"),
            FillAndName(SkPathFillType.kInverseEvenOdd, "Inverse Even / Odd"),
        )
        val gStyles: List<StyleAndName> = listOf(
            StyleAndName(SkPaint.Style.kFill_Style, "Fill"),
            StyleAndName(SkPaint.Style.kStroke_Style, "Stroke"),
            StyleAndName(SkPaint.Style.kStrokeAndFill_Style, "Stroke And Fill"),
        )
    }
}

package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/linepaths.cpp::linepath` (DEF_SIMPLE_GM,
 * 1240 × 390).
 *
 * Draws a `moveTo(25, 15) ; lineTo(75, 15)` (2-vertex non-closed) path
 * into a 100 × 30 rect clip, repeated for the full Cartesian product of
 *   - 3 cap/join combos : `(Butt, Bevel)`, `(Round, Round)`, `(Square,
 *     Bevel)`
 *   - 4 fill rules : Winding / EvenOdd / InverseWinding / InverseEvenOdd
 *   - 3 paint styles : Fill / Stroke / StrokeAndFill
 *
 * Each cell carries 3 label lines (style / fill / cap). Stroke width 10.
 *
 * The sister GM `lineclosepath` adds a `close()` on the path. We share
 * the layout via [LinePathGM.draw] with a `doClose` flag.
 */
public open class LinePathGM(private val doClose: Boolean = false) : GM() {

    override fun getName(): String = if (doClose) "lineclosepath" else "linepath"
    override fun getISize(): SkISize = SkISize.Make(1240, 390)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val builder = SkPathBuilder()
            .moveTo(25f, 15f)
            .lineTo(75f, 15f)
        if (doClose) builder.close()
        val path: SkPath = builder.detach()
        val pathLabel = if (doClose) "moveTo-line-close" else "moveTo-line"

        val titlePaint = SkPaint().apply {
            color = SK_ColorBLACK
            isAntiAlias = true
        }
        val font = ToolUtils.DefaultPortableFont(15f)
        val title = if (doClose) {
            "Line Closed Drawn Into Rectangle Clips With Indicated Style, Fill and Linecaps, with stroke width 10"
        } else {
            "Line Drawn Into Rectangle Clips With Indicated Style, Fill and Linecaps, with stroke width 10"
        }
        c.drawString(title, 20f, 20f, font, titlePaint)

        val rect = SkRect.MakeWH(100f, 30f)
        c.save()
        c.translate(10f, 30f)
        c.save()
        for ((capIdx, capInfo) in gCaps.withIndex()) {
            if (capIdx > 0) {
                c.translate((rect.width() + 40f) * gStyles.size, 0f)
            }
            c.save()
            for ((fillIdx, fillInfo) in gFills.withIndex()) {
                if (fillIdx > 0) {
                    c.translate(0f, rect.height() + 40f)
                }
                c.save()
                for ((styleIdx, styleInfo) in gStyles.withIndex()) {
                    if (styleIdx > 0) {
                        c.translate(rect.width() + 40f, 0f)
                    }

                    val color = ToolUtils.colorTo565(0xFF007000.toInt())
                    drawPath(
                        c, path, color, rect,
                        capInfo.cap, capInfo.join, styleInfo.style, fillInfo.fill, 10f,
                    )

                    val rectPaint = SkPaint().apply {
                        this.color = SK_ColorBLACK
                        style = SkPaint.Style.kStroke_Style
                        strokeWidth = -1f
                        isAntiAlias = true
                    }
                    c.drawRect(rect, rectPaint)

                    val labelPaint = SkPaint().apply { this.color = color }
                    val labelFont = ToolUtils.DefaultPortableFont(10f)
                    c.drawString(styleInfo.label, 0f, rect.height() + 12f, labelFont, labelPaint)
                    c.drawString(fillInfo.label, 0f, rect.height() + 24f, labelFont, labelPaint)
                    c.drawString(capInfo.label, 0f, rect.height() + 36f, labelFont, labelPaint)
                }
                c.restore()
            }
            c.restore()
        }
        c.restore()
        c.restore()

        // pathLabel is unused — upstream stores it on a struct but never
        // renders it. Kept here so the diff vs upstream stays minimal.
        @Suppress("UNUSED_VARIABLE") val unused = pathLabel
    }

    private fun drawPath(
        canvas: SkCanvas,
        srcPath: SkPath,
        color: Int,
        clip: SkRect,
        cap: SkPaint.Cap,
        join: SkPaint.Join,
        style: SkPaint.Style,
        fill: SkPathFillType,
        strokeWidth: Float,
    ) {
        val path = srcPath.makeFillType(fill)
        val paint = SkPaint().apply {
            strokeCap = cap
            this.strokeWidth = strokeWidth
            strokeJoin = join
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
    private data class CapAndName(val cap: SkPaint.Cap, val join: SkPaint.Join, val label: String)

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
        val gCaps: List<CapAndName> = listOf(
            CapAndName(SkPaint.Cap.kButt_Cap, SkPaint.Join.kBevel_Join, "Butt"),
            CapAndName(SkPaint.Cap.kRound_Cap, SkPaint.Join.kRound_Join, "Round"),
            CapAndName(SkPaint.Cap.kSquare_Cap, SkPaint.Join.kBevel_Join, "Square"),
        )
    }
}

/** Closed-path sibling of [LinePathGM]. Same layout, but the path is
 * closed via `close()`, exercising the closing-line stroke in the
 * `gCaps` matrix. */
public class LineClosePathGM : LinePathGM(doClose = true)

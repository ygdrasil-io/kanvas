package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkShader
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Shared grid layout for the upstream `linepath` / `lineclosepath` /
 * `cubicclosepath` / `cubicpath_shader` / `quadpath` / `quadclosepath`
 * GM family (`gm/linepaths.cpp`, `gm/cubicpaths.cpp`, `gm/quadpaths.cpp`).
 *
 * Each subclass supplies a path + title + optional shader. The class
 * handles the 3 caps × 4 fills (incl. `kInverse*`) × 3 styles matrix,
 * each drawn into a 100 × 30 clipped rect with three label lines
 * underneath.
 *
 * The 12 inner cells (3 fills × 3 styles per cap, repeated for 3 caps)
 * are laid out left-to-right by cap, top-to-bottom by fill, left-to-
 * right by style. Stroke width 10. Title at `(20, 20)` font size 15.
 *
 * @param gmName       the canonical Skia GM name (e.g. `cubicclosepath`).
 * @param canvasSize   `SkISize` matching the upstream reference PNG.
 * @param shape        the path to draw in every cell. Caller pre-builds
 *                     it (its [SkPathFillType] is overridden per-cell
 *                     via `makeFillType`).
 * @param title        title string drawn at the top of the canvas.
 * @param paintShader  optional shader applied to every cell's paint
 *                     instead of the solid colour. When `null`, a solid
 *                     `0xFF007000` fill is used (matches upstream).
 */
public abstract class PathCapsFillsGridGM(
    private val gmName: String,
    private val canvasSize: SkISize,
    private val shape: SkPath,
    private val title: String,
    private val paintShader: SkShader? = null,
) : GM() {

    override fun getName(): String = gmName
    override fun getISize(): SkISize = canvasSize

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val titlePaint = SkPaint().apply {
            color = SK_ColorBLACK
            isAntiAlias = true
        }
        val font = ToolUtils.DefaultPortableFont(15f)
        c.drawString(title, 20f, 20f, font, titlePaint)

        val cellRect = SkRect.MakeWH(100f, 30f)
        c.save()
        c.translate(10f, 30f)
        c.save()
        for ((capIdx, cap) in gCaps.withIndex()) {
            if (capIdx > 0) {
                c.translate((cellRect.width() + 40f) * gStyles.size, 0f)
            }
            c.save()
            for ((fillIdx, fill) in gFills.withIndex()) {
                if (fillIdx > 0) {
                    c.translate(0f, cellRect.height() + 40f)
                }
                c.save()
                for ((styleIdx, style) in gStyles.withIndex()) {
                    if (styleIdx > 0) {
                        c.translate(cellRect.width() + 40f, 0f)
                    }

                    val color = 0xFF007000.toInt()
                    drawCell(
                        c, shape, color, cellRect,
                        cap.cap, cap.join, style.style, fill.fill, 10f,
                    )

                    val rectPaint = SkPaint().apply {
                        this.color = SK_ColorBLACK
                        this.style = SkPaint.Style.kStroke_Style
                        strokeWidth = -1f       // hairline
                        isAntiAlias = true
                    }
                    c.drawRect(cellRect, rectPaint)

                    val labelPaint = SkPaint().apply { this.color = color }
                    val labelFont = ToolUtils.DefaultPortableFont(10f)
                    c.drawString(style.label, 0f, cellRect.height() + 12f, labelFont, labelPaint)
                    c.drawString(fill.label, 0f, cellRect.height() + 24f, labelFont, labelPaint)
                    c.drawString(cap.label, 0f, cellRect.height() + 36f, labelFont, labelPaint)
                }
                c.restore()
            }
            c.restore()
        }
        c.restore()
        c.restore()
    }

    private fun drawCell(
        canvas: SkCanvas,
        srcPath: SkPath,
        solidColor: Int,
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
            this.style = style
            if (paintShader != null) shader = paintShader else color = solidColor
        }
        canvas.save()
        canvas.clipRect(clip)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    protected data class FillAndName(val fill: SkPathFillType, val label: String)
    protected data class StyleAndName(val style: SkPaint.Style, val label: String)
    protected data class CapAndName(
        val cap: SkPaint.Cap,
        val join: SkPaint.Join,
        val label: String,
    )

    protected companion object {
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

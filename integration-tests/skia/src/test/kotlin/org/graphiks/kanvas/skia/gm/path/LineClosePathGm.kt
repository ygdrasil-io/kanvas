package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/linepaths.cpp::lineclosepath` (DEF_SIMPLE_GM,
 * 1240 × 390).
 *
 * Closed line path (moveTo(25, 15); lineTo(75, 15); close())
 * drawn into the PathCapsFillsGrid matrix: 3 cap/join combos × 4 fill
 * rules (incl. kInverse*) × 3 paint styles (fill / stroke / strokeAndFill).
 *
 * @see https://github.com/google/skia/blob/main/gm/linepaths.cpp
 */
class LineClosePathGm : SkiaGm {
    override val name = "lineclosepath"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1240
    override val height = 390

    private val shape = Path {
        moveTo(25f, 15f)
        lineTo(75f, 15f)
        close()
    }

    private val gFills = listOf(
        FillType.WINDING to "Winding",
        FillType.EVEN_ODD to "Even / Odd",
        FillType.INVERSE_WINDING to "Inverse Winding",
        FillType.INVERSE_EVEN_ODD to "Inverse Even / Odd",
    )
    private val gStyles = listOf(
        PaintStyle.FILL to "Fill",
        PaintStyle.STROKE to "Stroke",
        PaintStyle.STROKE_AND_FILL to "Stroke And Fill",
    )
    private val gCaps = listOf(
        Triple(StrokeCap.BUTT, StrokeJoin.BEVEL, "Butt"),
        Triple(StrokeCap.ROUND, StrokeJoin.ROUND, "Round"),
        Triple(StrokeCap.SQUARE, StrokeJoin.BEVEL, "Square"),
    )

    private val titleFont = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 15f,
    )
    private val labelFont = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 10f,
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val title = "Line Closed Drawn Into Rectangle Clips With " +
            "Indicated Style, Fill and Linecaps, with stroke width 10"
        canvas.drawString(
            title, 20f, 20f, titleFont,
            Paint(color = Color.BLACK, antiAlias = true),
        )

        val cellRect = Rect.fromXYWH(0f, 0f, 100f, 30f)
        val cellColor = Color.fromRGBA(0f, 0.44f, 0f, 1f)
        canvas.save()
        canvas.translate(10f, 30f)
        canvas.save()
        for ((capIdx, cap) in gCaps.withIndex()) {
            if (capIdx > 0) {
                canvas.translate((cellRect.width + 40f) * gStyles.size, 0f)
            }
            canvas.save()
            for ((fillIdx, fill) in gFills.withIndex()) {
                if (fillIdx > 0) {
                    canvas.translate(0f, cellRect.height + 40f)
                }
                canvas.save()
                for ((styleIdx, style) in gStyles.withIndex()) {
                    if (styleIdx > 0) {
                        canvas.translate(cellRect.width + 40f, 0f)
                    }
                    drawCell(
                        canvas, shape, cellColor, cellRect,
                        cap.first, cap.second, style.first, fill.first, 10f,
                    )
                    val rectPaint = Paint(
                        color = Color.BLACK,
                        style = PaintStyle.STROKE,
                        strokeWidth = 0f,
                        antiAlias = true,
                    )
                    canvas.drawRect(cellRect, rectPaint)

                    canvas.drawString(
                        style.second, 0f, cellRect.height + 12f,
                        labelFont, Paint(color = cellColor),
                    )
                    canvas.drawString(
                        fill.second, 0f, cellRect.height + 24f,
                        labelFont, Paint(color = cellColor),
                    )
                    canvas.drawString(
                        cap.third, 0f, cellRect.height + 36f,
                        labelFont, Paint(color = cellColor),
                    )
                }
                canvas.restore()
            }
            canvas.restore()
        }
        canvas.restore()
        canvas.restore()
    }

    private fun drawCell(
        canvas: GmCanvas,
        srcPath: Path,
        solidColor: Color,
        clip: Rect,
        cap: StrokeCap,
        join: StrokeJoin,
        style: PaintStyle,
        fill: FillType,
        strokeWidth: Float,
    ) {
        val path = Path { }
        path.addPath(srcPath)
        path.fillType = fill
        val paint = Paint(
            color = solidColor,
            style = style,
            strokeCap = cap,
            strokeJoin = join,
            strokeWidth = strokeWidth,
        )
        canvas.save()
        canvas.clipRect(clip)
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}

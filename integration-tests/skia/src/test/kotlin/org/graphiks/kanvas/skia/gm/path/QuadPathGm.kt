package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/quadpaths.cpp::QuadPathGM` (1240 x 390).
 * Open quad path (moveTo(25, 10) ; quadTo(50, 20, 75, 10)) drawn into
 * the PathCapsFillsGrid matrix: 3 cap/join combos x 4 fill rules
 * (incl. kInverse*) x 3 paint styles.
 * @see https://github.com/google/skia/blob/main/gm/quadpaths.cpp
 */
class QuadPathGm : SkiaGm {
    override val name = "quadpath"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1240
    override val height = 390

    private val shape = Path {
        moveTo(25f, 10f)
        quadTo(50f, 20f, 75f, 10f)
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

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val cellRect = Rect.fromXYWH(0f, 0f, 100f, 30f)
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
                    val cellColor = Color.fromRGBA(0x00 / 255f, 0x70 / 255f, 0x00 / 255f, 1f)
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
            strokeCap = cap,
            strokeWidth = strokeWidth,
            strokeJoin = join,
            style = style,
            color = solidColor,
        )
        canvas.save()
        canvas.clipRect(clip)
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}

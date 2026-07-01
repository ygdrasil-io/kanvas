package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/inversepaths.cpp::inverse_paths` (800 × 1200).
 * Stress test for inverse-fill paths under combinations of paint
 * style + stroke width + path-shape generators.
 * @see https://github.com/google/skia/blob/main/gm/inversepaths.cpp
 */
class InversePathsGm : SkiaGm {
    override val name = "inverse_paths"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 1200

    private data class Style(val paintStyle: PaintStyle, val pathEffect: PathEffect? = null)

    private fun makeDash(): PathEffect = PathEffect.Dash(floatArrayOf(4f, 3f), 0f)

    private val styles: Array<Style> = arrayOf(
        Style(PaintStyle.STROKE),
        Style(PaintStyle.STROKE_AND_FILL),
        Style(PaintStyle.FILL),
        Style(PaintStyle.STROKE, makeDash()),
    )

    private val pathSizes = floatArrayOf(40f, 10f, 0f)
    private val strokeWidths = floatArrayOf(10f, 0f)
    private val paths: Array<(Float, Float, Float) -> Path> = arrayOf(
        ::generateSquare, ::generateRectLine, ::generateCircle, ::generateLine,
    )

    private fun generateSquare(cx: Float, cy: Float, w: Float): Path =
        Path { }.apply { addRect(Rect.fromXYWH(cx - w / 2, cy - w / 2, w, w)) }

    private fun generateRectLine(cx: Float, cy: Float, l: Float): Path =
        Path { }.apply { addRect(Rect.fromXYWH(cx - l / 2, cy, l, 0f)) }

    private fun generateCircle(cx: Float, cy: Float, d: Float): Path =
        Path { }.apply { addCircle(cx, cy, d / 2) }

    private fun generateLine(cx: Float, cy: Float, l: Float): Path =
        Path { moveTo(cx - l / 2, cy); lineTo(cx + l / 2, cy) }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val slideWidth = 90f
        val slideHeight = 90f
        val slideBoundary = 5f

        val cx = slideWidth / 2 + slideBoundary
        val cy = slideHeight / 2 + slideBoundary
        val dx = slideWidth + 2 * slideBoundary
        val dy = slideHeight + 2 * slideBoundary

        val clipRect = Rect.fromLTRB(
            slideBoundary, slideBoundary,
            slideBoundary + slideWidth, slideBoundary + slideHeight,
        )
        val clipPaint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 2f,
        )
        val outlinePaint = Paint(
            color = Color.fromRGBA(0f, 0f, 0f, 0.25f),
            style = PaintStyle.STROKE,
            strokeWidth = 0f,
        )

        for (style in styles) {
            for (size in pathSizes) {
                canvas.save()
                for (sw in strokeWidths) {
                    val paint = Paint(
                        color = Color.fromRGBA(0f, 0.439f, 0f, 1f),
                        strokeWidth = sw,
                        style = style.paintStyle,
                        pathEffect = style.pathEffect,
                    )
                    for (gen in paths) {
                        canvas.drawRect(clipRect, clipPaint)
                        canvas.save()
                        canvas.clipRect(clipRect)
                        val path = gen(cx, cy, size)
                        path.fillType = FillType.INVERSE_WINDING
                        canvas.drawPath(path, paint)
                        path.fillType = FillType.WINDING
                        canvas.drawPath(path, outlinePaint)
                        canvas.restore()
                        canvas.translate(dx, 0f)
                    }
                }
                canvas.restore()
                canvas.translate(0f, dy)
            }
        }
    }
}

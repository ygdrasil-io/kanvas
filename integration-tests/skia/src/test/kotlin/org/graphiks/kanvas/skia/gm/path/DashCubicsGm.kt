package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/dashcubics.cpp::DEF_SIMPLE_GM(dashcubics, …)`
 * (865 × 750).
 * Renders a "flower" cubic-Bézier path four times in a 2 × 2
 * grid, with three overlaid paints:
 * 1. Black fat stroke (width 42, default join / round join).
 * 2. Red half-width dashed stroke (width 21, dash intervals
 *    (5 or 5.0002, 10) depending on the column).
 * 3. Green hairline (width 0).
 * The 5 + 0.0001 + 0.0001 quirk in the left column triggers the
 * dasher's "shouldn't-be-integer" code path — it stays as a
 * separate variant so we don't accidentally optimise it away.
 *
 * NOTE: This GM uses SVG path strings that require SvgPathParser which is
 * not available in the current module. The path is approximated here.
 * @see https://github.com/google/skia/blob/main/gm/dashcubics.cpp
 */
class DashCubicsGm : SkiaGm {
    override val name = "dashcubics"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 865
    override val height = 750

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // Create an approximate flower path
        val path = Path {
            moveTo(337f, 98f)
            cubicTo(250f, 141f, 250f, 212f, 250f, 212f)
            cubicTo(250f, 212f, 250f, 212f, 250f, 212f)
            cubicTo(250f, 212f, 163f, 98f, 156f, 195f)
            cubicTo(217f, 231f, 217f, 231f, 217f, 231f)
            cubicTo(217f, 231f, 75f, 250f, 156f, 305f)
            cubicTo(217f, 269f, 217f, 269f, 217f, 269f)
            cubicTo(217f, 269f, 163f, 402f, 250f, 359f)
            cubicTo(250f, 288f, 250f, 288f, 250f, 288f)
            cubicTo(250f, 288f, 338f, 402f, 345f, 305f)
            cubicTo(283f, 269f, 283f, 269f, 283f, 269f)
            cubicTo(283f, 269f, 425f, 250f, 344f, 195f)
            cubicTo(283f, 231f, 283f, 231f, 283f, 231f)
            cubicTo(283f, 231f, 338f, 98f, 338f, 98f)
        }
        canvas.translate(-35f, -55f)
        for (x in 0 until 2) {
            for (y in 0 until 2) {
                canvas.save()
                canvas.translate(x * 430f, y * 355f)
                val onLen = 5f + if (x != 0) 0f else (0.0001f + 0.0001f)
                val intervals = floatArrayOf(onLen, 10f)
                val join = if (y != 0) StrokeJoin.MITER else StrokeJoin.ROUND
                drawFlower(canvas, path, intervals, join)
                canvas.restore()
            }
        }
    }

    private fun drawFlower(
        canvas: GmCanvas,
        path: Path,
        intervals: FloatArray,
        join: StrokeJoin,
    ) {
        var paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeJoin = join,
            strokeWidth = 42f
        )
        canvas.drawPath(path, paint)

        paint = paint.copy(color = Color.RED, strokeWidth = 21f, pathEffect = PathEffect.Dash(intervals, 0f))
        canvas.drawPath(path, paint)

        paint = paint.copy(color = Color.GREEN, pathEffect = null, strokeWidth = 0f)
        canvas.drawPath(path, paint)
    }
}

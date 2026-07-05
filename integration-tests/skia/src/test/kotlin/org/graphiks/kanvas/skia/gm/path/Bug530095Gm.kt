package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/bug530095.cpp::bug530095` (900 x 1200).
 *
 * Stresses dash path effect under extreme intervals + matrix scales :
 *
 *  - Circle r=124 at (200,200), stroke 26, dash `[700, 700]` phase -40.
 *  - Same shape at 1/100 scale (circle r=1.24 at (2,2), stroke 0.26,
 *    dash `[7, 7]` phase -0.40), drawn under a `scale(100, 100)` CTM at
 *    `translate(4, 0)`.
 *  - The two configurations repeat with phase=0 in a second column at
 *    `translate(0, 400)`.
 *
 * Validates that the dasher correctly handles giant intervals (700 >>
 * the circle's perimeter), negative phases, and tiny intervals under
 * 100x CTM zoom -- both should rasterize identically modulo CTM.
 * @see https://github.com/google/skia/blob/main/gm/bug530095.cpp
 */
class Bug530095Gm : SkiaGm {
    override val name = "bug530095"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 77.6
    override val width = 900
    override val height = 1200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path1 = Path { }.apply { addCircle(200f, 200f, 124f) }
        val path2 = Path { }.apply { addCircle(2f, 2f, 1.24f) }

        var paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 26f,
        )
        paint = paint.copy(pathEffect = PathEffect.Dash(floatArrayOf(700f, 700f), -40f))
        canvas.drawPath(path1, paint)

        paint = paint.copy(strokeWidth = 0.26f, pathEffect = PathEffect.Dash(floatArrayOf(7f, 7f), -0.40f))
        canvas.save()
        canvas.scale(100f, 100f)
        canvas.translate(4f, 0f)
        canvas.drawPath(path2, paint)
        canvas.restore()

        paint = paint.copy(strokeWidth = 26f, pathEffect = PathEffect.Dash(floatArrayOf(700f, 700f), 0f))
        canvas.save()
        canvas.translate(0f, 400f)
        canvas.drawPath(path1, paint)
        canvas.restore()

        paint = paint.copy(strokeWidth = 0.26f, pathEffect = PathEffect.Dash(floatArrayOf(7f, 7f), 0f))
        canvas.scale(100f, 100f)
        canvas.translate(4f, 4f)
        canvas.drawPath(path2, paint)
    }
}

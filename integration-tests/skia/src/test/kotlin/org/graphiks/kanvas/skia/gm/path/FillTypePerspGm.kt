package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/filltypespersp.cpp`.
 *  Tests fill types with perspective — draws star-shaped paths with
 *  EvenOdd and Winding fill types under perspective transforms.
 *  @see https://github.com/google/skia/blob/main/gm/filltypespersp.cpp
 */
class FillTypePerspGm : SkiaGm {
    override val name = "filltypespersp"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 835
    override val height = 840

    private lateinit var fPath: Path

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val radius = 45f
        fPath = Path { }.apply {
            addCircle(50f, 50f, radius)
            addCircle(100f, 100f, radius)
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bgCenter = org.graphiks.kanvas.types.Point(100f, 100f)
        val bgStops = listOf(
            GradientStop(0f, Color.BLACK),
            GradientStop(0.25f, Color.fromRGBA(0f, 1f, 1f, 1f)),
            GradientStop(0.75f, Color.fromRGBA(1f, 1f, 0f, 1f)),
            GradientStop(1f, Color.WHITE),
        )
        val bgPaint = Paint(
            shader = Shader.RadialGradient(bgCenter, 1000f, bgStops, TileMode.CLAMP),
        )
        canvas.save()
        canvas.translate(100f, 100f)
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), bgPaint)
        canvas.restore()

        canvas.translate(20f, 20f)
        val scale = 5f / 4f

        showFour(canvas, 1f, false)
        canvas.translate(450f, 0f)
        showFour(canvas, scale, false)

        canvas.translate(-450f, 450f)
        showFour(canvas, 1f, true)
        canvas.translate(450f, 0f)
        showFour(canvas, scale, true)
    }

    private fun showFour(canvas: GmCanvas, scale: Float, aa: Boolean) {
        val center = org.graphiks.kanvas.types.Point(100f, 100f)
        val stops = listOf(
            GradientStop(0f, Color.BLUE),
            GradientStop(0.5f, Color.RED),
            GradientStop(1f, Color.GREEN),
        )
        val paint = Paint(
            shader = Shader.RadialGradient(center, 100f, stops, TileMode.CLAMP),
            antiAlias = aa,
        )
        showPath(canvas, 0, 0, 1f, scale, paint)
        showPath(canvas, 200, 0, 2f, scale, paint)
        showPath(canvas, 0, 200, 3f, scale, paint)
        showPath(canvas, 200, 200, 4f, scale, paint)
    }

    private fun showPath(
        canvas: GmCanvas, x: Int, y: Int, ft: Float, scale: Float, paint: Paint,
    ) {
        val r = Rect(0f, 0f, 150f, 150f)
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(r)
        canvas.drawRect(r, Paint(color = Color.WHITE))
        val withFill = fPath
        canvas.translate(r.left + r.width / 2f, r.top + r.height / 2f)
        canvas.scale(scale, scale)
        canvas.translate(-(r.left + r.width / 2f), -(r.top + r.height / 2f))
        canvas.drawPath(withFill, paint)
        canvas.restore()
    }
}

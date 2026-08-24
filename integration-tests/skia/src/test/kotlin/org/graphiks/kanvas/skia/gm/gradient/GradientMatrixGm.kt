package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.math.sqrt

/**
 * Port of Skia's `gm/gradient_matrix.cpp`.
 * Exercises gradient-shader local-matrix path with non-square transforms.
 * @see https://github.com/google/skia/blob/main/gm/gradient_matrix.cpp
 */
class GradientMatrixGm : SkiaGm {
    override val name = "gradient_matrix"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    private val red = ColorARGB.fromRGBA(1f, 0f, 0f, 1f)
    private val yellow = ColorARGB.fromRGBA(1f, 1f, 0f, 1f)
    private val gradStops = listOf(
        GradientStop(0f, red),
        GradientStop(1f, yellow),
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            RectF32(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = ColorARGB.fromRGBA(0xDD / 255f, 0xDD / 255f, 0xDD / 255f, 1f)),
        )
        drawGradients(canvas, ::makeLinearGradient, linearPts)
        canvas.translate(0f, TESTGRID_Y)
        drawGradients(canvas, ::makeRadialGradient, radialPts)
    }

    private fun drawGradients(
        canvas: GmCanvas,
        makeShader: (Array<Point2F32>) -> Shader,
        ptsArray: Array<Array<Point2F32>>,
    ) {
        val rectGrad = RectF32.ofLTRB(43f, 61f, 181f, 167f)
        val tw = rectGrad.width()
        val th = rectGrad.height()
        val tl = rectGrad.left
        val tt = rectGrad.top

        canvas.save()
        for (i in 0 until ptsArray.size) {
            if (i % IMAGES_X == 0 && i != 0) {
                canvas.restore()
                canvas.translate(0f, TESTGRID_Y)
                canvas.save()
            }
            val raw = ptsArray[i]
            val pts = arrayOf(
                Point2F32(raw[0].x * tw + tl, raw[0].y * th + tt),
                Point2F32(raw[1].x * tw + tl, raw[1].y * th + tt),
            )
            val paint = Paint(shader = makeShader(pts))
            canvas.drawRect(rectGrad, paint)
            canvas.translate(TESTGRID_X, 0f)
        }
        canvas.restore()
    }

    private fun makeLinearGradient(pts: Array<Point2F32>): Shader {
        return Shader.LinearGradient(
            start = pts[0], end = pts[1],
            stops = gradStops, tileMode = TileMode.CLAMP,
        )
    }

    private fun makeRadialGradient(pts: Array<Point2F32>): Shader {
        val cx = (pts[0].x + pts[1].x) * 0.5f
        val cy = (pts[0].y + pts[1].y) * 0.5f
        val dx = cx - pts[0].x
        val dy = cy - pts[0].y
        val radius = sqrt(dx * dx + dy * dy)
        return Shader.RadialGradient(
            center = Point2F32(cx, cy), radius = radius,
            stops = gradStops, tileMode = TileMode.CLAMP,
        )
    }

    private companion object {
        const val TESTGRID_X: Float = 200f
        const val TESTGRID_Y: Float = 200f
        const val IMAGES_X: Int = 4

        const val sZero: Float = 0f
        const val sHalf: Float = 0.5f
        const val sOne: Float = 1f

        val linearPts: Array<Array<Point2F32>> = arrayOf(
            arrayOf(Point2F32(sZero, sZero), Point2F32(sOne, sZero)),
            arrayOf(Point2F32(sZero, sZero), Point2F32(sZero, sOne)),
            arrayOf(Point2F32(sOne, sZero), Point2F32(sZero, sZero)),
            arrayOf(Point2F32(sZero, sOne), Point2F32(sZero, sZero)),
            arrayOf(Point2F32(sZero, sZero), Point2F32(sOne, sOne)),
            arrayOf(Point2F32(sOne, sOne), Point2F32(sZero, sZero)),
            arrayOf(Point2F32(sOne, sZero), Point2F32(sZero, sOne)),
            arrayOf(Point2F32(sZero, sOne), Point2F32(sOne, sZero)),
        )

        val radialPts: Array<Array<Point2F32>> = arrayOf(
            arrayOf(Point2F32(sZero, sHalf), Point2F32(sOne, sHalf)),
            arrayOf(Point2F32(sHalf, sZero), Point2F32(sHalf, sOne)),
            arrayOf(Point2F32(sOne, sHalf), Point2F32(sZero, sHalf)),
            arrayOf(Point2F32(sHalf, sOne), Point2F32(sHalf, sZero)),
            arrayOf(Point2F32(sZero, sZero), Point2F32(sOne, sOne)),
            arrayOf(Point2F32(sOne, sOne), Point2F32(sZero, sZero)),
            arrayOf(Point2F32(sOne, sZero), Point2F32(sZero, sOne)),
            arrayOf(Point2F32(sZero, sOne), Point2F32(sOne, sZero)),
        )
    }
}

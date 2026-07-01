package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/gradients_2pt_conical.cpp` — inside case (840 x 815).
 * 4-column x 7-row grid of two-point conical gradients with centres inside
 * the drawn rect.
 * @see https://github.com/google/skia/blob/main/gm/gradients_2pt_conical.cpp
 */
class ConicalGradients2ptInsideGm : SkiaGm {
    override val name = "gradients_2pt_conical_inside_nodither"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 0.0
    override val width = 840
    override val height = 815

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f)

        val pts = arrayOf(Point(0f, 0f), Point(100f, 100f))
        val rect = Rect.fromXYWH(0f, 0f, 100f, 100f)

        canvas.translate(20f, 20f)

        for (i in gradData.indices) {
            canvas.save()
            for (j in makerFns.indices) {
                val localMatrix = if (i == 3) {
                    Matrix33.translate(25f, 25f) * Matrix33.scale(0.5f, 0.5f)
                } else {
                    Matrix33.identity()
                }
                val shader = makerFns[j](pts, gradData[i], localMatrix)
                if (shader != null) {
                    val paint = Paint(antiAlias = true, shader = shader)
                    canvas.drawRect(rect, paint)
                }
                canvas.translate(0f, 120f)
            }
            canvas.restore()
            canvas.translate(120f, 0f)
        }
    }

    private data class GradData(
        val colors: List<Color>,
        val positions: List<Float>,
    )

    private fun midpoint(a: Float, b: Float): Float = (a + b) * 0.5f
    private fun interp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun makeInside(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val c0 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        val c1 = Point(interp(pts[0].x, pts[1].x, 3f / 5f), interp(pts[0].y, pts[1].y, 1f / 4f))
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c1, startRadius = (pts[1].x - pts[0].x) / 7f,
                end = c0, endRadius = (pts[1].x - pts[0].x) / 2f,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private fun makeInsideFlip(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val c0 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        val c1 = Point(interp(pts[0].x, pts[1].x, 3f / 5f), interp(pts[0].y, pts[1].y, 1f / 4f))
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c0, startRadius = (pts[1].x - pts[0].x) / 2f,
                end = c1, endRadius = (pts[1].x - pts[0].x) / 7f,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private fun makeInsideCenter(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val c0 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c0, startRadius = (pts[1].x - pts[0].x) / 7f,
                end = c0, endRadius = (pts[1].x - pts[0].x) / 2f,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private fun makeInsideCenterReversed(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val c0 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c0, startRadius = (pts[1].x - pts[0].x) / 2f,
                end = c0, endRadius = (pts[1].x - pts[0].x) / 7f,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private fun makeZeroRad(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val c0 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        val c1 = Point(interp(pts[0].x, pts[1].x, 3f / 5f), interp(pts[0].y, pts[1].y, 1f / 4f))
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c1, startRadius = 0f,
                end = c0, endRadius = (pts[1].x - pts[0].x) / 2f,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private fun makeZeroRadFlip(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val c0 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        val c1 = Point(interp(pts[0].x, pts[1].x, 3f / 5f), interp(pts[0].y, pts[1].y, 1f / 4f))
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c1, startRadius = (pts[1].x - pts[0].x) / 2f,
                end = c0, endRadius = 0f,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private fun makeZeroRadCenter(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val c0 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c0, startRadius = 0f,
                end = c0, endRadius = (pts[1].x - pts[0].x) / 2f,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private val makerFns: List<(Array<Point>, GradData, Matrix33) -> Shader?> = listOf(
        ::makeInside, ::makeInsideFlip, ::makeInsideCenter,
        ::makeZeroRad, ::makeZeroRadFlip, ::makeZeroRadCenter,
        ::makeInsideCenterReversed,
    )

    private val baseColors = listOf(Color.RED, Color.fromRGBA(0f, 1f, 0f), Color.BLUE, Color.WHITE, Color.BLACK)

    private val gradData: List<GradData> = listOf(
        GradData(baseColors.take(2), listOf(0f, 1f)),
        GradData(baseColors.take(2), listOf(0.25f, 0.75f)),
        GradData(baseColors.take(5), listOf(0f, 0.125f, 0.5f, 0.875f, 1f)),
        GradData(
            listOf(Color.RED, Color.fromRGBA(0f, 1f, 0f), Color.fromRGBA(0f, 1f, 0f), Color.BLUE),
            listOf(0f, 0f, 1f, 1f),
        ),
    )
}

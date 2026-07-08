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
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/gradients_2pt_conical.cpp` — edge case (840 x 815).
 * 4-column x 7-row grid of two-point conical gradient edge cases.
 * @see https://github.com/google/skia/blob/main/gm/gradients_2pt_conical.cpp
 */
class ConicalGradientsGm : SkiaGm {
    override val name = "conicalgradients"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
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

    private fun makeEdgeX(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val r0 = (pts[1].x - pts[0].x) / 7f
        val r1 = (pts[1].x - pts[0].x) / 3f
        val c1 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        val c0 = Point(c1.x + r1, c1.y)
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c0, startRadius = r0,
                end = c1, endRadius = r1,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private fun makeEdgeY(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val r0 = (pts[1].x - pts[0].x) / 7f
        val r1 = (pts[1].x - pts[0].x) / 3f
        val c1 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        val c0 = Point(c1.x, c1.y + r1)
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c0, startRadius = r0,
                end = c1, endRadius = r1,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private fun makeZeroRadEdgeX(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val r0 = 0f
        val r1 = (pts[1].x - pts[0].x) / 3f
        val c1 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        val c0 = Point(c1.x + r1, c1.y)
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c0, startRadius = r0,
                end = c1, endRadius = r1,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private fun makeZeroRadEdgeY(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val r0 = 0f
        val r1 = (pts[1].x - pts[0].x) / 3f
        val c1 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        val c0 = Point(c1.x, c1.y + r1)
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c0, startRadius = r0,
                end = c1, endRadius = r1,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private fun makeTouchX(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val r0 = (pts[1].x - pts[0].x) / 7f
        val r1 = (pts[1].x - pts[0].x) / 3f
        val c1 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        val c0 = Point(c1.x - r1 + r0, c1.y)
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c0, startRadius = r0,
                end = c1, endRadius = r1,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private fun makeTouchY(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val r0 = (pts[1].x - pts[0].x) / 7f
        val r1 = (pts[1].x - pts[0].x) / 3f
        val c1 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        val c0 = Point(c1.x, c1.y + r1 - r0)
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c0, startRadius = r0,
                end = c1, endRadius = r1,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private fun makeInsideSmallRad(pts: Array<Point>, data: GradData, lm: Matrix33): Shader? {
        val c0 = Point(midpoint(pts[0].x, pts[1].x), midpoint(pts[0].y, pts[1].y))
        return Shader.WithLocalMatrix(
            Shader.ConicalGradient(
                start = c0, startRadius = 0.0000000000000000001f,
                end = c0, endRadius = (pts[1].x - pts[0].x) / 2f,
                stops = data.colors.zip(data.positions) { c, p -> GradientStop(p, c) },
                tileMode = TileMode.CLAMP,
            ), lm,
        )
    }

    private val makerFns: List<(Array<Point>, GradData, Matrix33) -> Shader?> = listOf(
        ::makeEdgeX, ::makeEdgeY,
        ::makeZeroRadEdgeX, ::makeZeroRadEdgeY,
        ::makeTouchX, ::makeTouchY,
        ::makeInsideSmallRad,
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

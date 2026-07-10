package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/mandoline.cpp`.
 * Tests path slice operations (line, quad, cubic, conic) with randomized near-zero chop ts.
 * @see https://github.com/google/skia/blob/main/gm/mandoline.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.math.sqrt

class MandolineGm : SkiaGm {
    override val name = "mandoline"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 84.8
    override val width = 560
    override val height = 475

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(color = Color.WHITE, antiAlias = true)

        canvas.drawColor(0f, 0f, 0f, 1f)

        val mandoline = MandolineSlicer(Point(41f, 43f))
        mandoline.sliceCubic(Point(5f, 277f), Point(381f, -74f), Point(243f, 162f))
        mandoline.sliceLine(Point(41f, 43f))
        canvas.drawPath(mandoline.path(), paint)

        mandoline.reset(Point(357.049988f, 446.049988f))
        mandoline.sliceCubic(
            Point(472.750000f, -71.950012f),
            Point(639.750000f, 531.950012f),
            Point(309.049988f, 347.950012f),
        )
        mandoline.sliceLine(Point(309.049988f, 419f))
        mandoline.sliceLine(Point(357.049988f, 446.049988f))
        canvas.drawPath(mandoline.path(), paint)

        canvas.save()
        canvas.translate(421f, 105f)
        canvas.scale(100f, 81f)
        mandoline.reset(Point(-cosD(-60f), sinD(-60f)))
        mandoline.sliceConic(
            Point(-2f, 0f),
            Point(-cosD(60f), sinD(60f)),
            0.5f,
        )
        mandoline.sliceConic(
            Point(-cosD(120f) * 2f, sinD(120f) * 2f),
            Point(1f, 0f),
            0.5f,
        )
        mandoline.sliceLine(Point(0f, 0f))
        mandoline.sliceLine(Point(-cosD(-60f), sinD(-60f)))
        canvas.drawPath(mandoline.path(), paint)
        canvas.restore()

        canvas.save()
        canvas.translate(150f, 300f)
        canvas.scale(75f, 75f)
        mandoline.reset(Point(1f, 0f))
        val nquads = 5
        for (i in 0 until nquads) {
            val theta1 = 2f * PI_F / nquads * (i + 0.5f)
            val theta2 = 2f * PI_F / nquads * (i + 1f)
            mandoline.sliceQuadratic(
                Point(cos(theta1) * 2f, sin(theta1) * 2f),
                Point(cos(theta2), sin(theta2)),
            )
        }
        canvas.drawPath(mandoline.path(), paint)
        canvas.restore()
    }

    private companion object {
        const val PI_F: Float = kotlin.math.PI.toFloat()

        fun cosD(deg: Float): Float = cos(Math.toRadians(deg.toDouble())).toFloat()
        fun sinD(deg: Float): Float = sin(Math.toRadians(deg.toDouble())).toFloat()
    }

    private class MandolineSlicer(anchorPt: Point) {
        private var path: Path = Path { }
        private var anchorPt: Point = anchorPt
        private var lastPt: Point = anchorPt
        private val rand = Random(1)

        init { reset(anchorPt) }

        fun reset(anchor: Point) {
            path = Path { }
            this.anchorPt = anchor
            this.lastPt = anchor
        }

        fun sliceLine(pt: Point, numSubdivisions: Int = K_DEFAULT_SUBDIVISIONS) {
            if (numSubdivisions <= 0) {
                path.moveTo(anchorPt.x, anchorPt.y)
                path.lineTo(lastPt.x, lastPt.y)
                path.lineTo(pt.x, pt.y)
                path.close()
                lastPt = pt
                return
            }
            val t = chooseChopT(numSubdivisions)
            if (t == 0f) return
            val mid = Point(
                lastPt.x * (1f - t) + pt.x * t,
                lastPt.y * (1f - t) + pt.y * t,
            )
            sliceLine(mid, numSubdivisions - 1)
            sliceLine(pt, numSubdivisions - 1)
        }

        fun sliceQuadratic(p1: Point, p2: Point, numSubdivisions: Int = K_DEFAULT_SUBDIVISIONS) {
            if (numSubdivisions <= 0) {
                path.moveTo(anchorPt.x, anchorPt.y)
                path.lineTo(lastPt.x, lastPt.y)
                path.quadTo(p1.x, p1.y, p2.x, p2.y)
                path.close()
                lastPt = p2
                return
            }
            val t = chooseChopT(numSubdivisions)
            if (t == 0f) return
            val pp = chopQuadAt(arrayOf(lastPt, p1, p2), t)
            sliceQuadratic(pp[1], pp[2], numSubdivisions - 1)
            sliceQuadratic(pp[3], pp[4], numSubdivisions - 1)
        }

        fun sliceCubic(p1: Point, p2: Point, p3: Point, numSubdivisions: Int = K_DEFAULT_SUBDIVISIONS) {
            if (numSubdivisions <= 0) {
                path.moveTo(anchorPt.x, anchorPt.y)
                path.lineTo(lastPt.x, lastPt.y)
                path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
                path.close()
                lastPt = p3
                return
            }
            val t = chooseChopT(numSubdivisions)
            if (t == 0f) return
            val pp = chopCubicAt(arrayOf(lastPt, p1, p2, p3), t)
            sliceCubic(pp[1], pp[2], pp[3], numSubdivisions - 1)
            sliceCubic(pp[4], pp[5], pp[6], numSubdivisions - 1)
        }

        fun sliceConic(p1: Point, p2: Point, w: Float, numSubdivisions: Int = K_DEFAULT_SUBDIVISIONS) {
            if (numSubdivisions <= 0) {
                path.moveTo(anchorPt.x, anchorPt.y)
                path.lineTo(lastPt.x, lastPt.y)
                path.quadTo(p1.x, p1.y, p2.x, p2.y)
                path.close()
                lastPt = p2
                return
            }
            val t = chooseChopT(numSubdivisions)
            if (t == 0f) return
            val (left, right) = chopConicAt(lastPt, p1, p2, w, t)
            sliceConic(left.p1, left.p2, left.w, numSubdivisions - 1)
            sliceConic(right.p1, right.p2, right.w, numSubdivisions - 1)
        }

        fun path(): Path = path

        private fun chooseChopT(numSubdivisions: Int): Float {
            require(numSubdivisions > 0)
            if (numSubdivisions > 1) return 0.5f
            val mod = kotlin.math.abs(rand.nextInt()) % 10
            if (mod == 0) return 0f
            val exp = -rand.nextInt(10, 150)
            return java.lang.Math.scalb(1.0, exp).toFloat()
        }

        companion object {
            const val K_DEFAULT_SUBDIVISIONS: Int = 10
        }
    }
}

private fun chopQuadAt(p: Array<Point>, t: Float): Array<Point> {
    val a = lerp(p[0], p[1], t)
    val b = lerp(p[1], p[2], t)
    val mid = lerp(a, b, t)
    return arrayOf(p[0], a, mid, b, p[2])
}

private fun chopCubicAt(p: Array<Point>, t: Float): Array<Point> {
    val a = lerp(p[0], p[1], t)
    val b = lerp(p[1], p[2], t)
    val c = lerp(p[2], p[3], t)
    val d = lerp(a, b, t)
    val e = lerp(b, c, t)
    val mid = lerp(d, e, t)
    return arrayOf(p[0], a, d, mid, e, c, p[3])
}

private fun chopConicAt(
    p0: Point,
    p1: Point,
    p2: Point,
    w: Float,
    t: Float,
): Pair<ConicHalf, ConicHalf> {
    val w0 = 1f
    val w1 = w
    val w2 = 1f
    val rx0 = p0.x * w0; val ry0 = p0.y * w0
    val rx1 = p1.x * w1; val ry1 = p1.y * w1
    val rx2 = p2.x * w2; val ry2 = p2.y * w2

    val ax = rx0 + (rx1 - rx0) * t
    val ay = ry0 + (ry1 - ry0) * t
    val aw = w0 + (w1 - w0) * t
    val bx = rx1 + (rx2 - rx1) * t
    val by = ry1 + (ry2 - ry1) * t
    val bw = w1 + (w2 - w1) * t
    val mx = ax + (bx - ax) * t
    val my = ay + (by - ay) * t
    val mw = aw + (bw - aw) * t

    val newP1L = Point(ax / aw, ay / aw)
    val newP1R = Point(bx / bw, by / bw)
    val mid = Point(mx / mw, my / mw)

    val wL = aw / sqrt(w0 * mw)
    val wR = bw / sqrt(mw * w2)

    return ConicHalf(newP1L, mid, wL) to ConicHalf(newP1R, p2, wR)
}

private data class ConicHalf(val p1: Point, val p2: Point, val w: Float)

private fun lerp(a: Point, b: Point, t: Float): Point =
    Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

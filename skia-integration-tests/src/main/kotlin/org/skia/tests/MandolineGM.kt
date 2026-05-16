package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.skia.tools.SkRandom
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of upstream Skia's `gm/mandoline.cpp::SliverPathsGM` (registered
 * as `mandoline`). Slices three closed paths (cubic, conic, quad) into
 * thousands of sliver-shaped contours by recursively chopping each
 * segment at random `T` values, then fills the resulting compound
 * path. Renders as a black background with thin white slivers.
 *
 * Translation notes
 * -----------------
 * Upstream relies on `SkChopQuadAt`, `SkChopCubicAt`, and
 * `SkConic::chopAt` from `src/core/SkGeometry.h` — none are exposed
 * publicly in `:kanvas-skia`. We inline pure-Kotlin de Casteljau
 * subdivisions (`chopQuadAt`, `chopCubicAt`, `chopConicAt`) which
 * produce the same control polygons. `SkRandom` is the same
 * upstream-bit-compatible port we use elsewhere, so the chosen
 * `chooseChopT` sequence (and hence the sliver geometry) is
 * deterministic and reproducible.
 */
public class MandolineGM : GM() {

    init { setBGColor(SK_ColorBLACK) }

    override fun getName(): String = "mandoline"
    override fun getISize(): SkISize = SkISize.Make(560, 475)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            color = SK_ColorWHITE
            isAntiAlias = true
        }

        val mandoline = MandolineSlicer(SkPoint(41f, 43f))
        mandoline.sliceCubic(SkPoint(5f, 277f), SkPoint(381f, -74f), SkPoint(243f, 162f))
        mandoline.sliceLine(SkPoint(41f, 43f))
        c.drawPath(mandoline.path(), paint)

        mandoline.reset(SkPoint(357.049988f, 446.049988f))
        mandoline.sliceCubic(
            SkPoint(472.750000f, -71.950012f),
            SkPoint(639.750000f, 531.950012f),
            SkPoint(309.049988f, 347.950012f),
        )
        mandoline.sliceLine(SkPoint(309.049988f, 419f))
        mandoline.sliceLine(SkPoint(357.049988f, 446.049988f))
        c.drawPath(mandoline.path(), paint)

        c.save()
        c.translate(421f, 105f)
        c.scale(100f, 81f)
        mandoline.reset(SkPoint(-cosD(-60f), sinD(-60f)))
        mandoline.sliceConic(
            SkPoint(-2f, 0f),
            SkPoint(-cosD(60f), sinD(60f)),
            0.5f,
        )
        mandoline.sliceConic(
            SkPoint(-cosD(120f) * 2f, sinD(120f) * 2f),
            SkPoint(1f, 0f),
            0.5f,
        )
        mandoline.sliceLine(SkPoint(0f, 0f))
        mandoline.sliceLine(SkPoint(-cosD(-60f), sinD(-60f)))
        c.drawPath(mandoline.path(), paint)
        c.restore()

        c.save()
        c.translate(150f, 300f)
        c.scale(75f, 75f)
        mandoline.reset(SkPoint(1f, 0f))
        val nquads = 5
        for (i in 0 until nquads) {
            val theta1 = 2f * PI_F / nquads * (i + 0.5f)
            val theta2 = 2f * PI_F / nquads * (i + 1f)
            mandoline.sliceQuadratic(
                SkPoint(cos(theta1) * 2f, sin(theta1) * 2f),
                SkPoint(cos(theta2), sin(theta2)),
            )
        }
        c.drawPath(mandoline.path(), paint)
        c.restore()
    }

    private companion object {
        const val PI_F: Float = Math.PI.toFloat()

        fun cosD(deg: Float): Float = cos(Math.toRadians(deg.toDouble())).toFloat()
        fun sinD(deg: Float): Float = sin(Math.toRadians(deg.toDouble())).toFloat()
    }

    /**
     * Slices paths into sliver-size contours shaped like ice cream
     * cones — direct port of `MandolineSlicer` from
     * `gm/mandoline.cpp`. The state is a single [SkPathBuilder]
     * accumulating closed slivers anchored at [anchorPt].
     */
    private class MandolineSlicer(anchorPt: SkPoint) {
        private val builder = SkPathBuilder()
        private var anchorPt: SkPoint = anchorPt
        private var lastPt: SkPoint = anchorPt
        private val rand = SkRandom()

        init { reset(anchorPt) }

        fun reset(anchor: SkPoint) {
            builder.reset()
            this.anchorPt = anchor
            this.lastPt = anchor
        }

        fun sliceLine(pt: SkPoint, numSubdivisions: Int = K_DEFAULT_SUBDIVISIONS) {
            if (numSubdivisions <= 0) {
                builder.moveTo(anchorPt.fX, anchorPt.fY)
                builder.lineTo(lastPt.fX, lastPt.fY)
                builder.lineTo(pt.fX, pt.fY)
                builder.close()
                lastPt = pt
                return
            }
            val t = chooseChopT(numSubdivisions)
            if (t == 0f) return
            val mid = SkPoint(
                lastPt.fX * (1f - t) + pt.fX * t,
                lastPt.fY * (1f - t) + pt.fY * t,
            )
            sliceLine(mid, numSubdivisions - 1)
            sliceLine(pt, numSubdivisions - 1)
        }

        fun sliceQuadratic(p1: SkPoint, p2: SkPoint, numSubdivisions: Int = K_DEFAULT_SUBDIVISIONS) {
            if (numSubdivisions <= 0) {
                builder.moveTo(anchorPt.fX, anchorPt.fY)
                builder.lineTo(lastPt.fX, lastPt.fY)
                builder.quadTo(p1.fX, p1.fY, p2.fX, p2.fY)
                builder.close()
                lastPt = p2
                return
            }
            val t = chooseChopT(numSubdivisions)
            if (t == 0f) return
            val pp = chopQuadAt(arrayOf(lastPt, p1, p2), t)
            sliceQuadratic(pp[1], pp[2], numSubdivisions - 1)
            sliceQuadratic(pp[3], pp[4], numSubdivisions - 1)
        }

        fun sliceCubic(p1: SkPoint, p2: SkPoint, p3: SkPoint, numSubdivisions: Int = K_DEFAULT_SUBDIVISIONS) {
            if (numSubdivisions <= 0) {
                builder.moveTo(anchorPt.fX, anchorPt.fY)
                builder.lineTo(lastPt.fX, lastPt.fY)
                builder.cubicTo(p1.fX, p1.fY, p2.fX, p2.fY, p3.fX, p3.fY)
                builder.close()
                lastPt = p3
                return
            }
            val t = chooseChopT(numSubdivisions)
            if (t == 0f) return
            val pp = chopCubicAt(arrayOf(lastPt, p1, p2, p3), t)
            sliceCubic(pp[1], pp[2], pp[3], numSubdivisions - 1)
            sliceCubic(pp[4], pp[5], pp[6], numSubdivisions - 1)
        }

        fun sliceConic(p1: SkPoint, p2: SkPoint, w: Float, numSubdivisions: Int = K_DEFAULT_SUBDIVISIONS) {
            if (numSubdivisions <= 0) {
                builder.moveTo(anchorPt.fX, anchorPt.fY)
                builder.lineTo(lastPt.fX, lastPt.fY)
                builder.conicTo(p1.fX, p1.fY, p2.fX, p2.fY, w)
                builder.close()
                lastPt = p2
                return
            }
            val t = chooseChopT(numSubdivisions)
            if (t == 0f) return
            val (left, right) = chopConicAt(lastPt, p1, p2, w, t)
            sliceConic(left.p1, left.p2, left.w, numSubdivisions - 1)
            sliceConic(right.p1, right.p2, right.w, numSubdivisions - 1)
        }

        fun path(): SkPath = builder.snapshot()

        /**
         * Mirrors `MandolineSlicer::chooseChopT` — at the leaf of the
         * recursion tree (`numSubdivisions == 1`) we draw a random `T`
         * with a 1-in-10 chance of being exactly `0` and otherwise a
         * power-of-two between 2^-10 and 2^-149. Inner nodes always
         * split at the geometric midpoint.
         */
        private fun chooseChopT(numSubdivisions: Int): Float {
            require(numSubdivisions > 0)
            if (numSubdivisions > 1) return 0.5f
            val u = rand.nextU()
            // Kotlin Int → unsigned modulo
            val mod = (u.toLong() and 0xFFFFFFFFL) % 10L
            if (mod == 0L) return 0f
            val exp = -rand.nextRangeU(10, 149)
            return Math.scalb(1.0, exp).toFloat()
        }

        companion object {
            const val K_DEFAULT_SUBDIVISIONS: Int = 10
        }
    }
}

// ── geometry helpers (de Casteljau) ────────────────────────────────────

/** Subdivide a quadratic at [t]; returns 5 points `(p0, p01, mid, p12, p2)`. */
private fun chopQuadAt(p: Array<SkPoint>, t: Float): Array<SkPoint> {
    val a = lerp(p[0], p[1], t)
    val b = lerp(p[1], p[2], t)
    val mid = lerp(a, b, t)
    return arrayOf(p[0], a, mid, b, p[2])
}

/** Subdivide a cubic at [t]; returns 7 points (`p0, q0, q1, mid, q2, q3, p3`). */
private fun chopCubicAt(p: Array<SkPoint>, t: Float): Array<SkPoint> {
    val a = lerp(p[0], p[1], t)
    val b = lerp(p[1], p[2], t)
    val c = lerp(p[2], p[3], t)
    val d = lerp(a, b, t)
    val e = lerp(b, c, t)
    val mid = lerp(d, e, t)
    return arrayOf(p[0], a, d, mid, e, c, p[3])
}

/**
 * Conic subdivision at [t] using the rational de Casteljau form
 * (Farin / Sederberg). Returns the two halves as
 * [ConicHalf]. Mirrors `SkConic::chopAt`.
 *
 * Each conic is encoded as `(p0, p1, p2, w)` where `p1` is the off-
 * curve weighted control point. The split conics share `p[t]` (the
 * point on the curve at `t`) as their join.
 */
private fun chopConicAt(
    p0: SkPoint,
    p1: SkPoint,
    p2: SkPoint,
    w: Float,
    t: Float,
): Pair<ConicHalf, ConicHalf> {
    // Promote to rational : (x*w', y*w', w') where w' = 1 for endpoints, w for control.
    val w0 = 1f
    val w1 = w
    val w2 = 1f
    val rx0 = p0.fX * w0; val ry0 = p0.fY * w0
    val rx1 = p1.fX * w1; val ry1 = p1.fY * w1
    val rx2 = p2.fX * w2; val ry2 = p2.fY * w2

    val ax = rx0 + (rx1 - rx0) * t
    val ay = ry0 + (ry1 - ry0) * t
    val aw = w0 + (w1 - w0) * t
    val bx = rx1 + (rx2 - rx1) * t
    val by = ry1 + (ry2 - ry1) * t
    val bw = w1 + (w2 - w1) * t
    val mx = ax + (bx - ax) * t
    val my = ay + (by - ay) * t
    val mw = aw + (bw - aw) * t

    val newP1L = SkPoint(ax / aw, ay / aw)
    val newP1R = SkPoint(bx / bw, by / bw)
    val mid = SkPoint(mx / mw, my / mw)

    // Renormalise weights : w_new = w' / sqrt(w_left * w_right)
    val wL = aw / kotlin.math.sqrt(w0 * mw)
    val wR = bw / kotlin.math.sqrt(mw * w2)

    return ConicHalf(newP1L, mid, wL) to ConicHalf(newP1R, p2, wR)
}

private data class ConicHalf(val p1: SkPoint, val p2: SkPoint, val w: Float)

private fun lerp(a: SkPoint, b: SkPoint, t: Float): SkPoint =
    SkPoint(a.fX + (b.fX - a.fX) * t, a.fY + (b.fY - a.fY) * t)

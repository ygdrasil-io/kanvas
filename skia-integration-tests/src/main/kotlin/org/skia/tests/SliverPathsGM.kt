package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkGeometry
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.skia.tools.SkRandom
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Port of Skia's `gm/mandoline.cpp::SliverPathsGM` (`mandoline`,
 * 560 × 475, black BG).
 *
 * Stress-tests AA path rasterization of "ice-cream cone" sliver
 * contours produced by recursively chopping curves at random `t`
 * values (per-iteration `T ∈ {0} ∪ {2^-k | k ∈ [10,149]}`). Four
 * source curves : a cubic + line, a longer cubic + 2 lines, two
 * conics + 2 lines on a 100×81 scale, and 5 quadratics on a 75×75
 * scale.
 *
 * Each slice emits a triangle-fan-like contour (anchor → lastPt →
 * curve → close), so the final path is a fan of thousands of tiny
 * triangles. Stresses the AA edge-walker's robustness on degenerate
 * fan geometry.
 */
public class SliverPathsGM : GM() {

    init {
        setBGColor(SK_ColorBLACK)
    }

    override fun getName(): String = "mandoline"
    override fun getISize(): SkISize = SkISize.Make(560, 475)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply {
            color = SK_ColorWHITE
            isAntiAlias = true
        }

        val m = MandolineSlicer(SkPoint.Make(41f, 43f))
        m.sliceCubic(SkPoint.Make(5f, 277f), SkPoint.Make(381f, -74f), SkPoint.Make(243f, 162f))
        m.sliceLine(SkPoint.Make(41f, 43f))
        c.drawPath(m.path(), paint)

        m.reset(SkPoint.Make(357.049988f, 446.049988f))
        m.sliceCubic(
            SkPoint.Make(472.750000f, -71.950012f),
            SkPoint.Make(639.750000f, 531.950012f),
            SkPoint.Make(309.049988f, 347.950012f),
        )
        m.sliceLine(SkPoint.Make(309.049988f, 419f))
        m.sliceLine(SkPoint.Make(357.049988f, 446.049988f))
        c.drawPath(m.path(), paint)

        c.save()
        c.translate(421f, 105f)
        c.scale(100f, 81f)
        m.reset(SkPoint.Make(-cosDeg(-60f), sinDeg(-60f)))
        m.sliceConic(SkPoint.Make(-2f, 0f), SkPoint.Make(-cosDeg(60f), sinDeg(60f)), 0.5f)
        m.sliceConic(SkPoint.Make(-cosDeg(120f) * 2f, sinDeg(120f) * 2f), SkPoint.Make(1f, 0f), 0.5f)
        m.sliceLine(SkPoint.Make(0f, 0f))
        m.sliceLine(SkPoint.Make(-cosDeg(-60f), sinDeg(-60f)))
        c.drawPath(m.path(), paint)
        c.restore()

        c.save()
        c.translate(150f, 300f)
        c.scale(75f, 75f)
        m.reset(SkPoint.Make(1f, 0f))
        val nquads = 5
        for (i in 0 until nquads) {
            val theta1 = (2f * PI.toFloat() / nquads) * (i + 0.5f)
            val theta2 = (2f * PI.toFloat() / nquads) * (i + 1f)
            m.sliceQuadratic(
                SkPoint.Make(cos(theta1) * 2f, sin(theta1) * 2f),
                SkPoint.Make(cos(theta2), sin(theta2)),
            )
        }
        c.drawPath(m.path(), paint)
        c.restore()
    }

    private fun cosDeg(deg: Float): Float = cos(deg * PI.toFloat() / 180f)
    private fun sinDeg(deg: Float): Float = sin(deg * PI.toFloat() / 180f)

    /** Mirrors `gm/mandoline.cpp::MandolineSlicer`. */
    private class MandolineSlicer(anchor: SkPoint) {
        private val rand = SkRandom()
        private var builder = SkPathBuilder()
        private var anchorPt: SkPoint = anchor
        private var lastPt: SkPoint = anchor

        fun reset(anchor: SkPoint) {
            builder = SkPathBuilder()
            anchorPt = anchor
            lastPt = anchor
        }

        fun sliceLine(pt: SkPoint, numSubdivisions: Int = K_DEFAULT) {
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
            val midpt = SkPoint.Make(lastPt.fX * (1 - t) + pt.fX * t, lastPt.fY * (1 - t) + pt.fY * t)
            sliceLine(midpt, numSubdivisions - 1)
            sliceLine(pt, numSubdivisions - 1)
        }

        fun sliceQuadratic(p1: SkPoint, p2: SkPoint, numSubdivisions: Int = K_DEFAULT) {
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
            val pp = arrayOf(lastPt, p1, p2, SkPoint.Make(0f, 0f), SkPoint.Make(0f, 0f))
            SkGeometry.chopQuadAt(pp, t)
            sliceQuadratic(pp[1], pp[2], numSubdivisions - 1)
            sliceQuadratic(pp[3], pp[4], numSubdivisions - 1)
        }

        fun sliceCubic(p1: SkPoint, p2: SkPoint, p3: SkPoint, numSubdivisions: Int = K_DEFAULT) {
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
            val pp = arrayOf(
                lastPt, p1, p2, p3,
                SkPoint.Make(0f, 0f), SkPoint.Make(0f, 0f), SkPoint.Make(0f, 0f),
            )
            SkGeometry.chopCubicAt(pp, t)
            sliceCubic(pp[1], pp[2], pp[3], numSubdivisions - 1)
            sliceCubic(pp[4], pp[5], pp[6], numSubdivisions - 1)
        }

        fun sliceConic(p1: SkPoint, p2: SkPoint, w: Float, numSubdivisions: Int = K_DEFAULT) {
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
            val pp = arrayOf(lastPt, p1, p2, SkPoint.Make(0f, 0f), SkPoint.Make(0f, 0f))
            val (lw, rw) = SkGeometry.chopConicAt(pp, w, t)
            sliceConic(pp[1], pp[2], lw, numSubdivisions - 1)
            sliceConic(pp[3], pp[4], rw, numSubdivisions - 1)
        }

        fun path(): SkPath = builder.snapshot()

        private fun chooseChopT(numSubdivisions: Int): Float {
            if (numSubdivisions > 1) return 0.5f
            val u = rand.nextU().toLong() and 0xFFFFFFFFL
            return if (u % 10L == 0L) {
                0f
            } else {
                // scalbnf(1, -(int)nextRangeU(10, 149)) == 2^(-k)
                val k = rand.nextRangeU(10, 149)
                Math.scalb(1f, -k)
            }
        }

        companion object {
            const val K_DEFAULT = 10
        }
    }
}

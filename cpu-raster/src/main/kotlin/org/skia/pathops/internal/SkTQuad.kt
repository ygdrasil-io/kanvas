/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkTQuad : public SkTCurve` from
 * `src/pathops/SkPathOpsQuad.h`. Concrete [SkTCurve] implementation
 * wrapping an [SkDQuad].
 */
package org.skia.pathops.internal


import org.graphiks.math.SkDLine
import org.graphiks.math.SkDPoint
import org.graphiks.math.SkDVector
internal class SkTQuad(var quad: SkDQuad = SkDQuad()) : SkTCurve {

    override fun pointCount(): Int = SkDQuad.kPointCount
    override fun pointLast(): Int = SkDQuad.kPointLast
    override fun maxIntersections(): Int = SkDQuad.kMaxIntersections
    override fun isConic(): Boolean = false

    override fun get(n: Int): SkDPoint = quad[n]
    override fun set(n: Int, p: SkDPoint) { quad[n] = p }

    override fun collapsed(): Boolean = quad.collapsed()
    override fun controlsInside(): Boolean = quad.controlsInside()
    override fun dxdyAtT(t: Double): SkDVector = quad.dxdyAtT(t)
    override fun ptAtT(t: Double): SkDPoint = quad.ptAtT(t)

    override fun hullIntersects(quad: SkDQuad, isLinearOut: BooleanArray): Boolean =
        this.quad.hullIntersects(quad, isLinearOut)

    override fun hullIntersects(conic: SkDConic, isLinearOut: BooleanArray): Boolean =
        this.quad.hullIntersects(conic, isLinearOut)

    override fun hullIntersects(cubic: SkDCubic, isLinearOut: BooleanArray): Boolean =
        this.quad.hullIntersects(cubic, isLinearOut)

    override fun hullIntersects(curve: SkTCurve, isLinearOut: BooleanArray): Boolean =
        // Polymorphic dispatcher : ask `curve` to test against our quad.
        // Mirrors upstream `return curve.hullIntersects(fQuad, ...)`.
        when (curve) {
            is SkTQuad -> curve.quad.hullIntersects(this.quad, isLinearOut)
            is SkTConic -> curve.conic.hullIntersects(this.quad, isLinearOut)
            is SkTCubic -> curve.cubic.hullIntersects(this.quad, isLinearOut)
            else -> error("Unknown SkTCurve subtype : ${curve::class}")
        }

    override fun intersectRay(intersections: SkIntersections, line: SkDLine): Int =
        intersections.intersectRay(quad, line)

    override fun make(): SkTCurve = SkTQuad()

    override fun otherPts(oddMan: Int, endPt: Array<SkDPoint?>) {
        quad.otherPts(oddMan, endPt)
    }

    override fun setBounds(out: SkDRect) {
        out.setBounds(quad)
    }

    override fun subDivide(t1: Double, t2: Double, out: SkTCurve) {
        require(out is SkTQuad)
        out.quad = quad.subDivide(t1, t2)
    }
}

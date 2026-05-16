/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkTCubic : public SkTCurve` from
 * `src/pathops/SkPathOpsCubic.h`. Concrete [SkTCurve] implementation
 * wrapping an [SkDCubic].
 */
package org.skia.pathops.internal


import org.skia.math.SkDLine
import org.skia.math.SkDPoint
import org.skia.math.SkDVector
internal class SkTCubic(var cubic: SkDCubic = SkDCubic()) : SkTCurve {

    override fun pointCount(): Int = SkDCubic.kPointCount
    override fun pointLast(): Int = SkDCubic.kPointLast
    override fun maxIntersections(): Int = SkDCubic.kMaxIntersections
    override fun isConic(): Boolean = false

    override fun get(n: Int): SkDPoint = cubic[n]
    override fun set(n: Int, p: SkDPoint) { cubic[n] = p }

    override fun collapsed(): Boolean = cubic.collapsed()
    override fun controlsInside(): Boolean = cubic.controlsInside()
    override fun dxdyAtT(t: Double): SkDVector = cubic.dxdyAtT(t)
    override fun ptAtT(t: Double): SkDPoint = cubic.ptAtT(t)

    override fun hullIntersects(quad: SkDQuad, isLinearOut: BooleanArray): Boolean =
        cubic.hullIntersects(quad, isLinearOut)

    override fun hullIntersects(conic: SkDConic, isLinearOut: BooleanArray): Boolean =
        cubic.hullIntersects(conic, isLinearOut)

    override fun hullIntersects(cubic: SkDCubic, isLinearOut: BooleanArray): Boolean =
        // Upstream `SkTCubic::hullIntersects(SkDCubic)` calls
        // `cubic.hullIntersects(fCubic, ...)` (delegate to canonical
        // method which handles the convex-hull walk).
        cubic.hullIntersects(this.cubic, isLinearOut)

    override fun hullIntersects(curve: SkTCurve, isLinearOut: BooleanArray): Boolean =
        when (curve) {
            is SkTQuad -> curve.quad.hullIntersects(this.cubic, isLinearOut)
            is SkTConic -> curve.conic.hullIntersects(this.cubic, isLinearOut)
            is SkTCubic -> curve.cubic.hullIntersects(this.cubic, isLinearOut)
            else -> error("Unknown SkTCurve subtype : ${curve::class}")
        }

    override fun intersectRay(intersections: SkIntersections, line: SkDLine): Int =
        intersections.intersectRay(cubic, line)

    override fun make(): SkTCurve = SkTCubic()

    override fun otherPts(oddMan: Int, endPt: Array<SkDPoint?>) {
        cubic.otherPts(oddMan, endPt)
    }

    override fun setBounds(out: SkDRect) {
        out.setBounds(cubic)
    }

    override fun subDivide(t1: Double, t2: Double, out: SkTCurve) {
        require(out is SkTCubic)
        out.cubic = cubic.subDivide(t1, t2)
    }
}

/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkTConic : public SkTCurve` from
 * `src/pathops/SkPathOpsConic.h`. Concrete [SkTCurve] implementation
 * wrapping an [SkDConic].
 */
package org.skia.pathops.internal

internal class SkTConic(var conic: SkDConic = SkDConic()) : SkTCurve {

    override fun pointCount(): Int = SkDConic.kPointCount
    override fun pointLast(): Int = SkDConic.kPointLast
    override fun maxIntersections(): Int = SkDConic.kMaxIntersections
    override fun isConic(): Boolean = true

    override fun get(n: Int): SkDPoint = conic[n]
    override fun set(n: Int, p: SkDPoint) { conic[n] = p }

    override fun collapsed(): Boolean = conic.collapsed()
    override fun controlsInside(): Boolean = conic.controlsInside()
    override fun dxdyAtT(t: Double): SkDVector = conic.dxdyAtT(t)
    override fun ptAtT(t: Double): SkDPoint = conic.ptAtT(t)

    override fun hullIntersects(quad: SkDQuad, isLinearOut: BooleanArray): Boolean =
        // Upstream `SkTConic::hullIntersects(SkDQuad)` calls
        // `quad.hullIntersects(fConic, ...)`.
        quad.hullIntersects(this.conic, isLinearOut)

    override fun hullIntersects(conic: SkDConic, isLinearOut: BooleanArray): Boolean =
        this.conic.hullIntersects(conic, isLinearOut)

    override fun hullIntersects(cubic: SkDCubic, isLinearOut: BooleanArray): Boolean =
        // Mirrors `SkTConic::hullIntersects(SkDCubic)` →
        // `cubic.hullIntersects(fConic, ...)`.
        cubic.hullIntersects(this.conic, isLinearOut)

    override fun hullIntersects(curve: SkTCurve, isLinearOut: BooleanArray): Boolean =
        when (curve) {
            is SkTQuad -> curve.quad.hullIntersects(this.conic, isLinearOut)
            is SkTConic -> curve.conic.hullIntersects(this.conic, isLinearOut)
            is SkTCubic -> curve.cubic.hullIntersects(this.conic, isLinearOut)
            else -> error("Unknown SkTCurve subtype : ${curve::class}")
        }

    override fun intersectRay(intersections: SkIntersections, line: SkDLine): Int =
        intersections.intersectRay(conic, line)

    override fun make(): SkTCurve = SkTConic()

    override fun otherPts(oddMan: Int, endPt: Array<SkDPoint?>) {
        conic.otherPts(oddMan, endPt)
    }

    override fun setBounds(out: SkDRect) {
        out.setBounds(conic)
    }

    override fun subDivide(t1: Double, t2: Double, out: SkTCurve) {
        require(out is SkTConic)
        out.conic = conic.subDivide(t1, t2)
    }
}

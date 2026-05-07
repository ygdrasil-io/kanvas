/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `src/pathops/SkLineParameters.h` — a parameterized
 * line equation `a·x + b·y + c = 0` used to compute the perpendicular
 * distance from a point to the line. When `a² + b² == 1` the line is
 * normalized and the raw distance is the true Euclidean distance ;
 * otherwise distances must be divided by `sqrt(normalSquared())`.
 *
 * Phase D1.1.c — full port (no upstream methods deferred). Used by
 * `SkDQuad.isLinear`, `SkDCubic.isLinear`, `SkDConic.isLinear`, and
 * by the curve-curve intersection machinery (D1.1.e).
 *
 * Sources (per upstream comment) :
 *   Sederberg & Nishita, "Curve intersection using Bézier clipping",
 *   Computer-Aided Design, vol. 22 no. 9, November 1990, pp. 538-549.
 *   Online : http://cagd.cs.byu.edu/~tom/papers/bezclip.pdf
 */
package org.skia.pathops.internal

import kotlin.math.sqrt

internal class SkLineParameters {
    private var fA: Double = 0.0
    private var fB: Double = 0.0
    private var fC: Double = 0.0

    /** dx of the line (== `b`). */
    fun dx(): Double = fB
    /** dy of the line (== `−a`, sign convention from upstream). */
    fun dy(): Double = -fA

    /**
     * Raw perpendicular distance from [pt] to the line. To get the true
     * Euclidean distance, either call [normalize] first or divide by
     * `sqrt(normalSquared())`.
     */
    fun pointDistance(pt: SkDPoint): Double = fA * pt.x + fB * pt.y + fC

    /** Raw perpendicular distance from `quad[1]` to the line. */
    fun controlPtDistance(quad: SkDQuad): Double = fA * quad[1].x + fB * quad[1].y + fC

    /** Raw distance from `cubic[index]` to the line. [index] must be 1 or 2. */
    fun controlPtDistance(cubic: SkDCubic, index: Int): Double {
        require(index == 1 || index == 2)
        return fA * cubic[index].x + fB * cubic[index].y + fC
    }

    fun normalSquared(): Double = fA * fA + fB * fB

    /**
     * Scale `(a, b, c)` so that `a² + b² == 1`. After this, [pointDistance]
     * returns the true Euclidean perpendicular distance. Returns false
     * if the normal is approximately zero (degenerate line).
     */
    fun normalize(): Boolean {
        val normal = sqrt(normalSquared())
        if (approximately_zero(normal)) {
            fA = 0.0; fB = 0.0; fC = 0.0
            return false
        }
        val recip = 1 / normal
        fA *= recip
        fB *= recip
        fC *= recip
        return true
    }

    // ─── Line endpoints ─────────────────────────────────────────────

    /** Set parameters from the segment endpoints. Mirrors `lineEndPoints`. */
    fun lineEndPoints(line: SkDLine) {
        val s = line[0]; val e = line[1]
        fA = s.y - e.y
        fB = e.x - s.x
        fC = s.x * e.y - e.x * s.y
    }

    // ─── Quadratic endpoints ────────────────────────────────────────

    /**
     * Set parameters from the quad's `[s..e]` endpoints. Mirrors
     * `quadEndPoints(SkDQuad, int s, int e)`.
     */
    fun quadEndPoints(quad: SkDQuad, s: Int, e: Int) {
        fA = quad[s].y - quad[e].y
        fB = quad[e].x - quad[s].x
        fC = quad[s].x * quad[e].y - quad[e].x * quad[s].y
    }

    /**
     * Try to compute the line through quad[0]→quad[1], then quad[0]→quad[2]
     * if degenerate. Returns false only if all three points are coincident.
     * Mirrors `bool quadEndPoints(const SkDQuad&)`.
     */
    fun quadEndPoints(quad: SkDQuad): Boolean {
        quadEndPoints(quad, 0, 1)
        if (dy() != 0.0) return true
        if (dx() == 0.0) {
            quadEndPoints(quad, 0, 2)
            return false
        }
        if (dx() < 0) return true
        // FIXME (upstream) : after switching to round sort, remove this
        if (quad[0].y > quad[2].y) fA = DBL_EPSILON
        return true
    }

    /** Get the perpendicular distance from `quad[2]` to the line through quad[0]/quad[1]. */
    fun quadPart(part: SkDQuad): Double {
        quadEndPoints(part)
        return pointDistance(part[2])
    }

    /**
     * Sample the perpendicular distance at each quad control point and
     * write `(t, distance)` pairs to [distance]. `t` runs `0, 1/2, 1`.
     * Mirrors `quadDistanceY`.
     */
    fun quadDistanceY(pts: SkDQuad, distance: SkDQuad) {
        val oneHalf = 1 / 2.0
        for (i in 0 until 3) {
            distance[i] = SkDPoint(i * oneHalf, fA * pts[i].x + fB * pts[i].y + fC)
        }
    }

    // ─── Cubic endpoints ────────────────────────────────────────────

    fun cubicEndPoints(cubic: SkDCubic, s: Int, e: Int) {
        fA = cubic[s].y - cubic[e].y
        fB = cubic[e].x - cubic[s].x
        fC = cubic[s].x * cubic[e].y - cubic[e].x * cubic[s].y
    }

    /**
     * Try cubic[0]→cubic[1], then [0]→[2], then [0]→[3] if previous
     * candidates are degenerate. Returns false only if all four points
     * are colinear. Mirrors `bool cubicEndPoints(const SkDCubic&)`.
     */
    fun cubicEndPoints(cubic: SkDCubic): Boolean {
        var endIndex = 1
        cubicEndPoints(cubic, 0, endIndex)
        if (dy() != 0.0) return true
        if (dx() == 0.0) {
            endIndex++
            cubicEndPoints(cubic, 0, endIndex)
            if (dy() != 0.0) return true
            if (dx() == 0.0) {
                endIndex++
                cubicEndPoints(cubic, 0, endIndex) // line
                return false
            }
        }
        // FIXME (upstream) : after switching to round sort, remove fA bump
        if (dx() < 0) return true
        // tangent on x-axis — look at next control point to break tie
        endIndex++
        if (NotAlmostEqualUlps(cubic[0].y, cubic[endIndex].y)) {
            if (cubic[0].y > cubic[endIndex].y) fA = DBL_EPSILON
            return true
        }
        if (endIndex == 3) return true
        if (cubic[0].y > cubic[3].y) fA = DBL_EPSILON
        return true
    }

    /**
     * Get the larger perpendicular distance among the cubic's control
     * points (`pts[2]` or `pts[3]` depending on which one is significant).
     * Mirrors `double cubicPart(const SkDCubic&)`.
     */
    fun cubicPart(part: SkDCubic): Double {
        cubicEndPoints(part)
        // upstream : `if (part[0] == part[1] || ((SkDLine&)part[0]).nearRay(part[2]))`
        //   reinterprets part[0/1] as an SkDLine. We construct the line
        //   explicitly for type-safety in Kotlin.
        if (part[0] == part[1] || SkDLine(arrayOf(part[0], part[1])).nearRay(part[2])) {
            return pointDistance(part[3])
        }
        return pointDistance(part[2])
    }

    /**
     * Sample the perpendicular distance at each cubic control point and
     * write `(t, distance)` pairs to [distance]. `t` runs `0, 1/3, 2/3, 1`.
     * Mirrors `cubicDistanceY`.
     */
    fun cubicDistanceY(pts: SkDCubic, distance: SkDCubic) {
        val oneThird = 1 / 3.0
        for (i in 0 until 4) {
            distance[i] = SkDPoint(i * oneThird, fA * pts[i].x + fB * pts[i].y + fC)
        }
    }
}

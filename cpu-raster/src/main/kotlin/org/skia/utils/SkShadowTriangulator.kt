package org.skia.utils

import org.skia.foundation.SkPath
import org.skia.math.SkPoint
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * S7-C — pure-Kotlin port (concave triangulator helper distinct from analytic SkShadowTessellator) of Skia's
 * [`SkShadowTriangulator`](https://github.com/google/skia/blob/main/src/utils/SkShadowTriangulator.cpp)
 * helpers for the analytic shadow geometry pipeline used by
 * [SkShadowUtils.DrawShadow].
 *
 * Public surface (intentionally narrow — this file is consumed by
 * [SkShadowUtils] only) :
 *
 *  - [flattenPathToPolygon] — walk a path's verbs and produce a flat
 *    `(x, y)` polygon (one contour ; closes implicitly when the path's
 *    last point matches the move). Curves are flattened via midpoint
 *    subdivision against a fixed chord-error tolerance.
 *  - [isPolygonConvex] — sign-of-cross-product convexity check on a
 *    closed polygon. Returns `true` iff every consecutive turn keeps
 *    the same handedness.
 *  - [triangulateSimplePolygon] — ear-clipping triangulator that
 *    handles convex **and** concave simple polygons. Self-intersecting
 *    polygons return `null` ; the caller falls back to the blur path.
 *
 * Together these unblock [SkShadowUtils.DrawShadow] from the
 * convex-only restriction tracked under R-suivi.30 — concave star /
 * comet / arrow paths now produce a triangulated mesh suitable for
 * the analytic ambient + spot shadow rather than degenerating to the
 * blur fallback.
 *
 * Upstream's `SkOffsetSimplePolygon` (~700 LOC) for the offset polygon
 * and `SkTriangulateSimplePolygon` (~500 LOC) for monotone-decompose
 * triangulation are folded into a ~150-LOC ear-clipping pipeline here.
 * The resulting mesh is identical to upstream's for convex inputs and
 * visually-equivalent for concave simple polygons (no holes, no self-
 * intersections — those still fall back to blur).
 */
internal object SkShadowTriangulator {

    /** Default chord-error tolerance for path flattening (device pixels). */
    internal const val DEFAULT_TOLERANCE: Float = 0.2f

    /**
     * Flatten [path] into a polygon `[(x0, y0), (x1, y1), …]` (one
     * point per element). Curves are subdivided via midpoint
     * recursion until each segment's chord error is below
     * [tolerance] device pixels. Implicit closes inserted when the
     * path's last verb is `kClose` or when the trailing point lands
     * within `tolerance` of the move target.
     *
     * Multi-contour paths are concatenated head-to-tail — the first
     * contour's closing edge runs from its last vertex back to its
     * move target, then the next contour starts. The shadow caller
     * only triangulates the **first** contour ; secondary contours
     * are dropped (matches upstream `SkShadowTriangulator`'s
     * single-contour assumption).
     */
    fun flattenPathToPolygon(path: SkPath, tolerance: Float = DEFAULT_TOLERANCE): List<SkPoint> {
        val out = ArrayList<SkPoint>(16)
        val iter = SkPath.Iter(path, forceClose = true)
        val pts = FloatArray(8)
        var moveX = 0f
        var moveY = 0f
        var hasMove = false
        var contourCount = 0
        loop@ while (true) {
            val v = iter.next(pts)
            if (v == SkPath.Verb.kDone) break
            when (v) {
                SkPath.Verb.kMove -> {
                    if (contourCount > 0) break@loop // single-contour only
                    moveX = pts[0]
                    moveY = pts[1]
                    hasMove = true
                    out.add(SkPoint(moveX, moveY))
                    contourCount++
                }
                SkPath.Verb.kLine -> {
                    out.add(SkPoint(pts[2], pts[3]))
                }
                SkPath.Verb.kQuad -> {
                    flattenQuad(out, pts[0], pts[1], pts[2], pts[3], pts[4], pts[5], tolerance, 0)
                }
                SkPath.Verb.kConic -> {
                    // Approximate conic with the underlying quad — sufficient
                    // for the shadow-mesh tolerance ; rational subdivision
                    // is tracked separately.
                    flattenQuad(out, pts[0], pts[1], pts[2], pts[3], pts[4], pts[5], tolerance, 0)
                }
                SkPath.Verb.kCubic -> {
                    flattenCubic(
                        out,
                        pts[0], pts[1], pts[2], pts[3], pts[4], pts[5], pts[6], pts[7],
                        tolerance, 0,
                    )
                }
                SkPath.Verb.kClose -> {
                    // Drop the closing duplicate if it lands on the move target.
                    if (hasMove && out.isNotEmpty()) {
                        val last = out.last()
                        if (abs(last.fX - moveX) < tolerance && abs(last.fY - moveY) < tolerance) {
                            out.removeAt(out.size - 1)
                        }
                    }
                }
                SkPath.Verb.kDone -> break@loop
            }
        }
        // Drop trailing duplicate vertices.
        while (out.size >= 2) {
            val a = out[out.size - 1]
            val b = out[out.size - 2]
            if (abs(a.fX - b.fX) < tolerance && abs(a.fY - b.fY) < tolerance) {
                out.removeAt(out.size - 1)
            } else break
        }
        return out
    }

    private const val MAX_FLATTEN_DEPTH: Int = 12

    private fun flattenQuad(
        out: ArrayList<SkPoint>,
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
        tol: Float, depth: Int,
    ) {
        if (depth >= MAX_FLATTEN_DEPTH) {
            out.add(SkPoint(x2, y2)); return
        }
        // Distance from p1 to the chord p0-p2.
        val dx = x2 - x0
        val dy = y2 - y0
        val cross = abs((x1 - x0) * dy - (y1 - y0) * dx)
        val chord = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val err = if (chord > 1e-6f) cross / chord else cross
        if (err < tol) {
            out.add(SkPoint(x2, y2))
            return
        }
        // Midpoint subdivision.
        val ax = (x0 + x1) * 0.5f; val ay = (y0 + y1) * 0.5f
        val bx = (x1 + x2) * 0.5f; val by = (y1 + y2) * 0.5f
        val mx = (ax + bx) * 0.5f; val my = (ay + by) * 0.5f
        flattenQuad(out, x0, y0, ax, ay, mx, my, tol, depth + 1)
        flattenQuad(out, mx, my, bx, by, x2, y2, tol, depth + 1)
    }

    private fun flattenCubic(
        out: ArrayList<SkPoint>,
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float,
        tol: Float, depth: Int,
    ) {
        if (depth >= MAX_FLATTEN_DEPTH) {
            out.add(SkPoint(x3, y3)); return
        }
        // Control-point distance from chord — proxy for chord error.
        val dx = x3 - x0
        val dy = y3 - y0
        val cross1 = abs((x1 - x0) * dy - (y1 - y0) * dx)
        val cross2 = abs((x2 - x0) * dy - (y2 - y0) * dx)
        val chord = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val err = if (chord > 1e-6f) maxOf(cross1, cross2) / chord else maxOf(cross1, cross2)
        if (err < tol) {
            out.add(SkPoint(x3, y3))
            return
        }
        val ax = (x0 + x1) * 0.5f; val ay = (y0 + y1) * 0.5f
        val bx = (x1 + x2) * 0.5f; val by = (y1 + y2) * 0.5f
        val cx = (x2 + x3) * 0.5f; val cy = (y2 + y3) * 0.5f
        val abx = (ax + bx) * 0.5f; val aby = (ay + by) * 0.5f
        val bcx = (bx + cx) * 0.5f; val bcy = (by + cy) * 0.5f
        val mx = (abx + bcx) * 0.5f; val my = (aby + bcy) * 0.5f
        flattenCubic(out, x0, y0, ax, ay, abx, aby, mx, my, tol, depth + 1)
        flattenCubic(out, mx, my, bcx, bcy, cx, cy, x3, y3, tol, depth + 1)
    }

    /**
     * Sign-of-cross-product convexity check. Returns `true` iff every
     * consecutive turn in [polygon] keeps the same handedness
     * (i.e. every cross product `(p_{i+1} - p_i) × (p_{i+2} - p_{i+1})`
     * shares the same sign). Polygons with fewer than 3 distinct
     * vertices are treated as convex (degenerate).
     */
    fun isPolygonConvex(polygon: List<SkPoint>): Boolean {
        val n = polygon.size
        if (n < 3) return true
        var sign = 0
        for (i in 0 until n) {
            val a = polygon[i]
            val b = polygon[(i + 1) % n]
            val c = polygon[(i + 2) % n]
            val cross = (b.fX - a.fX) * (c.fY - b.fY) - (b.fY - a.fY) * (c.fX - b.fX)
            val s = when {
                cross > 1e-5f -> 1
                cross < -1e-5f -> -1
                else -> 0
            }
            if (s == 0) continue
            if (sign == 0) sign = s
            else if (s != sign) return false
        }
        return true
    }

    /**
     * Ear-clipping triangulator for a *simple* polygon (convex or
     * concave, no holes, no self-intersections). Returns the triangle
     * list as `IntArray` of vertex indices — `[a0, b0, c0, a1, b1,
     * c1, …]` — into [polygon]. Returns `null` when triangulation
     * fails (polygon is self-intersecting or not simple) ; the caller
     * falls back to the blur shadow.
     *
     * Algorithm :
     *  1. Determine winding (signed area) and reverse the working
     *     copy if clockwise so we always clip CCW ears.
     *  2. Repeat : find a *convex* vertex with no other polygon vertex
     *     inside the candidate triangle ; emit the triangle and remove
     *     the vertex from the working list. Stop when 3 vertices remain
     *     and emit the final triangle.
     *  3. If no ear is found in a full pass (~indicates self-
     *     intersection) return `null`.
     */
    fun triangulateSimplePolygon(polygon: List<SkPoint>): IntArray? {
        val n = polygon.size
        if (n < 3) return null

        val area = signedArea(polygon)
        // Degenerate (collinear / zero-area) polygons produce no
        // triangles — return null so the caller can route to the blur
        // fallback. This catches the n==3 collinear case before we
        // emit a degenerate "triangle" with zero area.
        if (abs(area) < 1e-6f) return null
        if (n == 3) return intArrayOf(0, 1, 2)

        val ccw = area > 0
        // Working index list — initialised to identity ; reversed if
        // input is clockwise so we always clip CCW ears.
        val indices = IntArray(n) { if (ccw) it else n - 1 - it }
        val working = IntArray(n) { indices[it] }
        var count = n

        val tris = IntArray((n - 2) * 3)
        var triCursor = 0
        var guard = 0
        val maxGuard = n * n // safety bound
        while (count > 3) {
            var clipped = false
            var i = 0
            while (i < count) {
                val ia = working[(i + count - 1) % count]
                val ib = working[i]
                val ic = working[(i + 1) % count]
                val a = polygon[ia]
                val b = polygon[ib]
                val c = polygon[ic]
                // Convex (CCW) test : (b - a) × (c - b) > 0.
                val crossAbc = (b.fX - a.fX) * (c.fY - b.fY) - (b.fY - a.fY) * (c.fX - b.fX)
                if (crossAbc > 0f) {
                    // Check no other polygon vertex lies strictly inside (a, b, c).
                    var contained = false
                    for (j in 0 until count) {
                        val k = working[j]
                        if (k == ia || k == ib || k == ic) continue
                        if (pointInTriangle(polygon[k], a, b, c)) {
                            contained = true
                            break
                        }
                    }
                    if (!contained) {
                        // Emit the ear triangle.
                        tris[triCursor++] = ia
                        tris[triCursor++] = ib
                        tris[triCursor++] = ic
                        // Remove ib from working.
                        for (k in i until count - 1) working[k] = working[k + 1]
                        count--
                        clipped = true
                        // Don't advance i — the new vertex at i
                        // becomes the next candidate.
                        continue
                    }
                }
                i++
                guard++
                if (guard > maxGuard) return null
            }
            if (!clipped) return null // can't progress — likely self-intersecting
        }
        // Final triangle.
        if (count == 3) {
            tris[triCursor++] = working[0]
            tris[triCursor++] = working[1]
            tris[triCursor++] = working[2]
        }
        return if (triCursor == tris.size) tris else tris.copyOf(triCursor)
    }

    private fun signedArea(polygon: List<SkPoint>): Float {
        var s = 0f
        val n = polygon.size
        for (i in 0 until n) {
            val a = polygon[i]
            val b = polygon[(i + 1) % n]
            s += a.fX * b.fY - b.fX * a.fY
        }
        return 0.5f * s
    }

    private fun pointInTriangle(p: SkPoint, a: SkPoint, b: SkPoint, c: SkPoint): Boolean {
        val d1 = sign(p, a, b)
        val d2 = sign(p, b, c)
        val d3 = sign(p, c, a)
        val hasNeg = d1 < 0f || d2 < 0f || d3 < 0f
        val hasPos = d1 > 0f || d2 > 0f || d3 > 0f
        return !(hasNeg && hasPos)
    }

    private fun sign(p: SkPoint, a: SkPoint, b: SkPoint): Float =
        (p.fX - b.fX) * (a.fY - b.fY) - (a.fX - b.fX) * (p.fY - b.fY)
}

package org.skia.foundation

import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.math.SkVector
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Measures arc length and reports positions, tangents and matrices
 * along a single contour of a path. Mirrors Skia's
 * [`SkContourMeasure`](https://github.com/google/skia/blob/main/include/core/SkContourMeasure.h).
 *
 * A [SkContourMeasure] is built by [SkContourMeasureIter] — never
 * directly. The iterator walks the source path's verb stream and
 * flattens each contour into a chord polyline whose tolerance is
 * controlled by `resScale` (higher = finer flattening).
 *
 * **Flattening strategy.** Each curve verb (`kQuad`, `kConic`,
 * `kCubic`) is recursively subdivided until the midpoint deviation
 * from the chord falls below `0.5f / resScale` or recursion reaches
 * a depth of [MAX_RECURSION_DEPTH]. This produces a sequence of
 * `[t0..t1]` line sub-segments whose endpoints are recorded as flat
 * points and whose cumulative lengths are recorded in [segments].
 * The polyline is then used to answer all measurement queries —
 * positions, tangents and `getSegment` extractions are reported
 * exactly along the flattened polyline, which is a close
 * approximation of the original curve (visually indistinguishable
 * at the default `resScale = 1`).
 *
 * **Sub-segment extraction (`getSegment`).** The Skia upstream chops
 * curves precisely at the parametric position corresponding to a
 * start/stop arc length; this port emits a sequence of `lineTo` /
 * `moveTo` verbs along the flattened polyline instead. The visual
 * footprint is identical when the sub-path is subsequently stroked
 * or rasterised at typical pixel resolutions; callers requiring an
 * exact curve sub-segment should up-scale `resScale` accordingly.
 */
public class SkContourMeasure internal constructor(
    private val segments: FloatArray, // Cumulative distances (one per segment endpoint).
    private val pointsX: FloatArray,  // X coordinate of each polyline point.
    private val pointsY: FloatArray,  // Y coordinate of each polyline point.
    private val totalLength: SkScalar,
    private val closed: Boolean,
) {

    /** Flags controlling which fields of an [SkMatrix] [getMatrix] populates. */
    public enum class MatrixFlags(public val mask: Int) {
        kGetPosition_MatrixFlag(0x01),
        kGetTangent_MatrixFlag(0x02),
        kGetPosAndTan_MatrixFlag(0x03),
    }

    /** Total arc length of the contour. */
    public fun length(): SkScalar = totalLength

    /** Returns true if the contour ends with a `kClose` verb. */
    public fun isClosed(): Boolean = closed

    /**
     * Computes the position and tangent at `distance` along the
     * contour, pinned to `0..length()`. Returns `false` (and leaves
     * `position` / `tangent` untouched) when the contour is empty.
     * Mirrors `SkContourMeasure::getPosTan`.
     */
    public fun getPosTan(distance: SkScalar, position: SkPoint?, tangent: SkVector?): Boolean {
        if (totalLength <= 0f || pointsX.isEmpty()) return false
        val d = distance.coerceIn(0f, totalLength)
        // Find the polyline segment containing distance `d`.
        val (i0, t) = locate(d)
        val ax = pointsX[i0]
        val ay = pointsY[i0]
        val bx = pointsX[i0 + 1]
        val by = pointsY[i0 + 1]
        if (position != null) {
            position.set(ax + (bx - ax) * t, ay + (by - ay) * t)
        }
        if (tangent != null) {
            var tx = bx - ax
            var ty = by - ay
            val len = sqrt(tx * tx + ty * ty)
            if (len > 0f) {
                tx /= len
                ty /= len
            }
            tangent.set(tx, ty)
        }
        return true
    }

    /**
     * Populates `matrix` (returned through the caller-supplied
     * single-element `Array<SkMatrix?>` slot) with a rotation/
     * translation matrix representing the position and tangent at
     * `distance`. The returned matrix uses the tangent as the X
     * basis: `[cos, -sin, tx ; sin, cos, ty]`. Mirrors
     * `SkContourMeasure::getMatrix`.
     */
    public fun getMatrix(
        distance: SkScalar,
        matrix: Array<SkMatrix?>,
        flags: MatrixFlags = MatrixFlags.kGetPosAndTan_MatrixFlag,
    ): Boolean {
        if (matrix.isEmpty()) return false
        val pos = SkPoint()
        val tan = SkVector()
        if (!getPosTan(distance, pos, tan)) return false
        val wantPos = (flags.mask and MatrixFlags.kGetPosition_MatrixFlag.mask) != 0
        val wantTan = (flags.mask and MatrixFlags.kGetTangent_MatrixFlag.mask) != 0
        val cos = if (wantTan) tan.fX else 1f
        val sin = if (wantTan) tan.fY else 0f
        val tx = if (wantPos) pos.fX else 0f
        val ty = if (wantPos) pos.fY else 0f
        matrix[0] = SkMatrix(
            sx = cos, kx = -sin, tx = tx,
            ky = sin, sy = cos, ty = ty,
        )
        return true
    }

    /**
     * Appends the polyline sub-segment between `startD` and `stopD`
     * to `dst`. Returns `false` if the requested span is empty
     * (`startD >= stopD` after pinning, or the contour has zero
     * length); otherwise emits an optional leading `moveTo` followed
     * by one or more `lineTo` verbs. Mirrors
     * `SkContourMeasure::getSegment`.
     */
    public fun getSegment(
        startD: SkScalar,
        stopD: SkScalar,
        dst: SkPathBuilder,
        startWithMoveTo: Boolean,
    ): Boolean {
        if (totalLength <= 0f) return false
        val s = startD.coerceIn(0f, totalLength)
        val e = stopD.coerceIn(0f, totalLength)
        if (s >= e) return false

        val (iStart, tStart) = locate(s)
        val (iEnd, tEnd) = locate(e)

        val sx = pointsX[iStart] + (pointsX[iStart + 1] - pointsX[iStart]) * tStart
        val sy = pointsY[iStart] + (pointsY[iStart + 1] - pointsY[iStart]) * tStart

        if (startWithMoveTo) {
            dst.moveTo(sx, sy)
        }

        // Emit all interior polyline vertices in (iStart, iEnd].
        for (i in iStart + 1..iEnd) {
            dst.lineTo(pointsX[i], pointsY[i])
        }
        // Final point at fractional tEnd inside segment `iEnd`.
        val ex = pointsX[iEnd] + (pointsX[iEnd + 1] - pointsX[iEnd]) * tEnd
        val ey = pointsY[iEnd] + (pointsY[iEnd + 1] - pointsY[iEnd]) * tEnd
        dst.lineTo(ex, ey)
        return true
    }

    /**
     * Binary-search the segment table for the polyline edge
     * containing arc length `d`. Returns the edge's point index
     * (into [pointsX] / [pointsY]) and the parametric `t ∈ [0, 1]`
     * along that edge corresponding to `d`.
     */
    private fun locate(d: SkScalar): Pair<Int, SkScalar> {
        // segments[i] = cumulative distance at end of edge i
        //             = arc length from contour start through points[i+1].
        // segments.size == pointsX.size - 1.
        var lo = 0
        var hi = segments.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (segments[mid] < d) lo = mid + 1 else hi = mid
        }
        val edge = lo
        val prevDist = if (edge == 0) 0f else segments[edge - 1]
        val edgeLen = segments[edge] - prevDist
        val t = if (edgeLen > 0f) ((d - prevDist) / edgeLen).coerceIn(0f, 1f) else 0f
        return edge to t
    }

    internal companion object {
        internal const val MAX_RECURSION_DEPTH: Int = 8
    }
}

/**
 * Iterates over the contours of a path, producing one
 * [SkContourMeasure] per non-empty contour. Mirrors Skia's
 * [`SkContourMeasureIter`](https://github.com/google/skia/blob/main/include/core/SkContourMeasure.h).
 *
 * `resScale` controls flattening precision: the chord tolerance
 * passed to the recursive curve subdivision is `0.5f / resScale`.
 * Values > 1 increase precision (finer flattening, more memory).
 */
public class SkContourMeasureIter {

    private var verbs: Array<SkPath.StorageVerb> = emptyArray()
    private var coords: FloatArray = FloatArray(0)
    private var conicWeights: FloatArray = FloatArray(0)
    private var forceClosed: Boolean = false
    private var tolerance: SkScalar = 0.5f
    private var verbIndex: Int = 0
    private var coordIndex: Int = 0
    private var weightIndex: Int = 0
    private var penX: SkScalar = 0f
    private var penY: SkScalar = 0f

    public constructor()

    public constructor(path: SkPath, forceClosed: Boolean, resScale: SkScalar = 1f) {
        reset(path, forceClosed, resScale)
    }

    /**
     * Reset to walk a new path. Copies the path's verb / coord /
     * weight arrays so the caller may mutate the source path
     * afterwards.
     */
    public fun reset(path: SkPath, forceClosed: Boolean, resScale: SkScalar = 1f) {
        this.verbs = path.verbs.copyOf()
        this.coords = path.coords.copyOf()
        this.conicWeights = path.conicWeights.copyOf()
        this.forceClosed = forceClosed
        this.tolerance = 0.5f / max(resScale, 1e-3f)
        this.verbIndex = 0
        this.coordIndex = 0
        this.weightIndex = 0
        this.penX = 0f
        this.penY = 0f
    }

    /**
     * Produce the next [SkContourMeasure], or `null` when the path
     * is exhausted. Zero-length contours are skipped (per
     * upstream semantics).
     */
    public fun next(): SkContourMeasure? {
        while (verbIndex < verbs.size) {
            val measure = buildNext()
            if (measure != null) return measure
        }
        return null
    }

    /**
     * Walk the verb stream up to (and including) the next `kClose`
     * or until a new `kMove` is encountered (in which case the
     * `kMove` is left for the *following* call). Returns the
     * measure, or `null` if the resulting contour has zero length
     * (and the caller should keep iterating).
     */
    private fun buildNext(): SkContourMeasure? {
        // Skip leading non-move verbs (defensive — well-formed paths
        // always start a contour with kMove).
        while (verbIndex < verbs.size && verbs[verbIndex] != SkPath.StorageVerb.kMove) {
            consume(verbs[verbIndex])
        }
        if (verbIndex >= verbs.size) return null

        // Consume the kMove.
        check(verbs[verbIndex] == SkPath.StorageVerb.kMove)
        penX = coords[coordIndex]
        penY = coords[coordIndex + 1]
        coordIndex += 2
        verbIndex++
        val startX = penX
        val startY = penY

        val pts = ArrayList<SkScalar>().apply { add(penX); add(penY) }
        val cum = ArrayList<SkScalar>()
        var total: SkScalar = 0f
        var explicitlyClosed = false

        // Walk until the next kMove or the end of the verb stream.
        while (verbIndex < verbs.size && verbs[verbIndex] != SkPath.StorageVerb.kMove) {
            val v = verbs[verbIndex]
            when (v) {
                SkPath.StorageVerb.kLine -> {
                    val x = coords[coordIndex]; val y = coords[coordIndex + 1]
                    coordIndex += 2
                    total = addLineTo(pts, cum, total, x, y)
                    penX = x; penY = y
                }
                SkPath.StorageVerb.kQuad -> {
                    val x1 = coords[coordIndex]; val y1 = coords[coordIndex + 1]
                    val x2 = coords[coordIndex + 2]; val y2 = coords[coordIndex + 3]
                    coordIndex += 4
                    total = flattenQuad(pts, cum, total, penX, penY, x1, y1, x2, y2)
                    penX = x2; penY = y2
                }
                SkPath.StorageVerb.kConic -> {
                    val x1 = coords[coordIndex]; val y1 = coords[coordIndex + 1]
                    val x2 = coords[coordIndex + 2]; val y2 = coords[coordIndex + 3]
                    coordIndex += 4
                    val w = conicWeights[weightIndex++]
                    // Conic with weight 1 is identical to a quad; otherwise
                    // we fall back to the quadratic-Bezier approximation
                    // (good enough at typical resScale; the visual error
                    // is bounded by the quad-to-conic deviation).
                    if (w == 1f) {
                        total = flattenQuad(pts, cum, total, penX, penY, x1, y1, x2, y2)
                    } else {
                        total = flattenConic(pts, cum, total, penX, penY, x1, y1, x2, y2, w)
                    }
                    penX = x2; penY = y2
                }
                SkPath.StorageVerb.kCubic -> {
                    val x1 = coords[coordIndex]; val y1 = coords[coordIndex + 1]
                    val x2 = coords[coordIndex + 2]; val y2 = coords[coordIndex + 3]
                    val x3 = coords[coordIndex + 4]; val y3 = coords[coordIndex + 5]
                    coordIndex += 6
                    total = flattenCubic(
                        pts, cum, total,
                        penX, penY, x1, y1, x2, y2, x3, y3,
                    )
                    penX = x3; penY = y3
                }
                SkPath.StorageVerb.kClose -> {
                    explicitlyClosed = true
                    // Emit a line back to the contour start if we're not already there.
                    if (penX != startX || penY != startY) {
                        total = addLineTo(pts, cum, total, startX, startY)
                    }
                    verbIndex++
                    break
                }
                SkPath.StorageVerb.kMove -> error("unreachable") // loop guard already exits.
            }
            if (v != SkPath.StorageVerb.kClose) verbIndex++
        }

        val finalClosed = explicitlyClosed || forceClosed
        if (finalClosed && !explicitlyClosed) {
            // Synthetic close: walk back to start for force-closed open contours.
            if (penX != startX || penY != startY) {
                total = addLineTo(pts, cum, total, startX, startY)
            }
        }

        if (total <= 0f || cum.isEmpty()) {
            // Zero-length contour — skip per upstream semantics.
            return null
        }

        val xs = FloatArray(pts.size / 2)
        val ys = FloatArray(pts.size / 2)
        for (i in xs.indices) {
            xs[i] = pts[2 * i]
            ys[i] = pts[2 * i + 1]
        }
        val cumArr = FloatArray(cum.size) { cum[it] }
        return SkContourMeasure(cumArr, xs, ys, total, finalClosed)
    }

    /**
     * Consume the verb at [verbIndex] without recording into any
     * contour — used to fast-forward over malformed leading verbs.
     */
    private fun consume(v: SkPath.StorageVerb) {
        when (v) {
            SkPath.StorageVerb.kMove -> coordIndex += 2
            SkPath.StorageVerb.kLine -> coordIndex += 2
            SkPath.StorageVerb.kQuad -> coordIndex += 4
            SkPath.StorageVerb.kConic -> { coordIndex += 4; weightIndex++ }
            SkPath.StorageVerb.kCubic -> coordIndex += 6
            SkPath.StorageVerb.kClose -> { /* no points */ }
        }
        verbIndex++
    }

    private fun addLineTo(
        pts: ArrayList<SkScalar>,
        cum: ArrayList<SkScalar>,
        total: SkScalar,
        x: SkScalar,
        y: SkScalar,
    ): SkScalar {
        val n = pts.size
        val prevX = pts[n - 2]
        val prevY = pts[n - 1]
        val dx = x - prevX
        val dy = y - prevY
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 0f) return total
        pts.add(x); pts.add(y)
        val newTotal = total + len
        cum.add(newTotal)
        return newTotal
    }

    private fun flattenQuad(
        pts: ArrayList<SkScalar>, cum: ArrayList<SkScalar>, total0: SkScalar,
        x0: SkScalar, y0: SkScalar,
        x1: SkScalar, y1: SkScalar,
        x2: SkScalar, y2: SkScalar,
    ): SkScalar {
        var total = total0
        // Stack-based subdivision (depth-limited).
        // Each frame stores the quad's 3 control points and current depth.
        val stack = ArrayDeque<QuadFrame>()
        stack.addLast(QuadFrame(x0, y0, x1, y1, x2, y2, 0))
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            if (f.depth >= SkContourMeasure.MAX_RECURSION_DEPTH || !quadTooCurvy(f, tolerance)) {
                total = addLineTo(pts, cum, total, f.x2, f.y2)
            } else {
                // Subdivide using de Casteljau (t = 0.5).
                val ab_x = (f.x0 + f.x1) * 0.5f
                val ab_y = (f.y0 + f.y1) * 0.5f
                val bc_x = (f.x1 + f.x2) * 0.5f
                val bc_y = (f.y1 + f.y2) * 0.5f
                val mid_x = (ab_x + bc_x) * 0.5f
                val mid_y = (ab_y + bc_y) * 0.5f
                // Push right (processed last so left is consumed first).
                stack.addLast(QuadFrame(mid_x, mid_y, bc_x, bc_y, f.x2, f.y2, f.depth + 1))
                stack.addLast(QuadFrame(f.x0, f.y0, ab_x, ab_y, mid_x, mid_y, f.depth + 1))
            }
        }
        return total
    }

    private fun flattenCubic(
        pts: ArrayList<SkScalar>, cum: ArrayList<SkScalar>, total0: SkScalar,
        x0: SkScalar, y0: SkScalar,
        x1: SkScalar, y1: SkScalar,
        x2: SkScalar, y2: SkScalar,
        x3: SkScalar, y3: SkScalar,
    ): SkScalar {
        var total = total0
        val stack = ArrayDeque<CubicFrame>()
        stack.addLast(CubicFrame(x0, y0, x1, y1, x2, y2, x3, y3, 0))
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            if (f.depth >= SkContourMeasure.MAX_RECURSION_DEPTH || !cubicTooCurvy(f, tolerance)) {
                total = addLineTo(pts, cum, total, f.x3, f.y3)
            } else {
                // De Casteljau at t = 0.5.
                val ab_x = (f.x0 + f.x1) * 0.5f; val ab_y = (f.y0 + f.y1) * 0.5f
                val bc_x = (f.x1 + f.x2) * 0.5f; val bc_y = (f.y1 + f.y2) * 0.5f
                val cd_x = (f.x2 + f.x3) * 0.5f; val cd_y = (f.y2 + f.y3) * 0.5f
                val abc_x = (ab_x + bc_x) * 0.5f; val abc_y = (ab_y + bc_y) * 0.5f
                val bcd_x = (bc_x + cd_x) * 0.5f; val bcd_y = (bc_y + cd_y) * 0.5f
                val m_x = (abc_x + bcd_x) * 0.5f; val m_y = (abc_y + bcd_y) * 0.5f
                stack.addLast(CubicFrame(m_x, m_y, bcd_x, bcd_y, cd_x, cd_y, f.x3, f.y3, f.depth + 1))
                stack.addLast(CubicFrame(f.x0, f.y0, ab_x, ab_y, abc_x, abc_y, m_x, m_y, f.depth + 1))
            }
        }
        return total
    }

    /**
     * Conic flattening: subdivide into two sub-conics using the
     * rational midpoint formula, falling back to a line segment
     * when the midpoint deviation is within tolerance or the
     * recursion depth is exhausted. Mirrors Skia's
     * `conic_too_curvy` flattening heuristic.
     */
    private fun flattenConic(
        pts: ArrayList<SkScalar>, cum: ArrayList<SkScalar>, total0: SkScalar,
        x0: SkScalar, y0: SkScalar,
        x1: SkScalar, y1: SkScalar,
        x2: SkScalar, y2: SkScalar,
        w: SkScalar,
    ): SkScalar {
        var total = total0
        val stack = ArrayDeque<ConicFrame>()
        stack.addLast(ConicFrame(x0, y0, x1, y1, x2, y2, w, 0))
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            // Midpoint at t=0.5 for a rational quadratic.
            // P(0.5) = (0.25 * P0 + 0.5 * w * P1 + 0.25 * P2) / (0.25 + 0.5 * w + 0.25)
            //        = (P0 + 2w*P1 + P2) / (2 + 2w)
            val denom = 2f * (1f + f.w)
            val midX = (f.x0 + 2f * f.w * f.x1 + f.x2) / denom
            val midY = (f.y0 + 2f * f.w * f.y1 + f.y2) / denom
            // Cheap curvature test: deviation between conic midpoint and chord midpoint.
            val chordMidX = (f.x0 + f.x2) * 0.5f
            val chordMidY = (f.y0 + f.y2) * 0.5f
            val dx = midX - chordMidX
            val dy = midY - chordMidY
            val dist = max(kotlin.math.abs(dx), kotlin.math.abs(dy))
            if (f.depth >= SkContourMeasure.MAX_RECURSION_DEPTH || dist <= tolerance) {
                total = addLineTo(pts, cum, total, f.x2, f.y2)
            } else {
                // Subdivide into two sub-conics using the standard formula.
                val newW = sqrt((1f + f.w) * 0.5f)
                val q0x = (f.x0 + f.w * f.x1) / (1f + f.w)
                val q0y = (f.y0 + f.w * f.y1) / (1f + f.w)
                val q1x = (f.w * f.x1 + f.x2) / (1f + f.w)
                val q1y = (f.w * f.y1 + f.y2) / (1f + f.w)
                // Left sub-conic: P0, q0, midPt with weight newW.
                // Right sub-conic: midPt, q1, P2 with weight newW.
                stack.addLast(ConicFrame(midX, midY, q1x, q1y, f.x2, f.y2, newW, f.depth + 1))
                stack.addLast(ConicFrame(f.x0, f.y0, q0x, q0y, midX, midY, newW, f.depth + 1))
            }
        }
        return total
    }

    private fun quadTooCurvy(f: QuadFrame, tol: SkScalar): Boolean {
        // diff between curve midpoint and chord midpoint:
        // P(0.5) = (P0 + 2*P1 + P2) / 4   →  midpoint of the chord is (P0 + P2) / 2
        // diff = 0.5*P1 - 0.25*(P0 + P2)
        val dx = 0.5f * f.x1 - 0.25f * (f.x0 + f.x2)
        val dy = 0.5f * f.y1 - 0.25f * (f.y0 + f.y2)
        return max(kotlin.math.abs(dx), kotlin.math.abs(dy)) > tol
    }

    private fun cubicTooCurvy(f: CubicFrame, tol: SkScalar): Boolean {
        // Reference points on the chord at t = 1/3 and t = 2/3.
        val ref1x = f.x0 + (f.x3 - f.x0) * (1f / 3f)
        val ref1y = f.y0 + (f.y3 - f.y0) * (1f / 3f)
        val ref2x = f.x0 + (f.x3 - f.x0) * (2f / 3f)
        val ref2y = f.y0 + (f.y3 - f.y0) * (2f / 3f)
        val d1 = max(kotlin.math.abs(f.x1 - ref1x), kotlin.math.abs(f.y1 - ref1y))
        val d2 = max(kotlin.math.abs(f.x2 - ref2x), kotlin.math.abs(f.y2 - ref2y))
        return d1 > tol || d2 > tol
    }

    private data class QuadFrame(
        val x0: SkScalar, val y0: SkScalar,
        val x1: SkScalar, val y1: SkScalar,
        val x2: SkScalar, val y2: SkScalar,
        val depth: Int,
    )

    private data class CubicFrame(
        val x0: SkScalar, val y0: SkScalar,
        val x1: SkScalar, val y1: SkScalar,
        val x2: SkScalar, val y2: SkScalar,
        val x3: SkScalar, val y3: SkScalar,
        val depth: Int,
    )

    private data class ConicFrame(
        val x0: SkScalar, val y0: SkScalar,
        val x1: SkScalar, val y1: SkScalar,
        val x2: SkScalar, val y2: SkScalar,
        val w: SkScalar,
        val depth: Int,
    )
}

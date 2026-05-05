package org.skia.foundation

import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import kotlin.math.abs

/**
 * Immutable path. Built with [SkPathBuilder] and produced via
 * [SkPathBuilder.detach] or via the static factories below
 * (`Rect` / `Circle` / `Oval` / `RRect` / `Line` / `Polygon`), which
 * mirror Skia 4.x.
 *
 * Storage layout — mirrors upstream Skia, parallel arrays:
 *   - `verbs[i]` records the verb kind.
 *   - `coords` is a flat sequence of `(x, y)` pairs, consumed
 *     left-to-right as verbs are walked. The number of points each
 *     verb consumes is encoded in [Verb.pointCount].
 *   - `conicWeights[j]` carries the weight of the j-th `kConic`
 *     verb, in occurrence order.
 *
 * Walk pattern (used by `SkBitmapDevice.buildEdges`):
 * ```
 * var coordIdx = 0; var weightIdx = 0
 * for (verb in path.verbs) {
 *     when (verb) {
 *         kMove  -> { px = coords[coordIdx++]; py = coords[coordIdx++] }
 *         kLine  -> { ...; px = coords[coordIdx++]; py = coords[coordIdx++] }
 *         kQuad  -> { c1; end (4 floats consumed) }
 *         kConic -> { c1; end (4 floats); w = weights[weightIdx++] }
 *         kCubic -> { c1; c2; end (6 floats consumed) }
 *         kClose -> { /* 0 points */ }
 *     }
 * }
 * ```
 */
public class SkPath internal constructor(
    internal val verbs: Array<Verb>,
    internal val coords: FloatArray,
    internal val conicWeights: FloatArray,
    public val fillType: SkPathFillType,
) {
    public enum class Verb(public val pointCount: Int) {
        kMove(1),
        kLine(1),
        kQuad(2),    // control + end
        kConic(2),   // control + end (with weight in conicWeights)
        kCubic(3),   // 2 controls + end
        kClose(0),
    }

    public fun isEmpty(): Boolean = verbs.isEmpty()

    /**
     * True if the last verb is `kClose`. Mirrors `SkPath::isLastContourClosed`
     * (`src/core/SkPath.cpp:148-152`).
     */
    public fun isLastContourClosed(): Boolean =
        verbs.isNotEmpty() && verbs.last() == Verb.kClose

    /**
     * True if every coordinate stored in [coords] is finite (`!isNaN`,
     * `!isInfinite`). Mirrors `SkPath::isFinite`. An empty path returns true.
     */
    public fun isFinite(): Boolean {
        for (c in coords) if (!c.isFinite()) return false
        return true
    }

    /**
     * Number of points stored in the path's flat coord array
     * (= sum of new points emitted by each verb). Mirrors
     * `SkPath::countPoints` (`include/core/SkPath.h:440`).
     */
    public fun countPoints(): Int = coords.size / 2

    /**
     * Number of verbs in the path. Mirrors `SkPath::countVerbs`
     * (`include/core/SkPath.h:441`).
     */
    public fun countVerbs(): Int = verbs.size

    /**
     * Bitmask of segment types present in the verb stream. Bits match
     * `SkPathSegmentMask` (`include/core/SkPathTypes.h:53-58`):
     * `1` = line, `2` = quad, `4` = conic, `8` = cubic. Move and close
     * never set bits.
     */
    public fun getSegmentMasks(): Int {
        var mask = 0
        for (v in verbs) {
            when (v) {
                Verb.kLine -> mask = mask or 1
                Verb.kQuad -> mask = mask or 2
                Verb.kConic -> mask = mask or 4
                Verb.kCubic -> mask = mask or 8
                Verb.kMove, Verb.kClose -> {}
            }
        }
        return mask
    }

    /**
     * Last point on the path, or `null` if the path is empty. Mirrors
     * `SkPath::getLastPt` (the `optional<SkPoint>` overload at
     * `include/core/SkPath.h:449`).
     */
    public fun getLastPt(): SkPoint? {
        if (coords.isEmpty()) return null
        val n = coords.size
        return SkPoint(coords[n - 2], coords[n - 1])
    }

    /**
     * If this path is exactly a single line — verb stream `kMove + kLine`,
     * with no close — returns the (start, end) point pair. Otherwise null.
     * Mirrors `SkPath::isLine` (`src/core/SkPath.cpp:154-167`).
     */
    public fun isLine(): Pair<SkPoint, SkPoint>? {
        if (verbs.size != 2 || verbs[1] != Verb.kLine) return null
        // verbs[0] is necessarily kMove (builder invariant).
        return SkPoint(coords[0], coords[1]) to SkPoint(coords[2], coords[3])
    }

    /**
     * If this path is the canonical axis-aligned rectangle contour
     * `kMove + 3×kLine + kClose` produced by [SkPathBuilder.addRect],
     * returns the bounds. Otherwise null.
     *
     * Conservative compared to upstream `SkPath::isRect` — Skia's
     * `SkPathPriv::IsRectContour` recognises a few extra shapes (closed
     * via final `lineTo`, leading/trailing zero-length moves, …) that we
     * don't bother with here. All paths produced by [SkPathBuilder.addRect]
     * are recognised.
     */
    public fun isRect(): SkRect? {
        if (verbs.size != 5) return null
        if (verbs[0] != Verb.kMove ||
            verbs[1] != Verb.kLine ||
            verbs[2] != Verb.kLine ||
            verbs[3] != Verb.kLine ||
            verbs[4] != Verb.kClose
        ) return null
        val x0 = coords[0]; val y0 = coords[1]
        val x1 = coords[2]; val y1 = coords[3]
        val x2 = coords[4]; val y2 = coords[5]
        val x3 = coords[6]; val y3 = coords[7]
        // Each consecutive segment must be axis-aligned (one of dx, dy = 0).
        // Walking the 4 edges (the kClose closes back to (x0, y0)) the points
        // must form an axis-aligned rectangle: opposite corners share an axis.
        val xs = floatArrayOf(x0, x1, x2, x3)
        val ys = floatArrayOf(y0, y1, y2, y3)
        var minX = xs[0]; var maxX = xs[0]
        var minY = ys[0]; var maxY = ys[0]
        for (i in 1..3) {
            if (xs[i] < minX) minX = xs[i]
            if (xs[i] > maxX) maxX = xs[i]
            if (ys[i] < minY) minY = ys[i]
            if (ys[i] > maxY) maxY = ys[i]
        }
        // Each xi ∈ {minX, maxX} and yi ∈ {minY, maxY}, and the 4 (x, y)
        // pairs must hit each rect corner exactly once.
        val seenCorners = BooleanArray(4)
        for (i in 0..3) {
            val cornerX = if (xs[i] == minX) 0 else if (xs[i] == maxX) 1 else return null
            val cornerY = if (ys[i] == minY) 0 else if (ys[i] == maxY) 1 else return null
            val idx = cornerY * 2 + cornerX
            if (seenCorners[idx]) return null
            seenCorners[idx] = true
        }
        // Edges must be axis-aligned (consecutive points share x or y).
        if (x0 != x1 && y0 != y1) return null
        if (x1 != x2 && y1 != y2) return null
        if (x2 != x3 && y2 != y3) return null
        if (x3 != x0 && y3 != y0) return null
        return SkRect.MakeLTRB(minX, minY, maxX, maxY)
    }

    /**
     * If this path is the canonical oval contour `kMove + 4×kConic + kClose`
     * produced by [SkPathBuilder.addOval] — all four conic weights equal
     * `√2/2` and the move/conic-end points sit on the four cardinal
     * positions of an axis-aligned ellipse — returns the bounding rect.
     * Otherwise null.
     *
     * Pattern-match approach (vs Skia's cached `fOvalInfo`): Phase 2 made
     * the verb stream canonical, so the recognition is reliable.
     */
    public fun isOval(): SkRect? {
        if (verbs.size != 6) return null
        if (verbs[0] != Verb.kMove ||
            verbs[1] != Verb.kConic ||
            verbs[2] != Verb.kConic ||
            verbs[3] != Verb.kConic ||
            verbs[4] != Verb.kConic ||
            verbs[5] != Verb.kClose
        ) return null
        if (conicWeights.size != 4) return null
        val w = SQRT2_OVER_2
        for (cw in conicWeights) if (abs(cw - w) > CONIC_WEIGHT_EPS) return null
        // Conic ends are the cardinals; the move start is also a cardinal.
        // Coords layout: move(2) + conic(2 ctrl + 2 end) × 4 = 18 floats.
        // Cardinal points indices: 0..1 (move), 4..5, 8..9, 12..13, 16..17.
        if (coords.size != 18) return null
        // Collect the four "end" points of conics + the move = 4 cardinals
        // (the last conic's end equals the move start, so 4 unique points).
        val pxs = floatArrayOf(coords[0], coords[4], coords[8], coords[12])
        val pys = floatArrayOf(coords[1], coords[5], coords[9], coords[13])
        var minX = pxs[0]; var maxX = pxs[0]
        var minY = pys[0]; var maxY = pys[0]
        for (i in 1..3) {
            if (pxs[i] < minX) minX = pxs[i]
            if (pxs[i] > maxX) maxX = pxs[i]
            if (pys[i] < minY) minY = pys[i]
            if (pys[i] > maxY) maxY = pys[i]
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        // The cardinals must be {(maxX, cy), (cx, minY), (minX, cy), (cx, maxY)}
        // in some order. Each pair (px, py) must equal a cardinal.
        for (i in 0..3) {
            val onCx = abs(pxs[i] - cx) <= CARDINAL_EPS
            val onCy = abs(pys[i] - cy) <= CARDINAL_EPS
            // Exactly one must be on-axis (cardinal positions are (cx, ±ry)
            // or (±rx, cy)).
            if (onCx == onCy) return null
        }
        // Last conic's end must equal the move start.
        if (abs(coords[16] - coords[0]) > CARDINAL_EPS ||
            abs(coords[17] - coords[1]) > CARDINAL_EPS
        ) return null
        return SkRect.MakeLTRB(minX, minY, maxX, maxY)
    }

    /**
     * If this path is the canonical rounded-rect contour
     * `kMove + (kLine + kConic) × 4 + kClose` produced by
     * [SkPathBuilder.addRRect], returns the matching [SkRRect]. Otherwise
     * null.
     *
     * The detector skips paths that came in via [SkPathBuilder.addRect]
     * or [SkPathBuilder.addOval] (those are recognised by [isRect] /
     * [isOval]); use the type-specific detectors when the broader query
     * matters.
     */
    public fun isRRect(): SkRRect? {
        if (verbs.size != 10) return null
        if (verbs[0] != Verb.kMove || verbs[9] != Verb.kClose) return null
        for (i in 0..3) {
            if (verbs[1 + i * 2] != Verb.kLine) return null
            if (verbs[2 + i * 2] != Verb.kConic) return null
        }
        if (conicWeights.size != 4) return null
        val w = SQRT2_OVER_2
        for (cw in conicWeights) if (abs(cw - w) > CONIC_WEIGHT_EPS) return null

        // Coords layout per emitRRectCorners (CW direction):
        //   move(L+tlx, T)             idx 0..1
        //   line(R-trx, T)             idx 2..3
        //   conic(R, T → R, T+try)     idx 4..7   (ctrl, end)
        //   line(R, B-bry)             idx 8..9
        //   conic(R, B → R-brx, B)     idx 10..13
        //   line(L+blx, B)             idx 14..15
        //   conic(L, B → L, B-bly)     idx 16..19
        //   line(L, T+tly)             idx 20..21
        //   conic(L, T → L+tlx, T)     idx 22..25
        if (coords.size != 26) return null
        // Conic control points are at the bbox corners.
        val tr = coords[4] to coords[5]
        val br = coords[10] to coords[11]
        val bl = coords[16] to coords[17]
        val tl = coords[22] to coords[23]
        val r = tr.first; val t = tr.second
        if (br.first != r || bl.second != br.second) return null
        val b = br.second
        if (bl.first != tl.first) return null
        val l = bl.first
        if (tl.second != t) return null
        if (l >= r || t >= b) return null
        // Recover per-corner radii from the line endpoints / conic ends.
        // See `emitRRectCorners` for the source layout — each radius can
        // be read out of two coords; we read one and cross-check the
        // other (return null on disagreement).
        val tlRxFromMove = coords[0] - l
        val tlRxFromLastConic = coords[24] - l
        val tlRy = coords[21] - t
        val trRx = r - coords[2]
        val trRy = coords[7] - t
        val brRx = r - coords[12]
        val brRy = b - coords[9]
        val blRx = coords[14] - l
        val blRy = b - coords[19]
        if (tlRxFromMove < 0f || tlRy < 0f ||
            trRx < 0f || trRy < 0f ||
            brRx < 0f || brRy < 0f ||
            blRx < 0f || blRy < 0f
        ) return null
        if (abs(tlRxFromMove - tlRxFromLastConic) > CARDINAL_EPS) return null
        return SkRRect.MakeRectRadii(
            SkRect.MakeLTRB(l, t, r, b),
            arrayOf(
                org.skia.math.SkVector(tlRxFromMove, tlRy),
                org.skia.math.SkVector(trRx, trRy),
                org.skia.math.SkVector(brRx, brRy),
                org.skia.math.SkVector(blRx, blRy),
            ),
        )
    }

    /**
     * **Fast bounds** — axis-aligned bounding box of every control point
     * stored in [coords]. Mirrors Skia's `SkPath::getBounds()`.
     *
     * For paths made entirely of `kMove` and `kLine` verbs this is exact;
     * curves' control points may extend slightly beyond the actual curve,
     * so the box is always a *conservative* (i.e. ≥) bound. Tight curve
     * bounds (Skia's `computeTightBounds()`) requires solving Bézier
     * derivative roots and is deferred to a follow-up.
     *
     * Empty path → an empty rect at the origin.
     */
    public fun computeBounds(): SkRect {
        if (coords.isEmpty()) return SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < coords.size) {
            val x = coords[i]; val y = coords[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            i += 2
        }
        return SkRect.MakeLTRB(minX, minY, maxX, maxY)
    }

    /**
     * Translated copy. Mirrors Skia's `SkPath::makeOffset(dx, dy)` —
     * verbs and conic weights are preserved untouched, only point
     * coordinates shift. Returns `this` when both deltas are zero
     * (no allocation).
     */
    public fun makeOffset(dx: SkScalar, dy: SkScalar): SkPath {
        if (dx == 0f && dy == 0f) return this
        val n = coords.size
        val out = FloatArray(n)
        var i = 0
        while (i < n) {
            out[i] = coords[i] + dx
            out[i + 1] = coords[i + 1] + dy
            i += 2
        }
        return SkPath(verbs, out, conicWeights, fillType)
    }

    /**
     * Apply [m] to every control point, returning a new [SkPath]. Verbs and
     * conic weights are preserved as-is — Bézier control points transform
     * naturally under affine maps. Mirrors Skia's `SkPath::transform(SkMatrix)`
     * for the affine case.
     *
     * Identity matrix → returns `this` (no allocation).
     */
    public fun makeTransform(m: SkMatrix): SkPath {
        if (m.isIdentity) return this
        val n = coords.size
        val out = FloatArray(n)
        val sx = m.sx; val kx = m.kx; val tx = m.tx
        val ky = m.ky; val sy = m.sy; val ty = m.ty
        var i = 0
        while (i < n) {
            val x = coords[i]; val y = coords[i + 1]
            out[i] = sx * x + kx * y + tx
            out[i + 1] = ky * x + sy * y + ty
            i += 2
        }
        return SkPath(verbs, out, conicWeights, fillType)
    }

    public companion object {
        /** `√2/2` — canonical conic weight for a 90° quarter-circle/ellipse. */
        private const val SQRT2_OVER_2: Float = 0.707106781f
        /** Tolerance on conic-weight equality during oval / rrect detection. */
        private const val CONIC_WEIGHT_EPS: Float = 1e-4f
        /** Tolerance on cardinal-point coincidence during oval / rrect detection. */
        private const val CARDINAL_EPS: Float = 1e-4f

        /** Closed rectangular contour. */
        public fun Rect(
            rect: SkRect,
            dir: SkPathDirection = SkPathDirection.kCW,
        ): SkPath = SkPathBuilder().addRect(rect, dir).detach()

        /** Closed circular contour, axis-aligned. */
        public fun Circle(
            cx: SkScalar, cy: SkScalar, r: SkScalar,
            dir: SkPathDirection = SkPathDirection.kCW,
        ): SkPath = SkPathBuilder().addCircle(cx, cy, r, dir).detach()

        /** Closed elliptical contour, axis-aligned. */
        public fun Oval(
            rect: SkRect,
            dir: SkPathDirection = SkPathDirection.kCW,
        ): SkPath = SkPathBuilder().addOval(rect, dir).detach()

        /**
         * Closed rounded-rectangle contour. Mirrors Skia's
         * `SkPath::RRect(const SkRRect&, SkPathDirection)`.
         *
         * If [rrect] has type [SkRRect.Type.kEmpty_Type], the returned
         * path is empty; if it's [SkRRect.Type.kRect_Type] or
         * [SkRRect.Type.kOval_Type], the path collapses to the appropriate
         * specialised contour. Otherwise the four corners are emitted as
         * cubic Béziers with the same kappa approximation used by
         * [SkPathBuilder.addOval].
         */
        public fun RRect(
            rrect: SkRRect,
            dir: SkPathDirection = SkPathDirection.kCW,
        ): SkPath = SkPathBuilder().addRRect(rrect, dir).detach()

        /** Single line segment from `a` to `b`, no close. */
        public fun Line(
            a: Pair<SkScalar, SkScalar>,
            b: Pair<SkScalar, SkScalar>,
        ): SkPath = SkPathBuilder()
            .moveTo(a.first, a.second)
            .lineTo(b.first, b.second)
            .detach()

        /**
         * Polygon contour: a single contour through `points`. If
         * `isClosed` is true an explicit `close` verb is emitted at the
         * end. Mirrors Skia's `SkPath::Polygon`.
         */
        public fun Polygon(
            points: Array<Pair<SkScalar, SkScalar>>,
            isClosed: Boolean,
            fillType: SkPathFillType = SkPathFillType.kWinding,
            @Suppress("UNUSED_PARAMETER") isVolatile: Boolean = false,
        ): SkPath = SkPathBuilder()
            .setFillType(fillType)
            .addPolygon(points, isClosed)
            .detach()
    }
}

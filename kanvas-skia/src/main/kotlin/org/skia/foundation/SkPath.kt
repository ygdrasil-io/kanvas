package org.skia.foundation

import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar

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

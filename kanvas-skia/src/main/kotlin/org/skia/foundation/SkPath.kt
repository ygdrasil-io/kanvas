package org.skia.foundation

import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * Immutable path. Built with [SkPathBuilder] and produced via
 * [SkPathBuilder.detach] or via the static factories below
 * (`Rect` / `Circle` / `Oval` / `Line` / `Polygon`), which mirror
 * Skia 4.x.
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

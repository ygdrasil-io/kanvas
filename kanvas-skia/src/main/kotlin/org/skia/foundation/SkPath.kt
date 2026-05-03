package org.skia.foundation

import org.skia.math.SkScalar

/**
 * Mutable path. Phase 3a covers a polygon-only subset for the rasterizer
 * (`moveTo`, `lineTo`, `close`); higher-order verbs (`quadTo` here, `cubicTo`
 * and `conicTo` in later slices) are **flattened on the fly** into line
 * segments by the path builder, so the persisted verb stream stays
 * line-only and `SkBitmapDevice.drawPath` does not yet need to know about
 * Béziers.
 *
 * The verb stream uses a parallel-array layout: `verbs[i]` records the verb
 * kind, and successive `(x, y)` pairs consumed from `coords` reconstruct
 * the geometry — same structural model as upstream Skia.
 */
public class SkPath public constructor() {
    public enum class Verb {
        kMove,  // 1 point
        kLine,  // 1 point
        kClose, // 0 points
    }

    internal val verbs: MutableList<Verb> = mutableListOf()
    /** Flat (x, y) pairs. */
    internal val coords: MutableList<SkScalar> = mutableListOf()

    /** Current pen position — used by curve verbs that need a start point. */
    private var lastX: SkScalar = 0f
    private var lastY: SkScalar = 0f
    /** Start of the current contour — `close` rewinds the pen here. */
    private var contourX: SkScalar = 0f
    private var contourY: SkScalar = 0f

    public var fillType: SkPathFillType = SkPathFillType.kWinding

    public fun moveTo(x: SkScalar, y: SkScalar): SkPath = apply {
        verbs.add(Verb.kMove); coords.add(x); coords.add(y)
        lastX = x; lastY = y
        contourX = x; contourY = y
    }

    public fun lineTo(x: SkScalar, y: SkScalar): SkPath = apply {
        verbs.add(Verb.kLine); coords.add(x); coords.add(y)
        lastX = x; lastY = y
    }

    /**
     * Quadratic Bézier from the current point through control `(x1, y1)`
     * to `(x2, y2)`, flattened into `segments` line segments. The default
     * of 16 keeps sub-pixel error well under 0.1 px for the curve scales
     * used by the path GMs (radii up to a few hundred pixels). Adaptive
     * flattening, with proper tangent-error bounds, can be revisited if a
     * GM exposes visible facetting.
     */
    public fun quadTo(
        x1: SkScalar, y1: SkScalar, x2: SkScalar, y2: SkScalar,
        segments: Int = 16,
    ): SkPath = apply {
        val px = lastX
        val py = lastY
        for (i in 1..segments) {
            val t = i.toFloat() / segments
            val u = 1f - t
            val nx = u * u * px + 2f * u * t * x1 + t * t * x2
            val ny = u * u * py + 2f * u * t * y1 + t * t * y2
            lineTo(nx, ny)
        }
    }

    public fun close(): SkPath = apply {
        verbs.add(Verb.kClose)
        lastX = contourX; lastY = contourY
    }

    /**
     * Append a polygon contour (one `moveTo` + `N-1` `lineTo`s, optionally
     * followed by `close`). Mirrors `SkPathBuilder::addPolygon`. Multiple
     * `addPolygon` calls produce a single path with several sub-contours,
     * which is exactly what `SkBitmapDevice.drawPath` expects to fill.
     */
    public fun addPolygon(
        points: Array<Pair<SkScalar, SkScalar>>,
        isClosed: Boolean,
    ): SkPath = apply {
        if (points.isEmpty()) return@apply
        moveTo(points[0].first, points[0].second)
        for (i in 1 until points.size) lineTo(points[i].first, points[i].second)
        if (isClosed) close()
    }

    public fun isEmpty(): Boolean = verbs.isEmpty()

    public fun setFillType(t: SkPathFillType): SkPath = apply { fillType = t }

    public companion object {
        /**
         * Build a polygon path: a single contour through `points`. If
         * `isClosed` is true an explicit `close` verb is emitted; otherwise
         * the rasterizer treats the contour as implicitly closed via the
         * standard winding/even-odd fill semantics. Mirrors Skia's
         * `SkPath::Polygon`.
         *
         * The `fillType` argument lets callers select winding vs even-odd
         * up-front; `isVolatile` is the upstream perf hint and is ignored.
         */
        public fun Polygon(
            points: Array<Pair<SkScalar, SkScalar>>,
            isClosed: Boolean,
            fillType: SkPathFillType = SkPathFillType.kWinding,
            @Suppress("UNUSED_PARAMETER") isVolatile: Boolean = false,
        ): SkPath {
            val p = SkPath()
            p.fillType = fillType
            if (points.isEmpty()) return p
            p.moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) p.lineTo(points[i].first, points[i].second)
            if (isClosed) p.close()
            return p
        }
    }
}

package org.skia.foundation

import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Mirrors Skia's
 * [`SkDashPathEffect`](https://github.com/google/skia/blob/main/include/effects/SkDashPathEffect.h)
 * — decomposes a path into a series of `on` / `off` segments
 * following a repeating interval pattern.
 *
 * Construct via [Make]. The pattern is `[on1, off1, on2, off2, …]`
 * — must have an **even** number of entries (every "on" needs a
 * matching "off"), all non-negative, with at least one positive entry.
 * The [phase] offset shifts the cycle along its arc-length axis :
 * `phase = on1` skips the first dash entirely, `phase = on1 + off1`
 * starts midway through the second dash.
 *
 * **Phase 7b coverage** :
 *  - `kMove` / `kLine` / `kClose` verbs — fully supported.
 *  - `kQuad` / `kConic` / `kCubic` — flattened to chord polylines
 *    via the same `CHORD_TOL` tolerance as the rasteriser ; each
 *    chord is then dashed individually. Visually-correct for moderate
 *    curvature ; long-arc-length curves at small dash intervals may
 *    show seam artefacts at chord boundaries (Skia uses a true arc-
 *    length parameterisation here, deferred to a Phase 7b' refinement
 *    if any GM in scope demands it).
 *
 * The dash effect produces a path of disconnected line segments —
 * the canvas's stroker subsequently thickens each segment to match
 * `paint.strokeWidth`. Caps are applied per dash (Skia's contract).
 *
 * Per-contour reset : the dash phase resets at every `kMove`. Two
 * disjoint contours dashed with the same effect get independent
 * patterns starting at [phase] each.
 */
public class SkDashPathEffect private constructor(
    private val intervals: FloatArray,
    private val phase: Float,
) : SkPathEffect() {

    /**
     * Mirrors Skia's `SkDashPathEffect::asADash(DashInfo*)` exposing
     * the on/off interval pattern. Returns a defensive copy ; mutating
     * the result does not affect this effect.
     *
     * Used by [org.skia.svg.SkSVGCanvas] (B2.2 paint slice) to build
     * the SVG `stroke-dasharray` attribute.
     */
    public fun getIntervals(): FloatArray = intervals.copyOf()

    /**
     * The dash phase — the cumulative distance into the
     * `intervals` cycle the first contour's first verb starts from.
     * Mirrors Skia's `SkDashPathEffect::asADash(DashInfo*)` `phase`
     * field. Used by [org.skia.svg.SkSVGCanvas] for the SVG
     * `stroke-dashoffset` attribute (B2.2).
     */
    public fun getPhase(): Float = phase

    private val totalCycle: Float = intervals.sum()
    private val normalisedPhase: Float = run {
        if (totalCycle <= 0f) 0f
        else {
            var p = phase % totalCycle
            if (p < 0f) p += totalCycle
            p
        }
    }

    override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? =
        filterPath(input, ctm, cullRect = null)

    /**
     * Cull-rect aware override (R-suivi.7). After the standard dash
     * decomposition, any emitted `moveTo + lineTo` pair whose segment
     * AABB lies entirely outside [cullRect] is dropped from the output.
     * This is a coarse but correct culling pass — segments that
     * straddle the cull rect are kept verbatim (the stroker will clip
     * them downstream).
     *
     * `cullRect = null` ⇒ identical behaviour to the 2-arg form.
     */
    override fun filterPath(input: SkPath, ctm: SkMatrix, cullRect: SkRect?): SkPath? {
        // Degenerate cases — the dash decomposition produces no segments.
        // We return an EMPTY path (not null) because returning null tells
        // the device to use the input unchanged, which would be wrong :
        // a dash with [0, 0] intervals is supposed to draw nothing.
        if (totalCycle <= 0f) return SkPathBuilder().detach()
        if (input.isEmpty()) return SkPathBuilder().detach()
        val out = SkPathBuilder()
        var penX = 0f; var penY = 0f
        var contourStartX = 0f; var contourStartY = 0f
        var distFromContourStart = 0f
        var coordIdx = 0

        for (verb in input.verbs) {
            when (verb) {
                SkPath.Verb.kMove -> {
                    penX = input.coords[coordIdx++]
                    penY = input.coords[coordIdx++]
                    contourStartX = penX
                    contourStartY = penY
                    distFromContourStart = 0f
                }
                SkPath.Verb.kLine -> {
                    val nx = input.coords[coordIdx++]
                    val ny = input.coords[coordIdx++]
                    distFromContourStart = dashLine(out, penX, penY, nx, ny, distFromContourStart, cullRect)
                    penX = nx; penY = ny
                }
                SkPath.Verb.kQuad -> {
                    val cx = input.coords[coordIdx++]; val cy = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]; val ey = input.coords[coordIdx++]
                    distFromContourStart = dashQuad(out,
                        penX, penY, cx, cy, ex, ey, distFromContourStart)
                    penX = ex; penY = ey
                }
                SkPath.Verb.kConic -> {
                    // Conic w is in conicWeights ; we flatten as a quad
                    // (drops the rational denominator — visually-close for
                    // moderate weights, exact for w == 1).
                    val cx = input.coords[coordIdx++]; val cy = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]; val ey = input.coords[coordIdx++]
                    distFromContourStart = dashQuad(out,
                        penX, penY, cx, cy, ex, ey, distFromContourStart)
                    penX = ex; penY = ey
                }
                SkPath.Verb.kCubic -> {
                    val c1x = input.coords[coordIdx++]; val c1y = input.coords[coordIdx++]
                    val c2x = input.coords[coordIdx++]; val c2y = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]; val ey = input.coords[coordIdx++]
                    distFromContourStart = dashCubic(out,
                        penX, penY, c1x, c1y, c2x, c2y, ex, ey, distFromContourStart)
                    penX = ex; penY = ey
                }
                SkPath.Verb.kClose -> {
                    distFromContourStart = dashLine(out, penX, penY,
                        contourStartX, contourStartY, distFromContourStart, cullRect)
                    penX = contourStartX; penY = contourStartY
                }
                SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
            }
        }
        return out.detach()
    }

    /**
     * Drop `moveTo + lineTo` pairs from [path] whose entire segment AABB
     * falls outside [cullRect]. Segments that straddle the rect are
     * preserved unchanged ; the rasteriser clips them on the storage edge.
     *
     * The dash output is — by construction — a sequence of
     * `moveTo(x0,y0) ; lineTo(x1,y1)` pairs (one pair per "on" sub-segment).
     * That assumption is exploited here to short-circuit the segment scan ;
     * if a future refactor of [filterPath] ever changes the output shape,
     * this helper must be revisited.
     */
    private fun cullDashedPath(path: SkPath, cullRect: SkRect): SkPath {
        if (path.isEmpty()) return path
        val out = SkPathBuilder()
        var coordIdx = 0
        var pendingMoveX = 0f; var pendingMoveY = 0f
        var i = 0
        val verbs = path.verbs
        while (i < verbs.size) {
            val v = verbs[i]
            when (v) {
                SkPath.Verb.kMove -> {
                    pendingMoveX = path.coords[coordIdx++]
                    pendingMoveY = path.coords[coordIdx++]
                    // Look ahead — if the next verb is kLine, decide together.
                    if (i + 1 < verbs.size && verbs[i + 1] == SkPath.Verb.kLine) {
                        val nx = path.coords[coordIdx++]
                        val ny = path.coords[coordIdx++]
                        if (segmentIntersectsRect(pendingMoveX, pendingMoveY, nx, ny, cullRect)) {
                            out.moveTo(pendingMoveX, pendingMoveY)
                            out.lineTo(nx, ny)
                        }
                        i += 2
                        continue
                    } else {
                        // Standalone move (rare for dash output) — emit as-is.
                        out.moveTo(pendingMoveX, pendingMoveY)
                    }
                }
                SkPath.Verb.kLine -> {
                    // Defensive : shouldn't be reached given the moveTo-then-lineTo
                    // pairing above, but keep correctness if the output shape changes.
                    val nx = path.coords[coordIdx++]
                    val ny = path.coords[coordIdx++]
                    if (segmentIntersectsRect(pendingMoveX, pendingMoveY, nx, ny, cullRect)) {
                        out.lineTo(nx, ny)
                    }
                    pendingMoveX = nx; pendingMoveY = ny
                }
                SkPath.Verb.kQuad,
                SkPath.Verb.kConic -> { coordIdx += 4 }
                SkPath.Verb.kCubic -> { coordIdx += 6 }
                SkPath.Verb.kClose -> { /* no coords */ }
                SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
            }
            i++
        }
        return out.detach()
    }

    /** Fast AABB-vs-rect overlap (inclusive). */
    private fun segmentIntersectsRect(
        x0: Float, y0: Float, x1: Float, y1: Float, r: SkRect,
    ): Boolean {
        val segL = min(x0, x1); val segR = max(x0, x1)
        val segT = min(y0, y1); val segB = max(y0, y1)
        // No overlap if segment AABB is strictly outside r on any axis.
        if (segR < r.left() || segL > r.right()) return false
        if (segB < r.top() || segT > r.bottom()) return false
        return true
    }

    /**
     * Walk a single straight segment, emitting the "on" sub-segments
     * to [builder]. Returns the new contour-relative arc length so
     * the next segment in the same contour continues the dash phase.
     */
    private fun dashLine(
        builder: SkPathBuilder,
        x0: Float, y0: Float, x1: Float, y1: Float,
        startDist: Float,
        cullRect: SkRect? = null,
    ): Float {
        val dx = x1 - x0; val dy = y1 - y0
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0f) return startDist
        val ux = dx / length; val uy = dy / length
        val (visibleStart, visibleEnd) = clippedDistanceRange(x0, y0, dx, dy, length, cullRect)
            ?: return startDist + length

        // Position in the dash cycle at the first visible point. The
        // original segment length still drives the phase, so culled-away
        // prefixes do not reset or shift the dash pattern.
        var (i, withinInterval) = locateInCycle(startDist + visibleStart)
        var remaining = intervals[i] - withinInterval
        var consumed = visibleStart

        while (consumed < visibleEnd) {
            val available = visibleEnd - consumed
            val step = if (remaining < available) remaining else available
            if (i % 2 == 0) {
                // "on" segment — emit a moveTo + lineTo (dash-effect
                // output is a series of disconnected segments ; the
                // stroker handles caps per segment).
                // Zero-length "on" segments (step == 0) are still emitted
                // as degenerate moveTo+lineTo pairs : with round or square
                // caps the stroker will draw a cap dot/square at that point,
                // matching Skia's upstream behaviour (bug583299 regression).
                builder.moveTo(x0 + ux * consumed, y0 + uy * consumed)
                builder.lineTo(x0 + ux * (consumed + step), y0 + uy * (consumed + step))
            }
            consumed += step
            remaining -= step
            if (remaining <= EPS) {
                i = (i + 1) % intervals.size
                remaining = intervals[i]
            }
        }
        return startDist + length
    }

    /**
     * Return the visible distance range for one source-space line segment,
     * or null when the segment cannot affect [cullRect]. Liang-Barsky clips
     * in parametric line space, so very large dashed segments can skip
     * directly to the visible interval instead of iterating through every
     * offscreen dash cycle first.
     */
    private fun clippedDistanceRange(
        x0: Float, y0: Float, dx: Float, dy: Float, length: Float, cullRect: SkRect?,
    ): Pair<Float, Float>? {
        if (cullRect == null) return 0f to length
        var t0 = 0f
        var t1 = 1f

        fun clip(p: Float, q: Float): Boolean {
            if (p == 0f) return q >= 0f
            val r = q / p
            return if (p < 0f) {
                if (r > t1) false else {
                    if (r > t0) t0 = r
                    true
                }
            } else {
                if (r < t0) false else {
                    if (r < t1) t1 = r
                    true
                }
            }
        }

        if (!clip(-dx, x0 - cullRect.left())) return null
        if (!clip(dx, cullRect.right() - x0)) return null
        if (!clip(-dy, y0 - cullRect.top())) return null
        if (!clip(dy, cullRect.bottom() - y0)) return null
        if (t1 < t0) return null
        return (t0 * length) to (t1 * length)
    }

    /** Flatten a quadratic Bézier and dash each chord. */
    private fun dashQuad(
        builder: SkPathBuilder,
        x0: Float, y0: Float, cx: Float, cy: Float, x2: Float, y2: Float,
        startDist: Float,
    ): Float {
        val (nx, ny) = flattenQuad(x0, y0, cx, cy, x2, y2)
        var d = startDist
        var px = x0; var py = y0
        for (i in 0 until nx.size) {
            d = dashLine(builder, px, py, nx[i], ny[i], d)
            px = nx[i]; py = ny[i]
        }
        return d
    }

    /** Flatten a cubic Bézier and dash each chord. */
    private fun dashCubic(
        builder: SkPathBuilder,
        x0: Float, y0: Float,
        c1x: Float, c1y: Float, c2x: Float, c2y: Float,
        x3: Float, y3: Float,
        startDist: Float,
    ): Float {
        val (nx, ny) = flattenCubic(x0, y0, c1x, c1y, c2x, c2y, x3, y3)
        var d = startDist
        var px = x0; var py = y0
        for (i in 0 until nx.size) {
            d = dashLine(builder, px, py, nx[i], ny[i], d)
            px = nx[i]; py = ny[i]
        }
        return d
    }

    /**
     * Locate the position [arcLen] along the contour in the dash
     * cycle, accounting for [normalisedPhase]. Returns the interval
     * index (0-based, even = "on") and the distance already consumed
     * within that interval.
     */
    private fun locateInCycle(arcLen: Float): Pair<Int, Float> {
        var d = (arcLen + normalisedPhase) % totalCycle
        if (d < 0f) d += totalCycle
        var i = 0
        while (i < intervals.size && d >= intervals[i]) {
            d -= intervals[i]
            i++
        }
        // Numerical edge : if we exhausted intervals (d ≈ totalCycle),
        // wrap to interval 0.
        if (i >= intervals.size) { i = 0; d = 0f }
        return i to d
    }

    public companion object {
        /**
         * Mirrors Skia's `SkDashPathEffect::Make(intervals, count, phase)`.
         *
         * @throws IllegalArgumentException if [intervals].size is odd, < 2,
         * or if any entry is negative.
         */
        public fun Make(intervals: FloatArray, phase: Float): SkPathEffect {
            require(intervals.size >= 2 && intervals.size % 2 == 0) {
                "intervals.size must be ≥ 2 and even, got ${intervals.size}"
            }
            for (v in intervals) require(v >= 0f) { "intervals must be non-negative, got $v" }
            return SkDashPathEffect(intervals.copyOf(), phase)
        }

        private const val EPS = 1e-6f

        /** Chord tolerance for Bézier flattening — same as the rasteriser. */
        private const val CHORD_TOL = 0.25f
        private const val MAX_LEVELS = 18

        /**
         * Recursive midpoint subdivision of a quadratic Bézier until each
         * sub-segment's chord error is under [CHORD_TOL]. Returns the
         * flattened end-point list (the start point is the input `x0,y0`
         * and is NOT included).
         */
        private fun flattenQuad(
            x0: Float, y0: Float, cx: Float, cy: Float, x2: Float, y2: Float,
        ): Pair<FloatArray, FloatArray> {
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            subdivideQuad(x0, y0, cx, cy, x2, y2, 0, xs, ys)
            return xs.toFloatArray() to ys.toFloatArray()
        }

        private fun subdivideQuad(
            x0: Float, y0: Float, cx: Float, cy: Float, x2: Float, y2: Float,
            level: Int, xs: ArrayList<Float>, ys: ArrayList<Float>,
        ) {
            // Distance from control point to chord midpoint approximates
            // the worst-case chord error for a quad.
            val mx = (x0 + x2) * 0.5f; val my = (y0 + y2) * 0.5f
            val ex = cx - mx; val ey = cy - my
            val err2 = ex * ex + ey * ey
            if (err2 < CHORD_TOL * CHORD_TOL || level >= MAX_LEVELS) {
                xs.add(x2); ys.add(y2)
                return
            }
            val ax = (x0 + cx) * 0.5f; val ay = (y0 + cy) * 0.5f
            val bx = (cx + x2) * 0.5f; val by = (cy + y2) * 0.5f
            val midX = (ax + bx) * 0.5f; val midY = (ay + by) * 0.5f
            subdivideQuad(x0, y0, ax, ay, midX, midY, level + 1, xs, ys)
            subdivideQuad(midX, midY, bx, by, x2, y2, level + 1, xs, ys)
        }

        /** Same shape as [flattenQuad] but for cubics. */
        private fun flattenCubic(
            x0: Float, y0: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float,
            x3: Float, y3: Float,
        ): Pair<FloatArray, FloatArray> {
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            subdivideCubic(x0, y0, c1x, c1y, c2x, c2y, x3, y3, 0, xs, ys)
            return xs.toFloatArray() to ys.toFloatArray()
        }

        private fun subdivideCubic(
            x0: Float, y0: Float,
            c1x: Float, c1y: Float, c2x: Float, c2y: Float,
            x3: Float, y3: Float,
            level: Int, xs: ArrayList<Float>, ys: ArrayList<Float>,
        ) {
            // Use the maximum of the two control-point-to-chord distances
            // as the cubic's chord error bound (matches the rasteriser).
            val mx = (x0 + x3) * 0.5f; val my = (y0 + y3) * 0.5f
            val e1x = c1x - mx; val e1y = c1y - my
            val e2x = c2x - mx; val e2y = c2y - my
            val err2 = maxOf(e1x * e1x + e1y * e1y, e2x * e2x + e2y * e2y)
            if (err2 < CHORD_TOL * CHORD_TOL || level >= MAX_LEVELS) {
                xs.add(x3); ys.add(y3)
                return
            }
            val ax = (x0 + c1x) * 0.5f; val ay = (y0 + c1y) * 0.5f
            val bx = (c1x + c2x) * 0.5f; val by = (c1y + c2y) * 0.5f
            val cx2 = (c2x + x3) * 0.5f; val cy2 = (c2y + y3) * 0.5f
            val dx2 = (ax + bx) * 0.5f; val dy2 = (ay + by) * 0.5f
            val ex2 = (bx + cx2) * 0.5f; val ey2 = (by + cy2) * 0.5f
            val midX = (dx2 + ex2) * 0.5f; val midY = (dy2 + ey2) * 0.5f
            subdivideCubic(x0, y0, ax, ay, dx2, dy2, midX, midY, level + 1, xs, ys)
            subdivideCubic(midX, midY, ex2, ey2, cx2, cy2, x3, y3, level + 1, xs, ys)
        }
    }
}

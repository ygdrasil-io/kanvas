package org.skia.foundation

import org.skia.math.SkMatrix
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

    override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? {
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
                    distFromContourStart = dashLine(out, penX, penY, nx, ny, distFromContourStart)
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
                        contourStartX, contourStartY, distFromContourStart)
                    penX = contourStartX; penY = contourStartY
                }
            }
        }
        return out.detach()
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
    ): Float {
        val dx = x1 - x0; val dy = y1 - y0
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0f) return startDist
        val ux = dx / length; val uy = dy / length

        // Position in the dash cycle at the start of the segment.
        var (i, withinInterval) = locateInCycle(startDist)
        var remaining = intervals[i] - withinInterval
        var consumed = 0f

        while (consumed < length) {
            val available = length - consumed
            val step = if (remaining < available) remaining else available
            if (i % 2 == 0 && step > 0f) {
                // "on" segment — emit a moveTo + lineTo (dash-effect
                // output is a series of disconnected segments ; the
                // stroker handles caps per segment).
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

package org.skia.foundation

import org.graphiks.math.SkMatrix
import kotlin.math.sqrt

/**
 * Mirrors Skia's
 * [`SkCornerPathEffect`](https://github.com/google/skia/blob/main/include/effects/SkCornerPathEffect.h)
 * — rounds the corners of a polyline path with a given [radius]. Each
 * non-terminal vertex `Vᵢ` between segments `(Vᵢ₋₁, Vᵢ)` and `(Vᵢ, Vᵢ₊₁)`
 * is replaced by :
 *
 * ```
 *   lineTo(pullback)         // move along incoming edge by `radius`
 *   quadTo(Vᵢ, pushforward)  // arc through the corner to a point
 *                            // `radius` along the outgoing edge
 * ```
 *
 * For a closed polygon every vertex is smoothed (including the
 * "closing corner" — `kMove` is replaced by `pullback at V₀` and the
 * final `kClose` brings us back through the smoothed start).
 *
 * For an open polyline the endpoints `V₀` / `Vₙ₋₁` keep their sharp
 * positions ; only interior vertices are smoothed.
 *
 * Per-corner safety : when an adjacent segment is shorter than
 * `radius`, the smoothing budget for that side is clamped to half
 * the segment length so two consecutive smooth-overs don't fight
 * over the same arc-length real estate.
 *
 * Construct via [Make]. `radius <= 0` returns `null` (no-op).
 *
 * **Phase 7p2 coverage** :
 *  - `kMove` + `kLine`* + optional `kClose` — fully supported.
 *  - `kQuad` / `kConic` / `kCubic` — passed through verbatim.
 *    Skia upstream smooths line→curve and curve→line junctions ;
 *    we defer that to a follow-up since the canonical use case
 *    (rounded polygons / rectangles / star paths) is line-only.
 */
public class SkCornerPathEffect private constructor(
    private val radius: Float,
) : SkPathEffect() {

    override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? {
        if (input.isEmpty()) return null
        val out = SkPathBuilder()

        // Pass 1 : split the verb stream into contours of polyline
        // vertices. Curve verbs (kQuad/kConic/kCubic) break the
        // current poly contour into a passthrough segment and start
        // a fresh polyline at their endpoint.
        var coordIdx = 0
        var contourPts = mutableListOf<FloatArray>()  // each element = [x, y]
        var contourClosed = false
        var contourStartX = 0f; var contourStartY = 0f
        var pen: FloatArray? = null

        for (verb in input.verbs) {
            when (verb) {
                SkPath.Verb.kMove -> {
                    flushPolyline(out, contourPts, contourClosed)
                    val mx = input.coords[coordIdx++]
                    val my = input.coords[coordIdx++]
                    contourPts = mutableListOf(floatArrayOf(mx, my))
                    contourClosed = false
                    contourStartX = mx; contourStartY = my
                    pen = floatArrayOf(mx, my)
                }
                SkPath.Verb.kLine -> {
                    val nx = input.coords[coordIdx++]
                    val ny = input.coords[coordIdx++]
                    contourPts.add(floatArrayOf(nx, ny))
                    pen = floatArrayOf(nx, ny)
                }
                SkPath.Verb.kClose -> {
                    contourClosed = true
                    flushPolyline(out, contourPts, contourClosed)
                    contourPts = mutableListOf()
                    contourClosed = false
                    pen = floatArrayOf(contourStartX, contourStartY)
                }
                SkPath.Verb.kQuad -> {
                    flushPolyline(out, contourPts, contourClosed)
                    val cx = input.coords[coordIdx++]
                    val cy = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]
                    val ey = input.coords[coordIdx++]
                    if (pen != null) out.moveTo(pen[0], pen[1])
                    out.quadTo(cx, cy, ex, ey)
                    pen = floatArrayOf(ex, ey)
                    contourPts = mutableListOf(floatArrayOf(ex, ey))
                    contourClosed = false
                    contourStartX = ex; contourStartY = ey
                }
                SkPath.Verb.kConic -> {
                    flushPolyline(out, contourPts, contourClosed)
                    val cx = input.coords[coordIdx++]
                    val cy = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]
                    val ey = input.coords[coordIdx++]
                    if (pen != null) out.moveTo(pen[0], pen[1])
                    // No conic in builder ⇒ degrade to quad.
                    out.quadTo(cx, cy, ex, ey)
                    pen = floatArrayOf(ex, ey)
                    contourPts = mutableListOf(floatArrayOf(ex, ey))
                    contourClosed = false
                    contourStartX = ex; contourStartY = ey
                }
                SkPath.Verb.kCubic -> {
                    flushPolyline(out, contourPts, contourClosed)
                    val c1x = input.coords[coordIdx++]
                    val c1y = input.coords[coordIdx++]
                    val c2x = input.coords[coordIdx++]
                    val c2y = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]
                    val ey = input.coords[coordIdx++]
                    if (pen != null) out.moveTo(pen[0], pen[1])
                    out.cubicTo(c1x, c1y, c2x, c2y, ex, ey)
                    pen = floatArrayOf(ex, ey)
                    contourPts = mutableListOf(floatArrayOf(ex, ey))
                    contourClosed = false
                    contourStartX = ex; contourStartY = ey
                }
                SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
            }
        }
        // Final flush for the trailing (potentially open) polyline.
        flushPolyline(out, contourPts, contourClosed)
        return out.detach()
    }

    /**
     * Emit a polyline contour into [out], with each interior vertex
     * smoothed to a quadratic curve of half-width [radius]. Handles
     * the closed (every vertex smoothed) and open (endpoints sharp)
     * cases per the Skia contract.
     */
    private fun flushPolyline(
        out: SkPathBuilder,
        pts: List<FloatArray>,
        closed: Boolean,
    ) {
        val n = pts.size
        if (n == 0) return
        if (n == 1) {
            // Degenerate single-point contour — emit moveTo + close so
            // the rasteriser sees a 0-length segment that can be
            // stroked into a hairline cap.
            out.moveTo(pts[0][0], pts[0][1])
            if (closed) out.close()
            return
        }
        if (n == 2) {
            // Single segment, no corner to smooth.
            out.moveTo(pts[0][0], pts[0][1])
            out.lineTo(pts[1][0], pts[1][1])
            if (closed) out.close()
            return
        }

        if (closed) {
            // Closed polygon : smooth every vertex including V₀.
            // Start by moving to the pulled-back position from V₀
            // toward V₁.
            val v0 = pts[0]; val v1 = pts[1]; val vLast = pts[n - 1]
            val pulled = pullBack(v0, v1, radius, vLast)
            out.moveTo(pulled[0], pulled[1])
            // Smooth every interior vertex.
            for (i in 0 until n) {
                val prev = pts[(i - 1 + n) % n]
                val cur = pts[i]
                val next = pts[(i + 1) % n]
                if (i == 0) {
                    // V₀'s pullback was already emitted via moveTo above ;
                    // emit only the quadTo + pushforward for V₀.
                    val push = pushForward(cur, next, radius, prev)
                    out.quadTo(cur[0], cur[1], push[0], push[1])
                } else {
                    smoothInteriorVertex(out, prev, cur, next)
                }
            }
            out.close()
        } else {
            // Open polyline : V₀ + V_{n-1} stay sharp, interior smoothed.
            out.moveTo(pts[0][0], pts[0][1])
            for (i in 1 until n - 1) {
                smoothInteriorVertex(out, pts[i - 1], pts[i], pts[i + 1])
            }
            out.lineTo(pts[n - 1][0], pts[n - 1][1])
        }
    }

    /**
     * Emit `lineTo(pullback) ; quadTo(cur, pushforward)` for an
     * interior vertex. The pullback / pushforward distances are
     * clamped to half the adjacent segment length so two consecutive
     * smoothings don't overshoot.
     */
    private fun smoothInteriorVertex(
        out: SkPathBuilder,
        prev: FloatArray, cur: FloatArray, next: FloatArray,
    ) {
        val pull = pullBack(cur, prev, radius, next)
        out.lineTo(pull[0], pull[1])
        val push = pushForward(cur, next, radius, prev)
        out.quadTo(cur[0], cur[1], push[0], push[1])
    }

    /**
     * Position [radius] along the segment from [from] toward [from2]
     * (i.e. the *incoming* segment's end-of-corner). Clamped to half
     * the segment length when [other]'s opposing segment is shorter
     * — the half-segment cap prevents two corners from overlapping.
     */
    private fun pullBack(
        from: FloatArray, from2: FloatArray, r: Float, other: FloatArray,
    ): FloatArray {
        val dx = from2[0] - from[0]; val dy = from2[1] - from[1]
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 0f) return floatArrayOf(from[0], from[1])
        // Cap : both this corner and the next one share the segment.
        val cap = minOf(r, len * 0.5f, distance(from, other) * 0.5f)
        val use = if (cap > 0f) cap else minOf(r, len * 0.5f)
        return floatArrayOf(from[0] + dx * (use / len), from[1] + dy * (use / len))
    }

    private fun pushForward(
        from: FloatArray, to: FloatArray, r: Float, other: FloatArray,
    ): FloatArray = pullBack(from, to, r, other)

    private fun distance(a: FloatArray, b: FloatArray): Float {
        val dx = b[0] - a[0]; val dy = b[1] - a[1]
        return sqrt(dx * dx + dy * dy)
    }

    public companion object {
        /**
         * Mirrors Skia's `SkCornerPathEffect::Make(radius)`. Returns
         * `null` for non-positive / non-finite [radius] (no-op).
         */
        public fun Make(radius: Float): SkPathEffect? {
            if (!radius.isFinite() || radius <= 0f) return null
            return SkCornerPathEffect(radius)
        }
    }
}

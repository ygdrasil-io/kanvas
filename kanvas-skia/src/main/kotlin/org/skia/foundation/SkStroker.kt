package org.skia.foundation

import org.graphiks.math.SkScalar
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Convert a stroked source-space [SkPath] into a filled outline path.
 *
 * Caps & joins (Phase 3c → 3g)
 * ----------------------------
 * - **Caps** — open-contour endpoint shapes:
 *   - [SkPaint.Cap.kButt_Cap]   : perpendicular flat end (Phase 3c).
 *   - [SkPaint.Cap.kSquare_Cap] : flat end extended by `halfW` along the
 *                                 segment tangent (Phase 3g).
 *   - [SkPaint.Cap.kRound_Cap]  : half-disc, two cubic Béziers per cap
 *                                 with the standard kappa approximation
 *                                 (Phase 3g).
 * - **Joins** — vertex shapes between consecutive segments:
 *   - [SkPaint.Join.kMiter_Join] : miter intersection with bevel fallback
 *                                  when `|M − P| > miterLimit × halfW`
 *                                  (Phase 3c).
 *   - [SkPaint.Join.kBevel_Join] : straight diagonal between offset
 *                                  endpoints (Phase 3g).
 *   - [SkPaint.Join.kRound_Join] : circular arc, sub-pixel-flattened to
 *                                  ~22.5° steps and appended as line
 *                                  segments to the offset polyline
 *                                  (Phase 3g).
 *
 * Algorithm
 * ---------
 * 1. **Flatten**: walk the source path verbs, recursively subdividing
 *    quad/cubic Béziers (De Casteljau, 0.25 px chord tolerance) and
 *    parametrically stepping conics into polyline contours. Result: one
 *    [Polyline] per contour (`closed` flag carried through).
 * 2. **Per-segment normals**: for each polyline edge, compute the unit
 *    perpendicular pointing left of direction (CCW rotation). Zero-length
 *    segments contribute the zero normal — the join formula tolerates it.
 * 3. **Side polylines**: walk the contour vertex by vertex emitting offset
 *    points on both sides via the [emitJoin] helper, which dispatches on
 *    the requested [SkPaint.Join] mode:
 *
 *    ```
 *    miter:   M − P = halfW / (1 + n_prev · n_next) · (n_prev + n_next)
 *             (bevel fallback when |M − P| > miterLimit × halfW)
 *    bevel:   two flat-end offset points
 *    round:   line-flattened arc at ~22.5° steps from n_prev to n_next
 *    ```
 *
 * 4. **Outline assembly**:
 *    - **Closed** contour → emit two closed sub-contours: outer side as-is
 *      (CW), inner side reversed (CCW). The winding fill rule then paints
 *      the band between them.
 *    - **Open** contour → emit a single closed contour wrapping
 *      `left + cap_end + reverse(right) + cap_start`. The end / start
 *      caps dispatch on [SkPaint.Cap]: butt, square, or round (cubic
 *      Bézier).
 *
 * Stroking happens in **source space** — the device CTM is applied later
 * by the rasterizer when filling the outline. Stroke width is therefore
 * scaled along with the geometry (matches Skia's default behaviour).
 *
 * `strokeWidth ≤ 0` is treated as a 1-unit hairline (no separate hairline
 * code path yet — this is an approximation that produces correct visible
 * output for typical cases; bit-exact hairline can come later).
 */
public class SkStroker private constructor(
    public val width: SkScalar,
    public val cap: SkPaint.Cap,
    public val join: SkPaint.Join,
    public val miterLimit: SkScalar,
    /**
     * CTM-driven flattening hint. The stroker's polyline IS the outline
     * vertex sequence, so its chord error is visible in the stroke shape
     * and the rasterizer can't compensate later. A `resScale` of `1f`
     * keeps source-space chord error ≤ [FLATNESS]; a `resScale` of e.g.
     * `1000f` (under `scale(1000, 1000)`) tightens the source-space
     * tolerance to `FLATNESS / resScale`, so device-space chord error
     * stays below 0.25 px regardless of CTM magnitude.
     */
    public val resScale: SkScalar,
) {
    private val halfW: Float = width * 0.5f

    /** `FLATNESS_SQ / resScale²` — pre-computed to avoid the divide per call. */
    private val flatnessSq: Float = FLATNESS_SQ / (resScale * resScale)

    /** Conic step count, scaled with `√resScale` to keep chord error bounded. */
    private val conicSteps: Int = max(CONIC_STEPS, ceil(CONIC_STEPS * sqrt(resScale)).toInt())
        .coerceAtMost(MAX_CONIC_STEPS)

    public fun stroke(src: SkPath): SkPath {
        if (src.isEmpty() || width <= 0f) {
            return SkPathBuilder().setFillType(SkPathFillType.kWinding).detach()
        }
        val out = SkPathBuilder().setFillType(SkPathFillType.kWinding)
        for (contour in flattenContours(src, flatnessSq, conicSteps)) {
            strokeContour(out, contour)
        }
        return out.detach()
    }

    private fun strokeContour(out: SkPathBuilder, contour: Polyline) {
        val pts = contour.coords
        var n = pts.size / 2
        if (n < 2) return  // degenerate (single moveTo) — would need a "dot" cap, deferred.

        // Defensive de-dup if a closed contour received an explicit final lineTo
        // back to its start (Skia's flatten pipeline doesn't, but be robust).
        if (contour.closed && n >= 3 &&
            pts[0] == pts[(n - 1) * 2] && pts[1] == pts[(n - 1) * 2 + 1]) {
            n -= 1
        }

        val nSegs = if (contour.closed) n else n - 1
        if (nSegs < 1) return

        // Per-segment unit normals (left = CCW perpendicular to direction).
        val nx = FloatArray(nSegs)
        val ny = FloatArray(nSegs)
        for (i in 0 until nSegs) {
            val a = i * 2
            val b = ((i + 1) % n) * 2
            val dx = pts[b] - pts[a]
            val dy = pts[b + 1] - pts[a + 1]
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-6f) {
                nx[i] = 0f; ny[i] = 0f
            } else {
                nx[i] = -dy / len; ny[i] = dx / len
            }
        }

        val left = FloatArrayList()
        val right = FloatArrayList()

        if (contour.closed) {
            // Wrap-around joins on every vertex — no caps.
            for (i in 0 until n) {
                val px = pts[i * 2]; val py = pts[i * 2 + 1]
                val prev = (i - 1 + nSegs) % nSegs
                val cur = i % nSegs
                emitJoin(left,  px, py, halfW, miterLimit, join,
                    nx[prev],  ny[prev],  nx[cur],  ny[cur])
                emitJoin(right, px, py, halfW, miterLimit, join,
                    -nx[prev], -ny[prev], -nx[cur], -ny[cur])
            }
            // Inner-offset orientation heuristic ("engulfed contour" fix)
            // -----------------------------------------------------------
            // Upstream Skia detects when the inner offset polyline crosses
            // itself or its sibling outer (because halfW exceeds the
            // contour's local inradius) and adjusts the band so the
            // winding fill doesn't paint a self-intersection "bowtie"
            // hole. The full upstream machinery (`SkPathStroker::cubicTo`
            // splits at inflection points, then `cubicStroke` recursion
            // handles per-segment reversal via fInner / fOuter sinks)
            // operates per Bézier segment ; we apply an equivalent
            // contour-level orientation check on the polyline result.
            //
            // The two offset polylines (`left` = + halfW · normal,
            // `right` = − halfW · normal) normally have OPPOSITE signed
            // areas relative to the source. When the stroke is so wide
            // that the inner offset sweeps PAST the source and ends up
            // on the OUTSIDE again (typical for "engulfed" tiny contours,
            // e.g. OverStrokeGM quad cell : 40-unit arch stroked at
            // halfW = 250), the inner polyline ends up SAME-SIGN as the
            // outer and the standard "reverse inner → winding subtract"
            // rule renders a bowtie hole instead of a solid band.
            //
            // Heuristic activates only when:
            //  (1) `halfW` ≥ 10 source units (avoids text-glyph hairline
            //      strokes where halfW is sub-pixel) ;
            //  (2) `halfW` > 1.5 × source bounding-box max dimension
            //      (the contour is genuinely engulfed) ;
            //  (3) the two offset areas are same-sign with ratio > 0.7
            //      (symmetric "engulfing" both above and below).
            // In the engulfed case, emit both offsets same-direction
            // (winding UNION). Otherwise fall back to the standard band
            // with the original (right = outer, left = inner) mapping so
            // closed contours of any orientation get the exact original
            // verb stream — keeping text-glyph + oval-donut renderings
            // pixel-stable.
            val srcArea = signedArea(pts, n)
            val outerArea: Float
            val innerArea: Float
            if (srcArea >= 0f) {
                outerArea = signedArea(right); innerArea = signedArea(left)
            } else {
                outerArea = signedArea(left); innerArea = signedArea(right)
            }
            val absInner = kotlin.math.abs(innerArea)
            val absOuter = kotlin.math.abs(outerArea)
            val ratio = if (absOuter > 0f) absInner / absOuter else 0f
            val sameSign = (outerArea >= 0f) == (innerArea >= 0f)
            val maxDim = contourMaxDimension(pts, n)
            val engulfed = sameSign && ratio > 0.7f && halfW > 10f &&
                halfW > 1.5f * maxDim
            if (engulfed) {
                val outerSide = if (srcArea >= 0f) right else left
                val innerSide = if (srcArea >= 0f) left else right
                emitClosedContour(out, outerSide, reversed = false)
                emitClosedContour(out, innerSide, reversed = false)
            } else {
                // Standard band (original behaviour preserved exactly).
                emitClosedContour(out, right, reversed = false)
                emitClosedContour(out, left, reversed = true)
            }
        } else {
            // First vertex: butt-cap-style flat offset on each side.
            val px0 = pts[0]; val py0 = pts[1]
            left.add(px0 + halfW * nx[0]);  left.add(py0 + halfW * ny[0])
            right.add(px0 - halfW * nx[0]); right.add(py0 - halfW * ny[0])

            // Internal joins.
            for (i in 1 until nSegs) {
                val px = pts[i * 2]; val py = pts[i * 2 + 1]
                emitJoin(left,  px, py, halfW, miterLimit, join,
                    nx[i - 1],  ny[i - 1],  nx[i],  ny[i])
                emitJoin(right, px, py, halfW, miterLimit, join,
                    -nx[i - 1], -ny[i - 1], -nx[i], -ny[i])
            }

            // Last vertex.
            val pxN = pts[(n - 1) * 2]; val pyN = pts[(n - 1) * 2 + 1]
            val nl = nSegs - 1
            left.add(pxN + halfW * nx[nl]);  left.add(pyN + halfW * ny[nl])
            right.add(pxN - halfW * nx[nl]); right.add(pyN - halfW * ny[nl])

            // Per-segment unit tangent at start and end. tangent = (ny, -nx).
            val tStartX = ny[0]; val tStartY = -nx[0]
            val tEndX = ny[nl]; val tEndY = -nx[nl]
            val px0Endpoint = pts[0]; val py0Endpoint = pts[1]
            val pxNEndpoint = pts[(n - 1) * 2]; val pyNEndpoint = pts[(n - 1) * 2 + 1]
            val nStartX = nx[0]; val nStartY = ny[0]
            val nEndX = nx[nl]; val nEndY = ny[nl]

            emitOpenStrokeOutline(
                out, left, right, cap, halfW,
                pxNEndpoint, pyNEndpoint, tEndX, tEndY, nEndX, nEndY,
                px0Endpoint, py0Endpoint, tStartX, tStartY, nStartX, nStartY,
            )
        }
    }

    /**
     * Shoelace-formula signed area of a closed polyline given as a flat
     * `(x, y)` FloatArray with `n` vertices (the implicit close edge is
     * `pts[n-1] → pts[0]`). Positive = CCW in the standard Cartesian
     * convention ; in Skia's y-down device space, positive = CW visually.
     * Used by the inner-offset reversal check : sign change between the
     * source contour and the inner offset means the inner has flipped.
     */
    private fun signedArea(pts: FloatArray, n: Int): Float {
        if (n < 3) return 0f
        var s = 0f
        var j = n - 1
        for (i in 0 until n) {
            s += (pts[j * 2] + pts[i * 2]) * (pts[i * 2 + 1] - pts[j * 2 + 1])
            j = i
        }
        return 0.5f * s
    }

    /** Bounding box max(width, height) for the closed contour's vertex set. */
    private fun contourMaxDimension(pts: FloatArray, n: Int): Float {
        if (n < 1) return 0f
        var minX = pts[0]; var maxX = pts[0]
        var minY = pts[1]; var maxY = pts[1]
        for (i in 1 until n) {
            val x = pts[i * 2]; val y = pts[i * 2 + 1]
            if (x < minX) minX = x else if (x > maxX) maxX = x
            if (y < minY) minY = y else if (y > maxY) maxY = y
        }
        return maxOf(maxX - minX, maxY - minY)
    }

    /** Overload : signed area of a closed [FloatArrayList] polyline. */
    private fun signedArea(poly: FloatArrayList): Float {
        val n = poly.size / 2
        if (n < 3) return 0f
        var s = 0f
        var j = n - 1
        for (i in 0 until n) {
            s += (poly[j * 2] + poly[i * 2]) * (poly[i * 2 + 1] - poly[j * 2 + 1])
            j = i
        }
        return 0.5f * s
    }

    /** Emit a closed sub-contour from [poly], optionally reversing direction. */
    private fun emitClosedContour(out: SkPathBuilder, poly: FloatArrayList, reversed: Boolean) {
        val n = poly.size / 2
        if (n < 2) return
        if (!reversed) {
            out.moveTo(poly[0], poly[1])
            for (i in 1 until n) out.lineTo(poly[i * 2], poly[i * 2 + 1])
        } else {
            out.moveTo(poly[(n - 1) * 2], poly[(n - 1) * 2 + 1])
            for (i in n - 2 downTo 0) out.lineTo(poly[i * 2], poly[i * 2 + 1])
        }
        out.close()
    }

    /**
     * Emit a single closed contour wrapping the left side, the **end cap**,
     * the right side reversed, and the **start cap**. The cap shape
     * dispatches on [cap]:
     *
     * - **kButt**  — straight line `left[end] → right[end]` and the implicit
     *   `close()` for the start cap.
     * - **kSquare** — extend `left[end]` and `right[end]` by `halfW` along
     *   the segment tangent at each endpoint, then close back.
     * - **kRound**  — two cubic Béziers per cap, kappa-approximating the
     *   half-disc around the endpoint.
     */
    private fun emitOpenStrokeOutline(
        out: SkPathBuilder, left: FloatArrayList, right: FloatArrayList,
        cap: SkPaint.Cap, halfW: Float,
        pNX: Float, pNY: Float, tEndX: Float, tEndY: Float, nEndX: Float, nEndY: Float,
        p0X: Float, p0Y: Float, tStartX: Float, tStartY: Float, nStartX: Float, nStartY: Float,
    ) {
        val ln = left.size / 2
        val rn = right.size / 2
        if (ln < 2 || rn < 2) return

        // Forward along the left side.
        out.moveTo(left[0], left[1])
        for (i in 1 until ln) out.lineTo(left[i * 2], left[i * 2 + 1])

        // End cap: from left[end] to right[end].
        val lEndX = left[(ln - 1) * 2]; val lEndY = left[(ln - 1) * 2 + 1]
        val rEndX = right[(rn - 1) * 2]; val rEndY = right[(rn - 1) * 2 + 1]
        when (cap) {
            SkPaint.Cap.kButt_Cap -> out.lineTo(rEndX, rEndY)
            SkPaint.Cap.kSquare_Cap -> {
                val dx = halfW * tEndX; val dy = halfW * tEndY
                out.lineTo(lEndX + dx, lEndY + dy)
                out.lineTo(rEndX + dx, rEndY + dy)
                out.lineTo(rEndX, rEndY)
            }
            SkPaint.Cap.kRound_Cap -> emitRoundCap(
                out, pNX, pNY, halfW, tEndX, tEndY, nEndX, nEndY, atEnd = true,
            )
        }

        // Reversed right side.
        for (i in rn - 2 downTo 0) out.lineTo(right[i * 2], right[i * 2 + 1])

        // Start cap: from right[0] back to left[0]. The implicit close() handles
        // kButt; kSquare and kRound emit explicit vertices/curves first.
        when (cap) {
            SkPaint.Cap.kButt_Cap -> { /* close() handles it */ }
            SkPaint.Cap.kSquare_Cap -> {
                val dx = -halfW * tStartX; val dy = -halfW * tStartY
                val rStartX = right[0]; val rStartY = right[1]
                out.lineTo(rStartX + dx, rStartY + dy)
                out.lineTo(left[0] + dx, left[1] + dy)
            }
            SkPaint.Cap.kRound_Cap -> emitRoundCap(
                out, p0X, p0Y, halfW, tStartX, tStartY, nStartX, nStartY, atEnd = false,
            )
        }
        out.close()
    }

    /**
     * Emit two cubic-Bézier quarters approximating the half-disc cap around
     * `(px, py)` with radius [halfW]. The arc connects the offset side
     * `+normal` to `−normal` via the **outward** direction `±tangent`:
     * `+tangent` for the end cap (`atEnd = true`), `−tangent` for the
     * start cap (`atEnd = false`).
     *
     * Math (end cap, `atEnd = true`, kappa = `(4/3)·(√2 − 1)`):
     * ```
     * A = p + halfW · n         B = p + halfW · t        C = p − halfW · n
     * cubic1: A → B   c1 = A + κ·halfW·t,  c2 = B + κ·halfW·n
     * cubic2: B → C   c1 = B − κ·halfW·n,  c2 = C + κ·halfW·t
     * ```
     * Start cap mirrors with `tangent → −tangent`.
     */
    private fun emitRoundCap(
        out: SkPathBuilder,
        px: Float, py: Float, halfW: Float,
        tx: Float, ty: Float, nx: Float, ny: Float,
        atEnd: Boolean,
    ) {
        val k = ROUND_KAPPA * halfW
        val sign = if (atEnd) 1f else -1f
        val outX = sign * tx; val outY = sign * ty   // outward direction
        val outKx = sign * k * tx; val outKy = sign * k * ty
        val nKx = k * nx; val nKy = k * ny
        // A = p + halfW · n  (start of cap, already at lastPt — caller emitted lineTo)
        val Ax = px + halfW * nx; val Ay = py + halfW * ny
        val Bx = px + halfW * outX; val By = py + halfW * outY
        val Cx = px - halfW * nx; val Cy = py - halfW * ny
        // Cubic 1: A → B.
        out.cubicTo(
            Ax + outKx, Ay + outKy,    // c1 = A + κh·outDir
            Bx + nKx,  By + nKy,        // c2 = B + κh·n
            Bx,        By,              // B
        )
        // Cubic 2: B → C.
        out.cubicTo(
            Bx - nKx,  By - nKy,        // c1 = B − κh·n
            Cx + outKx, Cy + outKy,     // c2 = C + κh·outDir
            Cx,        Cy,              // C
        )
    }

    public companion object {
        public fun fromPaint(paint: SkPaint, resScale: SkScalar = 1f): SkStroker = SkStroker(
            width = if (paint.strokeWidth <= 0f) 1f else paint.strokeWidth,
            cap = paint.strokeCap,
            join = paint.strokeJoin,
            miterLimit = paint.strokeMiter,
            resScale = resScale,
        )

        // Flattening tolerance — interpretable as a target chord error of
        // 0.25 device-space pixels. With `resScale = 1f` (no CTM scale) it
        // is also the source-space tolerance; with `resScale > 1f` (CTM
        // scaling up) the source-space tolerance shrinks to keep device-
        // space chord error bounded. The stroker's polyline IS the outline
        // vertex sequence — the rasterizer can't re-smooth a polyline, so
        // this resolution awareness is essential when the CTM blows the
        // path up by orders of magnitude (e.g. `Strokes4GM` at 1000×).
        internal const val FLATNESS: Float = 0.25f
        internal const val FLATNESS_SQ: Float = FLATNESS * FLATNESS
        internal const val MAX_DEPTH: Int = 18
        internal const val CONIC_STEPS: Int = 32
        /** Cap on conic step count under heavy `resScale` (avoids OOM). */
        internal const val MAX_CONIC_STEPS: Int = 4096
        /** `(4/3)·(√2 − 1)` — kappa for 90° cubic-Bézier arc approximation. */
        internal const val ROUND_KAPPA: Float = 0.5522847498307933f
        /** Round-join arc segments per quarter-turn (~22.5° step). */
        internal const val ROUND_JOIN_SEGS_PER_QUARTER: Int = 8
    }
}

// ----------------------------------------------------------------------
// Internal helpers — file-private to keep the SkStroker class concise.
// ----------------------------------------------------------------------

internal class Polyline(val coords: FloatArray, val closed: Boolean)

/** Tiny growable FloatArray — avoids the boxing cost of `MutableList<Float>`. */
internal class FloatArrayList(initialCapacity: Int = 16) {
    private var arr: FloatArray = FloatArray(initialCapacity)
    var size: Int = 0
        private set
    fun add(v: Float) {
        if (size == arr.size) arr = arr.copyOf(if (size == 0) 16 else size * 2)
        arr[size++] = v
    }
    operator fun get(i: Int): Float = arr[i]
    fun toFloatArray(): FloatArray = arr.copyOf(size)
}

/**
 * Walk [path]'s verb stream and produce a list of polyline contours.
 * Béziers are flattened in source space using recursive De Casteljau
 * subdivision, with chord-error threshold [flatnessSq] (= `FLATNESS_SQ /
 * resScale²`); conics are stepped uniformly with [conicSteps] segments.
 *
 * The chord error of the resulting polyline IS the visible stroke shape
 * error — the rasterizer flattens straight-line input no further. Pass
 * the CTM scale to the stroker so it can pre-tighten this tolerance.
 */
internal fun flattenContours(
    path: SkPath,
    flatnessSq: Float,
    conicSteps: Int,
): List<Polyline> {
    val out = ArrayList<Polyline>()
    var current = ArrayList<Float>()
    var hasContour = false
    var coordIdx = 0
    var weightIdx = 0
    var px = 0f; var py = 0f
    val coords = path.coords
    val weights = path.conicWeights

    fun finalize(closed: Boolean) {
        if (current.size >= 4) out.add(Polyline(current.toFloatArray(), closed))
        current = ArrayList()
        hasContour = false
    }

    for (verb in path.verbs) {
        when (verb) {
            SkPath.Verb.kMove -> {
                if (hasContour) finalize(closed = false)
                px = coords[coordIdx++]; py = coords[coordIdx++]
                current.add(px); current.add(py)
                hasContour = true
            }
            SkPath.Verb.kLine -> {
                px = coords[coordIdx++]; py = coords[coordIdx++]
                current.add(px); current.add(py)
            }
            SkPath.Verb.kQuad -> {
                val x1 = coords[coordIdx++]; val y1 = coords[coordIdx++]
                val x2 = coords[coordIdx++]; val y2 = coords[coordIdx++]
                flattenQuad(current, px, py, x1, y1, x2, y2, 0, flatnessSq)
                px = x2; py = y2
            }
            SkPath.Verb.kConic -> {
                val x1 = coords[coordIdx++]; val y1 = coords[coordIdx++]
                val x2 = coords[coordIdx++]; val y2 = coords[coordIdx++]
                val w = weights[weightIdx++]
                flattenConic(current, px, py, x1, y1, x2, y2, w, conicSteps)
                px = x2; py = y2
            }
            SkPath.Verb.kCubic -> {
                val x1 = coords[coordIdx++]; val y1 = coords[coordIdx++]
                val x2 = coords[coordIdx++]; val y2 = coords[coordIdx++]
                val x3 = coords[coordIdx++]; val y3 = coords[coordIdx++]
                flattenCubic(current, px, py, x1, y1, x2, y2, x3, y3, 0, flatnessSq)
                px = x3; py = y3
            }
            SkPath.Verb.kClose -> {
                if (hasContour) finalize(closed = true)
            }
            SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
        }
    }
    if (hasContour) finalize(closed = false)
    return out
}

private fun flattenQuad(
    out: ArrayList<Float>,
    x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    depth: Int,
    flatnessSq: Float,
) {
    if (depth >= SkStroker.MAX_DEPTH || isQuadFlat(x0, y0, x1, y1, x2, y2, flatnessSq)) {
        out.add(x2); out.add(y2); return
    }
    val m01x = (x0 + x1) * 0.5f; val m01y = (y0 + y1) * 0.5f
    val m12x = (x1 + x2) * 0.5f; val m12y = (y1 + y2) * 0.5f
    val mx = (m01x + m12x) * 0.5f; val my = (m01y + m12y) * 0.5f
    flattenQuad(out, x0, y0, m01x, m01y, mx, my, depth + 1, flatnessSq)
    flattenQuad(out, mx, my, m12x, m12y, x2, y2, depth + 1, flatnessSq)
}

private fun isQuadFlat(
    x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    flatnessSq: Float,
): Boolean {
    val dx = x2 - x0; val dy = y2 - y0
    val chord2 = dx * dx + dy * dy
    if (chord2 < 1e-12f) return true
    val cross = (x1 - x0) * dy - (y1 - y0) * dx
    return (cross * cross) <= flatnessSq * chord2
}

private fun flattenCubic(
    out: ArrayList<Float>,
    x0: Float, y0: Float, x1: Float, y1: Float,
    x2: Float, y2: Float, x3: Float, y3: Float,
    depth: Int,
    flatnessSq: Float,
) {
    if (depth >= SkStroker.MAX_DEPTH ||
        isCubicFlat(x0, y0, x1, y1, x2, y2, x3, y3, flatnessSq)) {
        out.add(x3); out.add(y3); return
    }
    val m01x = (x0 + x1) * 0.5f; val m01y = (y0 + y1) * 0.5f
    val m12x = (x1 + x2) * 0.5f; val m12y = (y1 + y2) * 0.5f
    val m23x = (x2 + x3) * 0.5f; val m23y = (y2 + y3) * 0.5f
    val m012x = (m01x + m12x) * 0.5f; val m012y = (m01y + m12y) * 0.5f
    val m123x = (m12x + m23x) * 0.5f; val m123y = (m12y + m23y) * 0.5f
    val mx = (m012x + m123x) * 0.5f; val my = (m012y + m123y) * 0.5f
    flattenCubic(out, x0, y0, m01x, m01y, m012x, m012y, mx, my, depth + 1, flatnessSq)
    flattenCubic(out, mx, my, m123x, m123y, m23x, m23y, x3, y3, depth + 1, flatnessSq)
}

private fun isCubicFlat(
    x0: Float, y0: Float, x1: Float, y1: Float,
    x2: Float, y2: Float, x3: Float, y3: Float,
    flatnessSq: Float,
): Boolean {
    val dx = x3 - x0; val dy = y3 - y0
    val chord2 = dx * dx + dy * dy
    if (chord2 < 1e-12f) return true
    val c1 = (x1 - x0) * dy - (y1 - y0) * dx
    val c2 = (x2 - x0) * dy - (y2 - y0) * dx
    val maxCross2 = maxOf(c1 * c1, c2 * c2)
    return maxCross2 <= flatnessSq * chord2
}

private fun flattenConic(
    out: ArrayList<Float>,
    x0: Float, y0: Float, x1: Float, y1: Float,
    x2: Float, y2: Float, w: Float,
    conicSteps: Int,
) {
    val n = conicSteps
    for (k in 1..n) {
        val t = k.toFloat() / n
        val u = 1f - t
        val numW = u * u + 2f * u * t * w + t * t
        val numX = u * u * x0 + 2f * u * t * w * x1 + t * t * x2
        val numY = u * u * y0 + 2f * u * t * w * y1 + t * t * y2
        out.add(numX / numW); out.add(numY / numW)
    }
}

/**
 * Append a join vertex (or several, for round) to [poly] for the corner
 * at `(px, py)` between two segments with normals `n_prev` and `n_next`.
 *
 * Dispatches on [join]:
 *
 *  - **kMiter** : miter intersection
 *    `M − P = halfW / (1 + cos) · (n_prev + n_next)`, with bevel fallback
 *    when `|M − P| > miterLimit · halfW`.
 *  - **kBevel** : two flat-end offset points, no miter computation.
 *  - **kRound** : line-flattened arc from `n_prev` to `n_next` around `P`,
 *    ~22.5° step. The polyline-of-line-segments is later emitted as
 *    `lineTo` verbs by [emitClosedContour] / [emitOpenStrokeOutline]; for
 *    typical stroke widths the visual is indistinguishable from a true
 *    cubic-Bézier arc.
 *
 * Antiparallel normals (180° turn) on a non-round join skip the formula
 * and emit two distinct flat-end points (degenerate corner).
 */
private fun emitJoin(
    poly: FloatArrayList,
    px: Float, py: Float, halfW: Float, miterLimit: Float, join: SkPaint.Join,
    nPrevX: Float, nPrevY: Float, nNextX: Float, nNextY: Float,
) {
    val cos = nPrevX * nNextX + nPrevY * nNextY
    if (cos > 0.9999f) {
        // Nearly collinear — single point regardless of join mode.
        poly.add(px + halfW * nPrevX); poly.add(py + halfW * nPrevY)
        return
    }
    val denom = 1f + cos
    val antiparallel = denom < 1e-6f

    if (join == SkPaint.Join.kMiter_Join && !antiparallel) {
        val mFactor = halfW / denom
        val mDx = mFactor * (nPrevX + nNextX)
        val mDy = mFactor * (nPrevY + nNextY)
        val mDistSq = mDx * mDx + mDy * mDy
        val limit = miterLimit * halfW
        if (mDistSq <= limit * limit) {
            poly.add(px + mDx); poly.add(py + mDy)
            return
        }
        // Miter exceeded the limit — fall through to bevel.
    } else if (join == SkPaint.Join.kRound_Join && !antiparallel) {
        emitRoundJoinPolyline(poly, px, py, halfW, nPrevX, nPrevY, nNextX, nNextY)
        return
    }

    // Bevel (or kMiter > limit, or antiparallel on any mode).
    poly.add(px + halfW * nPrevX); poly.add(py + halfW * nPrevY)
    poly.add(px + halfW * nNextX); poly.add(py + halfW * nNextY)
}

/**
 * Emit a circular arc from `P + halfW · n_prev` to `P + halfW · n_next`
 * around `(px, py)` as `nSegs` line segments (~22.5° step). The first
 * point is included; the last point lands at the next-segment offset
 * start, which the caller would otherwise emit explicitly — no overlap
 * between consecutive joins.
 */
private fun emitRoundJoinPolyline(
    poly: FloatArrayList,
    px: Float, py: Float, halfW: Float,
    nPrevX: Float, nPrevY: Float, nNextX: Float, nNextY: Float,
) {
    val cosA = (nPrevX * nNextX + nPrevY * nNextY).toDouble().coerceIn(-1.0, 1.0)
    val theta = acos(cosA)
    // Sweep direction: positive cross → arc goes from n_prev CCW to n_next.
    val cross = nPrevX * nNextY - nPrevY * nNextX
    val sweep = if (cross >= 0f) theta else -theta
    val nSegs = max(1, ceil(theta / (PI / 2.0) * SkStroker.ROUND_JOIN_SEGS_PER_QUARTER).toInt())
    // Perpendicular CCW of n_prev (pre-computed for speed).
    val perpX = -nPrevY; val perpY = nPrevX
    poly.add(px + halfW * nPrevX); poly.add(py + halfW * nPrevY)
    for (i in 1..nSegs) {
        val a = sweep * i / nSegs
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        val rx = ca * nPrevX + sa * perpX
        val ry = ca * nPrevY + sa * perpY
        poly.add(px + halfW * rx); poly.add(py + halfW * ry)
    }
}

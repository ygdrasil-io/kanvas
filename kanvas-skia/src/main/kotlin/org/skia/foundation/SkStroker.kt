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
 *
 * Cubic cusp band-aid
 * -------------------
 * When a cubic has a cusp (point where the derivative magnitude collapses
 * to zero), the naive "outer offset / inner offset" approach leaves a
 * bowtie / butterfly hole at the cusp because the two side polylines
 * meet at the same point with opposite directions. Mirroring upstream
 * Skia's `SkPathStroker::cubicTo` -> `fCusper.addCircle(cuspLoc, fRadius)`
 * fix (see `src/core/SkStroke.cpp:1363-1367`), we detect cubic cusps
 * during the flatten pass via [findCubicCusp] (port of `SkFindCubicCusp`
 * in `src/core/SkGeometry.cpp:1112`) and emit an extra filled disc of
 * radius `halfW` at each cusp location. The winding fill rule unions
 * the disc with the stroke band, hiding the bowtie hole. See K8 PR #607
 * for the RCA on `OverStrokeGM`.
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
        val cusps = FloatArrayList()
        for (contour in flattenContoursWithCusps(src, flatnessSq, conicSteps, cusps)) {
            strokeContour(out, contour)
        }
        // Upstream Skia (post-2016) port : `SkPathStroker::cubicTo` ->
        // `fCusper.addCircle(cuspLoc, fRadius)`. When a cubic has a cusp
        // (point of zero derivative + max curvature) the "outer offset /
        // inner offset" approach above leaves a bowtie/butterfly hole near
        // the cusp ; the winding-fill union with a disc of radius `halfW`
        // at the cusp location fills it. See K8 PR #607 for the RCA.
        val n = cusps.size / 2
        for (i in 0 until n) {
            out.addCircle(cusps[i * 2], cusps[i * 2 + 1], halfW)
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
            // Outer (right side, same winding as source) and inner (left side,
            // reversed) — winding fill paints the band between.
            emitClosedContour(out, right, reversed = false)
            emitClosedContour(out, left,  reversed = true)
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
): List<Polyline> = flattenContoursWithCusps(path, flatnessSq, conicSteps, cuspsOut = null)

/**
 * Same as [flattenContours], but additionally records cubic-cusp
 * locations (one `(x, y)` pair per detected cusp) into [cuspsOut].
 * Mirrors upstream Skia's `SkPathStroker::cubicTo` ->
 * `fCusper.addCircle(cuspLoc, fRadius)` band-aid. See [SkStroker.stroke].
 */
internal fun flattenContoursWithCusps(
    path: SkPath,
    flatnessSq: Float,
    conicSteps: Int,
    cuspsOut: FloatArrayList?,
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
                if (cuspsOut != null) {
                    val t = findCubicCusp(px, py, x1, y1, x2, y2, x3, y3)
                    if (t > 0f) {
                        val cx = evalCubicX(px, x1, x2, x3, t)
                        val cy = evalCubicY(py, y1, y2, y3, t)
                        cuspsOut.add(cx); cuspsOut.add(cy)
                    }
                }
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

// ----------------------------------------------------------------------
// Cubic-cusp detection (port of upstream Skia `SkFindCubicCusp` +
// `SkFindCubicMaxCurvature` from `src/core/SkGeometry.cpp`). Used by
// the stroker as a band-aid for the "overstroke" bowtie artifact —
// when a cubic has a cusp we fill a disc of radius `halfW` at the
// cusp location, hiding the self-intersection hole of the outer/inner
// offset polyline.
// ----------------------------------------------------------------------

internal fun evalCubicX(x0: Float, x1: Float, x2: Float, x3: Float, t: Float): Float {
    val u = 1f - t
    return u * u * u * x0 + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * x3
}

internal fun evalCubicY(y0: Float, y1: Float, y2: Float, y3: Float, t: Float): Float {
    val u = 1f - t
    return u * u * u * y0 + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y3
}

/**
 * Returns t in (0, 1) of the cubic cusp, or `-1f` if there is none.
 * Port of `SkFindCubicCusp` (`src/core/SkGeometry.cpp:1112`).
 *
 * A cubic has a cusp when both legs (P0,P1) and (P2,P3) cross the
 * middle leg (P1,P2) — equivalent to the control polygon "X"-ing
 * itself. At the cusp, derivative magnitude vanishes (close to zero
 * relative to the cubic's overall extent).
 */
internal fun findCubicCusp(
    x0: Float, y0: Float, x1: Float, y1: Float,
    x2: Float, y2: Float, x3: Float, y3: Float,
): Float {
    // Same-side filters used by upstream — match degenerate cubics
    // (cf. the on_same_side helper).
    if (x0 == x1 && y0 == y1) return -1f
    if (x2 == x3 && y2 == y3) return -1f
    if (onSameSide(x0, y0, x1, y1, x2, y2, x3, y3)) return -1f  // ends both relative to (P0,P1)
    if (onSameSide(x2, y2, x3, y3, x0, y0, x1, y1)) return -1f  // start/control relative to (P2,P3)

    val roots = FloatArray(3)
    val n = findCubicMaxCurvature(x0, y0, x1, y1, x2, y2, x3, y3, roots)

    val precision = (
        sqDist(x0, y0, x1, y1) +
        sqDist(x1, y1, x2, y2) +
        sqDist(x2, y2, x3, y3)
    ) * 1e-8f

    for (i in 0 until n) {
        val t = roots[i]
        if (t <= 0f || t >= 1f) continue
        // F'(t) at t — checks magnitude.
        val u = 1f - t
        val dxA = 3f * (x1 - x0); val dxB = 3f * (x2 - x1); val dxC = 3f * (x3 - x2)
        val dyA = 3f * (y1 - y0); val dyB = 3f * (y2 - y1); val dyC = 3f * (y3 - y2)
        val dx = u * u * dxA + 2f * u * t * dxB + t * t * dxC
        val dy = u * u * dyA + 2f * u * t * dyB + t * t * dyC
        if (dx * dx + dy * dy < precision) return t
    }
    return -1f
}

private fun sqDist(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    return dx * dx + dy * dy
}

/**
 * Returns true if both points (tx0,ty0), (tx1,ty1) are in the same
 * half-plane defined by the segment (lx0,ly0)->(lx1,ly1). Port of
 * the static `on_same_side` helper in `SkGeometry.cpp`.
 */
private fun onSameSide(
    lx0: Float, ly0: Float, lx1: Float, ly1: Float,
    tx0: Float, ty0: Float, tx1: Float, ty1: Float,
): Boolean {
    val lineX = lx1 - lx0; val lineY = ly1 - ly0
    val c0 = lineX * (ty0 - ly0) - lineY * (tx0 - lx0)
    val c1 = lineX * (ty1 - ly0) - lineY * (tx1 - lx0)
    return c0 * c1 >= 0f
}

/**
 * Port of `SkFindCubicMaxCurvature` — finds up to 3 t values where
 * F'(t) · F''(t) = 0 (max curvature candidates), solving the cubic
 * polynomial `C·C t³ + 3B·C t² + (2B·B + C·A) t + A·B = 0` for each
 * axis-summed coefficient. Returns the number of real roots written
 * to [tValues] (already clamped to `[0, 1]` and sorted).
 */
private fun findCubicMaxCurvature(
    x0: Float, y0: Float, x1: Float, y1: Float,
    x2: Float, y2: Float, x3: Float, y3: Float,
    tValues: FloatArray,
): Int {
    val coeffX = FloatArray(4)
    val coeffY = FloatArray(4)
    formulateF1DotF2(x0, x1, x2, x3, coeffX)
    formulateF1DotF2(y0, y1, y2, y3, coeffY)
    for (i in 0..3) coeffX[i] += coeffY[i]
    return solveCubicPoly(coeffX, tValues)
}

private fun formulateF1DotF2(s0: Float, s1: Float, s2: Float, s3: Float, coeff: FloatArray) {
    val a = s1 - s0
    val b = s2 - 2f * s1 + s0
    val c = s3 + 3f * (s1 - s2) - s0
    coeff[0] = c * c
    coeff[1] = 3f * b * c
    coeff[2] = 2f * b * b + c * a
    coeff[3] = a * b
}

/**
 * Cubic-poly root solver — port of static `solve_cubic_poly` in
 * `SkGeometry.cpp:961`. Returns 1, 2 or 3 roots in `tValues`, all
 * clamped to `[0, 1]` and (for 3-root case) sorted ascending with
 * duplicates collapsed.
 */
private fun solveCubicPoly(coeff: FloatArray, tValues: FloatArray): Int {
    if (kotlin.math.abs(coeff[0]) < 1e-7f) {
        return solveQuadRoots(coeff[1], coeff[2], coeff[3], tValues)
    }
    val inva = 1f / coeff[0]
    val a = coeff[1] * inva
    val b = coeff[2] * inva
    val c = coeff[3] * inva

    val Q = (a * a - b * 3f) / 9f
    val R = (2f * a * a * a - 9f * a * b + 27f * c) / 54f
    val Q3 = Q * Q * Q
    val R2MinusQ3 = R * R - Q3
    val adiv3 = a / 3f

    if (R2MinusQ3 < 0f) {  // 3 real roots
        val sqQ = kotlin.math.sqrt(Q.toDouble())
        val sqQ3 = sqQ * sqQ * sqQ
        val ratio = (R.toDouble() / sqQ3).coerceIn(-1.0, 1.0)
        val theta = acos(ratio)
        val neg2RootQ = -2.0 * sqQ
        tValues[0] = (neg2RootQ * cos(theta / 3.0) - adiv3.toDouble())
            .toFloat().coerceIn(0f, 1f)
        tValues[1] = (neg2RootQ * cos((theta + 2.0 * PI) / 3.0) - adiv3.toDouble())
            .toFloat().coerceIn(0f, 1f)
        tValues[2] = (neg2RootQ * cos((theta - 2.0 * PI) / 3.0) - adiv3.toDouble())
            .toFloat().coerceIn(0f, 1f)
        bubbleSort3(tValues)
        return collapseDuplicates(tValues, 3)
    }
    // 1 real root
    var A = kotlin.math.abs(R) + kotlin.math.sqrt(R2MinusQ3)
    A = cbrtF(A)
    if (R > 0f) A = -A
    if (A != 0f) A += Q / A
    tValues[0] = (A - adiv3).coerceIn(0f, 1f)
    return 1
}

private fun cbrtF(v: Float): Float {
    val d = v.toDouble()
    val r = if (d >= 0.0) Math.cbrt(d) else -Math.cbrt(-d)
    return r.toFloat()
}

/** Port of `SkFindUnitQuadRoots`. Returns 0..2 roots clamped to `[0, 1]`. */
private fun solveQuadRoots(A: Float, B: Float, C: Float, roots: FloatArray): Int {
    if (kotlin.math.abs(A) < 1e-7f) {
        if (B == 0f) return 0
        val t = (-C / B).coerceIn(0f, 1f)
        roots[0] = t
        return 1
    }
    val disc = B * B - 4f * A * C
    if (disc < 0f) return 0
    val sq = kotlin.math.sqrt(disc)
    val q = if (B < 0f) -(B - sq) * 0.5f else -(B + sq) * 0.5f
    var count = 0
    val r0 = (q / A).coerceIn(0f, 1f)
    roots[count++] = r0
    if (q != 0f) {
        val r1 = (C / q).coerceIn(0f, 1f)
        if (r1 != r0) roots[count++] = r1
    }
    if (count == 2 && roots[0] > roots[1]) {
        val tmp = roots[0]; roots[0] = roots[1]; roots[1] = tmp
    }
    return count
}

private fun bubbleSort3(a: FloatArray) {
    if (a[0] > a[1]) { val t = a[0]; a[0] = a[1]; a[1] = t }
    if (a[1] > a[2]) { val t = a[1]; a[1] = a[2]; a[2] = t }
    if (a[0] > a[1]) { val t = a[0]; a[0] = a[1]; a[1] = t }
}

private fun collapseDuplicates(a: FloatArray, count: Int): Int {
    var write = 1
    for (i in 1 until count) {
        if (a[i] != a[write - 1]) {
            a[write++] = a[i]
        }
    }
    return write
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

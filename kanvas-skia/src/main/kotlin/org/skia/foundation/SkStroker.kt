package org.skia.foundation

import org.skia.math.SkScalar
import kotlin.math.sqrt

/**
 * Convert a stroked source-space [SkPath] into a filled outline path. Phase 3c
 * implements the most common configuration:
 *  - [SkPaint.Cap.kButt_Cap] — perpendicular flat ends on open contours.
 *  - [SkPaint.Join.kMiter_Join] with bevel fallback when the miter length
 *    exceeds `miterLimit × halfWidth`.
 *
 * Other cap and join modes degrade silently to butt/miter; round and
 * square caps + round/bevel-only joins are deferred.
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
 *    points on both sides via the [emitJoin] helper, which decides per
 *    vertex between miter (single intersection) and bevel (two flat ends
 *    of neighbouring segments). The miter formula is:
 *
 *    ```
 *    M − P = halfW / (1 + n_prev · n_next) · (n_prev + n_next)
 *    ```
 *
 *    Bevel fires when `|M − P| > miterLimit × halfW`.
 * 4. **Outline assembly**:
 *    - **Closed** contour → emit two closed sub-contours: outer side as-is
 *      (CW), inner side reversed (CCW). The winding fill rule then paints
 *      the band between them.
 *    - **Open** contour → emit a single closed contour wrapping
 *      `left + cap_end + reverse(right) + cap_start`.
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
) {
    private val halfW: Float = width * 0.5f

    public fun stroke(src: SkPath): SkPath {
        if (src.isEmpty() || width <= 0f) {
            return SkPathBuilder().setFillType(SkPathFillType.kWinding).detach()
        }
        val out = SkPathBuilder().setFillType(SkPathFillType.kWinding)
        for (contour in flattenContours(src)) {
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
                emitJoin(left,  px, py, halfW, miterLimit,
                    nx[prev],  ny[prev],  nx[cur],  ny[cur])
                emitJoin(right, px, py, halfW, miterLimit,
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
                emitJoin(left,  px, py, halfW, miterLimit,
                    nx[i - 1],  ny[i - 1],  nx[i],  ny[i])
                emitJoin(right, px, py, halfW, miterLimit,
                    -nx[i - 1], -ny[i - 1], -nx[i], -ny[i])
            }

            // Last vertex.
            val pxN = pts[(n - 1) * 2]; val pyN = pts[(n - 1) * 2 + 1]
            val nl = nSegs - 1
            left.add(pxN + halfW * nx[nl]);  left.add(pyN + halfW * ny[nl])
            right.add(pxN - halfW * nx[nl]); right.add(pyN - halfW * ny[nl])

            emitOpenStrokeOutline(out, left, right)
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

    /** Emit `left + cap_end + reverse(right) + close` as a single closed contour. */
    private fun emitOpenStrokeOutline(
        out: SkPathBuilder, left: FloatArrayList, right: FloatArrayList,
    ) {
        val ln = left.size / 2
        val rn = right.size / 2
        if (ln < 2 || rn < 2) return
        // Forward along the left side.
        out.moveTo(left[0], left[1])
        for (i in 1 until ln) out.lineTo(left[i * 2], left[i * 2 + 1])
        // Butt cap at end: straight line connecting to the right side.
        out.lineTo(right[(rn - 1) * 2], right[(rn - 1) * 2 + 1])
        // Reverse along the right side.
        for (i in rn - 2 downTo 0) out.lineTo(right[i * 2], right[i * 2 + 1])
        // close() implicitly emits the butt cap at the start (line back to left[0]).
        out.close()
    }

    public companion object {
        public fun fromPaint(paint: SkPaint): SkStroker = SkStroker(
            width = if (paint.strokeWidth <= 0f) 1f else paint.strokeWidth,
            cap = paint.strokeCap,
            join = paint.strokeJoin,
            miterLimit = paint.strokeMiter,
        )

        // Flattening tolerance lives in source space here — the rasterizer
        // re-flattens to 0.25 px in device space anyway (so the stroker's
        // outline is filled accurately even when the CTM scales by ≠ 1×).
        // 0.25 source-space units is a tight floor that still keeps
        // recursion shallow.
        internal const val FLATNESS: Float = 0.25f
        internal const val FLATNESS_SQ: Float = FLATNESS * FLATNESS
        internal const val MAX_DEPTH: Int = 18
        internal const val CONIC_STEPS: Int = 32
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
 * Béziers are flattened in source space using the same recursive De
 * Casteljau approach as `SkBitmapDevice.buildEdges` (kept in lockstep so
 * stroker output and direct fill share visual fidelity).
 */
internal fun flattenContours(path: SkPath): List<Polyline> {
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
                flattenQuad(current, px, py, x1, y1, x2, y2, 0)
                px = x2; py = y2
            }
            SkPath.Verb.kConic -> {
                val x1 = coords[coordIdx++]; val y1 = coords[coordIdx++]
                val x2 = coords[coordIdx++]; val y2 = coords[coordIdx++]
                val w = weights[weightIdx++]
                flattenConic(current, px, py, x1, y1, x2, y2, w)
                px = x2; py = y2
            }
            SkPath.Verb.kCubic -> {
                val x1 = coords[coordIdx++]; val y1 = coords[coordIdx++]
                val x2 = coords[coordIdx++]; val y2 = coords[coordIdx++]
                val x3 = coords[coordIdx++]; val y3 = coords[coordIdx++]
                flattenCubic(current, px, py, x1, y1, x2, y2, x3, y3, 0)
                px = x3; py = y3
            }
            SkPath.Verb.kClose -> {
                if (hasContour) finalize(closed = true)
            }
        }
    }
    if (hasContour) finalize(closed = false)
    return out
}

private fun flattenQuad(
    out: ArrayList<Float>,
    x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    depth: Int,
) {
    if (depth >= SkStroker.MAX_DEPTH || isQuadFlat(x0, y0, x1, y1, x2, y2)) {
        out.add(x2); out.add(y2); return
    }
    val m01x = (x0 + x1) * 0.5f; val m01y = (y0 + y1) * 0.5f
    val m12x = (x1 + x2) * 0.5f; val m12y = (y1 + y2) * 0.5f
    val mx = (m01x + m12x) * 0.5f; val my = (m01y + m12y) * 0.5f
    flattenQuad(out, x0, y0, m01x, m01y, mx, my, depth + 1)
    flattenQuad(out, mx, my, m12x, m12y, x2, y2, depth + 1)
}

private fun isQuadFlat(
    x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
): Boolean {
    val dx = x2 - x0; val dy = y2 - y0
    val chord2 = dx * dx + dy * dy
    if (chord2 < 1e-12f) return true
    val cross = (x1 - x0) * dy - (y1 - y0) * dx
    return (cross * cross) <= SkStroker.FLATNESS_SQ * chord2
}

private fun flattenCubic(
    out: ArrayList<Float>,
    x0: Float, y0: Float, x1: Float, y1: Float,
    x2: Float, y2: Float, x3: Float, y3: Float,
    depth: Int,
) {
    if (depth >= SkStroker.MAX_DEPTH || isCubicFlat(x0, y0, x1, y1, x2, y2, x3, y3)) {
        out.add(x3); out.add(y3); return
    }
    val m01x = (x0 + x1) * 0.5f; val m01y = (y0 + y1) * 0.5f
    val m12x = (x1 + x2) * 0.5f; val m12y = (y1 + y2) * 0.5f
    val m23x = (x2 + x3) * 0.5f; val m23y = (y2 + y3) * 0.5f
    val m012x = (m01x + m12x) * 0.5f; val m012y = (m01y + m12y) * 0.5f
    val m123x = (m12x + m23x) * 0.5f; val m123y = (m12y + m23y) * 0.5f
    val mx = (m012x + m123x) * 0.5f; val my = (m012y + m123y) * 0.5f
    flattenCubic(out, x0, y0, m01x, m01y, m012x, m012y, mx, my, depth + 1)
    flattenCubic(out, mx, my, m123x, m123y, m23x, m23y, x3, y3, depth + 1)
}

private fun isCubicFlat(
    x0: Float, y0: Float, x1: Float, y1: Float,
    x2: Float, y2: Float, x3: Float, y3: Float,
): Boolean {
    val dx = x3 - x0; val dy = y3 - y0
    val chord2 = dx * dx + dy * dy
    if (chord2 < 1e-12f) return true
    val c1 = (x1 - x0) * dy - (y1 - y0) * dx
    val c2 = (x2 - x0) * dy - (y2 - y0) * dx
    val maxCross2 = maxOf(c1 * c1, c2 * c2)
    return maxCross2 <= SkStroker.FLATNESS_SQ * chord2
}

private fun flattenConic(
    out: ArrayList<Float>,
    x0: Float, y0: Float, x1: Float, y1: Float,
    x2: Float, y2: Float, w: Float,
) {
    val n = SkStroker.CONIC_STEPS
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
 * Append a join vertex (or two, for bevel) to [poly] for the corner at
 * `(px, py)` between two segments with normals `n_prev` and `n_next`.
 *
 * The miter intersection lies at `M = P + halfW / (1 + cos) · (n_prev + n_next)`,
 * where `cos = n_prev · n_next`. Bevel fires when `|M − P| > miterLimit · halfW`.
 *
 * Antiparallel normals (180° turn) skip the miter formula and emit two
 * distinct flat-end points (degenerate corner).
 */
private fun emitJoin(
    poly: FloatArrayList,
    px: Float, py: Float, halfW: Float, miterLimit: Float,
    nPrevX: Float, nPrevY: Float, nNextX: Float, nNextY: Float,
) {
    val cos = nPrevX * nNextX + nPrevY * nNextY
    if (cos > 0.9999f) {
        // Nearly collinear — single point.
        poly.add(px + halfW * nPrevX); poly.add(py + halfW * nPrevY)
        return
    }
    val denom = 1f + cos
    if (denom < 1e-6f) {
        poly.add(px + halfW * nPrevX); poly.add(py + halfW * nPrevY)
        poly.add(px + halfW * nNextX); poly.add(py + halfW * nNextY)
        return
    }
    val mFactor = halfW / denom
    val mDx = mFactor * (nPrevX + nNextX)
    val mDy = mFactor * (nPrevY + nNextY)
    val mDistSq = mDx * mDx + mDy * mDy
    val limit = miterLimit * halfW
    if (mDistSq > limit * limit) {
        poly.add(px + halfW * nPrevX); poly.add(py + halfW * nPrevY)
        poly.add(px + halfW * nNextX); poly.add(py + halfW * nNextY)
    } else {
        poly.add(px + mDx); poly.add(py + mDy)
    }
}

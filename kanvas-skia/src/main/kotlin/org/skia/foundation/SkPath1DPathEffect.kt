package org.skia.foundation

import org.graphiks.math.SkMatrix
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Mirrors Skia's
 * [`SkPath1DPathEffect`](https://github.com/google/skia/blob/main/include/effects/Sk1DPathEffect.h)
 * — tile a custom *stamp* path along the input path at every
 * [advance] units of arc length.
 *
 * Construct via [Make]. Each stamp is positioned (and optionally
 * rotated or morphed) so its origin lies on the input path at the
 * cumulative arc length `phase + i * advance`.
 *
 * **Style** — controls how each stamp is oriented :
 *  - [Style.kTranslate] : stamp is translated to the path point ; no
 *    rotation.
 *  - [Style.kRotate] : stamp is translated **and** rotated so its
 *    `+x` axis aligns with the path tangent at the stamp position.
 *  - [Style.kMorph] : stamp is bent to follow the local curvature.
 *    Each control point of the stamp at local coords `(sx, sy)` is
 *    mapped to `pos(d + sx) + sy · normal(d + sx)`, where `pos` and
 *    `normal` come from the input path's chord-polyline parametrisation
 *    at distance `d + sx`. `kLine` verbs are upgraded to degenerate
 *    quads (control = midpoint) before morphing so straight stamp
 *    segments bend naturally on curved input paths — matches Skia's
 *    `morphpath` exactly.
 *
 * Curve verbs (`kQuad` / `kConic` / `kCubic`) on the **input** path are
 * flattened to chord polylines via the same midpoint-subdivision used
 * by [SkDashPathEffect] / [SkDiscretePathEffect] before arc-length
 * walking. The stamp itself is preserved verbatim — only its
 * position and rotation (or per-point morph displacement) change
 * per-instance.
 */
public class SkPath1DPathEffect private constructor(
    private val stamp: SkPath,
    private val advance: Float,
    private val phase: Float,
    private val style: Style,
) : SkPathEffect() {

    /** Mirrors Skia's `SkPath1DPathEffect::Style`. */
    public enum class Style {
        /** Translate-only : stamp orientation unchanged. */
        kTranslate,
        /** Translate + tangent-aligned rotation. */
        kRotate,
        /** Translate + per-point bend along the input path's normal. */
        kMorph,
    }

    /**
     * Pre-normalised initial offset (in arc-length units) at which
     * the first stamp is placed inside each contour. Mirrors upstream's
     * `fInitialOffset` calculation : positive [phase] is interpreted
     * as a forward delay (so the first stamp lands at
     * `advance - phase`), and the value is wrapped into `[0, advance)`.
     */
    private val initialOffset: Float = run {
        var p = phase
        if (p < 0f) {
            p = -p
            if (p > advance) p %= advance
        } else {
            if (p > advance) p %= advance
            p = advance - p
        }
        if (p >= advance) 0f else p
    }

    override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? {
        if (input.isEmpty() || stamp.isEmpty()) return null
        val out = SkPathBuilder()
        for (contour in buildContours(input)) {
            stampContour(out, contour)
        }
        return out.detach()
    }

    /**
     * Walk one contour's chord polyline, placing stamps at
     * `initialOffset, initialOffset + advance, …` until the
     * cumulative arc length is exhausted.
     */
    private fun stampContour(out: SkPathBuilder, contour: ContourMeasure) {
        if (contour.length <= 0f) return
        var d = initialOffset
        // Hard cap on iterations to defend against pathological tiny
        // [advance] values relative to the contour length (mirrors
        // upstream's `MAX_REASONABLE_ITERATIONS = 100000`).
        var governor = MAX_ITERATIONS
        while (d < contour.length + EPS && governor-- > 0) {
            stampOnce(out, contour, d)
            d += advance
        }
    }

    /** Place a single stamp on the contour at arc distance [d]. */
    private fun stampOnce(out: SkPathBuilder, contour: ContourMeasure, d: Float) {
        when (style) {
            Style.kTranslate -> {
                val pt = contour.getPosTan(d)
                val transform = SkMatrix.MakeTrans(pt[0], pt[1])
                out.addPath(stamp.makeTransform(transform))
            }
            Style.kRotate -> {
                val pt = contour.getPosTan(d)
                val angleRad = atan2(pt[3].toDouble(), pt[2].toDouble()).toFloat()
                val angleDeg = angleRad * RAD_TO_DEG
                val transform = SkMatrix.MakeTrans(pt[0], pt[1]).preRotate(angleDeg)
                out.addPath(stamp.makeTransform(transform))
            }
            Style.kMorph -> morphStamp(out, contour, d)
        }
    }

    /**
     * Walk the stamp's verbs and emit a morphed copy. Each control
     * point at local coords `(sx, sy)` is mapped to
     * `pos(d + sx) + sy · normal(d + sx)` where the position and
     * tangent are read from [contour] via [ContourMeasure.getPosTan].
     *
     * **kLine upgrade** : straight stamp segments are replaced by a
     * degenerate quad (control point = midpoint) so they bend with
     * the input path's curvature. Mirrors Skia's `morphpath`
     * lines→quads upgrade.
     */
    private fun morphStamp(out: SkPathBuilder, contour: ContourMeasure, d: Float) {
        var coordIdx = 0
        var weightIdx = 0
        var penX = 0f
        var penY = 0f
        // Track the morphed pen — Skia emits the morph relative to it
        // for kLine→quad fall-through, but we don't need that as we
        // recompute every point from the source.
        for (verb in stamp.verbs) {
            when (verb) {
                SkPath.Verb.kMove -> {
                    val sx = stamp.coords[coordIdx++]
                    val sy = stamp.coords[coordIdx++]
                    val m = morphPoint(contour, d, sx, sy)
                    out.moveTo(m[0], m[1])
                    penX = sx; penY = sy
                }
                SkPath.Verb.kLine -> {
                    // Upgrade to degenerate quad : control = midpoint.
                    val ex = stamp.coords[coordIdx++]
                    val ey = stamp.coords[coordIdx++]
                    val midX = (penX + ex) * 0.5f
                    val midY = (penY + ey) * 0.5f
                    val mc = morphPoint(contour, d, midX, midY)
                    val me = morphPoint(contour, d, ex, ey)
                    out.quadTo(mc[0], mc[1], me[0], me[1])
                    penX = ex; penY = ey
                }
                SkPath.Verb.kQuad -> {
                    val cx = stamp.coords[coordIdx++]
                    val cy = stamp.coords[coordIdx++]
                    val ex = stamp.coords[coordIdx++]
                    val ey = stamp.coords[coordIdx++]
                    val mc = morphPoint(contour, d, cx, cy)
                    val me = morphPoint(contour, d, ex, ey)
                    out.quadTo(mc[0], mc[1], me[0], me[1])
                    penX = ex; penY = ey
                }
                SkPath.Verb.kConic -> {
                    val cx = stamp.coords[coordIdx++]
                    val cy = stamp.coords[coordIdx++]
                    val ex = stamp.coords[coordIdx++]
                    val ey = stamp.coords[coordIdx++]
                    val w = stamp.conicWeights[weightIdx++]
                    val mc = morphPoint(contour, d, cx, cy)
                    val me = morphPoint(contour, d, ex, ey)
                    out.conicTo(mc[0], mc[1], me[0], me[1], w)
                    penX = ex; penY = ey
                }
                SkPath.Verb.kCubic -> {
                    val c1x = stamp.coords[coordIdx++]
                    val c1y = stamp.coords[coordIdx++]
                    val c2x = stamp.coords[coordIdx++]
                    val c2y = stamp.coords[coordIdx++]
                    val ex = stamp.coords[coordIdx++]
                    val ey = stamp.coords[coordIdx++]
                    val m1 = morphPoint(contour, d, c1x, c1y)
                    val m2 = morphPoint(contour, d, c2x, c2y)
                    val me = morphPoint(contour, d, ex, ey)
                    out.cubicTo(m1[0], m1[1], m2[0], m2[1], me[0], me[1])
                    penX = ex; penY = ey
                }
                SkPath.Verb.kClose -> {
                    out.close()
                }
                SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
            }
        }
    }

    /**
     * Map a stamp-local coordinate `(sx, sy)` onto the contour as
     * `pos(d + sx) + sy · normal(d + sx)`. The normal is the
     * tangent rotated +90°  — `(-tan.y, tan.x)` — chosen so positive
     * `sy` lies "above" the path in stamp-local coords (matches
     * upstream's `morphpoints` matrix-construction convention).
     */
    private fun morphPoint(
        contour: ContourMeasure,
        d: Float,
        sx: Float,
        sy: Float,
    ): FloatArray {
        val pt = contour.getPosTan(d + sx)
        val px = pt[0]; val py = pt[1]
        val tx = pt[2]; val ty = pt[3]
        // Normal = tangent rotated +90° : (-ty, tx).
        val mx = px - ty * sy
        val my = py + tx * sy
        return floatArrayOf(mx, my)
    }

    // ─── Per-contour chord-polyline measure ────────────────────────────

    /**
     * One contour of the input path, flattened to a chord polyline
     * with cumulative arc-length per vertex. Cheap port of
     * `SkPathMeasure` — supports just enough to answer
     * [getPosTan] queries at arbitrary distances along the contour.
     */
    private class ContourMeasure(
        val xs: FloatArray,
        val ys: FloatArray,
        val cum: FloatArray,
    ) {
        val length: Float get() = if (cum.isEmpty()) 0f else cum.last()

        /**
         * Return `(posX, posY, tangentX, tangentY)` at arc distance
         * [d], clamped to `[0, length]`. The tangent is the unit
         * vector along the chord that contains [d] ; it falls back
         * to `(1, 0)` for degenerate (zero-length) chords so morph
         * outputs remain finite.
         */
        fun getPosTan(d: Float): FloatArray {
            if (xs.size <= 1) return floatArrayOf(
                if (xs.isEmpty()) 0f else xs[0],
                if (ys.isEmpty()) 0f else ys[0],
                1f, 0f,
            )
            val clamped = d.coerceIn(0f, length)
            // Binary search for the segment whose [cum[lo], cum[hi]]
            // brackets [clamped].
            var lo = 0
            var hi = cum.size - 1
            while (lo + 1 < hi) {
                val mid = (lo + hi) ushr 1
                if (cum[mid] <= clamped) lo = mid else hi = mid
            }
            val segLen = cum[hi] - cum[lo]
            val t = if (segLen > 0f) (clamped - cum[lo]) / segLen else 0f
            val px = xs[lo] + t * (xs[hi] - xs[lo])
            val py = ys[lo] + t * (ys[hi] - ys[lo])
            val dx = xs[hi] - xs[lo]
            val dy = ys[hi] - ys[lo]
            val len = sqrt(dx * dx + dy * dy)
            val ux = if (len > 0f) dx / len else 1f
            val uy = if (len > 0f) dy / len else 0f
            return floatArrayOf(px, py, ux, uy)
        }
    }

    /**
     * Flatten [input] into one [ContourMeasure] per `kMove`-rooted
     * contour. Curve verbs are subdivided via midpoint until the
     * chord error is below [CHORD_TOL] ; degenerate (single-point)
     * contours are emitted with `length = 0` and skipped at stamp
     * time.
     */
    private fun buildContours(input: SkPath): List<ContourMeasure> {
        val out = ArrayList<ContourMeasure>()
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val cum = ArrayList<Float>()
        var arcLen = 0f
        var penX = 0f; var penY = 0f
        var startX = 0f; var startY = 0f
        var coordIdx = 0
        // Conic weights aren't needed for arc-length walking — the
        // chord polyline already encodes the curve approximation —
        // but we increment the cursor in lockstep to stay in sync.
        var weightIdx = 0

        fun pushPoint(x: Float, y: Float) {
            if (xs.isNotEmpty()) {
                val dx = x - xs.last()
                val dy = y - ys.last()
                arcLen += sqrt(dx * dx + dy * dy)
            }
            xs.add(x); ys.add(y); cum.add(arcLen)
        }

        fun finishContour() {
            if (xs.isNotEmpty()) {
                out.add(ContourMeasure(xs.toFloatArray(), ys.toFloatArray(), cum.toFloatArray()))
            }
            xs.clear(); ys.clear(); cum.clear()
            arcLen = 0f
        }

        for (verb in input.verbs) {
            when (verb) {
                SkPath.Verb.kMove -> {
                    finishContour()
                    penX = input.coords[coordIdx++]
                    penY = input.coords[coordIdx++]
                    startX = penX; startY = penY
                    pushPoint(penX, penY)
                }
                SkPath.Verb.kLine -> {
                    val nx = input.coords[coordIdx++]
                    val ny = input.coords[coordIdx++]
                    pushPoint(nx, ny)
                    penX = nx; penY = ny
                }
                SkPath.Verb.kQuad -> {
                    val cx = input.coords[coordIdx++]
                    val cy = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]
                    val ey = input.coords[coordIdx++]
                    flattenQuad(penX, penY, cx, cy, ex, ey) { x, y -> pushPoint(x, y) }
                    penX = ex; penY = ey
                }
                SkPath.Verb.kConic -> {
                    val cx = input.coords[coordIdx++]
                    val cy = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]
                    val ey = input.coords[coordIdx++]
                    weightIdx++
                    // Treat conics as quads for arc-length walking ;
                    // the weight only adjusts shape, not contour order.
                    flattenQuad(penX, penY, cx, cy, ex, ey) { x, y -> pushPoint(x, y) }
                    penX = ex; penY = ey
                }
                SkPath.Verb.kCubic -> {
                    val c1x = input.coords[coordIdx++]
                    val c1y = input.coords[coordIdx++]
                    val c2x = input.coords[coordIdx++]
                    val c2y = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]
                    val ey = input.coords[coordIdx++]
                    flattenCubic(penX, penY, c1x, c1y, c2x, c2y, ex, ey) { x, y -> pushPoint(x, y) }
                    penX = ex; penY = ey
                }
                SkPath.Verb.kClose -> {
                    pushPoint(startX, startY)
                    penX = startX; penY = startY
                }
                SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
            }
        }
        finishContour()
        return out
    }

    public companion object {
        /**
         * Mirrors Skia's `SkPath1DPathEffect::Make(stamp, advance,
         * phase, style)`. Returns `null` for non-positive [advance]
         * (no-op).
         */
        public fun Make(
            stamp: SkPath,
            advance: Float,
            phase: Float,
            style: Style,
        ): SkPathEffect? {
            if (!advance.isFinite() || advance <= 0f) return null
            return SkPath1DPathEffect(stamp, advance, phase, style)
        }

        private const val EPS = 1e-4f
        private const val CHORD_TOL = 0.5f
        private const val MAX_LEVELS = 16
        private const val MAX_ITERATIONS = 100000
        private const val RAD_TO_DEG = (180.0 / kotlin.math.PI).toFloat()

        /** Recursively subdivide a quad until each chord is within tolerance. */
        private fun flattenQuad(
            x0: Float, y0: Float, cx: Float, cy: Float, x2: Float, y2: Float,
            emit: (Float, Float) -> Unit,
        ) {
            subdivQuad(x0, y0, cx, cy, x2, y2, 0, emit)
        }

        private fun subdivQuad(
            x0: Float, y0: Float, cx: Float, cy: Float, x2: Float, y2: Float,
            level: Int, emit: (Float, Float) -> Unit,
        ) {
            val mx = (x0 + x2) * 0.5f; val my = (y0 + y2) * 0.5f
            val ex = cx - mx; val ey = cy - my
            val err2 = ex * ex + ey * ey
            if (err2 < CHORD_TOL * CHORD_TOL || level >= MAX_LEVELS) {
                emit(x2, y2); return
            }
            val ax = (x0 + cx) * 0.5f; val ay = (y0 + cy) * 0.5f
            val bx = (cx + x2) * 0.5f; val by = (cy + y2) * 0.5f
            val midX = (ax + bx) * 0.5f; val midY = (ay + by) * 0.5f
            subdivQuad(x0, y0, ax, ay, midX, midY, level + 1, emit)
            subdivQuad(midX, midY, bx, by, x2, y2, level + 1, emit)
        }

        private fun flattenCubic(
            x0: Float, y0: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float,
            x3: Float, y3: Float,
            emit: (Float, Float) -> Unit,
        ) {
            subdivCubic(x0, y0, c1x, c1y, c2x, c2y, x3, y3, 0, emit)
        }

        private fun subdivCubic(
            x0: Float, y0: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float,
            x3: Float, y3: Float,
            level: Int, emit: (Float, Float) -> Unit,
        ) {
            val mx = (x0 + x3) * 0.5f; val my = (y0 + y3) * 0.5f
            val e1x = c1x - mx; val e1y = c1y - my
            val e2x = c2x - mx; val e2y = c2y - my
            val err2 = maxOf(e1x * e1x + e1y * e1y, e2x * e2x + e2y * e2y)
            if (err2 < CHORD_TOL * CHORD_TOL || level >= MAX_LEVELS) {
                emit(x3, y3); return
            }
            val ax = (x0 + c1x) * 0.5f; val ay = (y0 + c1y) * 0.5f
            val bx = (c1x + c2x) * 0.5f; val by = (c1y + c2y) * 0.5f
            val cx2 = (c2x + x3) * 0.5f; val cy2 = (c2y + y3) * 0.5f
            val dx2 = (ax + bx) * 0.5f; val dy2 = (ay + by) * 0.5f
            val ex2 = (bx + cx2) * 0.5f; val ey2 = (by + cy2) * 0.5f
            val midX = (dx2 + ex2) * 0.5f; val midY = (dy2 + ey2) * 0.5f
            subdivCubic(x0, y0, ax, ay, dx2, dy2, midX, midY, level + 1, emit)
            subdivCubic(midX, midY, ex2, ey2, cx2, cy2, x3, y3, level + 1, emit)
        }
    }
}

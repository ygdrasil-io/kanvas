package org.skia.foundation

import org.skia.math.SkMatrix
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Mirrors Skia's
 * [`SkPath1DPathEffect`](https://github.com/google/skia/blob/main/include/effects/Sk1DPathEffect.h)
 * — tile a custom *stamp* path along the input path at every
 * [advance] units of arc length.
 *
 * Construct via [Make]. Each stamp is positioned (and optionally
 * rotated) so its origin lies on the input path at the cumulative
 * arc length `phase + i * advance`.
 *
 * **Style** — controls how each stamp is oriented :
 *  - [Style.kTranslate] : stamp is translated to the path point ; no
 *    rotation.
 *  - [Style.kRotate] : stamp is translated **and** rotated so its
 *    `+x` axis aligns with the path tangent at the stamp position.
 *  - [Style.kMorph] : stamp is bent to follow the local curvature.
 *    **Not implemented in Phase 7p_t** — falls back to [Style.kRotate]
 *    (visually-close for low-curvature inputs ; significant drift on
 *    sharp turns). A follow-up will port Skia's morph distortion.
 *
 * Curve verbs (`kQuad` / `kConic` / `kCubic`) on the input path are
 * flattened to chord polylines via the same midpoint-subdivision used
 * by [SkDashPathEffect] / [SkDiscretePathEffect] before arc-length
 * walking. The stamp itself is preserved verbatim — only its
 * position and rotation change per-instance.
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
        /** Translate + bend (deferred to follow-up — falls back to [kRotate]). */
        kMorph,
    }

    private val normalisedPhase: Float = run {
        var p = phase % advance
        if (p < 0f) p += advance
        p
    }

    override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? {
        if (input.isEmpty() || stamp.isEmpty()) return null
        val out = SkPathBuilder()

        // For each contour, walk segments tracking cumulative arc
        // length. At every `phase + k * advance` distance, stamp the
        // stamp path positioned/rotated per [style].
        var coordIdx = 0
        var contourStartX = 0f; var contourStartY = 0f
        var penX = 0f; var penY = 0f
        var arcLen = 0f

        for (verb in input.verbs) {
            when (verb) {
                SkPath.Verb.kMove -> {
                    penX = input.coords[coordIdx++]
                    penY = input.coords[coordIdx++]
                    contourStartX = penX
                    contourStartY = penY
                    arcLen = 0f
                }
                SkPath.Verb.kLine -> {
                    val nx = input.coords[coordIdx++]
                    val ny = input.coords[coordIdx++]
                    arcLen = stampSegment(out, penX, penY, nx, ny, arcLen)
                    penX = nx; penY = ny
                }
                SkPath.Verb.kQuad -> {
                    val cx = input.coords[coordIdx++]
                    val cy = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]
                    val ey = input.coords[coordIdx++]
                    arcLen = stampQuad(out, penX, penY, cx, cy, ex, ey, arcLen)
                    penX = ex; penY = ey
                }
                SkPath.Verb.kConic -> {
                    val cx = input.coords[coordIdx++]
                    val cy = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]
                    val ey = input.coords[coordIdx++]
                    arcLen = stampQuad(out, penX, penY, cx, cy, ex, ey, arcLen)
                    penX = ex; penY = ey
                }
                SkPath.Verb.kCubic -> {
                    val c1x = input.coords[coordIdx++]
                    val c1y = input.coords[coordIdx++]
                    val c2x = input.coords[coordIdx++]
                    val c2y = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]
                    val ey = input.coords[coordIdx++]
                    arcLen = stampCubic(out, penX, penY, c1x, c1y, c2x, c2y, ex, ey, arcLen)
                    penX = ex; penY = ey
                }
                SkPath.Verb.kClose -> {
                    arcLen = stampSegment(out, penX, penY, contourStartX, contourStartY, arcLen)
                    penX = contourStartX; penY = contourStartY
                }
            }
        }
        return out.detach()
    }

    /**
     * Walk a single line segment, stamping the stamp path at every
     * `phase + k * advance` distance the segment crosses. Returns the
     * new cumulative arc length.
     */
    private fun stampSegment(
        out: SkPathBuilder,
        x0: Float, y0: Float, x1: Float, y1: Float,
        startDist: Float,
    ): Float {
        val dx = x1 - x0; val dy = y1 - y0
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0f) return startDist
        val ux = dx / length; val uy = dy / length

        // Find the first stamp position on this segment.
        // Cumulative distance modulo advance, normalised to [0, advance).
        val phaseAtStart = (startDist + normalisedPhase) % advance
        var firstOffset = if (phaseAtStart == 0f) 0f else advance - phaseAtStart
        // Walk all stamp positions within [firstOffset, length].
        var off = firstOffset
        while (off <= length + EPS) {
            val px = x0 + ux * off
            val py = y0 + uy * off
            stampAt(out, px, py, ux, uy)
            off += advance
        }
        return startDist + length
    }

    /** Stamp [stamp] at `(px, py)` with tangent direction `(ux, uy)`. */
    private fun stampAt(out: SkPathBuilder, px: Float, py: Float, ux: Float, uy: Float) {
        val transform: SkMatrix = when (style) {
            Style.kTranslate -> SkMatrix.MakeTrans(px, py)
            Style.kRotate, Style.kMorph -> {
                val angleRad = atan2(uy.toDouble(), ux.toDouble()).toFloat()
                val angleDeg = angleRad * 180f / kotlin.math.PI.toFloat()
                SkMatrix.MakeTrans(px, py).preRotate(angleDeg)
            }
        }
        val transformed = stamp.makeTransform(transform)
        out.addPath(transformed)
    }

    /** Flatten a quadratic and stamp each chord. */
    private fun stampQuad(
        out: SkPathBuilder,
        x0: Float, y0: Float, cx: Float, cy: Float, x2: Float, y2: Float,
        startDist: Float,
    ): Float {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        subdivQuad(x0, y0, cx, cy, x2, y2, 0, xs, ys)
        var d = startDist; var px = x0; var py = y0
        for (i in 0 until xs.size) {
            d = stampSegment(out, px, py, xs[i], ys[i], d)
            px = xs[i]; py = ys[i]
        }
        return d
    }

    /** Flatten a cubic and stamp each chord. */
    private fun stampCubic(
        out: SkPathBuilder,
        x0: Float, y0: Float,
        c1x: Float, c1y: Float, c2x: Float, c2y: Float,
        x3: Float, y3: Float,
        startDist: Float,
    ): Float {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        subdivCubic(x0, y0, c1x, c1y, c2x, c2y, x3, y3, 0, xs, ys)
        var d = startDist; var px = x0; var py = y0
        for (i in 0 until xs.size) {
            d = stampSegment(out, px, py, xs[i], ys[i], d)
            px = xs[i]; py = ys[i]
        }
        return d
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

        private fun subdivQuad(
            x0: Float, y0: Float, cx: Float, cy: Float, x2: Float, y2: Float,
            level: Int, xs: ArrayList<Float>, ys: ArrayList<Float>,
        ) {
            val mx = (x0 + x2) * 0.5f; val my = (y0 + y2) * 0.5f
            val ex = cx - mx; val ey = cy - my
            val err2 = ex * ex + ey * ey
            if (err2 < CHORD_TOL * CHORD_TOL || level >= MAX_LEVELS) {
                xs.add(x2); ys.add(y2); return
            }
            val ax = (x0 + cx) * 0.5f; val ay = (y0 + cy) * 0.5f
            val bx = (cx + x2) * 0.5f; val by = (cy + y2) * 0.5f
            val midX = (ax + bx) * 0.5f; val midY = (ay + by) * 0.5f
            subdivQuad(x0, y0, ax, ay, midX, midY, level + 1, xs, ys)
            subdivQuad(midX, midY, bx, by, x2, y2, level + 1, xs, ys)
        }

        private fun subdivCubic(
            x0: Float, y0: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float,
            x3: Float, y3: Float,
            level: Int, xs: ArrayList<Float>, ys: ArrayList<Float>,
        ) {
            val mx = (x0 + x3) * 0.5f; val my = (y0 + y3) * 0.5f
            val e1x = c1x - mx; val e1y = c1y - my
            val e2x = c2x - mx; val e2y = c2y - my
            val err2 = maxOf(e1x * e1x + e1y * e1y, e2x * e2x + e2y * e2y)
            if (err2 < CHORD_TOL * CHORD_TOL || level >= MAX_LEVELS) {
                xs.add(x3); ys.add(y3); return
            }
            val ax = (x0 + c1x) * 0.5f; val ay = (y0 + c1y) * 0.5f
            val bx = (c1x + c2x) * 0.5f; val by = (c1y + c2y) * 0.5f
            val cx2 = (c2x + x3) * 0.5f; val cy2 = (c2y + y3) * 0.5f
            val dx2 = (ax + bx) * 0.5f; val dy2 = (ay + by) * 0.5f
            val ex2 = (bx + cx2) * 0.5f; val ey2 = (by + cy2) * 0.5f
            val midX = (dx2 + ex2) * 0.5f; val midY = (dy2 + ey2) * 0.5f
            subdivCubic(x0, y0, ax, ay, dx2, dy2, midX, midY, level + 1, xs, ys)
            subdivCubic(midX, midY, ex2, ey2, cx2, cy2, x3, y3, level + 1, xs, ys)
        }
    }
}

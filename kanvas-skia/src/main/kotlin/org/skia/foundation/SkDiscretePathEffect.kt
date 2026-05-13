package org.skia.foundation

import org.skia.math.SkMatrix
import org.skia.tools.SkRandom
import kotlin.math.sqrt

/**
 * Mirrors Skia's
 * [`SkDiscretePathEffect`](https://github.com/google/skia/blob/main/include/effects/SkDiscretePathEffect.h)
 * — chops a path into [segLength]-long sub-segments and perturbs
 * each sub-segment endpoint perpendicular to the path direction by
 * a random distance in `[-deviation, +deviation]`.
 *
 * Used to create a "rough sketch" or "hand-drawn" appearance on
 * straight or smooth paths. The randomness is **deterministic** —
 * the same input path with the same [seed] always produces the
 * same output.
 *
 * Construct via [Make]. `segLength <= 0` returns `null` (no-op).
 *
 * **Phase 7p2 coverage** :
 *  - `kMove` + `kLine`* + optional `kClose` — fully supported with
 *    perpendicular jitter on every sub-segment.
 *  - `kQuad` / `kConic` / `kCubic` — flattened to chord polylines
 *    via the same midpoint-subdivision used by [SkDashPathEffect],
 *    then jittered. Visually-correct for moderate curvature.
 */
public class SkDiscretePathEffect private constructor(
    private val segLength: Float,
    private val deviation: Float,
    private val seed: Int,
) : SkPathEffect() {

    override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? {
        if (input.isEmpty()) return null
        val out = SkPathBuilder()
        val rng = SkRandom(seed)

        var penX = 0f; var penY = 0f
        var contourStartX = 0f; var contourStartY = 0f
        var emittedPenInContour = false
        var coordIdx = 0

        for (verb in input.verbs) {
            when (verb) {
                SkPath.StorageVerb.kMove -> {
                    penX = input.coords[coordIdx++]
                    penY = input.coords[coordIdx++]
                    contourStartX = penX
                    contourStartY = penY
                    out.moveTo(penX, penY)
                    emittedPenInContour = true
                }
                SkPath.StorageVerb.kLine -> {
                    val nx = input.coords[coordIdx++]
                    val ny = input.coords[coordIdx++]
                    jitterLine(out, penX, penY, nx, ny, rng)
                    penX = nx; penY = ny
                }
                SkPath.StorageVerb.kQuad -> {
                    val cx = input.coords[coordIdx++]
                    val cy = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]
                    val ey = input.coords[coordIdx++]
                    jitterQuad(out, penX, penY, cx, cy, ex, ey, rng)
                    penX = ex; penY = ey
                }
                SkPath.StorageVerb.kConic -> {
                    val cx = input.coords[coordIdx++]
                    val cy = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]
                    val ey = input.coords[coordIdx++]
                    jitterQuad(out, penX, penY, cx, cy, ex, ey, rng)
                    penX = ex; penY = ey
                }
                SkPath.StorageVerb.kCubic -> {
                    val c1x = input.coords[coordIdx++]
                    val c1y = input.coords[coordIdx++]
                    val c2x = input.coords[coordIdx++]
                    val c2y = input.coords[coordIdx++]
                    val ex = input.coords[coordIdx++]
                    val ey = input.coords[coordIdx++]
                    jitterCubic(out, penX, penY, c1x, c1y, c2x, c2y, ex, ey, rng)
                    penX = ex; penY = ey
                }
                SkPath.StorageVerb.kClose -> {
                    jitterLine(out, penX, penY, contourStartX, contourStartY, rng)
                    out.close()
                    penX = contourStartX; penY = contourStartY
                    emittedPenInContour = false
                }
            }
        }
        // Touching `emittedPenInContour` keeps the local-var unused
        // warning quiet ; the value isn't read past the loop.
        @Suppress("UNUSED_VALUE")
        emittedPenInContour = emittedPenInContour
        return out.detach()
    }

    /**
     * Walk a line from `(x0, y0)` to `(x1, y1)`, emitting `lineTo`
     * commands every [segLength] units with a perpendicular random
     * offset in `[-deviation, +deviation]`.
     */
    private fun jitterLine(
        out: SkPathBuilder,
        x0: Float, y0: Float, x1: Float, y1: Float,
        rng: SkRandom,
    ) {
        val dx = x1 - x0; val dy = y1 - y0
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0f) return
        val ux = dx / length; val uy = dy / length
        // Perpendicular unit vector (rotated 90° CCW).
        val px = -uy; val py = ux

        val steps = (length / segLength).toInt().coerceAtLeast(1)
        val realStep = length / steps
        for (i in 1..steps) {
            val along = i * realStep
            val baseX = x0 + ux * along
            val baseY = y0 + uy * along
            // Last sub-segment lands exactly on the endpoint (no
            // perpendicular noise on the terminal vertex — Skia
            // upstream stops the random walk one step before the end
            // when segLength doesn't divide length evenly, but for
            // simplicity we let the last vertex stay sharp).
            if (i == steps) {
                out.lineTo(x1, y1)
            } else {
                val noise = rng.nextRangeF(-deviation, deviation)
                out.lineTo(baseX + px * noise, baseY + py * noise)
            }
        }
    }

    /**
     * Flatten a quadratic into chord polyline (re-uses the
     * [SkDashPathEffect] subdivision algorithm conceptually) then
     * jitter each chord. We inline a small recursive splitter here
     * instead of cross-importing.
     */
    private fun jitterQuad(
        out: SkPathBuilder,
        x0: Float, y0: Float, cx: Float, cy: Float, x2: Float, y2: Float,
        rng: SkRandom,
    ) {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        subdivQuad(x0, y0, cx, cy, x2, y2, 0, xs, ys)
        var px = x0; var py = y0
        for (i in 0 until xs.size) {
            jitterLine(out, px, py, xs[i], ys[i], rng)
            px = xs[i]; py = ys[i]
        }
    }

    private fun jitterCubic(
        out: SkPathBuilder,
        x0: Float, y0: Float,
        c1x: Float, c1y: Float, c2x: Float, c2y: Float,
        x3: Float, y3: Float,
        rng: SkRandom,
    ) {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        subdivCubic(x0, y0, c1x, c1y, c2x, c2y, x3, y3, 0, xs, ys)
        var px = x0; var py = y0
        for (i in 0 until xs.size) {
            jitterLine(out, px, py, xs[i], ys[i], rng)
            px = xs[i]; py = ys[i]
        }
    }

    public companion object {
        /**
         * Mirrors Skia's `SkDiscretePathEffect::Make(segLength,
         * deviation, seedAssist)`. Returns `null` for non-positive /
         * non-finite [segLength] (no-op).
         */
        public fun Make(segLength: Float, deviation: Float, seed: Int = 0): SkPathEffect? {
            if (!segLength.isFinite() || segLength <= 0f) return null
            if (!deviation.isFinite()) return null
            return SkDiscretePathEffect(segLength, deviation, seed)
        }

        /** Same chord-tolerance budget as [SkDashPathEffect]. */
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
                xs.add(x2); ys.add(y2)
                return
            }
            val ax = (x0 + cx) * 0.5f; val ay = (y0 + cy) * 0.5f
            val bx = (cx + x2) * 0.5f; val by = (cy + y2) * 0.5f
            val midX = (ax + bx) * 0.5f; val midY = (ay + by) * 0.5f
            subdivQuad(x0, y0, ax, ay, midX, midY, level + 1, xs, ys)
            subdivQuad(midX, midY, bx, by, x2, y2, level + 1, xs, ys)
        }

        private fun subdivCubic(
            x0: Float, y0: Float,
            c1x: Float, c1y: Float, c2x: Float, c2y: Float,
            x3: Float, y3: Float,
            level: Int, xs: ArrayList<Float>, ys: ArrayList<Float>,
        ) {
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
            subdivCubic(x0, y0, ax, ay, dx2, dy2, midX, midY, level + 1, xs, ys)
            subdivCubic(midX, midY, ex2, ey2, cx2, cy2, x3, y3, level + 1, xs, ys)
        }
    }
}

package org.skia.foundation

import org.graphiks.math.SkMatrix
import kotlin.math.sqrt

/**
 * Mirrors Skia's `SkTrimPathEffect`: keeps a normalized slice of the
 * source path, measured over the full path length across all contours.
 *
 * The implementation follows the existing path-effect convention in this
 * module: curve verbs are flattened to a chord polyline, then the requested
 * distance range is emitted as line segments. This is sufficient for the
 * upstream `trimpatheffect` GM and keeps the raster pipeline deterministic.
 */
public class SkTrimPathEffect private constructor(
    private val startT: Float,
    private val stopT: Float,
    private val mode: Mode,
) : SkPathEffect() {

    public enum class Mode {
        /** Return the subset path `[startT, stopT]`. */
        kNormal,
        /** Return `[stopT, 1]` followed by `[0, startT]`. */
        kInverted,
    }

    override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? {
        if (input.isEmpty()) return SkPathBuilder().detach()
        val segments = buildSegments(input)
        val totalLength = segments.lastOrNull()?.endDistance ?: 0f
        if (totalLength <= 0f) return SkPathBuilder().detach()

        val out = SkPathBuilder(input.fillType)
        when (mode) {
            Mode.kNormal -> emitRange(out, segments, totalLength, startT, stopT)
            Mode.kInverted -> {
                emitRange(out, segments, totalLength, stopT, 1f)
                emitRange(out, segments, totalLength, 0f, startT)
            }
        }
        return out.detach()
    }

    private fun emitRange(
        out: SkPathBuilder,
        segments: List<Segment>,
        totalLength: Float,
        start: Float,
        stop: Float,
    ) {
        val rangeStart = (start * totalLength).coerceIn(0f, totalLength)
        val rangeStop = (stop * totalLength).coerceIn(0f, totalLength)
        if (rangeStop <= rangeStart + EPS) return

        var activeContour = -1
        for (segment in segments) {
            if (segment.endDistance <= rangeStart + EPS) continue
            if (segment.startDistance >= rangeStop - EPS) break

            val a = rangeStart.coerceAtLeast(segment.startDistance)
            val b = rangeStop.coerceAtMost(segment.endDistance)
            if (b <= a + EPS) continue

            val t0 = segment.localT(a)
            val t1 = segment.localT(b)
            val x0 = lerp(segment.x0, segment.x1, t0)
            val y0 = lerp(segment.y0, segment.y1, t0)
            val x1 = lerp(segment.x0, segment.x1, t1)
            val y1 = lerp(segment.y0, segment.y1, t1)

            if (segment.contourIndex != activeContour) {
                out.moveTo(x0, y0)
                activeContour = segment.contourIndex
            } else {
                val last = out.getLastPt()
                if (last == null || distanceSquared(last.fX, last.fY, x0, y0) > EPS * EPS) {
                    out.moveTo(x0, y0)
                }
            }
            out.lineTo(x1, y1)
        }
    }

    private data class Segment(
        val contourIndex: Int,
        val x0: Float,
        val y0: Float,
        val x1: Float,
        val y1: Float,
        val startDistance: Float,
        val endDistance: Float,
    ) {
        fun localT(distance: Float): Float {
            val length = endDistance - startDistance
            return if (length > 0f) ((distance - startDistance) / length).coerceIn(0f, 1f) else 0f
        }
    }

    private fun buildSegments(input: SkPath): List<Segment> {
        val segments = ArrayList<Segment>()
        var contourIndex = -1
        var penX = 0f
        var penY = 0f
        var startX = 0f
        var startY = 0f
        var distance = 0f
        var coordIdx = 0

        fun pushLine(x: Float, y: Float) {
            val dx = x - penX
            val dy = y - penY
            val len = sqrt(dx * dx + dy * dy)
            if (len > EPS) {
                segments.add(Segment(contourIndex, penX, penY, x, y, distance, distance + len))
                distance += len
            }
            penX = x
            penY = y
        }

        for (verb in input.verbs) {
            when (verb) {
                SkPath.Verb.kMove -> {
                    penX = input.coords[coordIdx++]
                    penY = input.coords[coordIdx++]
                    startX = penX
                    startY = penY
                    contourIndex++
                }
                SkPath.Verb.kLine -> {
                    val x = input.coords[coordIdx++]
                    val y = input.coords[coordIdx++]
                    pushLine(x, y)
                }
                SkPath.Verb.kQuad -> {
                    val cx = input.coords[coordIdx++]
                    val cy = input.coords[coordIdx++]
                    val x = input.coords[coordIdx++]
                    val y = input.coords[coordIdx++]
                    flattenQuad(penX, penY, cx, cy, x, y) { fx, fy -> pushLine(fx, fy) }
                }
                SkPath.Verb.kConic -> {
                    val cx = input.coords[coordIdx++]
                    val cy = input.coords[coordIdx++]
                    val x = input.coords[coordIdx++]
                    val y = input.coords[coordIdx++]
                    flattenQuad(penX, penY, cx, cy, x, y) { fx, fy -> pushLine(fx, fy) }
                }
                SkPath.Verb.kCubic -> {
                    val c1x = input.coords[coordIdx++]
                    val c1y = input.coords[coordIdx++]
                    val c2x = input.coords[coordIdx++]
                    val c2y = input.coords[coordIdx++]
                    val x = input.coords[coordIdx++]
                    val y = input.coords[coordIdx++]
                    flattenCubic(penX, penY, c1x, c1y, c2x, c2y, x, y) { fx, fy -> pushLine(fx, fy) }
                }
                SkPath.Verb.kClose -> {
                    pushLine(startX, startY)
                }
                SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
            }
        }
        return segments
    }

    public companion object {
        /**
         * Mirrors `SkTrimPathEffect::Make`. Non-finite values return `null`;
         * input values are pinned to `[0, 1]`.
         */
        public fun Make(startT: Float, stopT: Float, mode: Mode = Mode.kNormal): SkPathEffect? {
            if (!startT.isFinite() || !stopT.isFinite()) return null
            if (startT <= 0f && stopT >= 1f && mode == Mode.kNormal) return null
            val start = startT.coerceIn(0f, 1f)
            val stop = stopT.coerceIn(0f, 1f)
            if (start >= stop && mode == Mode.kInverted) return null
            return SkTrimPathEffect(start, stop, mode)
        }

        private const val EPS = 1e-4f
        private const val CHORD_TOL = 0.5f
        private const val MAX_LEVELS = 16

        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        private fun distanceSquared(x0: Float, y0: Float, x1: Float, y1: Float): Float {
            val dx = x1 - x0
            val dy = y1 - y0
            return dx * dx + dy * dy
        }

        private fun flattenQuad(
            x0: Float,
            y0: Float,
            cx: Float,
            cy: Float,
            x2: Float,
            y2: Float,
            emit: (Float, Float) -> Unit,
        ) {
            subdivQuad(x0, y0, cx, cy, x2, y2, 0, emit)
        }

        private fun subdivQuad(
            x0: Float,
            y0: Float,
            cx: Float,
            cy: Float,
            x2: Float,
            y2: Float,
            level: Int,
            emit: (Float, Float) -> Unit,
        ) {
            val mx = (x0 + x2) * 0.5f
            val my = (y0 + y2) * 0.5f
            val ex = cx - mx
            val ey = cy - my
            if (ex * ex + ey * ey < CHORD_TOL * CHORD_TOL || level >= MAX_LEVELS) {
                emit(x2, y2)
                return
            }
            val ax = (x0 + cx) * 0.5f
            val ay = (y0 + cy) * 0.5f
            val bx = (cx + x2) * 0.5f
            val by = (cy + y2) * 0.5f
            val midX = (ax + bx) * 0.5f
            val midY = (ay + by) * 0.5f
            subdivQuad(x0, y0, ax, ay, midX, midY, level + 1, emit)
            subdivQuad(midX, midY, bx, by, x2, y2, level + 1, emit)
        }

        private fun flattenCubic(
            x0: Float,
            y0: Float,
            c1x: Float,
            c1y: Float,
            c2x: Float,
            c2y: Float,
            x3: Float,
            y3: Float,
            emit: (Float, Float) -> Unit,
        ) {
            subdivCubic(x0, y0, c1x, c1y, c2x, c2y, x3, y3, 0, emit)
        }

        private fun subdivCubic(
            x0: Float,
            y0: Float,
            c1x: Float,
            c1y: Float,
            c2x: Float,
            c2y: Float,
            x3: Float,
            y3: Float,
            level: Int,
            emit: (Float, Float) -> Unit,
        ) {
            val mx = (x0 + x3) * 0.5f
            val my = (y0 + y3) * 0.5f
            val e1x = c1x - mx
            val e1y = c1y - my
            val e2x = c2x - mx
            val e2y = c2y - my
            if (maxOf(e1x * e1x + e1y * e1y, e2x * e2x + e2y * e2y) <
                CHORD_TOL * CHORD_TOL || level >= MAX_LEVELS
            ) {
                emit(x3, y3)
                return
            }
            val ax = (x0 + c1x) * 0.5f
            val ay = (y0 + c1y) * 0.5f
            val bx = (c1x + c2x) * 0.5f
            val by = (c1y + c2y) * 0.5f
            val cx2 = (c2x + x3) * 0.5f
            val cy2 = (c2y + y3) * 0.5f
            val dx2 = (ax + bx) * 0.5f
            val dy2 = (ay + by) * 0.5f
            val ex2 = (bx + cx2) * 0.5f
            val ey2 = (by + cy2) * 0.5f
            val midX = (dx2 + ex2) * 0.5f
            val midY = (dy2 + ey2) * 0.5f
            subdivCubic(x0, y0, ax, ay, dx2, dy2, midX, midY, level + 1, emit)
            subdivCubic(midX, midY, ex2, ey2, cx2, cy2, x3, y3, level + 1, emit)
        }
    }
}

package org.graphiks.kanvas.geometry

import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

class PathMeasure(path: Path, val forceClosed: Boolean = false, resScale: Float = 1f) {
    private data class Contour(
        val segments: List<ContourSegment>,
        val cumulativeLengths: FloatArray,
        val closed: Boolean,
    ) {
        val length: Float get() = if (cumulativeLengths.isEmpty()) 0f else cumulativeLengths.last()
    }

    private data class ContourSegment(
        val type: PathVerb,
        val x1: Float, val y1: Float,
        val cx1: Float, val cy1: Float,
        val cx2: Float, val cy2: Float,
        val x2: Float, val y2: Float,
    ) {
        val length: Float
            get() = when (type) {
                PathVerb.LINE -> {
                    val dx = x2 - x1; val dy = y2 - y1
                    sqrt(dx * dx + dy * dy)
                }
                PathVerb.QUAD -> {
                    val steps = 8
                    var len = 0f
                    var px = x1; var py = y1
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps
                        val u = 1f - t
                        val x = u * u * x1 + 2f * u * t * cx1 + t * t * x2
                        val y = u * u * y1 + 2f * u * t * cy1 + t * t * y2
                        val dx = x - px; val dy = y - py
                        len += sqrt(dx * dx + dy * dy)
                        px = x; py = y
                    }
                    len
                }
                PathVerb.CUBIC -> {
                    val steps = 16
                    var len = 0f
                    var px = x1; var py = y1
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps
                        val u = 1f - t
                        val x = u * u * u * x1 + 3f * u * u * t * cx1 + 3f * u * t * t * cx2 + t * t * t * x2
                        val y = u * u * u * y1 + 3f * u * u * t * cy1 + 3f * u * t * t * cy2 + t * t * t * y2
                        val dx = x - px; val dy = y - py
                        len += sqrt(dx * dx + dy * dy)
                        px = x; py = y
                    }
                    len
                }
                else -> {
                    val dx = x2 - x1; val dy = y2 - y1
                    sqrt(dx * dx + dy * dy)
                }
            }
    }

    private val contours = mutableListOf<Contour>()
    private var contourIndex = 0

    val length: Float
        get() = contours.getOrNull(contourIndex)?.length ?: 0f

    val isClosed: Boolean
        get() = contours.getOrNull(contourIndex)?.closed ?: false

    init {
        var pi = 0
        val pathVerbs = path.verbs()
        val pathPoints = path.points()

        var i = 0
        while (i < pathVerbs.size) {
            if (pathVerbs[i] != PathVerb.MOVE) { i++; continue }
            val segs = mutableListOf<ContourSegment>()
            var prevX = pathPoints[pi].x; var prevY = pathPoints[pi].y
            val firstX = prevX; val firstY = prevY
            pi++
            val contourStart = i
            i++

            while (i < pathVerbs.size && pathVerbs[i] != PathVerb.MOVE) {
                val verb = pathVerbs[i]
                when (verb) {
                    PathVerb.LINE -> {
                        val x = pathPoints[pi].x; val y = pathPoints[pi].y
                        segs.add(ContourSegment(PathVerb.LINE, prevX, prevY, 0f, 0f, 0f, 0f, x, y))
                        prevX = x; prevY = y; pi++
                    }
                    PathVerb.QUAD -> {
                        val cx = pathPoints[pi].x; val cy = pathPoints[pi].y
                        val x = pathPoints[pi + 1].x; val y = pathPoints[pi + 1].y
                        segs.add(ContourSegment(PathVerb.QUAD, prevX, prevY, cx, cy, 0f, 0f, x, y))
                        prevX = x; prevY = y; pi += 2
                    }
                    PathVerb.CUBIC -> {
                        val cx1 = pathPoints[pi].x; val cy1 = pathPoints[pi].y
                        val cx2 = pathPoints[pi + 1].x; val cy2 = pathPoints[pi + 1].y
                        val x = pathPoints[pi + 2].x; val y = pathPoints[pi + 2].y
                        segs.add(ContourSegment(PathVerb.CUBIC, prevX, prevY, cx1, cy1, cx2, cy2, x, y))
                        prevX = x; prevY = y; pi += 3
                    }
                    PathVerb.ARC_TO -> {
                        val x = pathPoints[pi + 3].x; val y = pathPoints[pi + 3].y
                        segs.add(ContourSegment(PathVerb.LINE, prevX, prevY, 0f, 0f, 0f, 0f, x, y))
                        prevX = x; prevY = y; pi += 4
                    }
                    PathVerb.CLOSE -> {
                        if (prevX != firstX || prevY != firstY) {
                            segs.add(ContourSegment(PathVerb.LINE, prevX, prevY, 0f, 0f, 0f, 0f, firstX, firstY))
                            prevX = firstX; prevY = firstY
                        }
                    }
                    PathVerb.MOVE -> {} // unreachable
                }
                i++
            }

            val contourVerbs = pathVerbs.drop(contourStart).takeWhile { it != PathVerb.MOVE }
            val hasClose = contourVerbs.lastOrNull() == PathVerb.CLOSE
            val closed = hasClose || forceClosed

            if (closed && segs.isNotEmpty()) {
                val last = segs.last()
                if (last.x2 != firstX || last.y2 != firstY) {
                    segs.add(ContourSegment(PathVerb.LINE, last.x2, last.y2, 0f, 0f, 0f, 0f, firstX, firstY))
                }
            }

            val cumLengths = FloatArray(segs.size)
            var running = 0f
            for (s in segs.indices) {
                running += segs[s].length
                cumLengths[s] = running
            }
            contours.add(Contour(segs, cumLengths, closed))
        }
    }

    fun getPosition(distance: Float, position: Point?, tangent: Point?): Boolean {
        val contour = contours.getOrNull(contourIndex) ?: return false
        val cl = contour.cumulativeLengths
        if (cl.isEmpty()) return false
        val total = contour.length
        val d = distance.coerceIn(0f, total)

        var lo = 0; var hi = cl.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (cl[mid] < d) lo = mid + 1
            else hi = mid
        }
        val segIdx = lo
        val segStart = if (segIdx > 0) cl[segIdx - 1] else 0f
        val segLen = contour.segments[segIdx].length
        val frac = if (segLen > 1e-6f) (d - segStart) / segLen else 0f
        val seg = contour.segments[segIdx]

        var px: Float; var py: Float
        var tx: Float; var ty: Float

        when (seg.type) {
            PathVerb.LINE -> {
                px = seg.x1 + frac * (seg.x2 - seg.x1)
                py = seg.y1 + frac * (seg.y2 - seg.y1)
                tx = seg.x2 - seg.x1; ty = seg.y2 - seg.y1
                val len = sqrt(tx * tx + ty * ty)
                if (len > 1e-6f) { tx /= len; ty /= len }
            }
            PathVerb.QUAD -> {
                val t = frac; val u = 1f - t
                px = u * u * seg.x1 + 2f * u * t * seg.cx1 + t * t * seg.x2
                py = u * u * seg.y1 + 2f * u * t * seg.cy1 + t * t * seg.y2
                tx = 2f * u * (seg.cx1 - seg.x1) + 2f * t * (seg.x2 - seg.cx1)
                ty = 2f * u * (seg.cy1 - seg.y1) + 2f * t * (seg.y2 - seg.cy1)
                val len = sqrt(tx * tx + ty * ty)
                if (len > 1e-6f) { tx /= len; ty /= len }
            }
            PathVerb.CUBIC -> {
                val t = frac; val u = 1f - t
                px = u * u * u * seg.x1 + 3f * u * u * t * seg.cx1 + 3f * u * t * t * seg.cx2 + t * t * t * seg.x2
                py = u * u * u * seg.y1 + 3f * u * u * t * seg.cy1 + 3f * u * t * t * seg.cy2 + t * t * t * seg.y2
                tx = 3f * u * u * (seg.cx1 - seg.x1) + 6f * u * t * (seg.cx2 - seg.cx1) + 3f * t * t * (seg.x2 - seg.cx2)
                ty = 3f * u * u * (seg.cy1 - seg.y1) + 6f * u * t * (seg.cy2 - seg.cy1) + 3f * t * t * (seg.y2 - seg.cy2)
                val len = sqrt(tx * tx + ty * ty)
                if (len > 1e-6f) { tx /= len; ty /= len }
            }
            else -> {
                px = seg.x1; py = seg.y1; tx = 1f; ty = 0f
            }
        }

        position?.let { it.x = px; it.y = py }
        tangent?.let { it.x = tx; it.y = ty }
        return true
    }

    fun getSegment(startD: Float, stopD: Float, dst: Path, startWithMoveTo: Boolean): Boolean {
        val contour = contours.getOrNull(contourIndex) ?: return false
        val total = contour.length
        if (total <= 0f) return false
        var s = startD.coerceIn(0f, total)
        var e = stopD.coerceIn(0f, total)
        if (s > e) { val tmp = s; s = e; e = tmp }
        if (s >= e) return false

        val startPos = Point(0f, 0f)
        val endPos = Point(0f, 0f)
        if (!getPosition(s, startPos, null)) return false
        if (!getPosition(e, endPos, null)) return false

        if (startWithMoveTo) dst.moveTo(startPos.x, startPos.y)
        dst.lineTo(endPos.x, endPos.y)
        return true
    }

    fun getMatrix(distance: Float, matrix: Matrix33, flags: Int): Boolean {
        // Matrix33 constructor is private; cannot write into the parameter
        return false
    }

    fun nextContour(): Boolean {
        if (contourIndex + 1 < contours.size) {
            contourIndex++
            return true
        }
        return false
    }
}

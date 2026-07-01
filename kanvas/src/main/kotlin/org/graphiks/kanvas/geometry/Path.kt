package org.graphiks.kanvas.geometry

import org.graphiks.kanvas.dsl.PathScope
import org.graphiks.kanvas.types.Line
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.math.abs
import kotlin.math.sqrt

class Path internal constructor() {
    var fillType: FillType = FillType.WINDING

    private val verbs = mutableListOf<PathVerb>()
    private val points = mutableListOf<Point>()

    fun moveTo(x: Float, y: Float): Path {
        verbs.add(PathVerb.MOVE); points.add(Point(x, y)); return this
    }
    fun lineTo(x: Float, y: Float): Path {
        verbs.add(PathVerb.LINE); points.add(Point(x, y)); return this
    }
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): Path {
        verbs.add(PathVerb.QUAD); points.add(Point(cx, cy)); points.add(Point(x, y)); return this
    }
    fun cubicTo(cx1: Float, cy1: Float, cx2: Float, cy2: Float, x: Float, y: Float): Path {
        verbs.add(PathVerb.CUBIC)
        points.add(Point(cx1, cy1)); points.add(Point(cx2, cy2)); points.add(Point(x, y))
        return this
    }
    fun arcTo(rx: Float, ry: Float, xAxisRotation: Float, largeArc: Boolean, sweep: Boolean, x: Float, y: Float): Path {
        verbs.add(PathVerb.ARC_TO)
        points.add(Point(rx, ry)); points.add(Point(xAxisRotation, if (largeArc) 1f else 0f))
        points.add(Point(if (sweep) 1f else 0f, 0f)); points.add(Point(x, y))
        return this
    }
    fun close(): Path { verbs.add(PathVerb.CLOSE); return this }

    fun addRect(rect: Rect): Path {
        moveTo(rect.left, rect.top)
        lineTo(rect.right, rect.top)
        lineTo(rect.right, rect.bottom)
        lineTo(rect.left, rect.bottom)
        close()
        return this
    }

    fun addOval(rect: Rect): Path {
        val cx = rect.center.x; val cy = rect.center.y
        val rx = rect.width / 2f; val ry = rect.height / 2f
        val k = 0.5522847498f
        moveTo(cx + rx, cy)
        cubicTo(cx + rx, cy - k * ry, cx + k * rx, cy - ry, cx, cy - ry)
        cubicTo(cx - k * rx, cy - ry, cx - rx, cy - k * ry, cx - rx, cy)
        cubicTo(cx - rx, cy + k * ry, cx - k * rx, cy + ry, cx, cy + ry)
        cubicTo(cx + k * rx, cy + ry, cx + rx, cy + k * ry, cx + rx, cy)
        close()
        return this
    }

    fun addCircle(cx: Float, cy: Float, r: Float): Path = addOval(Rect.fromLTRB(cx - r, cy - r, cx + r, cy + r))

    fun addRRect(rrect: RRect): Path {
        val r = rrect.rect
        val tl = rrect.topLeft; val tr = rrect.topRight
        val br = rrect.bottomRight; val bl = rrect.bottomLeft
        moveTo(r.left + tl.x, r.top)
        lineTo(r.right - tr.x, r.top)
        arcTo(tr.x, tr.y, 0f, false, true, r.right, r.top + tr.y)
        lineTo(r.right, r.bottom - br.y)
        arcTo(br.x, br.y, 0f, false, true, r.right - br.x, r.bottom)
        lineTo(r.left + bl.x, r.bottom)
        arcTo(bl.x, bl.y, 0f, false, true, r.left, r.bottom - bl.y)
        lineTo(r.left, r.top + tl.y)
        arcTo(tl.x, tl.y, 0f, false, true, r.left + tl.x, r.top)
        close()
        return this
    }

    fun addPath(path: Path): Path {
        verbs.addAll(path.verbs)
        points.addAll(path.points)
        return this
    }

    fun reverseAddPath(src: Path): Path {
        val sVerbs = src.verbs
        val sPoints = src.points

        var vi = 0
        var pi = 0

        val ptCounts = mapOf(
            PathVerb.MOVE to 1, PathVerb.LINE to 1,
            PathVerb.QUAD to 2, PathVerb.CUBIC to 3,
            PathVerb.ARC_TO to 4, PathVerb.CLOSE to 0,
        )

        while (vi < sVerbs.size) {
            if (sVerbs[vi] != PathVerb.MOVE) { vi++; pi++; continue }

            val segVerbs = mutableListOf<PathVerb>()
            val segPtStart = mutableListOf<Int>()
            val segPtCount = mutableListOf<Int>()

            var v = vi
            var p = pi
            while (v < sVerbs.size && (v == vi || sVerbs[v] != PathVerb.MOVE)) {
                val c = ptCounts[sVerbs[v]]!!
                segVerbs.add(sVerbs[v])
                segPtStart.add(p)
                segPtCount.add(c)
                p += c
                v++
            }

            val hasClose = segVerbs.last() == PathVerb.CLOSE
            val lastReal = if (hasClose) segVerbs.size - 2 else segVerbs.size - 1

            val (lastEndX, lastEndY) = segmentEndPoint(segVerbs[lastReal], sPoints, segPtStart[lastReal])
            moveTo(lastEndX, lastEndY)

            for (si in lastReal downTo 1) {
                val startX: Float
                val startY: Float
                if (si > 1) {
                    val p = segmentEndPoint(segVerbs[si - 1], sPoints, segPtStart[si - 1])
                    startX = p.first; startY = p.second
                } else {
                    startX = sPoints[segPtStart[0]].x
                    startY = sPoints[segPtStart[0]].y
                }

                val verb = segVerbs[si]
                val base = segPtStart[si]
                when (verb) {
                    PathVerb.LINE -> lineTo(startX, startY)
                    PathVerb.QUAD -> quadTo(sPoints[base].x, sPoints[base].y, startX, startY)
                    PathVerb.CUBIC -> cubicTo(
                        sPoints[base + 1].x, sPoints[base + 1].y,
                        sPoints[base].x, sPoints[base].y,
                        startX, startY,
                    )
                    PathVerb.ARC_TO -> {
                        val rx = sPoints[base].x; val ry = sPoints[base].y
                        val axisRot = sPoints[base + 1].x
                        val largeArc = sPoints[base + 1].y > 0f
                        val sweep = !(sPoints[base + 2].x > 0f)
                        arcTo(rx, ry, axisRot, largeArc, sweep, startX, startY)
                    }
                    else -> {}
                }
            }

            if (hasClose) close()

            vi = v
            pi = p
        }

        return this
    }

    fun isEmpty(): Boolean = verbs.isEmpty()

    fun isRect(rect: Rect? = null): Boolean {
        val v = verbs
        if (v.size < 5) return false
        if (v[0] != PathVerb.MOVE) return false
        val hasClose = v.last() == PathVerb.CLOSE
        val lineCount = if (hasClose) v.size - 2 else v.size - 1
        if (lineCount < 3 || lineCount > 4) return false
        val endIdx = if (hasClose) v.size - 1 else v.size
        for (i in 1 until endIdx) {
            if (v[i] != PathVerb.LINE) return false
        }
        val p = points
        val corners = listOf(p[0], p[1], p[2], p[3])
        val closePt = if (lineCount == 4) p[4] else p[0]
        for (i in 0..3) {
            val next = if (i == 3) closePt else corners[i + 1]
            val dx = next.x - corners[i].x
            val dy = next.y - corners[i].y
            if (dx != 0f && dy != 0f) return false
        }
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0..3) {
            minX = minOf(minX, corners[i].x); minY = minOf(minY, corners[i].y)
            maxX = maxOf(maxX, corners[i].x); maxY = maxOf(maxY, corners[i].y)
        }
        rect?.let { r ->
            r.left = minX; r.top = minY; r.right = maxX; r.bottom = maxY
        }
        return true
    }

    fun isOval(bounds: Rect? = null): Boolean {
        val v = verbs
        if (v.size < 5) return false
        if (v[0] != PathVerb.MOVE) return false
        val hasClose = v.last() == PathVerb.CLOSE
        val cubicCount = v.size - 1 - (if (hasClose) 1 else 0)
        if (cubicCount != 4) return false
        for (i in 1 until v.size - (if (hasClose) 1 else 0)) {
            if (v[i] != PathVerb.CUBIC) return false
        }
        val p = points
        // Compute bounds from all 5 endpoints: p[0], p[3], p[6], p[9], p[12]
        val endIndices = listOf(0, 3, 6, 9, 12)
        if (p.size <= endIndices.last()) return false
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (idx in endIndices) {
            minX = minOf(minX, p[idx].x); minY = minOf(minY, p[idx].y)
            maxX = maxOf(maxX, p[idx].x); maxY = maxOf(maxY, p[idx].y)
        }
        val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f
        val rx = (maxX - minX) / 2f; val ry = (maxY - minY) / 2f
        if (rx < 1e-6f || ry < 1e-6f) return false
        bounds?.let { b ->
            b.left = minX; b.top = minY; b.right = maxX; b.bottom = maxY
        }
        return true
    }

    fun isRRect(rrect: RRect? = null): Boolean {
        val v = verbs
        if (v.size < 9) return false
        if (v[0] != PathVerb.MOVE) return false
        val hasClose = v.last() == PathVerb.CLOSE
        val checkCount = if (hasClose) v.size - 1 else v.size
        if (checkCount != 9) return false
        val expected = listOf(
            PathVerb.MOVE, PathVerb.LINE, PathVerb.ARC_TO,
            PathVerb.LINE, PathVerb.ARC_TO,
            PathVerb.LINE, PathVerb.ARC_TO,
            PathVerb.LINE, PathVerb.ARC_TO,
        )
        for (i in expected.indices) {
            if (v[i] != expected[i]) return false
        }
        if (rrect != null) {
            val p = points
            val pts = listOf(p[0], p[1], p[3], p[5])
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (pt in pts) {
                minX = minOf(minX, pt.x); minY = minOf(minY, pt.y)
                maxX = maxOf(maxX, pt.x); maxY = maxOf(maxY, pt.y)
            }
            rrect.rect.left = minX; rrect.rect.top = minY
            rrect.rect.right = maxX; rrect.rect.bottom = maxY
        }
        return true
    }

    fun isLine(line: Line? = null): Boolean {
        val v = verbs
        if (v.size != 2) return false
        if (v[0] != PathVerb.MOVE || v[1] != PathVerb.LINE) return false
        line?.let {
            it.p1 = Point(points[0].x, points[0].y)
            it.p2 = Point(points[1].x, points[1].y)
        }
        return true
    }

    fun isConvex(): Boolean {
        val poly = mutableListOf<Point>()
        var firstPt: Point? = null
        var pi = 0
        for (verb in verbs) {
            when (verb) {
                PathVerb.MOVE -> {
                    poly.clear(); firstPt = points[pi]
                    poly.add(points[pi]); pi++
                }
                PathVerb.LINE -> { poly.add(points[pi]); pi++ }
                PathVerb.CLOSE -> firstPt?.let { poly.add(it) }
                else -> {
                    pi += when (verb) {
                        PathVerb.QUAD -> 2; PathVerb.CUBIC -> 3
                        PathVerb.ARC_TO -> 4; else -> 0
                    }
                }
            }
        }
        if (poly.size < 3) return true
        var sign = 0f
        val n = poly.size - 1
        for (i in 0 until n) {
            val dx1 = poly[(i + 1) % n].x - poly[i].x
            val dy1 = poly[(i + 1) % n].y - poly[i].y
            val dx2 = poly[(i + 2) % n].x - poly[(i + 1) % n].x
            val dy2 = poly[(i + 2) % n].y - poly[(i + 1) % n].y
            val cross = dx1 * dy2 - dy1 * dx2
            if (cross != 0f) {
                if (sign == 0f) sign = cross
                else if (cross * sign < 0f) return false
            }
        }
        return true
    }

    fun isInterpolatable(other: Path): Boolean {
        val v1 = verbs; val v2 = other.verbs
        if (v1.size != v2.size) return false
        for (i in v1.indices) {
            if (v1[i] != v2[i]) return false
        }
        return true
    }

    fun contains(point: Point): Boolean {
        if (verbs.isEmpty()) return false
        val px = point.x; val py = point.y
        var winding = 0
        val segments = collectSegments()
        for (seg in segments) {
            val (x1, y1, x2, y2) = seg
            if (y1 == y2) continue
            if (py < minOf(y1, y2) || py >= maxOf(y1, y2)) continue
            val xIntersect = x1 + (py - y1) * (x2 - x1) / (y2 - y1)
            if (xIntersect > px) {
                if (y2 > y1) winding++ else winding--
            }
        }
        return when (fillType) {
            FillType.WINDING, FillType.INVERSE_WINDING -> winding != 0
            FillType.EVEN_ODD, FillType.INVERSE_EVEN_ODD -> winding % 2 != 0
        }
    }

    fun conservativelyContainsRect(rect: Rect): Boolean {
        val tl = Point(rect.left, rect.top)
        val tr = Point(rect.right, rect.top)
        val bl = Point(rect.left, rect.bottom)
        val br = Point(rect.right, rect.bottom)
        return contains(tl) && contains(tr) && contains(bl) && contains(br)
    }

    fun transform(tx: Float, ty: Float, sx: Float, sy: Float): Path {
        val m = Matrix33.translate(tx, ty) * Matrix33.scale(sx, sy)
        return transform(m)
    }

    fun transform(matrix: Matrix33): Path {
        val result = Path()
        result.fillType = fillType
        result.verbs.addAll(verbs)
        result.points.addAll(points.map { matrix * it })
        return result
    }

    internal fun verbs(): List<PathVerb> = verbs.toList()
    internal fun points(): List<Point> = points.toList()

    private data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    private fun collectSegments(): List<Segment> {
        val result = mutableListOf<Segment>()
        var pi = 0
        var prevX = 0f; var prevY = 0f
        var firstX = 0f; var firstY = 0f
        var hasPrev = false
        for (verb in verbs) {
            when (verb) {
                PathVerb.MOVE -> {
                    firstX = points[pi].x; firstY = points[pi].y
                    prevX = firstX; prevY = firstY
                    hasPrev = true; pi++
                }
                PathVerb.LINE -> {
                    val x = points[pi].x; val y = points[pi].y
                    result.add(Segment(prevX, prevY, x, y))
                    prevX = x; prevY = y; pi++
                }
                PathVerb.QUAD -> {
                    val cx = points[pi].x; val cy = points[pi].y
                    val x = points[pi + 1].x; val y = points[pi + 1].y
                    linearizeQuad(prevX, prevY, cx, cy, x, y, result)
                    prevX = x; prevY = y; pi += 2
                }
                PathVerb.CUBIC -> {
                    val cx1 = points[pi].x; val cy1 = points[pi].y
                    val cx2 = points[pi + 1].x; val cy2 = points[pi + 1].y
                    val x = points[pi + 2].x; val y = points[pi + 2].y
                    linearizeCubic(prevX, prevY, cx1, cy1, cx2, cy2, x, y, result)
                    prevX = x; prevY = y; pi += 3
                }
                PathVerb.ARC_TO -> {
                    val x = points[pi + 3].x; val y = points[pi + 3].y
                    result.add(Segment(prevX, prevY, x, y))
                    prevX = x; prevY = y; pi += 4
                }
                PathVerb.CLOSE -> {
                    if (hasPrev && (prevX != firstX || prevY != firstY)) {
                        result.add(Segment(prevX, prevY, firstX, firstY))
                        prevX = firstX; prevY = firstY
                    }
                }
            }
        }
        return result
    }

    companion object {
        operator fun invoke(block: PathScope.() -> Unit): Path {
            val scope = PathScope()
            scope.block()
            return scope.build()
        }

        private fun linearizeQuad(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, out: MutableList<Segment>) {
            val steps = 8
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val u = 1f - t
                val x = u * u * x0 + 2f * u * t * cx + t * t * x1
                val y = u * u * y0 + 2f * u * t * cy + t * t * y1
                if (i == 1) out.add(Segment(x0, y0, x, y))
                else {
                    val last = out.last()
                    out.add(Segment(last.x2, last.y2, x, y))
                }
            }
        }

        private fun linearizeCubic(x0: Float, y0: Float, cx1: Float, cy1: Float, cx2: Float, cy2: Float, x1: Float, y1: Float, out: MutableList<Segment>) {
            val steps = 16
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val u = 1f - t
                val x = u * u * u * x0 + 3f * u * u * t * cx1 + 3f * u * t * t * cx2 + t * t * t * x1
                val y = u * u * u * y0 + 3f * u * u * t * cy1 + 3f * u * t * t * cy2 + t * t * t * y1
                if (i == 1) out.add(Segment(x0, y0, x, y))
                else {
                    val last = out.last()
                    out.add(Segment(last.x2, last.y2, x, y))
                }
            }
        }

        private fun segmentEndPoint(
            verb: PathVerb, points: List<Point>, ptBase: Int,
        ): Pair<Float, Float> {
            val idx = when (verb) {
                PathVerb.MOVE, PathVerb.LINE -> ptBase
                PathVerb.QUAD -> ptBase + 1
                PathVerb.CUBIC -> ptBase + 2
                PathVerb.ARC_TO -> ptBase + 3
                else -> ptBase
            }
            return points[idx].x to points[idx].y
        }
    }
}

enum class PathVerb { MOVE, LINE, QUAD, CUBIC, ARC_TO, CLOSE }

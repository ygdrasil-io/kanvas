package org.graphiks.kanvas.geometry

import org.graphiks.kanvas.dsl.PathScope
import org.graphiks.kanvas.types.Line
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
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
        val (tl, tr, br, bl) = normalizedRadii(rrect)
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

    private fun normalizedRadii(rrect: RRect): Array<CornerRadii> {
        val width = rrect.rect.width.coerceAtLeast(0f)
        val height = rrect.rect.height.coerceAtLeast(0f)
        val tl = rrect.topLeft.nonNegative()
        val tr = rrect.topRight.nonNegative()
        val br = rrect.bottomRight.nonNegative()
        val bl = rrect.bottomLeft.nonNegative()
        val scale = min(
            1f,
            min(
                ratioOrOne(width, tl.x + tr.x),
                min(
                    ratioOrOne(width, bl.x + br.x),
                    min(
                        ratioOrOne(height, tl.y + bl.y),
                        ratioOrOne(height, tr.y + br.y),
                    ),
                ),
            ),
        )
        return arrayOf(tl.scaled(scale), tr.scaled(scale), br.scaled(scale), bl.scaled(scale))
    }

    private fun ratioOrOne(limit: Float, sum: Float): Float =
        if (sum > limit && sum > 0f) limit / sum else 1f

    private fun CornerRadii.nonNegative(): CornerRadii =
        CornerRadii(x.coerceAtLeast(0f), y.coerceAtLeast(0f))

    private fun CornerRadii.scaled(scale: Float): CornerRadii =
        CornerRadii(x * scale, y * scale)

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

    /**
     * Returns a conservative axis-aligned bound for the path's drawable
     * geometry, or `null` when it has no points. Bézier control points are
     * included, so the result contains the ink even when it is not tight.
     */
    fun computeBounds(): Rect? {
        var pointIndex = 0
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        fun include(point: Point) {
            minX = minOf(minX, point.x)
            minY = minOf(minY, point.y)
            maxX = maxOf(maxX, point.x)
            maxY = maxOf(maxY, point.y)
        }
        for (verb in verbs) {
            when (verb) {
                PathVerb.MOVE, PathVerb.LINE -> include(points[pointIndex++])
                PathVerb.QUAD -> {
                    include(points[pointIndex++])
                    include(points[pointIndex++])
                }
                PathVerb.CUBIC -> repeat(3) { include(points[pointIndex++]) }
                // Arc metadata is not a drawable point; include its endpoint.
                PathVerb.ARC_TO -> {
                    pointIndex += 3
                    include(points[pointIndex++])
                }
                PathVerb.CLOSE -> Unit
            }
        }
        return if (minX.isFinite()) Rect.fromLTRB(minX, minY, maxX, maxY) else null
    }

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
        // Verify the path is closed: last point must match first point
        if (lineCount == 4 && (closePt.x != p[0].x || closePt.y != p[0].y)) return false
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
        var contourCount = 0
        var pi = 0
        for (verb in verbs) {
            when (verb) {
                PathVerb.MOVE -> {
                    if (contourCount > 0 && poly.size >= 3) return false // multi-contour: cannot determine convexity
                    poly.clear(); firstPt = points[pi]
                    poly.add(points[pi]); pi++; contourCount++
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
        var pi = 0
        for (verb in verbs) {
            result.verbs.add(verb)
            when (verb) {
                PathVerb.MOVE, PathVerb.LINE -> {
                    result.points.add(matrix * points[pi])
                    pi++
                }
                PathVerb.QUAD -> {
                    result.points.add(matrix * points[pi])
                    result.points.add(matrix * points[pi + 1])
                    pi += 2
                }
                PathVerb.CUBIC -> {
                    result.points.add(matrix * points[pi])
                    result.points.add(matrix * points[pi + 1])
                    result.points.add(matrix * points[pi + 2])
                    pi += 3
                }
                PathVerb.ARC_TO -> {
                    val radius = points[pi]
                    val rotationAndLargeArc = points[pi + 1]
                    val sweep = points[pi + 2]
                    val endpoint = points[pi + 3]
                    val transformedArc = transformArcMetadata(
                        radius = radius,
                        xAxisRotation = rotationAndLargeArc.x,
                        sweep = sweep,
                        matrix = matrix,
                    )
                    result.points.add(Point(transformedArc.rx, transformedArc.ry))
                    result.points.add(Point(transformedArc.xAxisRotation, rotationAndLargeArc.y))
                    result.points.add(Point(transformedArc.sweepFlag, 0f))
                    result.points.add(matrix * endpoint)
                    pi += 4
                }
                PathVerb.CLOSE -> {}
            }
        }
        return result
    }

    private data class TransformedArcMetadata(
        val rx: Float,
        val ry: Float,
        val xAxisRotation: Float,
        val sweepFlag: Float,
    )

    private fun transformArcMetadata(
        radius: Point,
        xAxisRotation: Float,
        sweep: Point,
        matrix: Matrix33,
    ): TransformedArcMetadata {
        val angle = xAxisRotation.toDouble() * PI / 180.0
        val cosAngle = cos(angle)
        val sinAngle = sin(angle)
        val rx = abs(radius.x.toDouble())
        val ry = abs(radius.y.toDouble())
        val xAxisX = cosAngle * rx
        val xAxisY = sinAngle * rx
        val yAxisX = -sinAngle * ry
        val yAxisY = cosAngle * ry
        val transformedXAxisX = matrix.scaleX * xAxisX + matrix.skewX * xAxisY
        val transformedXAxisY = matrix.skewY * xAxisX + matrix.scaleY * xAxisY
        val transformedYAxisX = matrix.scaleX * yAxisX + matrix.skewX * yAxisY
        val transformedYAxisY = matrix.skewY * yAxisX + matrix.scaleY * yAxisY

        val xAxisLengthSquared = transformedXAxisX * transformedXAxisX + transformedXAxisY * transformedXAxisY
        val yAxisLengthSquared = transformedYAxisX * transformedYAxisX + transformedYAxisY * transformedYAxisY
        val axisDot = transformedXAxisX * transformedYAxisX + transformedXAxisY * transformedYAxisY
        val dotTolerance = 1e-6 * sqrt(xAxisLengthSquared * yAxisLengthSquared)
        val (transformedRx, transformedRy, transformedRotation) = if (abs(axisDot) <= dotTolerance) {
            val transformedRx = sqrt(xAxisLengthSquared)
            val transformedRy = sqrt(yAxisLengthSquared)
            val transformedRotation = when {
                transformedRx > 0.0 -> atan2(transformedXAxisY, transformedXAxisX) * 180.0 / PI
                transformedRy > 0.0 -> atan2(-transformedYAxisX, transformedYAxisY) * 180.0 / PI
                else -> xAxisRotation.toDouble()
            }
            Triple(transformedRx, transformedRy, transformedRotation)
        } else {
            val covarianceXX = transformedXAxisX * transformedXAxisX + transformedYAxisX * transformedYAxisX
            val covarianceXY = transformedXAxisX * transformedXAxisY + transformedYAxisX * transformedYAxisY
            val covarianceYY = transformedXAxisY * transformedXAxisY + transformedYAxisY * transformedYAxisY
            val trace = covarianceXX + covarianceYY
            val diff = covarianceXX - covarianceYY
            val root = sqrt(diff * diff + 4.0 * covarianceXY * covarianceXY)
            val major = ((trace + root) / 2.0).coerceAtLeast(0.0)
            val minor = ((trace - root) / 2.0).coerceAtLeast(0.0)
            val transformedRotation = if (major > 0.0) {
                0.5 * atan2(2.0 * covarianceXY, diff) * 180.0 / PI
            } else {
                xAxisRotation.toDouble()
            }
            Triple(sqrt(major), sqrt(minor), transformedRotation)
        }
        val determinant = matrix.scaleX * matrix.scaleY - matrix.skewX * matrix.skewY
        val sweepFlag = if (determinant < 0f) {
            if (sweep.x > 0f) 0f else 1f
        } else {
            sweep.x
        }
        return TransformedArcMetadata(
            rx = transformedRx.toFloat(),
            ry = transformedRy.toFloat(),
            xAxisRotation = transformedRotation.toFloat(),
            sweepFlag = sweepFlag,
        )
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

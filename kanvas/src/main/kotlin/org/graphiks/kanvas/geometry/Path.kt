package org.graphiks.kanvas.geometry

import org.graphiks.kanvas.dsl.PathScope
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

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

            // Collect contour into segments
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

            // Reversed start = original last segment's endpoint
            val (lastEndX, lastEndY) = segmentEndPoint(segVerbs[lastReal], sPoints, segPtStart[lastReal])
            moveTo(lastEndX, lastEndY)

            // Walk backwards, skipping MOVE at index 0
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

    companion object {
        operator fun invoke(block: PathScope.() -> Unit): Path {
            val scope = PathScope()
            scope.block()
            return scope.build()
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

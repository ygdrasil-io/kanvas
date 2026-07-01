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
    }
}

enum class PathVerb { MOVE, LINE, QUAD, CUBIC, ARC_TO, CLOSE }

package org.graphiks.kanvas

import org.graphiks.kanvas.gpu.renderer.geometry.GPUPathDescriptor
import org.graphiks.kanvas.gpu.renderer.geometry.PathData
import org.graphiks.kanvas.gpu.renderer.geometry.PathVerb
import org.graphiks.kanvas.gpu.renderer.geometry.Point

enum class KanvasFillType(val label: String) {
    WINDING("winding"),
    EVEN_ODD("even_odd"),
    INVERSE_WINDING("inverse_winding"),
    INVERSE_EVEN_ODD("inverse_even_odd"),
}

class Path(
    var fillType: KanvasFillType = KanvasFillType.WINDING,
) {
    private val verbs = mutableListOf<PathVerb>()

    fun moveTo(x: Float, y: Float): Path = apply {
        verbs.add(PathVerb.MoveTo(Point(x, y)))
    }

    fun lineTo(x: Float, y: Float): Path = apply {
        verbs.add(PathVerb.LineTo(Point(x, y)))
    }

    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): Path = apply {
        verbs.add(PathVerb.QuadTo(c = Point(cx, cy), p = Point(x, y)))
    }

    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float): Path = apply {
        verbs.add(PathVerb.CubicTo(c1 = Point(c1x, c1y), c2 = Point(c2x, c2y), p = Point(x, y)))
    }

    fun close(): Path = apply {
        verbs.add(PathVerb.Close)
    }

    fun addCircle(cx: Float, cy: Float, radius: Float): Path = apply {
        val k = 0.5522847498f
        moveTo(cx + radius, cy)
        cubicTo(cx + radius, cy - k * radius, cx + k * radius, cy - radius, cx, cy - radius)
        cubicTo(cx - k * radius, cy - radius, cx - radius, cy - k * radius, cx - radius, cy)
        cubicTo(cx - radius, cy + k * radius, cx - k * radius, cy + radius, cx, cy + radius)
        cubicTo(cx + k * radius, cy + radius, cx + radius, cy + k * radius, cx + radius, cy)
        close()
    }

    fun addOval(oval: Rect): Path = apply {
        val cx = oval.left + oval.width / 2f
        val cy = oval.top + oval.height / 2f
        val rx = oval.width / 2f
        val ry = oval.height / 2f
        val k = 0.5522847498f
        moveTo(cx + rx, cy)
        cubicTo(cx + rx, cy - k * ry, cx + k * rx, cy - ry, cx, cy - ry)
        cubicTo(cx - k * rx, cy - ry, cx - rx, cy - k * ry, cx - rx, cy)
        cubicTo(cx - rx, cy + k * ry, cx - k * rx, cy + ry, cx, cy + ry)
        cubicTo(cx + k * rx, cy + ry, cx + rx, cy + k * ry, cx + rx, cy)
        close()
    }

    internal fun toPathData(): PathData {
        val allPoints = mutableListOf<Point>()
        for (verb in verbs) {
            when (verb) {
                is PathVerb.MoveTo -> allPoints.add(verb.p)
                is PathVerb.LineTo -> allPoints.add(verb.p)
                is PathVerb.QuadTo -> { allPoints.add(verb.c); allPoints.add(verb.p) }
                is PathVerb.CubicTo -> { allPoints.add(verb.c1); allPoints.add(verb.c2); allPoints.add(verb.p) }
                is PathVerb.Close -> {}
            }
        }
        return PathData(verbs = verbs.toList(), points = allPoints)
    }

    fun lower(): GPUPathDescriptor {
        val isInverse = fillType == KanvasFillType.INVERSE_WINDING || fillType == KanvasFillType.INVERSE_EVEN_ODD
        val pointCount = verbs.count { it !is PathVerb.Close }
        return GPUPathDescriptor(
            pathKey = "kanvas-path-${System.identityHashCode(this)}",
            verbCount = verbs.size,
            pointCount = pointCount,
            fillRule = fillType.label,
            inverseFill = isInverse,
            finiteProof = "finite",
            volatility = "static",
        )
    }
}

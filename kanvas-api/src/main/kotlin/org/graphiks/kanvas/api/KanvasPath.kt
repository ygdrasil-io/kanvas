package org.graphiks.kanvas.api

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

class KanvasPath(
    var fillType: KanvasFillType = KanvasFillType.WINDING,
) {
    private val verbs = mutableListOf<PathVerb>()

    fun moveTo(x: Float, y: Float): KanvasPath = apply {
        verbs.add(PathVerb.MoveTo(Point(x, y)))
    }

    fun lineTo(x: Float, y: Float): KanvasPath = apply {
        verbs.add(PathVerb.LineTo(Point(x, y)))
    }

    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): KanvasPath = apply {
        verbs.add(PathVerb.QuadTo(c = Point(cx, cy), p = Point(x, y)))
    }

    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float): KanvasPath = apply {
        verbs.add(PathVerb.CubicTo(c1 = Point(c1x, c1y), c2 = Point(c2x, c2y), p = Point(x, y)))
    }

    fun close(): KanvasPath = apply {
        verbs.add(PathVerb.Close)
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

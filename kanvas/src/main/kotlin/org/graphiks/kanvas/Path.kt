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

    fun arcTo(
        rx: Float, ry: Float,
        xAxisRotation: Float,
        largeArcFlag: Boolean, sweepFlag: Boolean,
        x: Float, y: Float,
    ): Path = apply {
        val x1 = currentX()
        val y1 = currentY()
        if (x1 == x && y1 == y) return@apply
        var effectiveRx = rx; var effectiveRy = ry
        if (effectiveRx <= 0f || effectiveRy <= 0f) { lineTo(x, y); return@apply }

        val phi = Math.toRadians(xAxisRotation.toDouble()).toFloat()
        val cosPhi = kotlin.math.cos(phi)
        val sinPhi = kotlin.math.sin(phi)

        val dx2 = (x1 - x) / 2f
        val dy2 = (y1 - y) / 2f
        val x1p = cosPhi * dx2 + sinPhi * dy2
        val y1p = -sinPhi * dx2 + cosPhi * dy2

        val x1ps = x1p * x1p
        val y1ps = y1p * y1p

        val lambda = x1ps / (effectiveRx * effectiveRx) + y1ps / (effectiveRy * effectiveRy)
        if (lambda > 1f) {
            val scale = kotlin.math.sqrt(lambda)
            effectiveRx *= scale; effectiveRy *= scale
        }

        val rxs = effectiveRx * effectiveRx
        val rys = effectiveRy * effectiveRy
        val sign = if (largeArcFlag != sweepFlag) 1f else -1f
        val denom = rxs * y1ps + rys * x1ps
        val num = (rxs * rys - denom) / denom
        val coeff = sign * kotlin.math.sqrt(kotlin.math.max(num, 0f))
        val cxp = coeff * ((effectiveRx * y1p) / effectiveRy)
        val cyp = coeff * (-(effectiveRy * x1p) / effectiveRx)

        val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x) / 2f
        val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y) / 2f

        val ux = (x1p - cxp) / effectiveRx
        val uy = (y1p - cyp) / effectiveRy
        val vx = (-x1p - cxp) / effectiveRx
        val vy = (-y1p - cyp) / effectiveRy

        var theta1 = kotlin.math.atan2(uy, ux)
        var deltaTheta = kotlin.math.atan2(vy, vx) - theta1

        if (!sweepFlag && deltaTheta > 0f) deltaTheta -= (2f * Math.PI).toFloat()
        if (sweepFlag && deltaTheta < 0f) deltaTheta += (2f * Math.PI).toFloat()

        val segments = kotlin.math.ceil(kotlin.math.abs(deltaTheta) / (Math.PI / 2.0).toFloat()).toInt().coerceAtLeast(1)
        val segAngle = deltaTheta / segments
        val kappa = (4f / 3f) * kotlin.math.tan(segAngle / 4f)

        var currentAngle = theta1
        for (i in 0 until segments) {
            val sa = currentAngle
            val ea = currentAngle + segAngle
            val cosSa = kotlin.math.cos(sa); val sinSa = kotlin.math.sin(sa)
            val cosEa = kotlin.math.cos(ea); val sinEa = kotlin.math.sin(ea)

            val computedEx = cosPhi * effectiveRx * cosEa - sinPhi * effectiveRy * sinEa + cx
            val computedEy = sinPhi * effectiveRx * cosEa + cosPhi * effectiveRy * sinEa + cy

            val c1x = cosPhi * effectiveRx * (cosSa - kappa * sinSa) - sinPhi * effectiveRy * (sinSa + kappa * cosSa) + cx
            val c1y = sinPhi * effectiveRx * (cosSa - kappa * sinSa) + cosPhi * effectiveRy * (sinSa + kappa * cosSa) + cy
            val c2x = cosPhi * effectiveRx * (cosEa + kappa * sinEa) - sinPhi * effectiveRy * (sinEa - kappa * cosEa) + cx
            val c2y = sinPhi * effectiveRx * (cosEa + kappa * sinEa) + cosPhi * effectiveRy * (sinEa - kappa * cosEa) + cy

            val exactEnd = (i == segments - 1)
            val resultEx = if (exactEnd) x else computedEx
            val resultEy = if (exactEnd) y else computedEy

            verbs.add(PathVerb.CubicTo(c1 = Point(c1x, c1y), c2 = Point(c2x, c2y), p = Point(resultEx, resultEy)))

            currentAngle = ea
        }
    }

    fun close(): Path = apply {
        verbs.add(PathVerb.Close)
    }

    private fun currentX(): Float {
        for (i in verbs.indices.reversed()) {
            when (val v = verbs[i]) {
                is PathVerb.MoveTo -> return v.p.x
                is PathVerb.LineTo -> return v.p.x
                is PathVerb.QuadTo -> return v.p.x
                is PathVerb.CubicTo -> return v.p.x
                is PathVerb.Close -> {}
            }
        }
        return 0f
    }

    private fun currentY(): Float {
        for (i in verbs.indices.reversed()) {
            when (val v = verbs[i]) {
                is PathVerb.MoveTo -> return v.p.y
                is PathVerb.LineTo -> return v.p.y
                is PathVerb.QuadTo -> return v.p.y
                is PathVerb.CubicTo -> return v.p.y
                is PathVerb.Close -> {}
            }
        }
        return 0f
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

    fun transform(tx: Float, ty: Float, sx: Float, sy: Float): Path {
        val result = Path(fillType)
        for (verb in verbs) {
            when (verb) {
                is PathVerb.MoveTo -> result.moveTo(verb.p.x * sx + tx, verb.p.y * sy + ty)
                is PathVerb.LineTo -> result.lineTo(verb.p.x * sx + tx, verb.p.y * sy + ty)
                is PathVerb.QuadTo -> {
                    result.quadTo(verb.c.x * sx + tx, verb.c.y * sy + ty, verb.p.x * sx + tx, verb.p.y * sy + ty)
                }
                is PathVerb.CubicTo -> {
                    result.cubicTo(verb.c1.x * sx + tx, verb.c1.y * sy + ty, verb.c2.x * sx + tx, verb.c2.y * sy + ty, verb.p.x * sx + tx, verb.p.y * sy + ty)
                }
                is PathVerb.Close -> result.close()
            }
        }
        return result
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
